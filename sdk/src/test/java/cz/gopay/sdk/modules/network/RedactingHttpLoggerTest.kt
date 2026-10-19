package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.IntermediateSigningKey
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug log must never carry what would let someone act on a payment: the token request's
 * `payment_id:payment_secret`, the JWT it returns, a card token, or the encrypted card and Google
 * Pay data GoPay says must not be logged.
 */
class RedactingHttpLoggerTest {

    private val json = "application/json".toMediaType()

    /** Sends [request] through the SDK's logging interceptor to a stub answering [responseBody]. */
    private fun logOf(request: Request, responseBody: String): String {
        val lines = mutableListOf<String>()
        val gateway = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody.toResponseBody(json))
                .build()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkModule.loggingInterceptor(RedactingHttpLogger { lines += it }))
            .addInterceptor(gateway)
            .build()
        client.newCall(request).execute().use { it.body?.string() }
        return lines.joinToString("\n")
    }

    @Test
    fun `the token request logs neither the payment credentials nor the JWT`() {
        val basic = "Basic cGF5bWVudC0xOnRvcC1zZWNyZXQ="
        val log = logOf(
            Request.Builder()
                .url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/oauth2/token")
                .header("Authorization", basic)
                .post("grant_type=payment_credentials".toRequestBody())
                .build(),
            """{"access_token":"eyJhbGciOi.payload.sig","token_type":"bearer","expires_in":900}"""
        )

        assertFalse(log, log.contains(basic.removePrefix("Basic ")))
        assertFalse(log, log.contains("eyJhbGciOi"))
        assertTrue(log, log.contains("Authorization: ██"))
        assertTrue(log, log.contains("\"access_token\":\"██\""))
        // The rest of the body is still there to debug with.
        assertTrue(log, log.contains("\"expires_in\":900"))
    }

    @Test
    fun `a payment status read logs the payment without its payment_secret`() {
        // Payment-Details as GET /payments/{payment_id} answers it; payment_secret is required there.
        val log = logOf(
            Request.Builder()
                .url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/3000000001")
                .build(),
            """{"id":3000000001,"order_number":"ord-1","state":"CREATED","amount":10000,""" +
                """"currency":"CZK","customer":{"email":"shopper@example.com"},""" +
                """"gw_url":"https://gw.sandbox.gopay.com/gw/v3/abc","payment_secret":"ps_abc123"}"""
        )

        assertFalse(log, log.contains("ps_abc123"))
        assertTrue(log, log.contains("\"payment_secret\":\"██\""))
        assertTrue(log, log.contains("\"id\":3000000001"))
        assertTrue(log, log.contains("\"order_number\":\"ord-1\""))
        assertTrue(log, log.contains("\"state\":\"CREATED\""))
        assertTrue(log, log.contains("\"amount\":10000"))
        assertTrue(log, log.contains("\"email\":\"shopper@example.com\""))
        assertTrue(log, log.contains("\"gw_url\":\"https://gw.sandbox.gopay.com/gw/v3/abc\""))
    }

    /** Sends [request] as the SDK sends a charge: serialized by its own Moshi, through the log. */
    private fun chargeLog(request: ChargePaymentRequest): String = logOf(
        Request.Builder()
            .url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/1/charge")
            .post(
                NetworkModule.moshi.adapter(ChargePaymentRequest::class.java).toJson(request)
                    .toRequestBody(json)
            )
            .build(),
        """{"id":"1","state":"PROCESSING"}"""
    )

    @Test
    fun `a card token charge logs the request without the token`() {
        val log = chargeLog(ChargePaymentRequest.cardToken("tok_123", BROWSER_DATA))

        assertFalse(log, log.contains("tok_123"))
        assertTrue(log, log.contains("\"card_token\":\"██\""))
        assertTrue(log, log.contains("\"input_type\":\"CARD_TOKEN\""))
        assertTrue(log, log.contains("\"state\":\"PROCESSING\""))
    }

    @Test
    fun `an encrypted card charge logs the request without the JWE`() {
        val log = chargeLog(ChargePaymentRequest.encryptedCard("eyJ.jwe.x", BROWSER_DATA))

        assertFalse(log, log.contains("eyJ.jwe.x"))
        assertTrue(log, log.contains("\"payload\":\"██\""))
        assertTrue(log, log.contains("\"input_type\":\"ENCRYPTED_CARD\""))
    }

    @Test
    fun `a Google Pay charge logs the request without the payment data`() {
        // signedMessage is itself JSON, so it goes out with escaped quotes.
        val log = chargeLog(
            ChargePaymentRequest.googlePay(
                protocolVersion = "ECv2",
                signature = "sig",
                intermediateSigningKey = IntermediateSigningKey("key", listOf("keysig")),
                signedMessage = """{"encryptedMessage":"abc","ephemeralPublicKey":"pk","tag":"t"}""",
                browserData = BROWSER_DATA
            )
        )

        assertFalse(log, log.contains("encryptedMessage"))
        assertFalse(log, log.contains("ephemeralPublicKey"))
        assertTrue(log, log.contains("\"signedMessage\":\"██\""))
        assertTrue(log, log.contains("\"input_type\":\"GOOGLE_PAY\""))
    }

    @Test
    fun `redact masks spaced and escaped values and leaves other fields alone`() {
        assertEquals(
            """{"access_token" : "██", "token_type":"bearer"}""",
            RedactingHttpLogger.redact("""{"access_token" : "a\"b", "token_type":"bearer"}""")
        )
        assertEquals(
            """{"input_type":"ENCRYPTED_CARD"}""",
            RedactingHttpLogger.redact("""{"input_type":"ENCRYPTED_CARD"}""")
        )
    }

    private companion object {
        val BROWSER_DATA = BrowserData(
            language = "cs-CZ",
            timezone = -60,
            screenWidth = 1080,
            screenHeight = 2400,
            colorDepth = 24,
            userAgent = "Mozilla/5.0",
            acceptHeader = "text/html",
            javascriptEnabled = true,
            ip = "192.0.2.1"
        )
    }
}
