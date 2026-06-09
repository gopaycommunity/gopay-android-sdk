package cz.gopay.sdk.integration

import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayGatewayParameters
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayMethodParameters
import cz.gopay.sdk.model.GooglePayTokenizationSpec
import cz.gopay.sdk.model.GooglePayTransactionInfo
import cz.gopay.sdk.model.IntermediateSigningKey
import cz.gopay.sdk.model.PaymentCardInput
import cz.gopay.sdk.service.GooglePayHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Integration tests covering Google Pay JSON shaping and the PaymentCardInput factory.
 *
 * The PaymentService-based flow that previously sat in this file is now exercised through
 * [cz.gopay.sdk.session.PaymentSession.chargeWithGooglePay] and lives in PaymentSession's own
 * tests (added in a follow-up).
 */
@ExperimentalCoroutinesApi
class GooglePayIntegrationTest {

    @Test
    fun `buildPaymentDataRequestJson then parse produces consistent data`() {
        val info = buildFullGooglePayInfoResponse()

        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        assert(json.isNotEmpty())
        assert(json.contains("apiVersion"))
        assert(json.contains("gopay"))
        assert(json.contains("GoPay Czech"))
    }

    @Test
    fun `googlePay factory produces correct inputType and fields`() {
        val key = IntermediateSigningKey(
            signedKey = "{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}",
            signatures = listOf("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==")
        )

        val input = PaymentCardInput.googlePay(
            protocolVersion = "ECv2",
            signature = "test_signature",
            intermediateSigningKey = key,
            signedMessage = "test_signed_message"
        )

        assertEquals("GOOGLE_PAY", input.inputType)
        assertEquals("ECv2", input.protocolVersion)
        assertEquals("test_signature", input.signature)
        assertEquals("test_signed_message", input.signedMessage)
        assertEquals(key, input.intermediateSigningKey)
    }

    @Test
    fun `googlePay factory sets no card-token-specific fields`() {
        val key = IntermediateSigningKey(signedKey = "{}", signatures = listOf("sig"))
        val input = PaymentCardInput.googlePay(
            protocolVersion = "ECv2",
            signature = "sig",
            intermediateSigningKey = key,
            signedMessage = "msg"
        )

        assertEquals(null, input.cardToken)
        assertEquals(null, input.data)
        assertEquals(null, input.version)
        assertEquals(null, input.header)
    }

    private fun buildFullGooglePayInfoResponse() = GooglePayInfoResponse(
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
}
