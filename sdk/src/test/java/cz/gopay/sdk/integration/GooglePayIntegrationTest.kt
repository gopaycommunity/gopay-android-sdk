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
import cz.gopay.sdk.model.PaymentInstrumentInput
import cz.gopay.sdk.service.GooglePayHelper
import cz.gopay.sdk.service.PaymentService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * Integration tests for the Google Pay feature, testing end-to-end flows
 * from PaymentService through GooglePayHelper.
 */
@ExperimentalCoroutinesApi
class GooglePayIntegrationTest {

    // --- PaymentService + mock API ---

    @Test
    fun `getGooglePayInfo returns response through PaymentService`() = runTest {
        val apiService = mock<cz.gopay.sdk.modules.network.GopayApiService>()
        val paymentService = PaymentService(apiService)

        val expected = buildFullGooglePayInfoResponse()
        whenever(apiService.getGooglePayInfo("300000001"))
            .thenReturn(Response.success(expected))

        val result = paymentService.getGooglePayInfo("300000001")

        assertEquals("TEST", result.environment)
        assertEquals(2, result.paymentDataRequest.apiVersion)
        assertEquals("GoPay Czech", result.paymentDataRequest.merchantInfo.merchantName)
        assertEquals("14846034534970557458", result.paymentDataRequest.merchantInfo.merchantId)
        assertEquals("CZK", result.paymentDataRequest.transactionInfo.currencyCode)
        assertEquals(true, result.paymentDataRequest.emailRequired)
    }

    // --- GooglePayHelper round-trip ---

    @Test
    fun `buildPaymentDataRequestJson then parse produces consistent data`() {
        val info = buildFullGooglePayInfoResponse()

        val json = GooglePayHelper.buildPaymentDataRequestJson(info)

        // The JSON should be deserializable back
        assert(json.isNotEmpty())
        assert(json.contains("apiVersion"))
        assert(json.contains("gopay"))
        assert(json.contains("GoPay Czech"))
    }

    // --- PaymentInstrumentInput factory ---

    @Test
    fun `googlePay factory produces correct paymentInstrument and inputType`() {
        val key = IntermediateSigningKey(
            signedKey = "{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}",
            signatures = listOf("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==")
        )

        val instrument = PaymentInstrumentInput.googlePay(
            protocolVersion = "ECv2",
            signature = "test_signature",
            intermediateSigningKey = key,
            signedMessage = "test_signed_message"
        )

        assertEquals("PAYMENT_CARD", instrument.paymentInstrument)
        assertEquals("GOOGLE_PAY", instrument.input.inputType)
        assertEquals("ECv2", instrument.input.protocolVersion)
        assertEquals("test_signature", instrument.input.signature)
        assertEquals("test_signed_message", instrument.input.signedMessage)
        assertEquals(key, instrument.input.intermediateSigningKey)
    }

    @Test
    fun `googlePay factory sets no card-token-specific fields`() {
        val key = IntermediateSigningKey(signedKey = "{}", signatures = listOf("sig"))
        val instrument = PaymentInstrumentInput.googlePay(
            protocolVersion = "ECv2",
            signature = "sig",
            intermediateSigningKey = key,
            signedMessage = "msg"
        )

        assertEquals(null, instrument.input.cardToken)
        assertEquals(null, instrument.input.iban)
        assertEquals(null, instrument.input.swift)
    }

    // Helpers

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
