package cz.gopay.sdk.telemetry

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.modules.network.transportError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The browser SDK's redaction cases, the same inputs and the same results: what a message and a
 * body may still carry once they leave the device.
 */
class RedactionTest {

    private fun scrub(message: String) = Redaction.safeErrorMessage(message)

    @Suppress("UNCHECKED_CAST")
    private fun body(raw: String): Any? = JsonText.parse(Redaction.serializeBody(raw)!!)

    @Suppress("UNCHECKED_CAST")
    private fun bodyMap(raw: String) = body(raw) as Map<String, Any?>

    // safeErrorMessage

    @Test
    fun `redacts what a message must never carry`() {
        val cases = listOf(
            Triple("an e-mail address", "failed for jan.novak@example.com", "novak@"),
            Triple(
                "a compact JOSE token in prose",
                "payload eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn rejected",
                "eyJhbGciOiJ"
            ),
            Triple(
                "a token in the fragment",
                "redirect to https://x.test/cb#access_token=abc123def456&state=z",
                "access_token=abc"
            ),
            Triple("anything else in the fragment", "redirect to https://x.test/cb#sid=SESSION-9f3a2b1c", "SESSION-9f3a2b1c"),
            Triple(
                "a token ending a sentence",
                "rejected payload eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn.",
                "eyJhbGciOiJ"
            ),
            Triple(
                "a three-segment JWS ending a sentence",
                "token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dBjftJeZ4CVPmB92K27u.",
                "eyJzdWIiOiI"
            ),
            Triple("3DS fields posted as a bare pair", "posted MD=abc123def&PaRes=eNrtWEuTo0YM", "MD=abc"),
            Triple("a payment secret written into the message", "paymentSecret=abc123 was wrong", "paymentSecret=abc")
        )
        for ((label, input, leak) in cases) {
            assertFalse("$label: ${scrub(input)}", scrub(input).contains(leak))
        }
    }

    @Test
    fun `keeps the full stop after a redacted token`() {
        assertEquals(
            "rejected payload [redacted].",
            scrub("rejected payload eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn.")
        )
    }

    @Test
    fun `scrubs a hostile message without stalling`() {
        for (run in listOf("a".repeat(40_000), "x@" + "a.".repeat(20_000))) {
            val started = System.nanoTime()
            scrub(run)
            val ms = (System.nanoTime() - started) / 1_000_000
            assertTrue("took $ms ms", ms < 1_000)
        }
    }

    @Test
    fun `finds an address however it is surrounded`() {
        assertEquals("to [redacted], cc [redacted].", scrub("to a.b@example.com, cc c@mail.example.cz."))
        assertEquals("@example.com is no address", scrub("@example.com is no address"))
        assertEquals("user@localhost stays", scrub("user@localhost stays"))
    }

    @Test
    fun `leaves an ordinary message alone`() {
        val message = "Request timed out after 10000 ms (v1.2.3, api 4.0)"
        assertEquals(message, scrub(message))
    }

    @Test
    fun `redacts a PAN written whole and in groups`() {
        assertEquals("card [redacted] declined", scrub("card 4111111111111111 declined"))
        assertEquals(
            "card [redacted] and [redacted] declined",
            scrub("card 4111 1111 1111 1111 and 5500-0000-0000-0004 declined")
        )
    }

    @Test
    fun `redacts a pair under any name ending in token or secret`() {
        assertEquals(
            "bad [redacted] and [redacted] or [redacted]",
            scrub("bad payment_token=abc123 and card_token=zzz999 or clientSecret=s3")
        )
    }

    @Test
    fun `keeps a short digit run that cannot be a PAN`() {
        assertEquals("status 404 on attempt 2", scrub("status 404 on attempt 2"))
    }

    @Test
    fun `drops a query string`() {
        assertEquals(
            "failed https://pay.example.com/r?[redacted]",
            scrub("failed https://pay.example.com/r?token=abc&email=a@b.cz")
        )
    }

    @Test
    fun `keeps the card-comm form type but drops the signed token`() {
        assertEquals("iframe /gp-card-comm/g/[redacted]", scrub("iframe /gp-card-comm/g/eyJhbGciOiJIUzI1"))
    }

    @Test
    fun `caps the length at 500`() {
        assertEquals(500, scrub("x".repeat(900)).length)
    }

    @Test
    fun `reads no message as Unknown error`() {
        assertEquals("Unknown error", Redaction.safeErrorMessage(null))
    }

    @Test
    fun `matches digits and spaces the way the browser does, not the way ICU does`() {
        // Arabic-Indic digits are digits to ICU's \d, not to the browser's.
        val arabic = "card ٤١١١١١١١١١١١١١١١ declined"
        assertEquals(arabic, scrub(arabic))
        // A no-break space ends a query string in the browser, so it must here too.
        assertEquals("see /r?[redacted] after", scrub("see /r?token=abc after"))
    }

    // the target of a 3DS redirect

    @Test
    fun `keeps origin and path of a redirect, nothing of its query`() {
        assertEquals(
            "https://ACS.example-bank.com/challenge/start",
            Redaction.safeTarget("https://ACS.example-bank.com:443/challenge/start?creq=SECRET#frag")
        )
        assertEquals("https://acs.example.com/", Redaction.safeTarget("https://acs.example.com"))
        assertEquals("http://localhost:8080/x", Redaction.safeTarget("http://localhost:8080/x"))
        assertNull(Redaction.safeTarget("not a url"))
    }

    // the SDK's own words

    @Test
    fun `cuts foreign text out of an SDK message`() {
        val error = transportError("charge payment", SocketTimeoutException("connect to 10.0.0.1 timed out"))
        assertEquals("Failed to charge payment: timeout", Redaction.sdkMessage(error))
        val dns = transportError("fetch browser data", UnknownHostException("gw.sandbox.gopay.com"))
        assertEquals("Failed to fetch browser data: dns", Redaction.sdkMessage(dns))
    }

    @Test
    fun `replaces foreign text only as a whole word of three characters or more`() {
        // "fail" inside "failed" and the one-letter message stay; the whole-word "fail" goes.
        val word = GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "Google Pay failed: fail", cause = Exception("fail"))
        assertEquals("Google Pay failed: error", Redaction.sdkMessage(word))
        val short = GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "Google Pay failed: a", cause = Exception("a"))
        assertEquals("Google Pay failed: a", Redaction.sdkMessage(short))
        // A class named Error must not rewrite the SDK's "Error" in "Errors".
        class Error : RuntimeException("Error")
        val named = GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "Errors while charging: Error", cause = Error())
        assertEquals("Errors while charging: error", Redaction.sdkMessage(named))
    }

    @Test
    fun `cuts the gateway's fail_reason out of a declined charge`() {
        val state = ChargePaymentResponse(id = "c1", state = ChargeState.FAILED, failReason = "Insufficient funds on 4111111111111111")
        val error = GopaySDKException(
            GopayErrorCodes.PAYMENT_DECLINED,
            "Charge failed: Insufficient funds on 4111111111111111",
            chargeState = state
        )
        assertEquals("Charge failed", Redaction.sdkMessage(error))
    }

    @Test
    fun `names a foreign failure by kind, never by a class name R8 can rename`() {
        assertEquals("timeout", Redaction.failureLabel(SocketTimeoutException("x")))
        assertEquals("timeout", Redaction.failureLabel(InterruptedIOException("timeout")))
        assertEquals("io", Redaction.failureLabel(InterruptedIOException("canceled")))
        assertEquals("dns", Redaction.failureLabel(UnknownHostException("x")))
        assertEquals("io", Redaction.failureLabel(IOException("x")))
        assertEquals("error", Redaction.failureLabel(IllegalStateException("x")))
        assertEquals("PAYMENT_003", Redaction.failureLabel(GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "x")))
    }

    @Test
    fun `keeps an SDK message that quotes nothing foreign`() {
        val error = GopaySDKException(GopayErrorCodes.VALIDATION_INVALID_INPUT, "CVV must be 3-4 digits")
        assertEquals("CVV must be 3-4 digits", Redaction.sdkMessage(error))
    }

    @Test
    fun `names a failure by code, by status, or by class`() {
        assertEquals("PAYMENT_003", Redaction.describeFailure(GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "x")))
        assertEquals(
            "HTTP_409",
            Redaction.describeFailure(
                GopaySDKException("not a code", "x", httpContext = HttpErrorContext(statusCode = 409))
            )
        )
        assertEquals("error", Redaction.describeFailure(IllegalStateException("card 4111111111111111")))
    }

    // serializeBody

    private val pan = "4111111111111111"
    private val jwe = "eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn"
    private val secrets = mapOf(
        "paymentSecret" to "ps-SECRET-AAA",
        "card_token" to "ct-SECRET-BBB",
        "cvv" to "123",
        "email" to "jana@example.com",
        "phone_number" to "+420777123456",
        "first_name" to "Jana",
        "ip" to "198.51.100.23",
        "user_agent" to "Mozilla/5.0 (Shopper)",
        "redirect_url" to "https://acs.example-bank.com/c?creq=SECRETCREQ",
        "gw_url" to "https://gate.gopay.cz/gw/v3/SECRETGW",
        "access_token" to "at-SECRET-FFF"
    )

    @Test
    fun `never sends a secret, a JWE, a card token, PAN, CVV or PII in a body`() {
        val secretFields = secrets.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }
        val request = """
            {"payment_instrument":"PAYMENT_CARD",
             "input":{"input_type":"GOOGLE_PAY","data":"GOOGLE-ENCRYPTED-CARD-DATA","header":{"ephemeralPublicKey":"EPK-SECRET"}},
             "browser_data":{"ip":"198.51.100.23","user_agent":"Mozilla/5.0 (Shopper)","accept_header":"application/json"},
             "customer":{"email":"jana@example.com"},
             $secretFields,
             "note":"card $pan and $jwe"}
        """.trimIndent()
        val response = """
            {"state":"ACTION_REQUIRED","action":{"redirect_url":"https://acs.example-bank.com/c?creq=SECRETCREQ"},
             "payment_instrument":{"payment_instrument":"PAYMENT_CARD","details":{"masked_pan":"406821******1234",
               "expiration_month":"11","expiration_year":"93","scheme":"VISA","fingerprint":"73c8d0a48d91def89761bb"}},
             $secretFields}
        """.trimIndent()

        val wire = Redaction.serializeBody(request) + " " + Redaction.serializeBody(response)
        for (value in secrets.values + listOf("GOOGLE-ENCRYPTED-CARD-DATA", "EPK-SECRET", "SECRETCREQ", pan, "eyJhbGciOiJ", "73c8d0a48d91def89761bb")) {
            assertFalse("$value leaked: $wire", wire.contains(value))
        }

        val req = bodyMap(request)
        assertEquals("PAYMENT_CARD", req["payment_instrument"])
        assertEquals("[redacted]", req["input"])
        assertEquals("application/json", (req["browser_data"] as Map<*, *>)["accept_header"])
        assertEquals("[redacted]", req["paymentSecret"])
        assertEquals("card [redacted] and [redacted]", req["note"])
        val res = bodyMap(response)
        assertEquals("ACTION_REQUIRED", res["state"])
        assertEquals("[redacted]", (res["action"] as Map<*, *>)["redirect_url"])
        assertEquals(
            mapOf(
                "masked_pan" to "[redacted]",
                "expiration_month" to "[redacted]",
                "expiration_year" to "[redacted]",
                "scheme" to "VISA",
                "fingerprint" to "[redacted]"
            ),
            ((res["payment_instrument"] as Map<*, *>)["details"])
        )
    }

    @Test
    fun `ships an ordinary body as it is`() {
        val raw = """{"error":"PAYMENT_STATE_CONFLICT","state":"CANCELED","amount":1000,"ratio":1.50,"ok":true,"none":null}"""
        assertEquals(
            """{"error":"PAYMENT_STATE_CONFLICT","state":"CANCELED","amount":1000,"ratio":1.5,"ok":true,"none":null}""",
            Redaction.serializeBody(raw)
        )
    }

    @Test
    fun `cuts a long body at 2000 characters`() {
        val raw = "{\"items\":[" + List(400) { "\"item\"" }.joinToString(",") + "]}"
        assertEquals(2000, Redaction.serializeBody(raw)!!.length)
    }

    @Test
    fun `matches snake_case and camelCase keys alike`() {
        assertEquals(
            mapOf(
                "redirect_url" to "[redacted]",
                "redirectUrl" to "[redacted]",
                "CARD_NUMBER" to "[redacted]",
                "state" to "PAID"
            ),
            body("""{"redirect_url":"x","redirectUrl":"x","CARD_NUMBER":"x","state":"PAID"}""")
        )
    }

    @Test
    fun `redacts JSON that arrived as text by key`() {
        assertEquals(
            mapOf("payment_secret" to "[redacted]", "detail" to "x"),
            body("""{"wrapped":"{\"payment_secret\":\"S3cr3tV4lueXYZ\",\"detail\":\"x\"}"}""").let { (it as Map<*, *>)["wrapped"] }
        )
        assertEquals("\"not json, just text\"", Redaction.serializeBody("not json, just text"))
        assertEquals("\"grant_type=payment_credentials&scope=payment%3Aread\"", Redaction.serializeBody("grant_type=payment_credentials&scope=payment%3Aread"))
    }

    @Test
    fun `drops what is nested deeper than four levels`() {
        assertEquals(
            mapOf("a" to mapOf("b" to mapOf("c" to mapOf("d" to "[redacted]")))),
            body("""{"a":{"b":{"c":{"d":{"e":"deep"}}}}}""")
        )
        assertEquals(listOf(listOf(listOf(listOf("[redacted]")))), body("[[[[[1]]]]]"))
    }

    @Test
    fun `drops every key on the two lists`() {
        val byNameOnly = listOf(
            "response", "payment", "paymentData", "applePayPaymentData", "googlePaymentData", "card",
            "cards", "maskedCard", "maskedCards", "profile", "profiles", "srcProfile", "consumer",
            "consumerIdentity", "identity", "three_ds", "threeDSData", "challenge", "acs", "acsData",
            "credential", "credentials", "checkout", "checkoutResponse", "checkoutResponseSignature",
            "redirectParams", "merchantSession", "merchantSessionIdentifier", "validationData",
            "encryptedCard", "recognitionToken", "idToken", "idTokens", "digitalCardId",
            "recipientAddress", "accountName", "bic", "input", "customer", "contact", "payer", "ip",
            "ip_address", "user_agent", "masked_pan", "card_token", "jwe_payload", "cvv", "cvc",
            "online_cryptogram", "cavv", "payment_secret", "signature", "password", "identityValue",
            "cardNumber", "authentication_value", "otp", "email", "phone", "mobile", "first_name",
            "last_name", "account_number", "iban", "redirect_url", "maskedValidationChannel",
            "encrypted_payload", "signedMessage", "signedKey", "public_key", "nonce", "gw_url",
            "billing_address", "card_holder", "fingerprint", "expiration_month", "expiry",
            "exp_month", "exp_year", "security_code", "card_form_url"
        )
        // In batches, each body under the 2000-character cap.
        for (batch in byNameOnly.chunked(20)) {
            val raw = "{" + batch.joinToString(",") { "\"$it\":\"plain\"" } + ",\"state\":\"PAID\"}"
            val sanitized = bodyMap(raw)
            for (key in batch) assertEquals(key, "[redacted]", sanitized[key])
            assertEquals("PAID", sanitized["state"])
        }
    }

    @Test
    fun `drops the payee's account from the QR payment info, in every format`() {
        // The sandbox's answer to GET /payments/{id}/qr-payment/info, values shortened.
        val raw = """
            {"amount":100,"currency":"CZK",
             "recipient":{"name":"GoPay Czech","bank_account":{"local":{"prefix":"000000","account_number":"2600000001","bank_code":"2010","variable_symbol":"3309579338"},
               "international":{"bic":"FIOBCZPPXXX","iban":"CZ6520100000002600000001","reference":"3309579338"}}},
             "qr_code":{"spayd":"iVBORw0KGgoAAAANSUhEUgAAAMgAAADI","sepa":"BCD 002 1 SCT FIOBCZPPXXX","mnb_qr":"HCT 001","paybysquare":"0004A000"},
             "spayd":"SPD*1.0*ACC:CZ6520100000002600000001*AM:1.00","bank_account":{"iban":"x"},"pay_by_square":"x"}
        """.trimIndent()

        val sanitized = bodyMap(raw)
        assertEquals("[redacted]", sanitized["recipient"])
        assertEquals("[redacted]", sanitized["qr_code"])
        assertEquals("[redacted]", sanitized["spayd"])
        assertEquals("[redacted]", sanitized["bank_account"])
        assertEquals("[redacted]", sanitized["pay_by_square"])
        assertEquals("CZK", sanitized["currency"])
        val wire = Redaction.serializeBody(raw)!!
        for (leak in listOf("2600000001", "CZ65", "FIOBCZPP", "3309579338", "iVBOR")) assertFalse("$leak in $wire", wire.contains(leak))
    }

    @Test
    fun `reports a form as its fields, through the same key rules`() {
        assertEquals(
            """{"grant_type":"payment_credentials","scope":"payment:read","client_secret":"[redacted]"}""",
            Redaction.serializeBody(linkedMapOf("grant_type" to "payment_credentials", "scope" to "payment:read", "client_secret" to "s3"))
        )
    }

    @Test
    fun `keeps no body for none`() {
        assertNull(Redaction.serializeBody(null))
        assertNull(Redaction.serializeBody(""))
    }

    // params

    @Test
    fun `writes params as an object, scrubbed, without nulls`() {
        assertEquals(
            """{"caller":"isWalletAvailable","available":false,"note":"card [redacted]"}""",
            Redaction.describeParams(mapOf("caller" to "isWalletAvailable", "available" to false, "note" to "card $pan", "skip" to null))
        )
        assertNull(Redaction.describeParams(mapOf("skip" to null)))
    }

    @Test
    fun `writes a params value of any other type as its scrubbed text`() {
        assertEquals("""{"n":"4.5","list":"[a, b]"}""", Redaction.describeParams(mapOf("n" to 4.5, "list" to listOf("a", "b"))))
    }

    @Test
    fun `reports an over-long params object by its size`() {
        val fields = (1..20).associate { "k$it" to "v".repeat(400) }
        assertTrue(Redaction.describeParams(fields)!!.startsWith("{\"truncated\":"))
    }
}
