package cz.gopay.sdk.session

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.locales.systemLanguageTags
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.SessionAuthInterceptor
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.telemetry.ApiCallTelemetryInterceptor
import cz.gopay.sdk.telemetry.GwLoggerSchema
import cz.gopay.sdk.telemetry.JsonText
import cz.gopay.sdk.telemetry.TelemetryCapture
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `startPaymentSession` tells GoPay which SDK works with the payment through
 * `POST /payments/{id}/init-session`. Nothing depends on it, so it must never cost the payment
 * anything: the start does not wait for it, and its failure neither throws nor reaches
 * `errorCallback`. The cases are the browser SDK's (`init-session.test.ts`) and the iOS SDK's.
 *
 * The SDK runs as it does in an app, through `startPaymentSession` and a real OkHttp client with
 * the session's auth and telemetry interceptors; only the transport is faked, by the last
 * interceptor. [settle] waits for the request in the background to finish.
 */
class PaymentSessionInitSessionTest {

    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private val dispatcherThreads = Executors.newCachedThreadPool()
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val capture = TelemetryCapture()

    private val reported = Collections.synchronizedList(mutableListOf<GopaySDKException>())
    private val tokenExchanges = AtomicInteger()
    private val initRequests = Collections.synchronizedList(mutableListOf<Request>())
    private val initCallTimeoutsMs = Collections.synchronizedList(mutableListOf<Long>())
    private val otherRequests = Collections.synchronizedList(mutableListOf<Request>())

    /** How the gateway answers `init-session`; 204 unless a test says otherwise. */
    @Volatile
    private var initAnswer: (Interceptor.Chain) -> Response = { response(it, 204, "") }

    /** Runs for `init-session` before the session's auth does, on the request's thread. */
    @Volatile
    private var beforeAuth: (Interceptor.Chain) -> Unit = {}

    @Before
    fun setUp() {
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes.add(e) }
        TelemetryCapture.reset()
        capture.install()
        GopayLocales.preferredLanguages = { listOf("cs-CZ", "cs", "en-GB") }
    }

    @After
    fun tearDown() {
        GopayLocales.preferredLanguages = ::systemLanguageTags
        PaymentSession.initSessionTimeoutMs = 10_000L
        dispatcherThreads.shutdownNow()
        dispatcherThreads.awaitTermination(5, TimeUnit.SECONDS)
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        TelemetryCapture.reset()
        ErrorReporter.clear()
    }

    @Test
    fun `sends the Android SDK and its version, with the session's token and the system's languages`() {
        start()
        settle()

        val request = initRequests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/payments/$PAYMENT/init-session", request.url.encodedPath)
        assertEquals("Bearer jwt-1", request.header("Authorization"))
        assertTrue(request.body!!.contentType().toString().startsWith("application/json"))
        assertEquals("cs-CZ,cs;q=0.9,en-GB;q=0.8", request.header("Accept-Language"))
        assertEquals(
            mapOf("client_type" to "SDK_ANDROID", "client_version" to GopaySDK.version),
            JsonText.parse(bodyOf(request))
        )
        assertEquals("a 10 s timeout on the call", listOf(10_000L), initCallTimeoutsMs)
        assertTrue(reported.isEmpty())
        assertNoCrash()
    }

    @Test
    fun `does not hold up the start or the calls after it`() {
        val release = CountDownLatch(1)
        initAnswer = { chain ->
            release.await(5, TimeUnit.SECONDS)
            response(chain, 204, "")
        }

        val session = start()
        val state = runBlocking { session.getChargeState() }
        // Both returned while init-session still hangs; a start that waited for it would not have.
        assertEquals(1L, release.count)
        assertTrue("init-session is still on its way", initEvents().isEmpty())
        release.countDown()
        settle()

        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertEquals(1, initRequests.size)
        assertNoCrash()
    }

    @Test
    fun `an HTTP failure reaches neither the start nor errorCallback, only its api_call`() {
        for (status in listOf(400, 403, 404, 409, 500)) {
            initAnswer = { response(it, status, """{"errors":[]}""") }
            val session = start()
            val state = runBlocking { session.getChargeState() }

            assertEquals("$status", ChargeState.SUCCEEDED, state.state)
        }
        settle()

        assertEquals(5, initRequests.size)
        assertTrue("$reported", reported.isEmpty())
        assertEquals(
            listOf(400L, 403L, 404L, 409L, 500L),
            initEvents().map { status(it)!! }.sorted()
        )
        assertTrue("an HTTP status is not an error event", errorEvents().isEmpty())
        assertNoCrash()
    }

    @Test
    fun `a failure without a response reaches neither the start nor errorCallback, but goes out as its error`() {
        val failures = listOf(
            UnknownHostException("offline") to GopayErrorCodes.NETWORK_NO_CONNECTION,
            SocketTimeoutException("timeout") to GopayErrorCodes.NETWORK_TIMEOUT
        )
        for ((failure, _) in failures) {
            initAnswer = { throw failure }
            val session = start()
            val state = runBlocking { session.getChargeState() }
            assertEquals(ChargeState.SUCCEEDED, state.state)
        }
        settle()

        assertTrue("$reported", reported.isEmpty())
        assertEquals(listOf(null, null), initEvents().map { status(it) })
        assertEquals(
            failures.map { it.second }.sorted(),
            errorEvents().map { it["error_code"] as String }.sorted()
        )
        assertTrue(errorEvents().all { it["payment_session_id"] == PAYMENT })
        assertNoCrash()
    }

    @Test
    fun `a real call timeout goes out as its api_call and one NETWORK_TIMEOUT, not as a close`() {
        // OkHttp cancels a call that runs out of time, the way close() does.
        PaymentSession.initSessionTimeoutMs = 200
        initAnswer = { chain ->
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(10)
            // What the socket does to a cancelled exchange.
            throw IOException("Canceled")
        }

        val session = start()
        val state = runBlocking { session.getChargeState() }
        settle()

        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertTrue("$reported", reported.isEmpty())
        assertEquals(listOf<Long?>(null), initEvents().map { status(it) })
        assertEquals(listOf(GopayErrorCodes.NETWORK_TIMEOUT), errorEvents().map { it["error_code"] })
        assertNoCrash()
    }

    @Test
    fun `a request that never left the device reports nothing`() {
        // The token is gone by the time the request runs: the auth stops it on the device.
        val entered = CountDownLatch(1)
        val hold = CountDownLatch(1)
        beforeAuth = {
            entered.countDown()
            hold.await(5, TimeUnit.SECONDS)
        }

        val session = start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        session.invalidateToken()
        hold.countDown()
        settle()

        assertTrue("never reached the gateway", initRequests.isEmpty())
        assertTrue("$reported", reported.isEmpty())
        assertTrue("no api_call", initEvents().isEmpty())
        assertTrue("no error event", errorEvents().isEmpty())
        assertNoCrash()
    }

    @Test
    fun `a 401 leaves the session's token alone`() {
        initAnswer = { response(it, 401, """{"errors":[]}""") }

        val session = start()
        awaitInitSession()
        val state = runBlocking { session.getChargeState() }

        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertEquals("no second credentials exchange", 1, tokenExchanges.get())
        assertEquals("jwt-1", session.currentToken())
        assertEquals("Bearer jwt-1", otherRequests.last().header("Authorization"))
        assertTrue(reported.isEmpty())
        assertEquals(listOf(401L), initEvents().map { status(it) })
        assertNoCrash()
    }

    @Test
    fun `one per payment, and none for a duplicate session that lost the registration`() {
        val sdk = sdk()
        runBlocking { sdk.startPaymentSession(PAYMENT, "secret") }
        runBlocking { sdk.startPaymentSession(OTHER_PAYMENT, "secret") }
        assertThrows(GopaySDKException::class.java) {
            runBlocking { sdk.startPaymentSession(PAYMENT, "secret") }
        }
        settle()

        assertEquals(
            listOf("/api/payments/$PAYMENT/init-session", "/api/payments/$OTHER_PAYMENT/init-session"),
            initRequests.map { it.url.encodedPath }.sorted()
        )
        assertNoCrash()
    }

    @Test
    fun `a session without payment_read sends none`() {
        runBlocking { sdk().startPaymentSession(PAYMENT, "secret", scope = "payment:charge") }
        settle()

        assertTrue(initRequests.isEmpty())
        assertNoCrash()
    }

    @Test
    fun `close cancels the request on its way and reports nothing`() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        initAnswer = { chain ->
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            // What the socket does to a cancelled exchange.
            if (chain.call().isCanceled()) throw IOException("Canceled")
            response(chain, 204, "")
        }

        val session = start()
        assertTrue(arrived.await(5, TimeUnit.SECONDS))
        session.close()
        release.countDown()
        settle()

        assertTrue("$reported", reported.isEmpty())
        assertTrue("no api_call for a cancelled request", initEvents().isEmpty())
        assertTrue("no error event", errorEvents().isEmpty())
        assertNoCrash()
    }

    // --- Harness ---------------------------------------------------------------------------

    /** Starts a session for [PAYMENT] on a fresh SDK. */
    private fun start(): PaymentSession = runBlocking { sdk().startPaymentSession(PAYMENT, "secret") }

    private fun sdk(): GopaySDK {
        GopaySDK.initialize(
            GopayConfig(
                environment = Environment.SANDBOX,
                clientId = "client-1",
                errorCallback = { reported.add(it) }
            )
        )
        val sdk = GopaySDK.getInstance()
        val telemetry = sdk.telemetry!!
        sdk.setPaymentSessionFactoryForTest(
            PaymentSession.Factory(
                authApi = object : AuthApi {
                    override suspend fun token(authorization: String, grantType: String, scope: String?) =
                        retrofit2.Response.success(
                            TokenResponse(accessToken = "jwt-${tokenExchanges.incrementAndGet()}", tokenType = "Bearer")
                        )
                },
                paymentApiBuilder = { provider ->
                    val client = OkHttpClient.Builder()
                        .dispatcher(Dispatcher(dispatcherThreads))
                        .addInterceptor(Interceptor { chain ->
                            if (chain.request().url.encodedPath.endsWith("/init-session")) beforeAuth(chain)
                            chain.proceed(chain.request())
                        })
                        .addInterceptor(SessionAuthInterceptor(provider))
                        .addInterceptor(ApiCallTelemetryInterceptor(telemetry, basePath = "/api/"))
                        .addInterceptor(Interceptor { chain -> gateway(chain) })
                        .build()
                    NetworkModule.createRetrofit(client, "https://gw.example/api/").create(PaymentApi::class.java)
                },
                publicApi = { throw IOException("not used") },
                telemetry = telemetry
            )
        )
        return sdk
    }

    private fun gateway(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.endsWith("/init-session")) {
            initRequests.add(request)
            initCallTimeoutsMs.add(TimeUnit.NANOSECONDS.toMillis(chain.call().timeout().timeoutNanos()))
            return initAnswer(chain)
        }
        otherRequests.add(request)
        return response(chain, 200, """{"id":"c1","state":"SUCCEEDED","return_url":"https://shop.example/r"}""")
    }

    /**
     * Waits for every request in the background to finish, the init-session's callback
     * included: they all run on [dispatcherThreads]. Nothing can be sent after it.
     */
    private fun settle() {
        dispatcherThreads.shutdown()
        assertTrue(dispatcherThreads.awaitTermination(5, TimeUnit.SECONDS))
    }

    /** Waits until the init-session's `api_call` is out, for a test that goes on sending. */
    private fun awaitInitSession() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (initEvents().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10)
    }

    private fun assertNoCrash() = assertTrue("a thread died with $crashes", crashes.isEmpty())

    private fun events() = capture.events.onEach { GwLoggerSchema.assertValid(it) }

    private fun initEvents() = events().filter { it["target"] == "/payments/{id}/init-session" }

    private fun errorEvents() = events().filter { it["event_type"] == "js_event" && it.containsKey("error_code") }

    private fun status(event: Map<String, Any?>): Long? =
        (event["status_code"] as JsonText.JsonNumber?)?.literal?.toLong()

    private fun bodyOf(request: Request) = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    private fun response(chain: Interceptor.Chain, code: Int, body: String): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private companion object {
        const val PAYMENT = "3000000001"
        const val OTHER_PAYMENT = "3000000002"
    }
}
