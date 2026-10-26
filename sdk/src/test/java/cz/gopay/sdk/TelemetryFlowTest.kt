package cz.gopay.sdk

import android.app.Activity
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.session.AwaitChargeOptions
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.telemetry.GwLoggerSchema
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.ui.CardFormData
import cz.gopay.sdk.ui.CardFormHandlers
import cz.gopay.sdk.ui.CardFormStore
import cz.gopay.sdk.ui.submitCardForm
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import retrofit2.Response

/**
 * The operational data an SDK instance sends over a payment, through the public calls: init,
 * the session's start and close, its errors, and the card form's submit. The gateway is faked
 * behind the session factory; the events go to a [TelemetryCapture].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryFlowTest {

    private val capture = TelemetryCapture()
    private val formOwner = Any()
    private val defaultDebugSink = SdkLog.debugSink

    private val events get() = capture.events.onEach { GwLoggerSchema.assertValid(it) }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        TelemetryCapture.reset()
        capture.install()
    }

    @After
    fun tearDown() {
        SdkLog.debugSink = defaultDebugSink
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        TelemetryCapture.reset()
        ErrorReporter.clear()
        CardFormStore.unregister("form-1", formOwner)
        Dispatchers.resetMain()
    }

    /** An SDK whose sessions authenticate against [token] instead of the gateway. */
    private fun sdk(debug: Boolean = true, token: suspend () -> Response<TokenResponse>): GopaySDK {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, clientId = "client-1", debug = debug))
        val sdk = GopaySDK.getInstance()
        sdk.setPaymentSessionFactoryForTest(
            PaymentSession.Factory(
                authApi = object : AuthApi {
                    override suspend fun token(authorization: String, grantType: String, scope: String?) = token()
                },
                paymentApiBuilder = { mock<PaymentApi>() },
                publicApi = { mock() },
                telemetry = sdk.telemetry
            )
        )
        return sdk
    }

    private fun issued() = Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))

    @Test
    fun `initialize sends init, the start navigate, a session error and the close a single leave`() {
        val sdk = sdk { issued() }

        val session = runBlocking { sdk.startPaymentSession("3000000001", "secret") }
        session.close()
        session.close()
        assertThrows(GopaySDKException::class.java) {
            runBlocking { session.awaitChargeState(AwaitChargeOptions(intervalMs = 0)) }
        }

        val sent = events
        assertEquals(
            listOf("navigation" to "init", "navigation" to "navigate", "navigation" to "leave", "js_event" to null),
            sent.map { it["event_type"] to it["navigation_type"] }
        )
        assertFalse(sent[0].containsKey("payment_session_id"))
        assertEquals("client-1", sent[0]["client_id"])
        assertEquals("attach", sent[1]["flow"])
        assertEquals(listOf("3000000001", "3000000001", "3000000001"), sent.drop(1).map { it["payment_session_id"] })
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, sent[3]["function_name"])
        assertEquals(1, sent.map { it["sdk_instance_id"] }.toSet().size)
    }

    @Test
    fun `a failed start reports its error under the payment, and nothing after it does`() {
        val sdk = sdk {
            throw GopaySDKException(GopayErrorCodes.NETWORK_TIMEOUT, "Failed to acquire payment-scoped token: SocketTimeoutException: timeout")
        }

        assertThrows(GopaySDKException::class.java) { runBlocking { sdk.startPaymentSession("3000000001", "secret") } }
        assertThrows(GopaySDKException::class.java) { runBlocking { sdk.startPaymentSession("", "secret") } }

        val (init, failed, invalid) = events
        assertEquals("init", init["navigation_type"])
        assertEquals(GopayErrorCodes.NETWORK_TIMEOUT, failed["function_name"])
        assertEquals("3000000001", failed["payment_session_id"])
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, invalid["function_name"])
        assertFalse(invalid.containsKey("payment_session_id"))
        assertTrue(events.none { it["navigation_type"] == "navigate" })
    }

    @Test
    fun `a duplicate start neither navigates nor leaves, and the live session keeps its payment`() {
        val sdk = sdk { issued() }
        val live = runBlocking { sdk.startPaymentSession("3000000001", "secret") }

        assertThrows(GopaySDKException::class.java) { runBlocking { sdk.startPaymentSession("3000000001", "secret") } }
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = {}, submitAttempted = {}))
        CardFormStore.update("form-1", CardFormData("4444444444444448", "1228", "123"))
        runBlocking { submitCardForm("form-1") { "jwe" } }
        live.close()

        val kinds = events.map { (it["navigation_type"] ?: it["interaction_type"] ?: it["function_name"]) to it["payment_session_id"] }
        assertEquals(
            listOf(
                "init" to null,
                "navigate" to "3000000001",
                // Reported outside the session, so under the payment still open.
                GopayErrorCodes.AUTH_PAYMENT_SESSION_ALREADY_EXISTS to "3000000001",
                "submit" to "3000000001",
                "leave" to "3000000001"
            ),
            kinds
        )
    }

    @Test
    fun `a session reports through the instance that started it, after another initialize too`() {
        val sdk = sdk { issued() }
        val session = runBlocking { sdk.startPaymentSession("3000000001", "secret") }
        val first = events.first()["sdk_instance_id"]

        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, clientId = "client-1"))
        // The challenge is reported before it opens; opening it needs an Android runtime.
        runCatching {
            runBlocking { session.handle3dsVerification(mock<Activity>(), "https://acs.example-bank.com/challenge?creq=SECRET") }
        }
        session.close()

        val redirect = events.single { it["flow"] == "3ds-redirect" }
        assertEquals("leave", redirect["navigation_type"])
        assertEquals("https://acs.example-bank.com/challenge", redirect["target"])
        assertEquals(first, redirect["sdk_instance_id"])
        assertEquals(first, events.last()["sdk_instance_id"])
        assertEquals("leave", events.last()["navigation_type"])
        assertEquals(2, events.count { it["navigation_type"] == "init" })
    }

    @Test
    fun `names each sent event in one Logcat line, without its content, only with debug on`() {
        val lines = mutableListOf<String>()
        SdkLog.debugSink = { lines.add(it) }

        sdk(debug = false) { issued() }
        val quiet = lines.toList()
        runBlocking { sdk(debug = true) { issued() }.startPaymentSession("3000000001", "secret") }

        assertEquals(emptyList<String>(), quiet)
        assertEquals(listOf("telemetry: navigation init", "telemetry: navigation navigate"), lines)
    }

    @Test
    fun `the card form's submit says it happened and nothing of the card or the JWE`() {
        sdk { issued() }
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = {}, submitAttempted = {}))
        CardFormStore.update("form-1", CardFormData("4444444444444448", "1228", "123"))

        runBlocking { submitCardForm("form-1") { "eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn" } }

        val submit = events.last()
        assertEquals("interaction", submit["event_type"])
        assertEquals("card-form", submit["element_id"])
        assertEquals("card", submit["payment_method"])
        // The random ids aside, nothing in the event can have come from the form.
        val values = submit.filterKeys { it !in setOf("trace_id", "transaction_id", "sdk_instance_id") }.values
        for (leak in listOf("4444", "1228", "123", "eyJ")) {
            assertFalse("$leak in $values", values.any { it is String && it.contains(leak) })
        }
    }
}
