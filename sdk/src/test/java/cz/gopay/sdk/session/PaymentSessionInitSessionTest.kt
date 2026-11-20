package cz.gopay.sdk.session

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.locales.systemLanguageTags
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChargePaymentRequest
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
import cz.gopay.sdk.testing.SETTLE_TIMEOUT_MS
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.testing.awaitSettled
import cz.gopay.sdk.testing.drain
import cz.gopay.sdk.testing.settle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.SkipCallbackExecutor
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `startPaymentSession` tells GoPay which SDK works with the payment through
 * `POST /payments/{id}/init-session`. A charge must not overtake it, so the start waits for it
 * until it ends or gives up after 10 s without an answer, and its failure neither throws nor
 * reaches `errorCallback`. The cases are the browser SDK's (`init-session.test.ts`) and the iOS
 * SDK's; the ones mirrored from iOS carry its names.
 *
 * The SDK runs as it does in an app, through `startPaymentSession` and a real OkHttp client with
 * the session's auth and telemetry interceptors; only the transport is faked, by the last
 * interceptor. A test waits for the state it checks ([awaitSettled]), and before it checks that
 * something did not happen it lets every request in the background finish ([drain]); no fixed
 * pause anywhere.
 */
class PaymentSessionInitSessionTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())
    private val dispatcherThreads = Executors.newCachedThreadPool()
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val capture = TelemetryCapture()

    private val reported = Collections.synchronizedList(mutableListOf<GopaySDKException>())
    private val tokenExchanges = AtomicInteger()
    private val initRequests = Collections.synchronizedList(mutableListOf<Request>())
    private val initCallTimeoutsMs = Collections.synchronizedList(mutableListOf<Long>())
    private val otherRequests = Collections.synchronizedList(mutableListOf<Request>())

    /** What the gateway did, in order: `init-session` once answered, any other request on arrival. */
    private val gatewayOrder = Collections.synchronizedList(mutableListOf<String>())

    /** Complete, so the charge asks the gateway for nothing but the charge. */
    private val browserData = BrowserData(
        language = "cs",
        timezone = 60,
        screenWidth = 1080,
        screenHeight = 2400,
        colorDepth = 24,
        userAgent = "Mozilla/5.0",
        acceptHeader = "*/*",
        javascriptEnabled = true,
        ip = "192.0.2.1"
    )

    /** Where a start runs that the test must reach while it waits. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        scope.cancel()
        dispatcherThreads.shutdownNow()
        dispatcherThreads.awaitTermination(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        TelemetryCapture.reset()
        ErrorReporter.clear()
    }

    @Test
    fun `sends the Android SDK and its version, with the session's token and the system's languages`() {
        start()
        awaitInitEvents(1)
        drain(dispatcherThreads)

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
    fun `the start returns only once the init-session is answered`() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val answered = CountDownLatch(1)
        initAnswer = { chain ->
            arrived.countDown()
            release.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            response(chain, 204, "").also { answered.countDown() }
        }

        val start = startInBackground(sdk())
        assertTrue(arrived.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        // Read as the start returns, so no pause decides it.
        val returnedAnswered = CompletableFuture<Boolean>()
        start.invokeOnCompletion { returnedAnswered.complete(answered.count == 0L) }
        assertFalse("the start returned while init-session hangs", start.isCompleted)
        release.countDown()
        val session = runBlocking { start.await() }
        val state = runBlocking { session.getChargeState() }
        drain(dispatcherThreads)

        assertTrue(
            "the start returned after the answer",
            returnedAnswered.get(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertEquals(1, initRequests.size)
        assertTrue("$reported", reported.isEmpty())
        assertNoCrash()
    }

    @Test
    fun `a failed init-session reaches no callback and stops nothing`() {
        for ((i, status) in listOf(400, 403, 404, 409, 500).withIndex()) {
            initAnswer = { response(it, status, """{"errors":[]}""") }
            val session = start()
            val state = runBlocking { session.getChargeState() }

            assertEquals("$status", ChargeState.SUCCEEDED, state.state)
            awaitInitEvents(i + 1)
        }
        drain(dispatcherThreads)

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
        for ((i, failure) in failures.map { it.first }.withIndex()) {
            initAnswer = { throw failure }
            val session = start()
            val state = runBlocking { session.getChargeState() }
            assertEquals(ChargeState.SUCCEEDED, state.state)
            awaitInitEvents(i + 1)
        }
        awaitSettled("both error events") { errorEvents().size >= failures.size }
        drain(dispatcherThreads)

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
            // Shorter than the test's own wait, so a timeout that never comes still ends the call.
            settle(timeoutMs = 10_000) { chain.call().isCanceled() }
            // What the socket does to a cancelled exchange.
            throw IOException("Canceled")
        }

        val session = start()
        val state = runBlocking { session.getChargeState() }
        awaitInitEvents(1)
        awaitSettled("the NETWORK_TIMEOUT") { errorEvents().isNotEmpty() }
        drain(dispatcherThreads)

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
            hold.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }

        val sdk = sdk()
        val start = startInBackground(sdk)
        assertTrue(entered.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        // The start is still waiting for it, and the session already registered.
        val session = sdk.getPaymentSession(PAYMENT)!!
        session.invalidateToken()
        hold.countDown()
        assertSame(session, runBlocking { start.await() })
        drain(dispatcherThreads)

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
        // Done before the read, so its 401 could not be the read's.
        awaitInitEvents(1)
        val state = runBlocking { session.getChargeState() }
        drain(dispatcherThreads)

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
        drain(dispatcherThreads)

        assertEquals(
            listOf("/api/payments/$PAYMENT/init-session", "/api/payments/$OTHER_PAYMENT/init-session"),
            initRequests.map { it.url.encodedPath }.sorted()
        )
        assertNoCrash()
    }

    @Test
    fun `a scope without payment read sends none and does not wait`() {
        val session = runBlocking { sdk().startPaymentSession(PAYMENT, "secret", scope = "payment:charge") }
        drain(dispatcherThreads)

        assertEquals(PAYMENT, session.paymentId)
        assertTrue(initRequests.isEmpty())
        assertNoCrash()
    }

    @Test
    fun `close during the wait cancels the init-session and returns the closed session`() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        initAnswer = holdUntil(arrived, release)

        val sdk = sdk()
        val start = startInBackground(sdk)
        assertTrue(arrived.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val session = sdk.getPaymentSession(PAYMENT)!!
        session.close()
        // Returns while the gateway still holds the request: the close ended the wait.
        assertSame(session, runBlocking { start.await() })
        release.countDown()
        drain(dispatcherThreads)

        assertNull(sdk.getPaymentSession(PAYMENT))
        assertTrue("$reported", reported.isEmpty())
        assertTrue("no api_call for a cancelled request", initEvents().isEmpty())
        assertTrue("no error event", errorEvents().isEmpty())
        assertNoCrash()
    }

    @Test
    fun `cancelling the caller during the wait closes the session and throws the cancellation`() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        initAnswer = holdUntil(arrived, release)

        val sdk = sdk()
        val start = startInBackground(sdk)
        assertTrue(arrived.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val session = sdk.getPaymentSession(PAYMENT)!!
        start.cancel()
        // await on a cancelled start returns only once the start has finished.
        assertThrows(CancellationException::class.java) { runBlocking { start.await() } }
        release.countDown()
        initAnswer = { response(it, 204, "") }
        val again = runBlocking { sdk.startPaymentSession(PAYMENT, "secret") }
        drain(dispatcherThreads)

        assertNotSame(session, again)
        assertSame(again, sdk.getPaymentSession(PAYMENT))
        assertTrue("$reported", reported.isEmpty())
        assertTrue("no error event", errorEvents().isEmpty())
        val navigations = {
            events().filter { it["event_type"] == "navigation" && it["navigation_type"] != "init" }
                .map { it["navigation_type"] }
        }
        val expected = listOf("navigate", "leave", "navigate")
        awaitSettled("the cancelled session left, the second one did not") { navigations() == expected }
        assertEquals(expected, navigations())
        assertEquals("only the second one answered", listOf(204L), initEvents().map { status(it) })
        assertNoCrash()
    }

    @Test
    fun `a charge right after the start goes after the init-session`() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        initAnswer = holdUntil(arrived, release)

        val initPath = "/api/payments/$PAYMENT/init-session"
        val sdk = sdk()
        // One thread of its own, so the test can tell when the app's coroutine has nothing to do.
        val appThread = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
        // What an app does: the charge as soon as the start returns.
        val charge = scope.async(appThread.asCoroutineDispatcher()) {
            val session = sdk.startPaymentSession(PAYMENT, "secret")
            // Read as the start returns, so it does not hang on how soon the charge gets out.
            val answeredAtReturn = initPath in gatewayOrder
            answeredAtReturn to session.charge(
                ChargePaymentRequest.cardToken(cardToken = "token-1", browserData = browserData)
            )
        }
        assertTrue(arrived.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        // The answer waits until the coroutine is suspended: a start that waits is then waiting
        // for it, one that does not has returned and sent the charge already. No race decides it.
        awaitSettled("the app's coroutine suspended") {
            appThread.activeCount == 0 && appThread.queue.isEmpty()
        }
        release.countDown()
        val (answeredAtReturn, response) = runBlocking { charge.await() }
        appThread.shutdown()
        drain(dispatcherThreads)

        assertTrue("the start returned before init-session was answered", answeredAtReturn)
        assertEquals(ChargeState.SUCCEEDED, response.state)
        assertEquals(listOf(initPath, "/api/payments/$PAYMENT/charge"), gatewayOrder.toList())
        assertTrue("$reported", reported.isEmpty())
        assertNoCrash()
    }

    @Test
    fun `the init-session answer skips the main-thread callback executor`() {
        // Retrofit on Android hands an enqueued call's answer to the main thread; a host blocking
        // that thread on the start would then wait for it forever. JVM tests have no main thread,
        // so only the annotation can be checked.
        val initSession = PaymentApi::class.java.methods.single { it.name == "initSession" }
        assertTrue(initSession.isAnnotationPresent(SkipCallbackExecutor::class.java))
    }

    // --- Harness ---------------------------------------------------------------------------

    /** Starts a session for [PAYMENT] on a fresh SDK. */
    private fun start(): PaymentSession = runBlocking { sdk().startPaymentSession(PAYMENT, "secret") }

    /** Starts a session for [PAYMENT] on [sdk] in the background, for a test to reach while it waits. */
    private fun startInBackground(sdk: GopaySDK): Deferred<PaymentSession> =
        scope.async { sdk.startPaymentSession(PAYMENT, "secret") }

    /**
     * An `init-session` answer that says it [arrived] and hangs until [release], then fails as a
     * cancelled exchange does if the call was cancelled meanwhile, or answers 204.
     */
    private fun holdUntil(arrived: CountDownLatch, release: CountDownLatch): (Interceptor.Chain) -> Response =
        { chain ->
            arrived.countDown()
            release.await(SETTLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            // What the socket does to a cancelled exchange.
            if (chain.call().isCanceled()) throw IOException("Canceled")
            response(chain, 204, "")
        }

    private fun sdk(): GopaySDK {
        // A fresh instance each round: initialize with the same config would keep the last one,
        // with the session of the round before still open in it.
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        GopaySDK.initialize(
            GopayConfig(
                environment = GopayEnvironment.SANDBOX,
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
            return initAnswer(chain).also { gatewayOrder.add(request.url.encodedPath) }
        }
        gatewayOrder.add(request.url.encodedPath)
        otherRequests.add(request)
        return response(chain, 200, """{"id":"c1","state":"SUCCEEDED","return_url":"https://shop.example/r"}""")
    }

    /**
     * Waits until [count] init-session requests have been answered and reported: the `api_call`
     * goes out once the gateway has answered, on the request's thread.
     */
    private fun awaitInitEvents(count: Int) =
        awaitSettled("$count init-session api_call") { initEvents().size >= count }

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
