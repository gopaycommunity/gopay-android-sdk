package cz.gopay.sdk

import android.app.Activity
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.telemetry.CardFormProgress
import cz.gopay.sdk.telemetry.CardFormSteps
import cz.gopay.sdk.telemetry.GwLoggerSchema
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.ui.CardFormData
import cz.gopay.sdk.ui.CardFormHandlers
import cz.gopay.sdk.ui.CardFormStore
import cz.gopay.sdk.ui.submitCardForm
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CancellationException
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
import java.io.IOException

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
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, clientId = "client-1", debugLoggingEnabled = debug))
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
            runBlocking { session.awaitChargeState() }
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
        // The closed session is refused before the options are even looked at.
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, sent[3]["function_name"])
        assertTrue(sent.none { it.containsKey("sdk_instance_id") })
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
        CardFormStore.update("form-1", formOwner, CardFormData("4444444444444448", "1228", "123"))
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
        // The instances told apart by their client id, the one thing of theirs an event carries.
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, clientId = "client-2"))
        // The challenge is reported before it opens; opening it needs an Android runtime.
        runCatching {
            runBlocking { session.handle3dsVerification(mock<Activity>(), "https://acs.example-bank.com/challenge?creq=SECRET") }
        }
        session.close()

        val redirect = events.single { it["flow"] == "3ds-redirect" }
        assertEquals("leave", redirect["navigation_type"])
        assertEquals("https://acs.example-bank.com/challenge", redirect["target"])
        assertEquals("client-1", redirect["client_id"])
        assertEquals("client-1", events.last()["client_id"])
        assertEquals(listOf("client-1", "client-2"), events.filter { it["navigation_type"] == "init" }.map { it["client_id"] })
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
    fun `the card form's steps go out with the submit, and none of them carries the card`() {
        sdk { issued() }
        val steps = CardFormSteps()
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = steps::emptied, submitAttempted = {}, steps = steps))
        steps.ready()
        CardFormStore.update("form-1", formOwner, CardFormData())
        runCatching { runBlocking { submitCardForm("form-1") { error("not reached") } } }
        for (data in listOf(
            CardFormData("4444444444444448"),
            CardFormData("4444444444444448", "1228"),
            CardFormData("4444444444444448", "1228", "123")
        )) {
            CardFormStore.update("form-1", formOwner, data)
            steps.input(CardFormProgress.Field.PAN) { data.progress() }
        }

        runBlocking { submitCardForm("form-1") { "eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn" } }

        val card = events.filter { (it["function_name"] as String?)?.startsWith("PAYMENT_CARD.") == true || it["event_type"] == "interaction" }
        assertEquals(
            listOf(
                "PAYMENT_CARD.submit-request", "PAYMENT_CARD.field-errors", "PAYMENT_CARD.started",
                "PAYMENT_CARD.completed", "PAYMENT_CARD.submit-request", "PAYMENT_CARD.field-errors",
                "submit", "PAYMENT_CARD.interaction"
            ),
            card.map { it["function_name"] ?: it["interaction_type"] }
        )
        assertEquals("submit: completed", card.last()["return_value"])
        for (event in card) {
            val values = event.filterKeys { it !in setOf("trace_id", "transaction_id") }.values
            for (leak in listOf("4444", "1228", "123", "eyJ")) {
                assertFalse("$leak in $values", values.any { it is String && it.contains(leak) })
            }
        }
    }

    @Test
    fun `a submit whose encryption fails ends the visit with encrypt-error, a cancelled one does not`() {
        sdk { issued() }
        val card = CardFormData("4444444444444448", "1228", "123")
        for ((failure, summary) in listOf(
            IOException("public key unreachable") to "encrypt-error: completed",
            CancellationException("the host left") to "unmount: completed"
        )) {
            val steps = CardFormSteps()
            CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = steps::emptied, submitAttempted = {}, steps = steps))
            steps.ready()
            CardFormStore.update("form-1", formOwner, card)
            steps.input(CardFormProgress.Field.PAN) { card.progress() }

            val thrown = runCatching { runBlocking { submitCardForm("form-1") { throw failure } } }.exceptionOrNull()
            steps.unmounted()
            CardFormStore.unregister("form-1", formOwner)

            assertEquals(failure.javaClass, thrown?.javaClass)
            assertEquals(summary, events.last { it["function_name"] == "PAYMENT_CARD.interaction" }["return_value"])
        }
    }

    @Test
    fun `the card form's submit says it happened and nothing of the card or the JWE`() {
        sdk { issued() }
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = {}, submitAttempted = {}))
        CardFormStore.update("form-1", formOwner, CardFormData("4444444444444448", "1228", "123"))

        runBlocking { submitCardForm("form-1") { "eyJhbGciOiJSU0EtT0FFUCJ9.abcdefghij.klmnopqrst.uvwxyzabcd.efghijklmn" } }

        val submit = events.last()
        assertEquals("interaction", submit["event_type"])
        assertEquals("card-form", submit["element_id"])
        assertEquals("card", submit["payment_method"])
        // The random ids aside, nothing in the event can have come from the form.
        val values = submit.filterKeys { it !in setOf("trace_id", "transaction_id") }.values
        for (leak in listOf("4444", "1228", "123", "eyJ")) {
            assertFalse("$leak in $values", values.any { it is String && it.contains(leak) })
        }
    }
}
