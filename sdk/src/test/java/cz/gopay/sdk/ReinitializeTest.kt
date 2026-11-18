package cz.gopay.sdk

import android.app.Application
import android.content.Context
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.testing.awaitSettled
import cz.gopay.sdk.ui.ForegroundScreen
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import retrofit2.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A second `GopaySDK.initialize`, as React Native runs it on every reload: with the same
 * connection it keeps the instance and its sessions and takes over the callback, the locales and
 * the Context; with another it closes the sessions first. The scenario names are the iOS SDK's
 * too. The gateway is faked behind the session factory; the events go to a [TelemetryCapture].
 */
class ReinitializeTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val capture = TelemetryCapture()

    private val application = mock<Application>()
    private val context = mock<Context> {
        on { applicationContext }.thenReturn(application)
        on { packageName }.thenReturn("com.example.shop")
    }

    /** What the callback of the first config heard, and of the second. */
    private val heardFirst = mutableListOf<GopaySDKException>()
    private val heardSecond = mutableListOf<GopaySDKException>()

    /** When set, every token exchange waits for it, so a start can be caught halfway. */
    @Volatile
    private var tokenGate: CompletableDeferred<Unit>? = null

    /** Token exchanges that have begun. */
    @Volatile
    private var tokenCalls = 0

    private val threads = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        TelemetryCapture.reset()
        capture.install()
    }

    private val defaultDebugSink = SdkLog.debugSink

    @After
    fun tearDown() {
        // SharedSdkState drops the instance, the callback, the Context and Telemetry.current.
        tokenGate?.complete(Unit)
        threads.shutdownNow()
        threads.awaitTermination(30, TimeUnit.SECONDS)
        SdkLog.debugSink = defaultDebugSink
        GopayLocales.clearCustom()
        GopayLocales.setDefaultLocale(null)
        TelemetryCapture.reset()
    }

    private fun config(
        environment: GopayEnvironment = GopayEnvironment.SANDBOX,
        clientId: String? = "client-1",
        shareableKey: String? = "key-1",
        requestTimeoutMs: Long = GopayConfig.DEFAULT_REQUEST_TIMEOUT_MS,
        debugLoggingEnabled: Boolean = false,
        heard: MutableList<GopaySDKException> = heardFirst,
        locale: String? = null,
        customLocales: Map<String, GopayLocaleStrings> = emptyMap()
    ) = GopayConfig(
        environment = environment,
        clientId = clientId,
        shareableKey = shareableKey,
        requestTimeoutMs = requestTimeoutMs,
        debugLoggingEnabled = debugLoggingEnabled,
        errorCallback = { heard.add(it) },
        locale = locale,
        customLocales = customLocales
    )

    /** The gateway behind every session: each payment read answers 404. */
    private val paymentApi = mock<PaymentApi> {
        onBlocking { getChargeState(any()) }.thenReturn(Response.error(404, "".toResponseBody()))
    }

    /** `initialize` with [config], and the instance it leaves, its sessions authenticated by a fake. */
    private fun initialize(config: GopayConfig): GopaySDK {
        GopaySDK.initialize(context, config)
        val sdk = GopaySDK.getInstance()
        sdk.setPaymentSessionFactoryForTest(
            PaymentSession.Factory(
                authApi = object : AuthApi {
                    override suspend fun token(authorization: String, grantType: String, scope: String?):
                        Response<TokenResponse> {
                        tokenCalls++
                        tokenGate?.await()
                        return Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
                    }
                },
                paymentApiBuilder = { paymentApi },
                publicApi = { mock() },
                telemetry = sdk.telemetry
            )
        )
        return sdk
    }

    private fun GopaySDK.start(paymentId: String = PAYMENT): PaymentSession =
        runBlocking { startPaymentSession(paymentId, "secret") }

    /** Drops the instance between the cases of one test, as SharedSdkState does between tests. */
    private fun dropInstance() {
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
    }

    /** What the session holds in memory of the payment's credentials, read as a test may. */
    private fun PaymentSession.secretAndToken(): List<Any?> = listOf("paymentSecret", "token").map { name ->
        PaymentSession::class.java.getDeclaredField(name).apply { isAccessible = true }.get(this)
    }

    private fun closedError(session: PaymentSession): GopaySDKException =
        assertThrows(GopaySDKException::class.java) { runBlocking { session.awaitChargeState() } }

    private fun navigations(type: String) = capture.events.filter { it["navigation_type"] == type }

    @Test
    fun `reinitialize with the same connection keeps the open sessions`() {
        val sdk = initialize(config())
        val session = sdk.start()

        initialize(config(heard = heardSecond))

        assertSame("the instance stays", sdk, GopaySDK.getInstance())
        assertSame(session, GopaySDK.getInstance().getPaymentSession(PAYMENT))
        assertEquals("the credentials stay", listOf("secret", "jwt"), session.secretAndToken())
        assertTrue("nothing was closed", navigations("leave").isEmpty())
        val again = assertThrows(GopaySDKException::class.java) { sdk.start() }
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_ALREADY_EXISTS, again.code)
        assertEquals("the refused start leaves the open session attached", listOf(PAYMENT), sdk.telemetry!!.attachedPaymentIds())
    }

    @Test
    fun `reinitialize with the same connection sends no second init`() {
        initialize(config())
        initialize(config(heard = heardSecond, locale = "de"))
        @Suppress("DEPRECATION") // the deprecated initialize behaves the same
        GopaySDK.initialize(config())

        assertEquals(1, navigations("init").size)
    }

    @Test
    fun `reinitialize with the same connection reports errors of an older session to the new errorCallback`() {
        val sdk = initialize(config())
        val session = sdk.start()

        initialize(config(heard = heardSecond))
        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getChargeState() } }

        assertSame("the instance stays", sdk, GopaySDK.getInstance())
        assertSame("the session is still the open one", session, GopaySDK.getInstance().getPaymentSession(PAYMENT))
        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.code)
        assertEquals(listOf(e), heardSecond)
        assertTrue("the callback of the first config is not called any more", heardFirst.isEmpty())
    }

    @Test
    fun `reinitialize with the same connection applies the new locale`() {
        val first = config(locale = "de")
        val sdk = initialize(first)
        val session = sdk.start()
        assertSame(GopayLocales.DE, GopayLocales.resolve())

        val second = config(heard = heardSecond, locale = "en")
        initialize(second)

        assertSame(GopayLocales.EN, GopayLocales.resolve())
        assertSame("the instance stays", sdk, GopaySDK.getInstance())
        assertSame("with its session", session, sdk.getPaymentSession(PAYMENT))
        assertSame("the instance reads back the latest config", second, sdk.config)
    }

    @Test
    fun `reinitialize with the same connection takes over the new Context`() {
        val sdk = initialize(config())
        val session = sdk.start()
        val otherApplication = mock<Application>()
        val other = mock<Context> { on { applicationContext }.thenReturn(otherApplication) }

        GopaySDK.initialize(other, config(heard = heardSecond))

        assertSame(sdk, GopaySDK.getInstance())
        assertSame(session, sdk.getPaymentSession(PAYMENT))
        assertSame(otherApplication, ForegroundScreen.appContext)
    }

    @Test
    fun `reinitialize with the same connection registers the new customLocales`() {
        val sdk = initialize(config())
        val strings = GopayLocales.EN.copy(panLabel = "Card")

        initialize(config(heard = heardSecond, customLocales = mapOf("xx" to strings)))

        assertSame(sdk, GopaySDK.getInstance())
        assertSame(strings, GopayLocales.resolve("xx"))
    }

    @Test
    fun `two concurrent initialize calls with the same new connection make one`() {
        val old = initialize(config())
        val session = old.start()
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val instances = try {
            List(2) {
                pool.submit<GopaySDK> {
                    ready.countDown()
                    go.await(30, TimeUnit.SECONDS)
                    GopaySDK.initialize(context, config(clientId = "client-2", heard = heardSecond))
                    GopaySDK.getInstance()
                }
            }.also {
                assertTrue(ready.await(30, TimeUnit.SECONDS))
                go.countDown()
            }.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertSame("both see the one new instance", instances[0], instances[1])
        assertNotSame(old, instances[0])
        assertSame(instances[0], GopaySDK.getInstance())
        assertEquals("one init for the new connection", listOf("client-1", "client-2"), navigations("init").map { it["client_id"] })
        assertEquals(listOf(null, null), session.secretAndToken())
        assertEquals("closed once", 1, navigations("leave").size)
    }

    /** A case of the connection tables: what the second config changes, and that config. */
    private class Case(val name: String, val first: GopayConfig, val second: GopayConfig)

    private fun connectionChanges() = listOf(
        Case("environment", config(), config(environment = GopayEnvironment.PRODUCTION, heard = heardSecond)),
        Case(
            "development base URL",
            config(environment = GopayEnvironment.DEVELOPMENT("https://gw.example/api/")),
            config(environment = GopayEnvironment.DEVELOPMENT("https://gw.example/other/"), heard = heardSecond)
        ),
        Case("clientId", config(), config(clientId = "client-2", heard = heardSecond)),
        Case("shareableKey", config(), config(shareableKey = "key-2", heard = heardSecond)),
        Case("request timeout", config(), config(requestTimeoutMs = 10_000, heard = heardSecond)),
        Case("debugLoggingEnabled", config(), config(debugLoggingEnabled = true, heard = heardSecond))
    )

    private fun runtimeChanges() = listOf(
        Case("locale", config(), config(locale = "de", heard = heardFirst)),
        Case("customLocales", config(), config(customLocales = mapOf("xx" to GopayLocales.EN))),
        Case("errorCallback", config(), config(heard = heardSecond)),
        Case(
            "development base URL with a trailing slash",
            config(environment = GopayEnvironment.DEVELOPMENT("https://gw.example/api")),
            config(environment = GopayEnvironment.DEVELOPMENT("https://gw.example/api/"))
        ),
        Case("clientId missing or blank", config(clientId = null), config(clientId = " ")),
        Case("clientId empty or blank", config(clientId = ""), config(clientId = "\t")),
        Case("shareableKey missing or blank", config(shareableKey = null), config(shareableKey = "")),
        Case(
            "request timeout the SDK replaces by the default",
            config(requestTimeoutMs = 0),
            config(requestTimeoutMs = GopayConfig.DEFAULT_REQUEST_TIMEOUT_MS)
        )
    )

    @Test
    fun `reinitialize closes the open sessions when one connection field changes`() {
        for (case in connectionChanges()) {
            dropInstance()
            val old = initialize(case.first)
            val session = old.start()

            val new = initialize(case.second)

            assertNotSame(case.name, old, new)
            assertEquals(case.name, listOf(null, null), session.secretAndToken())
            assertNull(case.name, new.getPaymentSession(PAYMENT))
        }
    }

    @Test
    fun `reinitialize keeps the open sessions when only a runtime field changes`() {
        for (case in runtimeChanges()) {
            dropInstance()
            val sdk = initialize(case.first)
            val session = sdk.start()

            initialize(case.second)

            assertSame(case.name, sdk, GopaySDK.getInstance())
            assertSame(case.name, session, sdk.getPaymentSession(PAYMENT))
            assertEquals(case.name, listOf("secret", "jwt"), session.secretAndToken())
            assertSame(case.name, case.second, sdk.config)
        }
    }

    @Test
    fun `reinitialize with another connection closes the open sessions`() {
        val old = initialize(config())
        val first = old.start("3000000001")
        val second = old.start("3000000002")

        val new = initialize(config(clientId = "client-2", heard = heardSecond))

        assertNotSame(old, new)
        assertEquals(listOf(null, null), first.secretAndToken())
        assertEquals(listOf(null, null), second.secretAndToken())
        assertNull(old.getPaymentSession("3000000001"))
        assertNull(new.getPaymentSession("3000000001"))
        assertTrue("both left the old operational data", old.telemetry!!.attachedPaymentIds().isEmpty())
        val leaves = navigations("leave")
        assertEquals(listOf("3000000001", "3000000002"), leaves.map { it["payment_session_id"] }.sortedBy { it.toString() })
        assertEquals("sent with the old client_id", setOf("client-1"), leaves.map { it["client_id"] }.toSet())
        assertEquals(listOf("client-1", "client-2"), navigations("init").map { it["client_id"] })
        assertTrue("closing reports nothing", heardFirst.isEmpty() && heardSecond.isEmpty())
    }

    @Test
    fun `reinitialize with another connection lets the same payment start again`() {
        initialize(config()).start()

        val new = initialize(config(environment = GopayEnvironment.PRODUCTION, heard = heardSecond))
        val again = new.start()

        assertSame(again, new.getPaymentSession(PAYMENT))
        assertEquals(listOf("secret", "jwt"), again.secretAndToken())
        assertTrue(heardSecond.isEmpty())
    }

    @Test
    fun `a session closed by reinitialize fails as one closed by close`() {
        val closedByHand = initialize(config()).start("3000000009")
        closedByHand.close()
        val byClose = closedError(closedByHand)
        val session = GopaySDK.getInstance().start()

        initialize(config(clientId = "client-2", heard = heardSecond))
        val byReinitialize = closedError(session)

        assertEquals(byClose.code, byReinitialize.code)
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, byReinitialize.code)
        assertEquals("PaymentSession for $PAYMENT is closed", byReinitialize.message)
        assertEquals("reported to the callback in force", listOf(byReinitialize), heardSecond)
    }

    @Test
    fun `a start on an instance that initialize replaced fails without a token exchange`() {
        val old = initialize(config())
        initialize(config(clientId = "client-2", heard = heardSecond))

        val e = assertThrows(GopaySDKException::class.java) { old.start() }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertEquals(0, tokenCalls)
        assertTrue(old.telemetry!!.attachedPaymentIds().isEmpty())
        assertEquals(listOf(e), heardSecond)
    }

    @Test
    fun `a failed reinitialize changes nothing`() {
        val sdk = initialize(config())
        val session = sdk.start()

        val e = assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(
                context,
                config(environment = GopayEnvironment.DEVELOPMENT("http://gw.exa mple.com/api/"), heard = heardSecond)
            )
        }

        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
        assertSame(sdk, GopaySDK.getInstance())
        assertSame(session, sdk.getPaymentSession(PAYMENT))
        assertEquals(listOf("secret", "jwt"), session.secretAndToken())
        assertEquals(1, navigations("init").size)
        assertTrue(navigations("leave").isEmpty())
        assertEquals("only the failing config's callback heard of it", listOf(e), heardSecond)
    }

    @Test
    @Suppress("DEPRECATION") // the deprecated initialize is what is tested
    fun `the deprecated initialize keeps or closes the sessions the same way`() {
        val sdk = initialize(config())
        val session = sdk.start()

        GopaySDK.initialize(config(heard = heardSecond))
        assertSame(session, GopaySDK.getInstance().getPaymentSession(PAYMENT))

        GopaySDK.initialize(config(clientId = "client-2", heard = heardSecond))
        assertNotSame(sdk, GopaySDK.getInstance())
        assertEquals(listOf(null, null), session.secretAndToken())
    }

    /**
     * The race the reload can run into: `startPaymentSession` is waiting for its token when an
     * `initialize` with another connection comes in from another thread. The start then fails
     * as a call on the sessions that initialize closed does, its session closed and kept by
     * neither instance, as on iOS. Before this change the session stayed open in the replaced
     * instance, with nobody able to reach or close it.
     */
    @Test
    fun `reinitialize during startPaymentSession`() {
        val old = initialize(config())
        tokenGate = CompletableDeferred()
        val starting: Future<PaymentSession> = threads.submit<PaymentSession> { old.start() }
        awaitSettled("the start is waiting for its token") { tokenCalls == 1 }

        val new = initialize(config(clientId = "client-2", heard = heardSecond))
        tokenGate!!.complete(Unit)
        val failure = assertThrows(ExecutionException::class.java) { starting.get(30, TimeUnit.SECONDS) }.cause

        assertTrue(failure is GopaySDKException)
        val e = failure as GopaySDKException
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertEquals("reported once, to the callback in force", listOf(e), heardSecond)
        assertTrue(heardFirst.isEmpty())
        assertNull(old.getPaymentSession(PAYMENT))
        assertNull(new.getPaymentSession(PAYMENT))
        assertTrue(old.telemetry!!.attachedPaymentIds().isEmpty())
        assertTrue("never navigated, so it takes no leave", navigations("navigate").isEmpty() && navigations("leave").isEmpty())
        assertEquals("the error belongs to its payment", PAYMENT, capture.events.single { it["function_name"] == e.code }["payment_session_id"])
        assertSame("and the same payment can start on the new instance", new.start(), new.getPaymentSession(PAYMENT))
    }

    /**
     * The other order of the same race: the start registers its session before `initialize`
     * looks, so `initialize` closes it with the open ones, `leave` included, and the start then
     * fails all the same.
     */
    @Test
    fun `reinitialize closes a session that registered while it started`() {
        val old = initialize(config())
        val registered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        GopaySDK::class.java.getDeclaredField("sessions").apply { isAccessible = true }.set(
            old,
            object : ConcurrentHashMap<String, PaymentSession>() {
                override fun putIfAbsent(key: String, value: PaymentSession): PaymentSession? =
                    super.putIfAbsent(key, value).also {
                        registered.countDown()
                        proceed.await(30, TimeUnit.SECONDS)
                    }
            }
        )
        val starting: Future<PaymentSession> = threads.submit<PaymentSession> { old.start() }
        assertTrue("the start registered its session", registered.await(30, TimeUnit.SECONDS))

        val new = initialize(config(clientId = "client-2", heard = heardSecond))
        proceed.countDown()
        val failure = assertThrows(ExecutionException::class.java) { starting.get(30, TimeUnit.SECONDS) }.cause

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, (failure as GopaySDKException).code)
        assertEquals(listOf(failure), heardSecond)
        assertNull(old.getPaymentSession(PAYMENT))
        assertNull(new.getPaymentSession(PAYMENT))
        assertTrue("detached once, by its close", old.telemetry!!.attachedPaymentIds().isEmpty())
        assertEquals("closed by initialize, so it sent its leave", listOf(PAYMENT), navigations("leave").map { it["payment_session_id"] })
    }

    /**
     * What a start overtaken while `initialize` closes the old sessions sees: the new instance
     * and the new callback are already in place. Caught at the `leave` of the open session,
     * which `initialize` sends as it closes it, through the debug log both configs turn on.
     */
    @Test
    fun `a start overtaken while initialize closes the sessions reports to the new errorCallback`() {
        val old = initialize(config(debugLoggingEnabled = true))
        old.start("3000000009")
        tokenGate = CompletableDeferred()
        val starting: Future<PaymentSession> = threads.submit<PaymentSession> { old.start() }
        awaitSettled("the start is waiting for its token") { tokenCalls == 2 }
        var instanceAtLeave: GopaySDK? = null
        var startAtLeave: Throwable? = null
        SdkLog.debugSink = { line ->
            if (line == "telemetry: navigation leave" && instanceAtLeave == null) {
                instanceAtLeave = GopaySDK.getInstance()
                tokenGate!!.complete(Unit)
                startAtLeave = runCatching { starting.get(30, TimeUnit.SECONDS) }.exceptionOrNull()?.cause
            }
        }

        val new = initialize(config(clientId = "client-2", debugLoggingEnabled = true, heard = heardSecond))

        assertSame("getInstance() answers with the new instance while the old sessions close", new, instanceAtLeave)
        val e = startAtLeave as GopaySDKException
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertEquals("the new callback hears of it", listOf(e), heardSecond)
        assertTrue(heardFirst.isEmpty())
        assertEquals(
            "the leave goes through the old operational data",
            listOf("client-1"),
            navigations("leave").map { it["client_id"] }
        )
    }

    private companion object {
        const val PAYMENT = "3000000001"
    }
}
