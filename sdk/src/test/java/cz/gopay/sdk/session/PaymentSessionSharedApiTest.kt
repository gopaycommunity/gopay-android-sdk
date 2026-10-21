package cz.gopay.sdk.session

import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.CardFormUrl
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.GooglePayAllowedMethod
import cz.gopay.sdk.model.GooglePayDataRequest
import cz.gopay.sdk.model.GooglePayGatewayParameters
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GooglePayMerchantInfo
import cz.gopay.sdk.model.GooglePayMethodParameters
import cz.gopay.sdk.model.GooglePayTokenizationSpec
import cz.gopay.sdk.model.GooglePayTransactionInfo
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.TokenResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.Response

/**
 * The session API shared with the iOS SDK: the card helpers without an activity, and the
 * wallet names. An alias has one body, so one test per alias that it answers the same.
 */
class PaymentSessionSharedApiTest {

    /** Complete, so the charge asks the gateway for nothing. */
    private val browserData = BrowserData(
        language = "cs-CZ", timezone = -60, screenWidth = 1080, screenHeight = 2400, colorDepth = 24,
        userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36", ip = "192.0.2.42", acceptHeader = "*/*"
    )

    @Test
    fun `chargeWithCardToken without an activity sends the token with the browser data given`() = runBlocking {
        val api = Fakes()

        session(api).chargeWithCardToken("tok-1", browserData, ChallengePreference.AUTO)

        val sent = api.charges.single()
        assertEquals("CARD_TOKEN", sent.paymentInstrument.input.inputType)
        assertEquals("tok-1", sent.paymentInstrument.input.cardToken)
        assertEquals("192.0.2.42", sent.paymentInstrument.browserData.ip)
        assertEquals(ChallengePreference.AUTO, sent.paymentInstrument.challengePreference)
    }

    @Test
    fun `chargeWithEncryptedCard without an activity sends the JWE with the browser data given`() = runBlocking {
        val api = Fakes()

        session(api).chargeWithEncryptedCard("h.k.i.c.t", browserData)

        val sent = api.charges.single()
        assertEquals("ENCRYPTED_CARD", sent.paymentInstrument.input.inputType)
        assertEquals("h.k.i.c.t", sent.paymentInstrument.input.payload)
        assertEquals("cs-CZ", sent.paymentInstrument.browserData.language)
    }

    @Test
    fun `getWalletInfo and getGooglePayInfo are the same call`() = runBlocking {
        val api = Fakes()
        val session = session(api)

        val wallet = session.getWalletInfo()
        val googlePay = session.getGooglePayInfo()

        assertSame(api.info, wallet)
        assertSame(api.info, googlePay)
        assertEquals(2, api.infoCalls)
    }

    private fun session(api: Fakes): PaymentSession = runBlocking {
        PaymentSession.Factory(
            authApi = api,
            paymentApiBuilder = { api },
            publicApi = { api.publicApi }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
    }

    private class Fakes : PaymentApi, AuthApi {
        val charges = mutableListOf<ChargePaymentRequest>()
        var infoCalls = 0
        val info = GooglePayInfoResponse(
            environment = "TEST",
            paymentDataRequest = GooglePayDataRequest(
                apiVersion = 2, apiVersionMinor = 0,
                allowedPaymentMethods = listOf(
                    GooglePayAllowedMethod(
                        type = "CARD",
                        parameters = GooglePayMethodParameters(listOf("PAN_ONLY"), listOf("VISA")),
                        tokenizationSpecification = GooglePayTokenizationSpec(
                            "PAYMENT_GATEWAY", GooglePayGatewayParameters("gopay", "1")
                        )
                    )
                ),
                transactionInfo = GooglePayTransactionInfo("CZK", "CZ", "FINAL", "1.00"),
                merchantInfo = GooglePayMerchantInfo("Shop")
            )
        )

        val publicApi: PublicApi = object : PublicApi {
            override suspend fun getBrowserData(userAgent: String?): Response<BrowserDataDetected> {
                return Response.success(BrowserDataDetected(ip = "198.51.100.7", userAgent = "ua", acceptHeader = "*/*"))
            }
            override suspend fun getPublicKey(): Response<Jwk> = throw UnsupportedOperationException()
            override suspend fun getCardFormUrl(): Response<CardFormUrl> = throw UnsupportedOperationException()
        }

        override suspend fun token(authorization: String, grantType: String, scope: String?): Response<TokenResponse> =
            Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))

        override suspend fun chargePayment(paymentId: String, request: ChargePaymentRequest): Response<ChargePaymentResponse> {
            charges += request
            return Response.success(ChargePaymentResponse(id = "charge-1", state = ChargeState.PROCESSING))
        }

        override suspend fun getPaymentStatus(paymentId: String): Response<PaymentCreateResponse> =
            throw UnsupportedOperationException()

        override suspend fun getChargeState(paymentId: String): Response<ChargePaymentResponse> =
            throw UnsupportedOperationException()

        override suspend fun getQrPaymentInfo(paymentId: String, format: String?): Response<QrPaymentDetails> =
            throw UnsupportedOperationException()

        override suspend fun getGooglePayInfo(paymentId: String): Response<GooglePayInfoResponse> {
            infoCalls++
            return Response.success(info)
        }
    }
}
