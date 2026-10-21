package cz.gopay.sdk.service

import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayGatewayParameters
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayMethodParameters
import cz.gopay.sdk.model.GooglePayTokenizationSpec
import cz.gopay.sdk.model.GooglePayTransactionInfo
import com.google.android.gms.wallet.WalletConstants
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePayHelperTest {

    // --- buildPaymentDataRequestJson ---

    @Test
    fun `buildPaymentDataRequestJson produces JSON with correct apiVersion`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        assertTrue("Expected apiVersion in JSON", json.contains("\"apiVersion\":2"))
        assertTrue("Expected apiVersionMinor in JSON", json.contains("\"apiVersionMinor\":0"))
    }

    @Test
    fun `buildPaymentDataRequestJson includes merchantInfo`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        assertTrue("Expected merchantName in JSON", json.contains("GoPay Czech"))
        assertTrue("Expected merchantId in JSON", json.contains("14846034534970557458"))
    }

    @Test
    fun `buildPaymentDataRequestJson includes transactionInfo`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        assertTrue("Expected currencyCode in JSON", json.contains("CZK"))
        assertTrue("Expected totalPrice in JSON", json.contains("5.00"))
    }

    @Test
    fun `buildPaymentDataRequestJson includes allowedPaymentMethods`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        assertTrue("Expected CARD type", json.contains("CARD"))
        assertTrue("Expected gateway", json.contains("gopay"))
        assertTrue("Expected PAN_ONLY", json.contains("PAN_ONLY"))
    }

    // --- parseGooglePayToken ---

    @Test
    fun `parseGooglePayToken returns correct PaymentCardInput for valid JSON`() {
        val paymentDataJson = buildValidPaymentDataJson()
        val input = GooglePayHelper.parseGooglePayToken(paymentDataJson)

        assertEquals("GOOGLE_PAY", input.inputType)
        assertEquals("ECv2", input.protocolVersion)
        assertEquals("test_signature", input.signature)
        assertEquals("test_signed_message", input.signedMessage)
    }

    @Test
    fun `parseGooglePayToken parses intermediateSigningKey correctly`() {
        val paymentDataJson = buildValidPaymentDataJson()
        val input = GooglePayHelper.parseGooglePayToken(paymentDataJson)

        val key = input.intermediateSigningKey
        assertEquals("{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}", key?.signedKey)
        assertEquals(1, key?.signatures?.size)
        assertEquals("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==", key?.signatures?.get(0))
    }

    /** A token the SDK cannot read is PAYMENT_003, as an unreadable Apple Pay token is on iOS. */
    private fun assertUnreadable(json: String): GopaySDKException {
        val e = assertThrows(GopaySDKException::class.java) { GooglePayHelper.parseGooglePayToken(json) }
        assertEquals(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, e.errorCode)
        assertTrue(e.message!!.startsWith("Google Pay failed: "))
        return e
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 on malformed JSON, with the parse failure as cause`() {
        val reported = mutableListOf<GopaySDKException>()
        ErrorReporter.setErrorCallback { reported.add(it) }
        try {
            val e = assertUnreadable("not valid json at all {{{")
            assertNotNull(e.cause)
            assertEquals("a public helper, so it reports its own error once", listOf(e), reported)
        } finally {
            ErrorReporter.clear()
        }
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when paymentMethodData is missing`() {
        assertUnreadable("""{"apiVersion":2}""")
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when token field is missing`() {
        val json = """
            {
              "paymentMethodData": {
                "tokenizationData": {}
              }
            }
        """.trimIndent()
        assertUnreadable(json)
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when nested token JSON is missing protocolVersion`() {
        assertUnreadable(buildPaymentDataJsonWithToken("""{"signature":"sig","signedMessage":"msg"}"""))
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when nested token JSON is missing signature`() {
        assertUnreadable(buildPaymentDataJsonWithToken("""{"protocolVersion":"ECv2","signedMessage":"msg"}"""))
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when the nested token is not JSON`() {
        assertUnreadable(buildPaymentDataJsonWithToken("{not json"))
    }

    @Test
    fun `parseGooglePayToken throws PAYMENT_003 when the nested token is JSON null`() {
        val e = assertUnreadable(buildPaymentDataJsonWithToken("null"))
        assertNull(e.cause)
    }

    // --- buildIsReadyToPayRequestJson ---

    @Test
    fun `buildIsReadyToPayRequestJson includes apiVersion and allowedPaymentMethods`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildIsReadyToPayRequestJson(
            info.paymentDataRequest.allowedPaymentMethods
        )

        assertTrue(json.contains("\"apiVersion\":2"))
        assertTrue(json.contains("\"apiVersionMinor\":0"))
        assertTrue(json.contains("allowedPaymentMethods"))
        assertTrue(json.contains("CARD"))
        assertTrue(json.contains("PAN_ONLY"))
        assertTrue(json.contains("VISA"))
    }

    @Test
    fun `buildIsReadyToPayRequestJson does not include gateway parameters`() {
        val info = buildTestInfo()
        val json = GooglePayHelper.buildIsReadyToPayRequestJson(
            info.paymentDataRequest.allowedPaymentMethods
        )

        assertFalse("Should not include gateway", json.contains("gateway"))
        assertFalse("Should not include gatewayMerchantId", json.contains("gatewayMerchantId"))
        assertFalse("Should not include tokenizationSpecification", json.contains("tokenizationSpecification"))
    }

    // --- isWalletAvailable without info ---

    @Test
    fun `without info the readiness request asks for PAN_ONLY and CRYPTOGRAM_3DS with Visa and Mastercard`() {
        val json = GooglePayHelper.isReadyToPayRequestJson(null)

        assertEquals(
            """{"apiVersion":2,"apiVersionMinor":0,"allowedPaymentMethods":[{"type":"CARD","parameters":""" +
                """{"allowedAuthMethods":["PAN_ONLY","CRYPTOGRAM_3DS"],"allowedCardNetworks":["VISA","MASTERCARD"]}}]}""",
            json
        )
    }

    @Test
    fun `with info the readiness request uses the gateway's methods`() {
        val info = buildTestInfo()

        assertEquals(
            GooglePayHelper.buildIsReadyToPayRequestJson(info.paymentDataRequest.allowedPaymentMethods),
            GooglePayHelper.isReadyToPayRequestJson(info)
        )
    }

    @Test
    fun `the wallet environment follows the info, else the SDK's environment`() {
        val test = buildTestInfo()
        val production = test.copy(environment = "PRODUCTION")

        assertEquals(WalletConstants.ENVIRONMENT_TEST, GooglePayHelper.walletEnvironment(test, Environment.PRODUCTION))
        assertEquals(WalletConstants.ENVIRONMENT_PRODUCTION, GooglePayHelper.walletEnvironment(production, Environment.SANDBOX))
        assertEquals(WalletConstants.ENVIRONMENT_PRODUCTION, GooglePayHelper.walletEnvironment(null, Environment.PRODUCTION))
        assertEquals(WalletConstants.ENVIRONMENT_TEST, GooglePayHelper.walletEnvironment(null, Environment.SANDBOX))
        assertEquals(
            WalletConstants.ENVIRONMENT_TEST,
            GooglePayHelper.walletEnvironment(null, Environment.DEVELOPMENT.create("https://gw.example/"))
        )
    }

    // Helpers

    private fun buildTestInfo() = GooglePayInfoResponse(
        environment = "TEST",
        paymentDataRequest = GooglePayDataRequest(
            apiVersion = 2,
            apiVersionMinor = 0,
            allowedPaymentMethods = listOf(
                GooglePayAllowedMethod(
                    type = "CARD",
                    parameters = GooglePayMethodParameters(
                        allowedAuthMethods = listOf("PAN_ONLY", "CRYPTOGRAM_3DS"),
                        allowedCardNetworks = listOf("VISA", "MASTERCARD")
                    ),
                    tokenizationSpecification = GooglePayTokenizationSpec(
                        type = "PAYMENT_GATEWAY",
                        parameters = GooglePayGatewayParameters(
                            gateway = "gopay",
                            gatewayMerchantId = "26046768005768011132"
                        )
                    )
                )
            ),
            transactionInfo = GooglePayTransactionInfo(
                currencyCode = "CZK",
                countryCode = "CZ",
                totalPriceStatus = "FINAL",
                totalPrice = "5.00"
            ),
            merchantInfo = GooglePayMerchantInfo(
                merchantName = "GoPay Czech",
                merchantId = "14846034534970557458"
            ),
            emailRequired = true
        )
    )

    private fun buildValidPaymentDataJson(): String {
        val tokenContent = """
            {
              "protocolVersion": "ECv2",
              "signature": "test_signature",
              "intermediateSigningKey": {
                "signedKey": "{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}",
                "signatures": ["MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw=="]
              },
              "signedMessage": "test_signed_message"
            }
        """.trimIndent()

        // The token field value is a JSON-encoded string (escaped)
        val escapedToken = tokenContent
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("  ", " ")

        return """
            {
              "apiVersion": 2,
              "apiVersionMinor": 0,
              "paymentMethodData": {
                "type": "CARD",
                "tokenizationData": {
                  "type": "PAYMENT_GATEWAY",
                  "token": "$escapedToken"
                }
              }
            }
        """.trimIndent()
    }

    private fun buildPaymentDataJsonWithToken(tokenJson: String): String {
        val escaped = tokenJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return """
            {
              "paymentMethodData": {
                "tokenizationData": {
                  "token": "$escaped"
                }
              }
            }
        """.trimIndent()
    }
}
