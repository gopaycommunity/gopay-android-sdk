package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
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
import cz.gopay.sdk.model.syntheticUserAgent
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * The gateway requires `browser_data.ip` on every charge and the device cannot know its own
 * public address, so the session asks `GET /cards/browser-data` first, under the shareable key.
 * These tests pin what goes out on that request, what of the answer lands in the charge, and
 * that a charge is never sent blind when the answer does not come.
 */
class PaymentSessionBrowserDataTest {

    private val deviceData = BrowserData(
        language = "cs-CZ",
        timezone = -60,
        screenWidth = 1080,
        screenHeight = 2400,
        colorDepth = 24,
        userAgent = WEBVIEW_UA
    )

    private val detected = BrowserDataDetected(
        ip = "192.0.2.42",
        userAgent = WEBVIEW_UA,
        acceptHeader = """{"accept":"application/json"}"""
    )

    private val defaultWarnSink = SdkLog.warnSink

    /** Every warning SdkLog emitted during a test; the sink is swapped in [collectWarnings]. */
    private val warnings = mutableListOf<String>()

    @Before
    fun collectWarnings() {
        SdkLog.warnSink = { warnings.add(it) }
    }

    @After
    fun restoreLogSink() {
        SdkLog.warnSink = defaultWarnSink
    }

    @Test
    fun `charge fetches the browser data with the WebView User-Agent and sends it`() = runBlocking {
        val api = fakes(browserData = { Response.success(detected) })
        val session = session(api)

        session.charge(ChargePaymentRequest.cardToken("tok", deviceData))

        assertEquals(listOf(WEBVIEW_UA), api.browserDataUserAgents)
        val sent = api.charges.single().paymentInstrument.browserData
        assertEquals("192.0.2.42", sent.ip)
        assertEquals(WEBVIEW_UA, sent.userAgent)
        assertEquals("""{"accept":"application/json"}""", sent.acceptHeader)
        assertEquals(true, sent.javascriptEnabled)
        // The device readings are untouched.
        assertEquals("cs-CZ", sent.language)
        assertEquals(-60, sent.timezone)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `values the caller set are kept, only the missing ones are filled`() = runBlocking {
        val api = fakes(browserData = { Response.success(detected) })
        val own = deviceData.copy(
            userAgent = "Custom/1.0",
            acceptHeader = "text/html",
            javascriptEnabled = false
        )

        session(api).charge(ChargePaymentRequest.cardToken("tok", own))

        val sent = api.charges.single().paymentInstrument.browserData
        assertEquals("192.0.2.42", sent.ip)
        assertEquals("Custom/1.0", sent.userAgent)
        assertEquals("text/html", sent.acceptHeader)
        assertEquals(false, sent.javascriptEnabled)
        assertEquals(listOf("Custom/1.0"), api.browserDataUserAgents)
    }

    @Test
    fun `a charge that already carries ip, user_agent and accept_header skips the fetch`() = runBlocking {
        val api = fakes(browserData = { throw AssertionError("must not be called") })
        val complete = deviceData.copy(ip = "198.51.100.7", acceptHeader = "text/html")

        session(api).charge(ChargePaymentRequest.cardToken("tok", complete))

        assertTrue(api.browserDataUserAgents.isEmpty())
        val sent = api.charges.single().paymentInstrument.browserData
        assertEquals("198.51.100.7", sent.ip)
        assertEquals(true, sent.javascriptEnabled)
    }

    @Test
    fun `without a User-Agent of its own a synthesized WebView one goes on the request and into the charge`() = runBlocking {
        // A BrowserData built by hand without user_agent used to leave the header to the HTTP
        // client, so the gateway echoed the SDK's own User-Agent back and the charge carried it
        // into the AReq, the very mismatch with the challenge WebView the UA fix removed.
        val api = fakes(browserData = { Response.success(detected.copy(userAgent = "GoPay Android SDK 1.0")) })

        session(api).charge(ChargePaymentRequest.cardToken("tok", deviceData.copy(userAgent = null)))

        val standIn = syntheticUserAgent()
        assertEquals(listOf(standIn), api.browserDataUserAgents)
        assertEquals(standIn, api.charges.single().paymentInstrument.browserData.userAgent)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("user_agent"))
    }

    @Test
    fun `with ip and accept_header but no user_agent the fetch is skipped and the stand-in goes into the charge`() = runBlocking {
        val api = fakes(browserData = { throw AssertionError("must not be called") })
        val complete = deviceData.copy(userAgent = null, ip = "198.51.100.7", acceptHeader = "text/html")

        session(api).charge(ChargePaymentRequest.cardToken("tok", complete))

        assertTrue(api.browserDataUserAgents.isEmpty())
        assertEquals(syntheticUserAgent(), api.charges.single().paymentInstrument.browserData.userAgent)
        assertEquals(1, warnings.size)
    }

    @Test
    fun `the User-Agent header is reduced to printable ASCII, the charge keeps the value`() = runBlocking {
        // OkHttp refuses a header value outside printable ASCII with an IllegalArgumentException
        // the host never sees coming; a device model with an accent is enough to produce one.
        val api = fakes(browserData = { Response.success(detected) })
        val accented = syntheticUserAgent(release = "14", sdkInt = 34, model = "Zařízení™") + "\r\n"

        session(api).charge(ChargePaymentRequest.cardToken("tok", deviceData.copy(userAgent = accented)))

        assertEquals(
            listOf(syntheticUserAgent(release = "14", sdkInt = 34, model = "Za??zen??")),
            api.browserDataUserAgents
        )
        assertEquals(accented, api.charges.single().paymentInstrument.browserData.userAgent)
    }

    @Test
    fun `an HTTP error on the fetch fails the charge and the charge is not sent`() = runBlocking {
        val api = fakes(browserData = {
            Response.error(403, """{"message":"Token domain not permitted"}""".toResponseBody(JSON))
        })

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session(api).charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        assertEquals(403, e.httpContext?.statusCode)
        assertTrue(e.message!!.startsWith("Failed to charge payment: browser data could not be fetched"))
        val cause = e.cause as GopaySDKException
        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, cause.errorCode)
        assertTrue(cause.message!!.contains("fetch browser data"))
        assertTrue("the charge must not go out without ip", api.charges.isEmpty())
    }

    @Test
    fun `a transport failure on the fetch fails the charge with the IO code`() = runBlocking {
        val io = IOException("unreachable")
        val api = fakes(browserData = { throw io })

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session(api).charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.NETWORK_IO_ERROR, e.errorCode)
        assertSame(io, e.cause)
        assertNull(e.httpContext)
        assertTrue(api.charges.isEmpty())
    }

    @Test
    fun `completeBrowserData returns what the charge would send`() = runBlocking {
        val api = fakes(browserData = { Response.success(detected) })

        val completed = session(api).completeBrowserData(deviceData)

        assertEquals(deviceData.copy(
            ip = "192.0.2.42",
            acceptHeader = """{"accept":"application/json"}""",
            javascriptEnabled = true
        ), completed)
    }

    @Test
    fun `without clientId and shareableKey the charge fails before it is sent`() = runBlocking {
        val api = fakes(browserData = { throw AssertionError("must not be called") })
        val session = sessionWithoutShareableKey(api)

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.errorCode)
        assertTrue(e.message!!.startsWith("Failed to charge payment: browser data could not be fetched"))
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, (e.cause as GopaySDKException).errorCode)
        assertTrue(api.charges.isEmpty())
    }

    @Test
    fun `a closed session refuses the charge before asking for the browser data`() = runBlocking {
        // The fetch used to run first, so a closed session without the shareable key was
        // reported as AUTH_011 instead of AUTH_013, and one with the key made a request the
        // charge could never use.
        val api = fakes(browserData = { throw AssertionError("must not be called") })
        val session = sessionWithoutShareableKey(api)
        session.close()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.charge(ChargePaymentRequest.cardToken("tok", deviceData)) }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.errorCode)
        assertTrue(api.browserDataUserAgents.isEmpty())
        assertTrue(api.charges.isEmpty())
    }

    /** A live session, opened the way the SDK opens one, with [api] behind every endpoint. */
    private fun session(api: Fakes): PaymentSession = runBlocking {
        PaymentSession.Factory(
            authApi = api,
            paymentApiBuilder = { api },
            publicApi = { api.publicApi }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
    }

    /** The same, but with the public API failing the way NetworkManager does without the key. */
    private fun sessionWithoutShareableKey(api: Fakes): PaymentSession = runBlocking {
        PaymentSession.Factory(
            authApi = api,
            paymentApiBuilder = { api },
            publicApi = {
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING,
                    message = "clientId and shareableKey must be set on GopayConfig to use public endpoints"
                )
            }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
    }

    private fun fakes(browserData: () -> Response<BrowserDataDetected>) = Fakes(browserData)

    /**
     * The session's payment API, recording the charge bodies, paired with a public API that
     * records the User-Agent handed to the browser-data request and answers with [browserData],
     * and a token endpoint that lets the session open.
     */
    private class Fakes(
        private val browserData: () -> Response<BrowserDataDetected>
    ) : PaymentApi, AuthApi {
        val charges = mutableListOf<ChargePaymentRequest>()
        val browserDataUserAgents = mutableListOf<String?>()

        val publicApi: PublicApi = object : PublicApi {
            override suspend fun getBrowserData(userAgent: String?): Response<BrowserDataDetected> {
                browserDataUserAgents += userAgent
                return browserData()
            }

            override suspend fun getPublicKey(): Response<Jwk> = throw UnsupportedOperationException()

            override suspend fun getCardFormUrl(): Response<CardFormUrl> =
                throw UnsupportedOperationException()
        }

        override suspend fun token(
            authorization: String,
            grantType: String,
            scope: String?
        ): Response<TokenResponse> = Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))

        override suspend fun chargePayment(
            paymentId: String,
            request: ChargePaymentRequest
        ): Response<ChargePaymentResponse> {
            charges += request
            return Response.success(ChargePaymentResponse(id = "charge-1", state = ChargeState.PROCESSING))
        }

        override suspend fun getPaymentStatus(paymentId: String): Response<PaymentCreateResponse> =
            throw UnsupportedOperationException()

        override suspend fun getChargeState(paymentId: String): Response<ChargePaymentResponse> =
            throw UnsupportedOperationException()

        override suspend fun getQrPaymentInfo(paymentId: String, format: String?): Response<QrPaymentDetails> =
            throw UnsupportedOperationException()

        override suspend fun getGooglePayInfo(paymentId: String): Response<GooglePayInfoResponse> =
            throw UnsupportedOperationException()
    }

    private companion object {
        const val WEBVIEW_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val JSON = "application/json".toMediaType()
    }
}
