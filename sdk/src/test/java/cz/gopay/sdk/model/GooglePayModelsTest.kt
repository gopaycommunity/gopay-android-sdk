package cz.gopay.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GooglePayModelsTest {

    @Test
    fun `GooglePayInfoResponse constructs correctly`() {
        val info = GooglePayInfoResponse(
            environment = "TEST",
            paymentDataRequest = buildTestDataRequest()
        )

        assertEquals("TEST", info.environment)
        assertEquals(2, info.paymentDataRequest.apiVersion)
        assertEquals("GoPay Czech", info.paymentDataRequest.merchantInfo.merchantName)
    }

    @Test
    fun `GooglePayDataRequest emailRequired defaults to null`() {
        val request = GooglePayDataRequest(
            apiVersion = 2,
            apiVersionMinor = 0,
            allowedPaymentMethods = emptyList(),
            transactionInfo = buildTransactionInfo(),
            merchantInfo = buildMerchantInfo()
        )

        assertNull(request.emailRequired)
    }

    @Test
    fun `GooglePayDataRequest emailRequired can be set`() {
        val request = GooglePayDataRequest(
            apiVersion = 2,
            apiVersionMinor = 0,
            allowedPaymentMethods = emptyList(),
            transactionInfo = buildTransactionInfo(),
            merchantInfo = buildMerchantInfo(),
            emailRequired = true
        )

        assertEquals(true, request.emailRequired)
    }

    @Test
    fun `GooglePayAllowedMethod constructs correctly`() {
        val method = GooglePayAllowedMethod(
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

        assertEquals("CARD", method.type)
        assertEquals(listOf("PAN_ONLY", "CRYPTOGRAM_3DS"), method.parameters.allowedAuthMethods)
        assertEquals("gopay", method.tokenizationSpecification.parameters.gateway)
    }

    @Test
    fun `GooglePayInfoResponse data class equality`() {
        val a = GooglePayInfoResponse("TEST", buildTestDataRequest())
        val b = GooglePayInfoResponse("TEST", buildTestDataRequest())

        assertEquals(a, b)
    }

    @Test
    fun `IntermediateSigningKey constructs correctly`() {
        val key = IntermediateSigningKey(
            signedKey = "{\"keyExpiration\":\"1542323393147\",\"keyValue\":\"MFkw...\"}",
            signatures = listOf("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==")
        )

        assertEquals(1, key.signatures.size)
        assertEquals("MEYCIQCO2EIi48s8VTH+ilMEpoXLFfkxAw==", key.signatures[0])
    }

    // Helpers

    private fun buildTransactionInfo() = GooglePayTransactionInfo(
        currencyCode = "CZK",
        countryCode = "CZ",
        totalPriceStatus = "FINAL",
        totalPrice = "5.00"
    )

    private fun buildMerchantInfo() = GooglePayMerchantInfo(
        merchantName = "GoPay Czech",
        merchantId = "14846034534970557458"
    )

    private fun buildTestDataRequest() = GooglePayDataRequest(
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
        transactionInfo = buildTransactionInfo(),
        merchantInfo = buildMerchantInfo(),
        emailRequired = true
    )
}
