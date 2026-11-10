package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargeAction
import cz.gopay.sdk.model.ChargeActionType
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [ChargePoller] against faked charge states on virtual time, which it also reads as its clock:
 * nothing here waits for real. An undecided state is `REQUESTED`, `PROCESSING` or `UNKNOWN`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargePollerTest {

    /** Hands out [states] in order and records when each read started, in virtual ms. */
    private class Gateway(
        private val scope: TestScope,
        vararg states: ChargePaymentResponse,
        private val latencyMs: Long = 0
    ) {
        private val queue = ArrayDeque(states.toList())
        val readTimes = mutableListOf<Long>()
        val reads: Int get() = readTimes.size

        val read: suspend () -> ChargePaymentResponse = {
            readTimes.add(scope.currentTime)
            if (latencyMs > 0) delay(latencyMs)
            queue.removeFirstOrNull() ?: throw AssertionError("read #$reads past the scripted states")
        }
    }

    /** Everything the host and the challenge see, in order: `state:X`, `action:url`, `verify:url`. */
    private val events = mutableListOf<String>()
    private val states: List<String> get() = events.filter { it.startsWith("state:") }
    private val actions: List<String> get() = events.filter { it.startsWith("action:") }
    private val verifications: List<String> get() = events.filter { it.startsWith("verify:") }

    private val verify: suspend (String) -> Unit = { events.add("verify:$it") }

    private fun options(intervalMs: Long = 1_000, initialTimeoutMs: Long = 30_000) =
        AwaitChargeOptions(
            intervalMs = intervalMs,
            initialTimeoutMs = initialTimeoutMs,
            onStateChange = { events.add("state:${it.state}") },
            onActionRequired = { events.add("action:$it") }
        )

    /** A poller in automatic mode, with [verify] standing in for the 3DS screen. */
    private fun TestScope.automatic(
        gateway: Gateway,
        verify: suspend (String) -> Unit = this@ChargePollerTest.verify,
        options: AwaitChargeOptions = options()
    ) = ChargePoller(options, gateway.read, verify) { currentTime }

    /** A poller in manual mode: the host runs the challenge, so there is nothing to verify with. */
    private fun TestScope.manual(gateway: Gateway, options: AwaitChargeOptions = options()) =
        ChargePoller(options, gateway.read, verify = null) { currentTime }

    @Test
    fun `an immediate SUCCEEDED returns after one read`() = runTest {
        val gateway = Gateway(this, state(ChargeState.SUCCEEDED))

        val result = manual(gateway).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(1, gateway.reads)
        assertEquals(listOf("state:SUCCEEDED"), events)
        assertEquals(0, currentTime)
    }

    @Test
    fun `an undecided charge is read again after the interval until SUCCEEDED`() = runTest {
        val gateway = Gateway(
            this, state(ChargeState.REQUESTED), state(ChargeState.PROCESSING), state(ChargeState.SUCCEEDED)
        )

        val result = manual(gateway).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(listOf(0L, 1_000L, 2_000L), gateway.readTimes)
        assertEquals(listOf("state:REQUESTED", "state:PROCESSING", "state:SUCCEEDED"), states)
    }

    @Test
    fun `the interval starts when the answer arrives, not when the request went out`() = runTest {
        val gateway = Gateway(
            this, state(ChargeState.PROCESSING), state(ChargeState.PROCESSING), state(ChargeState.SUCCEEDED),
            latencyMs = 300
        )

        manual(gateway).run()

        assertEquals(listOf(0L, 1_300L, 2_600L), gateway.readTimes)
    }

    @Test
    fun `FAILED throws PAYMENT_006 carrying the charge and its fail reason`() = runTest {
        val failed = state(ChargeState.FAILED, failReason = "Insufficient funds")
        val gateway = Gateway(this, state(ChargeState.PROCESSING), failed)

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_DECLINED, e.code)
        assertSame(failed, e.chargeState)
        assertEquals("Charge failed: Insufficient funds", e.message)
        assertEquals("the final state is reported before the error", "state:FAILED", events.last())
    }

    @Test
    fun `FAILED without a fail reason throws PAYMENT_006 with the bare message`() = runTest {
        // A declined card on the sandbox may come this way, without a fail_reason.
        val failed = state(ChargeState.FAILED)
        val gateway = Gateway(this, failed)

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_DECLINED, e.code)
        assertSame(failed, e.chargeState)
        assertEquals("Charge failed", e.message)
    }

    @Test
    fun `a charge that never leaves PROCESSING throws PAYMENT_007 at the initial timeout`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.PROCESSING) })

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals(30_000L, currentTime)
        // Reads at 0, 1000, …, 30000: the one on the deadline decides, and comes on the error.
        assertEquals(31, gateway.reads)
        assertEquals(30_000L, gateway.readTimes.last())
        assertEquals(ChargeState.PROCESSING, e.chargeState?.state)
        assertEquals(
            "Charge did not leave PROCESSING within 30 s; read getChargeState() or wait for " +
                "the payment notification before offering a new payment",
            e.message
        )
    }

    @Test
    fun `the last wait is cut to what is left of the timeout`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.PROCESSING) })

        val e = assertThrowsSdk { manual(gateway, options(intervalMs = 7_000)).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals(listOf(0L, 7_000L, 14_000L, 21_000L, 28_000L, 30_000L), gateway.readTimes)
    }

    @Test
    fun `a charge that moves on the deadline read is handled as that state`() = runTest {
        val gateway = Gateway(this, *Array(30) { state(ChargeState.PROCESSING) }, state(ChargeState.SUCCEEDED))

        val result = manual(gateway).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(30_000L, gateway.readTimes.last())
    }

    @Test
    fun `UNKNOWN counts as undecided and is covered by the timeout`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.UNKNOWN) })

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals(ChargeState.UNKNOWN, e.chargeState?.state)
    }

    @Test
    fun `an answer that arrives after the deadline is never cut off`() = runTest {
        // Reads at 0, 6, …, 24 and on the deadline at 30; each answers 5 s later, the last one
        // SUCCEEDED at 35 s, past the deadline.
        val gateway = Gateway(
            this, *Array(5) { state(ChargeState.PROCESSING) }, state(ChargeState.SUCCEEDED),
            latencyMs = 5_000
        )

        val result = manual(gateway).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(30_000L, gateway.readTimes.last())
        assertEquals(35_000L, currentTime)
    }

    @Test
    fun `a charge still undecided after a late answer throws PAYMENT_007 without another read`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.PROCESSING) }, latencyMs = 5_000)

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals(6, gateway.reads)
        assertEquals(35_000L, currentTime)
    }

    @Test
    fun `manual mode disarms the timeout for good on the first reported address`() = runTest {
        val gateway = Gateway(
            this,
            state(ChargeState.PROCESSING),
            actionRequired("https://acs.example/1"),
            *Array(120) { state(ChargeState.PROCESSING) },   // two minutes, well past 30 s
            state(ChargeState.SUCCEEDED)
        )

        val result = manual(gateway).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(123, gateway.reads)
        assertEquals(listOf("action:https://acs.example/1"), actions)
    }

    @Test
    fun `an ACTION_REQUIRED without an address reports nothing and leaves the timeout running`() = runTest {
        for (missing in listOf(null, "")) {
            events.clear()
            val gateway = Gateway(this, *Array(100) { actionRequired(missing) })

            val manual = assertThrowsSdk { manual(gateway).run() }
            val automatic = assertThrowsSdk { automatic(gateway).run() }

            assertEquals("$missing", GopayErrorCodes.PAYMENT_TIMEOUT, manual.code)
            assertEquals("$missing", GopayErrorCodes.PAYMENT_TIMEOUT, automatic.code)
            assertEquals("$missing", ChargeState.ACTION_REQUIRED, automatic.chargeState?.state)
            assertTrue("$missing", actions.isEmpty())
            assertTrue("$missing", verifications.isEmpty())
        }
    }

    @Test
    fun `automatic mode reports the address, runs the challenge once and polls on after the interval`() = runTest {
        val gateway = Gateway(
            this,
            actionRequired("https://acs.example/1"),
            actionRequired("https://acs.example/1"),   // the gateway echoes the action
            state(ChargeState.PROCESSING),
            state(ChargeState.SUCCEEDED)
        )
        val verify: suspend (String) -> Unit = { url ->
            events.add("verify:$url")
            delay(20_000)   // the user takes a while in the challenge
        }

        val result = automatic(gateway, verify).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(
            listOf(
                "state:ACTION_REQUIRED", "action:https://acs.example/1", "verify:https://acs.example/1",
                "state:ACTION_REQUIRED", "state:PROCESSING", "state:SUCCEEDED"
            ),
            events
        )
        // The next read comes one interval after the challenge, like after any other answer.
        assertEquals(listOf(0L, 21_000L, 22_000L, 23_000L), gateway.readTimes)
    }

    @Test
    fun `automatic mode starts the timeout over once the challenge returns`() = runTest {
        val gateway = Gateway(
            this,
            *Array(20) { state(ChargeState.PROCESSING) },   // 20 s into the first timeout
            actionRequired("https://acs.example/1"),
            *Array(100) { state(ChargeState.PROCESSING) }
        )
        val verify: suspend (String) -> Unit = { delay(60_000) }

        val e = assertThrowsSdk { automatic(gateway, verify).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        // 20 s of polling, the read at 20 s, 60 s in the challenge, then a fresh 30 s.
        assertEquals(20_000L + 60_000L + 30_000L, currentTime)
        assertEquals(ChargeState.PROCESSING, e.chargeState?.state)
    }

    @Test
    fun `automatic mode runs a new address again, and the same one never twice`() = runTest {
        val gateway = Gateway(
            this,
            actionRequired("https://acs.example/1"),
            actionRequired("https://acs.example/1"),
            actionRequired("https://acs.example/2"),
            actionRequired("https://acs.example/1"),
            state(ChargeState.SUCCEEDED)
        )

        automatic(gateway).run()

        assertEquals(listOf("verify:https://acs.example/1", "verify:https://acs.example/2"), verifications)
        assertEquals(listOf("action:https://acs.example/1", "action:https://acs.example/2"), actions)
    }

    @Test
    fun `an echoed address after the challenge does not disarm the restarted timeout`() = runTest {
        val gateway = Gateway(this, *Array(100) { actionRequired("https://acs.example/1") })

        val e = assertThrowsSdk { automatic(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals(listOf("verify:https://acs.example/1"), verifications)
        assertEquals(30_000L, currentTime)
    }

    @Test
    fun `a failed challenge ends the call with the verification's own error`() = runTest {
        val gateway = Gateway(this, actionRequired("https://acs.example/1"), state(ChargeState.SUCCEEDED))
        val closed = GopaySDKException(GopayErrorCodes.PAYMENT_CANCELLED, "3DS verification was closed")
        val verify: suspend (String) -> Unit = { throw closed }

        val e = assertThrowsSdk { automatic(gateway, verify).run() }

        assertSame(closed, e)
        assertEquals("no read after the challenge failed", 1, gateway.reads)
    }

    @Test
    fun `manual mode reports each new address once and never opens the challenge`() = runTest {
        val gateway = Gateway(
            this,
            actionRequired("https://acs.example/1"),
            actionRequired("https://acs.example/1"),
            actionRequired("https://acs.example/2"),
            actionRequired(null),
            state(ChargeState.SUCCEEDED)
        )

        manual(gateway).run()

        assertEquals(listOf("action:https://acs.example/1", "action:https://acs.example/2"), actions)
        assertTrue(verifications.isEmpty())
        assertEquals(5, gateway.reads)
    }

    @Test
    fun `a failed read ends the call with that error unchanged`() = runTest {
        val offline = GopaySDKException(GopayErrorCodes.NETWORK_NO_CONNECTION, "offline")
        var reads = 0
        val read: suspend () -> ChargePaymentResponse = {
            if (reads++ == 0) state(ChargeState.PROCESSING) else throw offline
        }

        val e = assertThrowsSdk { ChargePoller(options(), read, verify = null) { currentTime }.run() }

        assertSame(offline, e)
        assertEquals(2, reads)
    }

    @Test
    fun `cancelling the caller stops the polling without another read`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.PROCESSING) })
        val call = async { manual(gateway).run() }
        advanceTimeBy(2_500)
        assertEquals(3, gateway.reads)

        call.cancel()
        advanceTimeBy(60_000)

        assertEquals(3, gateway.reads)
        assertTrue(call.isCancelled)
        try {
            call.await()
            fail("expected a CancellationException")
        } catch (e: CancellationException) {
            // The caller's own cancellation, not an SDK error.
        }
    }

    /** Runs inside the test's own scope, so virtual time keeps flowing through [block]. */
    private suspend fun assertThrowsSdk(block: suspend () -> Unit): GopaySDKException {
        try {
            block()
        } catch (e: GopaySDKException) {
            return e
        }
        throw AssertionError("expected a GopaySDKException")
    }

    // ---- no screen to open the challenge on: the loop waits for one and keeps reading

    /** How many times the wait for a screen was let go of, by the screen coming or by the loop. */
    private var screenWaitsEnded = 0

    /** A poller in automatic mode with no screen in front until [screen] completes. */
    private fun TestScope.withoutScreen(
        gateway: Gateway,
        screen: CompletableDeferred<Unit>,
        options: AwaitChargeOptions = options(),
        read: suspend () -> ChargePaymentResponse = gateway.read
    ) = ChargePoller(
        options,
        read,
        verify,
        screenReady = { screen.isCompleted },
        awaitScreen = { try { screen.await() } finally { screenWaitsEnded++ } }
    ) { currentTime }

    /** The screen comes to the front at [atMs] of virtual time. */
    private fun TestScope.screenAt(atMs: Long) = CompletableDeferred<Unit>().also { screen ->
        backgroundScope.launch { delay(atMs); screen.complete(Unit) }
    }

    @Test
    fun `without a screen the charge is read while the challenge waits, and opens when the screen comes`() = runTest {
        val gateway = Gateway(
            this, actionRequired("https://acs/1"), actionRequired("https://acs/1"), actionRequired("https://acs/1"),
            state(ChargeState.SUCCEEDED)
        )

        val result = withoutScreen(gateway, screenAt(2_500)).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(
            listOf(
                "state:ACTION_REQUIRED", "action:https://acs/1", "state:ACTION_REQUIRED", "state:ACTION_REQUIRED",
                "verify:https://acs/1", "state:SUCCEEDED"
            ),
            events
        )
        assertEquals("read every interval while waiting, then an interval after the challenge", listOf(0L, 1_000L, 2_000L, 3_500L), gateway.readTimes)
    }

    @Test
    fun `a screen that goes as the challenge is about to open sends the loop back to waiting, still reading`() = runTest {
        val gateway = Gateway(
            this, actionRequired("https://acs/1"), actionRequired("https://acs/1"), actionRequired("https://acs/1"),
            state(ChargeState.SUCCEEDED)
        )
        // In front when the loop looks, gone when the challenge is to open, back at 2.5 s.
        val back = screenAt(2_500)
        var attempts = 0
        val poller = ChargePoller(
            options(),
            gateway.read,
            verify = { url ->
                attempts++
                if (!back.isCompleted) throw ChargePoller.ScreenWentAway()
                events.add("verify:$url")
            },
            screenReady = { true },
            awaitScreen = { back.await() }
        ) { currentTime }

        val result = poller.run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals("tried at once, and again when the screen was back", 2, attempts)
        assertEquals("the address is reported once", listOf("action:https://acs/1"), actions)
        assertEquals(listOf("verify:https://acs/1"), verifications)
        assertEquals("read every interval while it waited again", listOf(0L, 1_000L, 2_000L, 3_500L), gateway.readTimes)
    }

    @Test
    fun `a charge decided after the screen went is returned without a challenge`() = runTest {
        val gateway = Gateway(this, actionRequired("https://acs/1"), actionRequired("https://acs/1"), state(ChargeState.SUCCEEDED))
        val poller = ChargePoller(
            options(),
            gateway.read,
            verify = { throw ChargePoller.ScreenWentAway() },
            screenReady = { true },
            awaitScreen = { CompletableDeferred<Unit>().await() }
        ) { currentTime }

        val result = poller.run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(listOf(0L, 1_000L, 2_000L), gateway.readTimes)
    }

    @Test
    fun `a charge that succeeds while the challenge waits for a screen is returned without a challenge`() = runTest {
        val gateway = Gateway(this, actionRequired("https://acs/1"), actionRequired("https://acs/1"), state(ChargeState.SUCCEEDED))

        val result = withoutScreen(gateway, CompletableDeferred()).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertTrue("no challenge was opened", verifications.isEmpty())
        assertEquals(listOf(0L, 1_000L, 2_000L), gateway.readTimes)
        assertEquals("the wait for a screen was let go of", 1, screenWaitsEnded)
    }

    @Test
    fun `a charge that fails while the challenge waits for a screen throws PAYMENT_006`() = runTest {
        val gateway = Gateway(this, actionRequired("https://acs/1"), state(ChargeState.FAILED, failReason = "PAYMENT_FAILED"))

        val e = failure { withoutScreen(gateway, CompletableDeferred()).run() }

        assertEquals(GopayErrorCodes.PAYMENT_DECLINED, e.code)
        assertEquals(ChargeState.FAILED, e.chargeState?.state)
        assertTrue(verifications.isEmpty())
    }

    @Test
    fun `a challenge the loop waited with and did not open is opened when the gateway asks again`() = runTest {
        val gateway = Gateway(
            this, actionRequired("https://acs/1"), state(ChargeState.PROCESSING), actionRequired("https://acs/1"),
            state(ChargeState.SUCCEEDED)
        )

        val result = withoutScreen(gateway, screenAt(1_500)).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals("the host hears of an address once", listOf("action:https://acs/1"), actions)
        assertEquals(listOf("verify:https://acs/1"), verifications)
        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L), gateway.readTimes)
    }

    @Test
    fun `a new address while waiting for a screen replaces the old challenge`() = runTest {
        val gateway = Gateway(this, actionRequired("https://acs/1"), actionRequired("https://acs/2"), state(ChargeState.SUCCEEDED))

        withoutScreen(gateway, screenAt(1_500)).run()

        assertEquals(listOf("action:https://acs/1", "action:https://acs/2"), actions)
        assertEquals("only the address the gateway asks for now", listOf("verify:https://acs/2"), verifications)
    }

    @Test
    fun `the timeout does not run while the challenge waits for a screen`() = runTest {
        // 45 s in the background, past the 30 s timeout, then the user comes back.
        val waiting = Array(45) { actionRequired("https://acs/1") }
        val gateway = Gateway(this, *waiting, state(ChargeState.SUCCEEDED))

        val result = withoutScreen(gateway, screenAt(44_500)).run()

        assertEquals(ChargeState.SUCCEEDED, result.state)
        assertEquals(listOf("verify:https://acs/1"), verifications)
        assertEquals(45_500L, gateway.readTimes.last())
    }

    @Test
    fun `the timeout starts over from the state the charge moved to while waiting`() = runTest {
        val processing = Array(31) { state(ChargeState.PROCESSING) }
        val gateway = Gateway(this, actionRequired("https://acs/1"), *processing)

        val e = failure { withoutScreen(gateway, CompletableDeferred()).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.code)
        assertEquals("30 s from the read that found it moved, at 1 s", 31_000L, gateway.readTimes.last())
        assertTrue(verifications.isEmpty())
    }

    @Test
    fun `cancelling the call ends the wait for a screen and the reads`() = runTest {
        val gateway = Gateway(this, *Array(10) { actionRequired("https://acs/1") })
        val call = async { withoutScreen(gateway, CompletableDeferred()).run() }

        advanceTimeBy(2_500)
        call.cancel()
        advanceTimeBy(60_000)

        assertTrue(call.isCancelled)
        assertEquals("no read after the cancellation", listOf(0L, 1_000L, 2_000L), gateway.readTimes)
        assertEquals(1, screenWaitsEnded)
        assertTrue(verifications.isEmpty())
    }

    @Test
    fun `an error of a read while waiting for a screen ends the call with it, as a closed session does`() = runTest {
        val closed = GopaySDKException(code = GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, message = "closed")
        val gateway = Gateway(this, actionRequired("https://acs/1"))
        var reads = 0
        val read: suspend () -> ChargePaymentResponse = { if (reads++ == 0) gateway.read() else throw closed }

        val e = failure { withoutScreen(gateway, CompletableDeferred(), read = read).run() }

        assertSame(closed, e)
        assertEquals(1, screenWaitsEnded)
    }

    private suspend fun failure(block: suspend () -> Unit): GopaySDKException = try {
        block()
        throw AssertionError("expected a GopaySDKException")
    } catch (e: GopaySDKException) {
        e
    }

    private fun state(state: ChargeState, failReason: String? = null) =
        ChargePaymentResponse(id = "ch-1", state = state, failReason = failReason)

    private fun actionRequired(redirectUrl: String?) = ChargePaymentResponse(
        id = "ch-1",
        state = ChargeState.ACTION_REQUIRED,
        action = ChargeAction(actionType = ChargeActionType.EMV3DS, redirectUrl = redirectUrl)
    )
}
