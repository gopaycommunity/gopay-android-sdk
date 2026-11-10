package cz.gopay.sdk.session

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChargeAction
import cz.gopay.sdk.model.ChargeActionType
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.ui.ForegroundScreen
import cz.gopay.sdk.ui.PaymentVerificationBridge
import cz.gopay.sdk.ui.SdkScreens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Response

/**
 * The calls that open a screen, with the Activity optional: which screen they open from, what
 * they do while there is none, and what they say when the SDK has no Context to find one with.
 * What gets started is recorded through [SdkScreens]; every wait is for a state, a screen coming
 * to the front or the challenge reporting, on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentSessionScreenTest {

    private val application = mock<Application>()
    private val context = mock<Context> { on { applicationContext }.thenReturn(application) }

    /** Every challenge started: the screen it was started from and its redirect URL. */
    private val challenges = mutableListOf<Pair<Activity, String>>()

    /** The id each challenge was started for, which travels in its intent. */
    private val challengeOwners = mutableListOf<Long>()
    private val wallets = mutableListOf<Activity>()
    private val reported = mutableListOf<GopaySDKException>()

    private val startVerification = SdkScreens.startVerification
    private val startGooglePay = SdkScreens.startGooglePay

    private val browserData = BrowserData(
        language = "cs-CZ", timezone = -60, screenWidth = 1080, screenHeight = 2400, colorDepth = 24,
        ip = "10.0.0.1", acceptHeader = "*/*", userAgent = "ua"
    )

    @Before
    fun setUp() {
        ForegroundScreen.reset()
        SdkScreens.startVerification = { screen, redirectUrl, _, ownerId ->
            challenges += screen to redirectUrl
            challengeOwners += ownerId
        }
        SdkScreens.startGooglePay = { screen, _, _, _ -> wallets += screen }
        ErrorReporter.setErrorCallback { reported += it }
    }

    @After
    fun tearDown() {
        PaymentVerificationBridge.currentOwner()?.let(PaymentVerificationBridge::dismiss)
        SdkScreens.startVerification = startVerification
        SdkScreens.startGooglePay = startGooglePay
        ErrorReporter.clear()
        ForegroundScreen.reset()
        Dispatchers.resetMain()
    }

    /** A test on virtual time, with the main dispatcher on the same clock. */
    private fun screenTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        block()
    }

    /** A screen of the host; one not [inFront] is one the watch saw go to the background. */
    private fun activity(finishing: Boolean = false, inFront: Boolean = true): Activity {
        val activity = mock<Activity> { on { isFinishing }.thenReturn(finishing) }
        if (!inFront) {
            ForegroundScreen.resumed(activity)
            ForegroundScreen.paused(activity)
        }
        return activity
    }

    /** A lifecycle-aware screen that answers with [states] in turn, the last one from then on. */
    private fun lifecycleActivity(vararg states: Lifecycle.State): Activity {
        val lifecycle = mock<Lifecycle> { on { currentState }.thenReturn(states.first(), *states.drop(1).toTypedArray()) }
        val activity = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((activity as LifecycleOwner).lifecycle).thenReturn(lifecycle)
        return activity
    }

    /** What the challenge screen does once it is up and the user answered. */
    private fun answerTheChallenge() {
        val owner = PaymentVerificationBridge.currentOwner()!!
        PaymentVerificationBridge.attach(owner) {}
        PaymentVerificationBridge.complete(owner)
    }

    private suspend fun failure(block: suspend () -> Unit): GopaySDKException = try {
        block()
        throw AssertionError("expected a GopaySDKException")
    } catch (e: GopaySDKException) {
        e
    }

    // ---- handle3dsVerification

    @Test
    fun `without an Activity and without the Context the challenge is CONFIG_004, and nothing opens`() = screenTest {
        val e = failure { session().handle3dsVerification(URL) }

        assertEquals(GopayErrorCodes.CONFIG_MISSING_CONTEXT, e.code)
        assertTrue(challenges.isEmpty())
        assertNull("the bridge was not claimed", PaymentVerificationBridge.currentOwner())
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `the 2_7 form with the Activity first still opens from that Activity, Context or not`() = screenTest {
        val named = activity()
        val call = async { session().handle3dsVerification(named, URL, "https://shop.example/return") }
        runCurrent()

        assertEquals(listOf(named to URL), challenges)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `without an Activity the challenge opens from the screen in front`() = screenTest {
        ForegroundScreen.install(context)
        val inFront = activity()
        ForegroundScreen.resumed(inFront)

        val call = async { session().handle3dsVerification(URL) }
        runCurrent()

        assertEquals(listOf(inFront to URL), challenges)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `a named Activity that is finishing gives way to the screen in front`() = screenTest {
        ForegroundScreen.install(context)
        val inFront = activity()
        ForegroundScreen.resumed(inFront)

        val call = async { session().handle3dsVerification(URL, activity = activity(finishing = true)) }
        runCurrent()

        assertSame(inFront, challenges.single().first)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `a named Activity in the background gives way to the screen in front`() = screenTest {
        ForegroundScreen.install(context)
        val inFront = activity()
        ForegroundScreen.resumed(inFront)

        // What an integration from 2.x does: it passes the Activity it holds, always.
        val call = async { session().handle3dsVerification(activity(inFront = false), URL) }
        runCurrent()

        assertSame(inFront, challenges.single().first)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `a named Activity in the background is waited for, not opened from`() = screenTest {
        ForegroundScreen.install(context)
        val named = activity(inFront = false)
        val call = async { session().handle3dsVerification(named, URL) }

        // A start from the background is dropped by the system; nothing is started at all.
        advanceTimeBy(120_000)
        runCurrent()
        assertTrue(challenges.isEmpty())
        assertFalse("and nothing runs into the 30 s limit", call.isCompleted)

        ForegroundScreen.resumed(named)
        runCurrent()

        assertEquals(listOf(named to URL), challenges)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `initialized without a Context, a named Activity that is replaced is followed, not waited for forever`() = screenTest {
        // The deprecated initialize(config): the SDK watches nothing until a call names a screen.
        val named = lifecycleActivity(Lifecycle.State.STARTED)
        val call = async { session().handle3dsVerification(named, URL) }
        runCurrent()
        assertTrue("in the background, so nothing opens yet", challenges.isEmpty())

        val system = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(system.capture())

        // A rotation: the named one goes for good, the recreated one comes to the front.
        whenever(named.isDestroyed).thenReturn(true)
        system.firstValue.onActivityDestroyed(named)
        runCurrent()
        assertFalse(call.isCompleted)
        val recreated = activity()
        system.firstValue.onActivityResumed(recreated)
        runCurrent()

        assertEquals(listOf(recreated to URL), challenges)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `a challenge that turns up after its call gave up finds nothing, not the next verification`() = screenTest {
        ForegroundScreen.install(context)
        ForegroundScreen.resumed(activity())
        val first = async { runCatching { session().handle3dsVerification(URL) } }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(
            GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE,
            (first.await().exceptionOrNull() as GopaySDKException).code
        )

        val second = async { session().handle3dsVerification(URL) }
        runCurrent()

        assertEquals(2, challengeOwners.size)
        // What each challenge looks up in its onCreate: the late one closes itself.
        assertNull(PaymentVerificationBridge.currentOwner(challengeOwners[0]))
        assertSame(PaymentVerificationBridge.currentOwner(), PaymentVerificationBridge.currentOwner(challengeOwners[1]))
        answerTheChallenge()
        second.await()
    }

    @Test
    fun `closing the session ends a verification that still waits for a screen, and the next one can start`() = screenTest {
        ForegroundScreen.install(context)
        val session = session()
        val waiting = async { runCatching { session.handle3dsVerification(URL) } }
        runCurrent()

        session.close()
        runCurrent()

        val e = waiting.await().exceptionOrNull() as GopaySDKException
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertNull("the bridge is free", PaymentVerificationBridge.currentOwner())

        // The next payment is not told a verification is in progress.
        ForegroundScreen.resumed(activity())
        val next = async { session().handle3dsVerification(URL) }
        runCurrent()
        assertEquals(1, challenges.size)
        answerTheChallenge()
        next.await()
    }

    @Test
    fun `closing the session leaves a challenge that is on screen`() = screenTest {
        ForegroundScreen.install(context)
        ForegroundScreen.resumed(activity())
        val session = session()
        val call = async { session.handle3dsVerification(URL) }
        runCurrent()
        val owner = PaymentVerificationBridge.currentOwner()!!
        PaymentVerificationBridge.attach(owner) { throw AssertionError("the challenge stays") }

        session.close()
        runCurrent()

        assertFalse(call.isCompleted)
        PaymentVerificationBridge.complete(owner)
        call.await()
    }

    @Test
    fun `with no screen in front the challenge waits for one, however long, and then opens`() = screenTest {
        ForegroundScreen.install(context)
        val call = async { session().handle3dsVerification(URL) }

        // In the background for two minutes: nothing opens, nothing fails.
        advanceTimeBy(120_000)
        runCurrent()
        assertTrue(challenges.isEmpty())
        assertFalse(call.isCompleted)

        val back = activity()
        ForegroundScreen.resumed(back)
        runCurrent()

        assertEquals(listOf(back to URL), challenges)
        answerTheChallenge()
        call.await()
    }

    @Test
    fun `cancelling the call ends the wait for a screen and frees the bridge`() = screenTest {
        ForegroundScreen.install(context)
        val call = async { session().handle3dsVerification(URL) }
        runCurrent()

        call.cancelAndJoin()
        ForegroundScreen.resumed(activity())
        runCurrent()

        assertTrue(call.isCancelled)
        assertTrue("a screen coming later opens nothing", challenges.isEmpty())
        assertNull(PaymentVerificationBridge.currentOwner())
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a second verification while the first waits for a screen is PAYMENT_008`() = screenTest {
        ForegroundScreen.install(context)
        val first = async { session().handle3dsVerification(URL) }
        runCurrent()

        val e = failure { session().handle3dsVerification(URL) }

        assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS, e.code)
        first.cancelAndJoin()
    }

    @Test
    fun `a challenge that was started and did not appear within 30 seconds is PAYMENT_010`() = screenTest {
        ForegroundScreen.install(context)
        ForegroundScreen.resumed(activity())
        val call = async { runCatching { session().handle3dsVerification(URL) } }
        runCurrent()
        assertEquals(1, challenges.size)

        advanceTimeBy(29_999)
        runCurrent()
        assertFalse(call.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        val e = call.await().exceptionOrNull() as GopaySDKException
        assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.code)
        assertNull(PaymentVerificationBridge.currentOwner())
    }

    // ---- awaitChargeState in automatic mode

    private fun actionRequired() = ChargePaymentResponse(
        id = "ch-1",
        state = ChargeState.ACTION_REQUIRED,
        action = ChargeAction(actionType = ChargeActionType.EMV3DS, redirectUrl = URL)
    )

    private fun succeeded() = ChargePaymentResponse(id = "ch-1", state = ChargeState.SUCCEEDED)

    @Test
    fun `awaitChargeState without an Activity reads the charge while it waits for a screen, then opens the challenge`() = screenTest {
        ForegroundScreen.install(context)
        var answer = actionRequired()
        val api = mock<PaymentApi> { onBlocking { getChargeState(any()) }.thenAnswer { Response.success(answer) } }
        val call = async { session(api).awaitChargeState(AwaitChargeOptions(intervalMs = 1_000)) }

        advanceTimeBy(3_500)
        runCurrent()
        assertTrue("no screen, no challenge", challenges.isEmpty())
        verify(api, atLeast(4)).getChargeState("pay-1")

        val back = activity()
        ForegroundScreen.resumed(back)
        runCurrent()
        assertEquals(listOf(back to URL), challenges)

        answer = succeeded()
        answerTheChallenge()
        assertEquals(ChargeState.SUCCEEDED, call.await().state)
    }

    @Test
    fun `awaitChargeState returns a charge that was decided while it waited for a screen`() = screenTest {
        ForegroundScreen.install(context)
        val api = mock<PaymentApi> {
            onBlocking { getChargeState(any()) }.thenReturn(
                Response.success(actionRequired()), Response.success(actionRequired()), Response.success(succeeded())
            )
        }

        val state = session(api).awaitChargeState(AwaitChargeOptions(intervalMs = 1_000))

        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertTrue("answered elsewhere, so no challenge", challenges.isEmpty())
        assertNull(PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `closing the session ends an awaitChargeState that waits for a screen`() = screenTest {
        ForegroundScreen.install(context)
        val api = mock<PaymentApi> {
            onBlocking { getChargeState(any()) }.thenReturn(Response.success(actionRequired()))
        }
        val session = session(api)
        val call = async { runCatching { session.awaitChargeState(AwaitChargeOptions(intervalMs = 1_000)) } }
        advanceTimeBy(1_500)

        session.close()
        advanceTimeBy(1_000)
        runCurrent()

        val e = call.await().exceptionOrNull() as GopaySDKException
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertTrue(challenges.isEmpty())
    }

    @Test
    fun `awaitChargeState keeps reading when the screen goes as the challenge is about to open`() = screenTest {
        ForegroundScreen.install(context)
        var answer = actionRequired()
        val api = mock<PaymentApi> { onBlocking { getChargeState(any()) }.thenAnswer { Response.success(answer) } }
        // In front when the loop looks, in the background by the time the challenge is to open.
        val named = lifecycleActivity(Lifecycle.State.RESUMED, Lifecycle.State.STARTED)

        val call = async { session(api).awaitChargeState(AwaitChargeOptions(intervalMs = 1_000, activity = named)) }
        advanceTimeBy(3_500)
        runCurrent()

        assertTrue(challenges.isEmpty())
        assertNull("the verification does not sit on the bridge meanwhile", PaymentVerificationBridge.currentOwner())
        verify(api, atLeast(4)).getChargeState("pay-1")

        answer = succeeded()
        advanceTimeBy(1_000)
        assertEquals("the charge was decided elsewhere", ChargeState.SUCCEEDED, call.await().state)
    }

    @Test
    fun `awaitChargeState without a Context follows the Activity that replaces the named one in a rotation`() = screenTest {
        // The deprecated initialize(config), and the Activity named in the options, as in 2.x.
        var answer = ChargePaymentResponse(id = "ch-1", state = ChargeState.PROCESSING)
        val api = mock<PaymentApi> { onBlocking { getChargeState(any()) }.thenAnswer { Response.success(answer) } }
        val named = lifecycleActivity(Lifecycle.State.RESUMED)
        val call = async {
            runCatching { session(api).awaitChargeState(AwaitChargeOptions(intervalMs = 1_000, activity = named)) }
        }
        advanceTimeBy(1_500)
        val system = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(system.capture())

        // The user rotates the phone, and only then does the gateway ask for the challenge.
        whenever(named.isDestroyed).thenReturn(true)
        system.firstValue.onActivityDestroyed(named)
        answer = actionRequired()
        advanceTimeBy(3_000)
        runCurrent()

        assertFalse("not CONFIG_004 in the middle of a payment", call.isCompleted)
        assertTrue(challenges.isEmpty())
        assertTrue(reported.isEmpty())

        val recreated = activity()
        system.firstValue.onActivityResumed(recreated)
        runCurrent()
        assertEquals(listOf(recreated to URL), challenges)

        answer = succeeded()
        answerTheChallenge()
        assertEquals(ChargeState.SUCCEEDED, call.await().getOrThrow().state)
    }

    @Test
    fun `a session closed just as its verification claims the bridge still frees it`() = screenTest {
        ForegroundScreen.install(context)
        val session = session()
        // Closed after the call began and before the session noted its verification: the
        // Activity is asked for its application Context in between.
        val named = mock<Activity> {
            on { applicationContext }.thenAnswer { session.close(); application }
        }
        ForegroundScreen.resumed(named)
        ForegroundScreen.paused(named)

        val e = failure { session.handle3dsVerification(named, URL) }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertTrue(challenges.isEmpty())
        assertNull("the next payment can verify", PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `awaitChargeState uses the Activity in its options while it lives`() = screenTest {
        var answer = actionRequired()
        val api = mock<PaymentApi> { onBlocking { getChargeState(any()) }.thenAnswer { Response.success(answer) } }
        val named = activity()

        val call = async { session(api).awaitChargeState(AwaitChargeOptions(intervalMs = 1_000, activity = named)) }
        runCurrent()

        assertEquals(listOf(named to URL), challenges)
        answer = succeeded()
        answerTheChallenge()
        assertEquals(ChargeState.SUCCEEDED, call.await().state)
    }

    // ---- the wallet and the card charges

    @Test
    fun `Google Pay does not wait, without a screen in front it is PAYMENT_003 at once`() = screenTest {
        ForegroundScreen.install(context)
        val api = mock<PaymentApi>()

        val e = failure { session(api).chargeWithWallet() }

        assertEquals(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, e.code)
        assertTrue(e.message!!.contains("no screen in the foreground"))
        assertEquals("no virtual time passed", 0L, testScheduler.currentTime)
        assertTrue(wallets.isEmpty())
        verifyNoInteractions(api)
    }

    @Test
    fun `Google Pay with its named Activity in the background is PAYMENT_003 at once`() = screenTest {
        ForegroundScreen.install(context)
        val api = mock<PaymentApi>()

        val e = failure { session(api).chargeWithWallet(activity(inFront = false)) }

        assertEquals(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, e.code)
        assertEquals("not after the 30 s a dropped start would take", 0L, testScheduler.currentTime)
        assertTrue(wallets.isEmpty())
        verifyNoInteractions(api)
    }

    @Test
    fun `Google Pay without an Activity and without the Context is CONFIG_004`() = screenTest {
        val api = mock<PaymentApi>()

        val e = failure { session(api).chargeWithWallet() }

        assertEquals(GopayErrorCodes.CONFIG_MISSING_CONTEXT, e.code)
        verifyNoInteractions(api)
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `a card charge that has to derive the browser data without any Context is CONFIG_004, before anything is sent`() = screenTest {
        val api = mock<PaymentApi>()
        val session = session(api)

        val token = failure { session.chargeWithCardToken("tok") }
        val jwe = failure { session.chargeWithEncryptedCard("h.k.i.c.t") }

        assertEquals(GopayErrorCodes.CONFIG_MISSING_CONTEXT, token.code)
        assertEquals(GopayErrorCodes.CONFIG_MISSING_CONTEXT, jwe.code)
        verifyNoInteractions(api)
        assertEquals("each reported once", listOf(token, jwe), reported)
    }

    @Test
    fun `a card charge given its browser data needs neither an Activity nor the Context`() = screenTest {
        val api = mock<PaymentApi> {
            onBlocking { chargePayment(any(), any()) }.thenReturn(Response.success(succeeded()))
        }

        val charged = session(api).chargeWithCardToken("tok", browserData)

        assertEquals(ChargeState.SUCCEEDED, charged.state)
    }

    /** A live session, opened the way the SDK opens one, over a faked payment API. */
    private suspend fun session(api: PaymentApi = mock()): PaymentSession =
        PaymentSession.Factory(
            authApi = object : AuthApi {
                override suspend fun token(
                    authorization: String,
                    grantType: String,
                    scope: String?
                ): Response<TokenResponse> =
                    Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
            },
            paymentApiBuilder = { api },
            publicApi = { throw UnsupportedOperationException() }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}

    private companion object {
        const val URL = "https://acs.bank.example/challenge/1"
    }
}
