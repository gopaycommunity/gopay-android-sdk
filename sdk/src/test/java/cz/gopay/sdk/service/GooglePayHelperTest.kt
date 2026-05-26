package cz.gopay.sdk.service

import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayGatewayParameters
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayMethodParameters
import cz.gopay.sdk.model.GooglePayTokenizationSpec
import cz.gopay.sdk.model.GooglePayTransactionInfo
import org.junit.Assert.assertEquals
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
    fun `parseGooglePayToken returns correct PaymentInstrumentInput for valid JSON`() {
        val paymentDataJson = buildValidPaymentDataJson()
        val instrument = GooglePayHelper.parseGooglePayToken(paymentDataJson)

        assertEquals("PAYMENT_CARD", instrument.paymentInstrument)
        assertEquals("GOOGLE_PAY", instrument.input.inputType)
        assertEquals("ECv2", instrument.input.protocolVersion)
        assertEquals("test_signature", instrument.input.signature)
        assertEquals("test_signed_message", instrument.input.signedMessage)
    }

    @Test
    fun `parseGooglePayToken parses intermediateSigningKey correctly`() {
        val paymentDataJson = buildValidPaymentDataJson()
        val instrument = GooglePayHelper.parseGooglePayToken(paymentDataJson)

        val key = instrument.input.intermediateSigningKey
        assertEquals("{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}", key?.signedKey)
        assertEquals(1, key?.signatures?.size)
        assertEquals("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==", key?.signatures?.get(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseGooglePayToken throws on malformed JSON`() {
        GooglePayHelper.parseGooglePayToken("not valid json at all {{{")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseGooglePayToken throws when paymentMethodData is missing`() {
        GooglePayHelper.parseGooglePayToken("""{"apiVersion":2}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseGooglePayToken throws when token field is missing`() {
        val json = """
            {
              "paymentMethodData": {
                "tokenizationData": {}
              }
            }
        """.trimIndent()
        GooglePayHelper.parseGooglePayToken(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseGooglePayToken throws when nested token JSON is missing protocolVersion`() {
        val tokenJson = """{"signature":"sig","signedMessage":"msg"}"""
        val json = buildPaymentDataJsonWithToken(tokenJson)
        GooglePayHelper.parseGooglePayToken(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseGooglePayToken throws when nested token JSON is missing signature`() {
        val tokenJson = """{"protocolVersion":"ECv2","signedMessage":"msg"}"""
        val json = buildPaymentDataJsonWithToken(tokenJson)
        GooglePayHelper.parseGooglePayToken(json)
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
