package cz.gopay.sdk.session

import android.app.Activity
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.reportErrors
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.CardFormUrl
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.TokenResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import retrofit2.Response

/**
 * The `errorCallback` rule on the session: every error a session method throws reaches the
 * callback once, as the error the host gets, and nothing else does. Before 2.2 the constructor
 * reported, so an error wrapped into another on the way out, the gateway's 401 behind
 * `AUTH_010`, reached the callback twice, and a session method's error reached it even when
 * the SDK did not throw it.
 */
class PaymentSessionErrorCallbackTest {

    private val received = mutableListOf<GopaySDKException>()

    private val deviceData = BrowserData(
        language = "cs-CZ", timezone = -60, screenWidth = 1080, screenHeight = 2400,
        colorDepth = 24, userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    )

    @Before
    fun collect() = ErrorReporter.setErrorCallback { received.add(it) }

    @After
    fun clear() = ErrorReporter.clear()

    @Test
    fun `the gateway turning the credentials down reaches the callback once, as AUTH_010`() {
        val api = Fakes(token = { Response.error(401, "{}".toResponseBody(JSON)) })

        // Factory.create runs inside startPaymentSession's reporting boundary; the eager auth
        // itself reports nothing, so the boundary is stood in for here.
        val e = assertThrows(GopaySDKException::class.java) { runBlocking { reportErrors { open(api) } } }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_CREDENTIALS_INVALID, e.errorCode)
        // The 401 behind it is the cause, not a second report.
        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, (e.cause as GopaySDKException).errorCode)
        assertEquals(listOf(e), received)
    }

    @Test
    fun `a charge whose browser data cannot be fetched reaches the callback once, as the charge error`() {
        val api = Fakes(browserData = { Response.error(403, "{}".toResponseBody(JSON)) })
        val session = runBlocking { open(api) }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        assertTrue(e.message!!.startsWith("Failed to charge payment: browser data could not be fetched"))
        assertEquals("the fetch's own error is the cause, not a report", listOf(e), received)
    }

    @Test
    fun `a wrapper calling charge reports its error once, not once per layer`() {
        val api = Fakes(browserData = { Response.error(500, "{}".toResponseBody(JSON)) })
        val session = runBlocking { open(api) }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.chargeWithCardToken(mock<Activity>(), "tok", browserData = deviceData) }
        }

        assertEquals(GopayErrorCodes.NETWORK_SERVER_ERROR, e.errorCode)
        assertEquals(listOf(e), received)
    }

    @Test
    fun `a closed session reports AUTH_013 once`() {
        val session = runBlocking { open(Fakes()) }
        session.close()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.errorCode)
        assertEquals(listOf(e), received)
    }

    @Test
    fun `an error the host built is not reported when the SDK throws its own`() {
        val session = runBlocking { open(Fakes()) }
        session.close()
        val hostsOwn = GopaySDKException("AUTH_013", "the host's")

        runCatching { runBlocking { session.charge(ChargePaymentRequest.cardToken("tok", deviceData)) } }

        assertEquals(1, received.size)
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, received.single().errorCode)
        assertTrue(received.none { it === hostsOwn })
    }

    @Test
    fun `a cancelled call reports nothing`() {
        val cancel = CancellationException("host left")
        val api = Fakes(token = { throw cancel })

        val e = assertThrows(CancellationException::class.java) { runBlocking { open(api) } }

        assertSame(cancel, e)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `awaitChargeState reports its option error once and never PAYMENT_007`() {
        val api = Fakes(chargeState = {
            Response.success(ChargePaymentResponse(id = "c1", state = ChargeState.PROCESSING))
        })
        val session = runBlocking { open(api) }

        val bad = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.awaitChargeState(AwaitChargeOptions(intervalMs = 0, threeDSMode = ThreeDSMode.MANUAL)) }
        }
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, bad.errorCode)
        assertEquals(listOf(bad), received)

        val timeout = assertThrows(GopaySDKException::class.java) {
            runBlocking {
                session.awaitChargeState(
                    AwaitChargeOptions(intervalMs = 1, initialTimeoutMs = 1, threeDSMode = ThreeDSMode.MANUAL)
                )
            }
        }
        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, timeout.errorCode)
        assertEquals("an undecided charge is not an error", listOf(bad), received)
    }

    @Test
    fun `a read that fails inside awaitChargeState is reported once`() {
        val api = Fakes(chargeState = { Response.error(404, "{}".toResponseBody(JSON)) })
        val session = runBlocking { open(api) }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.awaitChargeState(AwaitChargeOptions(threeDSMode = ThreeDSMode.MANUAL)) }
        }

        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        assertEquals("getChargeState and awaitChargeState are both entry points", listOf(e), received)
    }

    private suspend fun open(api: Fakes): PaymentSession = PaymentSession.Factory(
        authApi = api,
        paymentApiBuilder = { api },
        publicApi = { api.publicApi }
    ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}

    private class Fakes(
        private val token: () -> Response<TokenResponse> = {
            Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
        },
        private val browserData: () -> Response<BrowserDataDetected> = {
            Response.success(BrowserDataDetected(ip = "192.0.2.42", userAgent = "ua", acceptHeader = "*/*"))
        },
        private val chargeState: () -> Response<ChargePaymentResponse> = { throw UnsupportedOperationException() }
    ) : PaymentApi, AuthApi {
        val publicApi: PublicApi = object : PublicApi {
            override suspend fun getBrowserData(userAgent: String?): Response<BrowserDataDetected> = browserData()
            override suspend fun getPublicKey(): Response<Jwk> = throw UnsupportedOperationException()
            override suspend fun getCardFormUrl(): Response<CardFormUrl> = throw UnsupportedOperationException()
        }

        override suspend fun token(authorization: String, grantType: String, scope: String?): Response<TokenResponse> =
            token()

        override suspend fun chargePayment(paymentId: String, request: ChargePaymentRequest): Response<ChargePaymentResponse> =
            Response.success(ChargePaymentResponse(id = "charge-1", state = ChargeState.PROCESSING))

        override suspend fun getPaymentStatus(paymentId: String): Response<PaymentCreateResponse> =
            throw UnsupportedOperationException()

        override suspend fun getChargeState(paymentId: String): Response<ChargePaymentResponse> = chargeState()

        override suspend fun getQrPaymentInfo(paymentId: String, format: String?): Response<QrPaymentDetails> =
            throw UnsupportedOperationException()

        override suspend fun getGooglePayInfo(paymentId: String): Response<GooglePayInfoResponse> =
            throw UnsupportedOperationException()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
