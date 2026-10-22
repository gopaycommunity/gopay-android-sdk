package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargeAction
import cz.gopay.sdk.model.ChargeActionType
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

        assertEquals(GopayErrorCodes.PAYMENT_DECLINED, e.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_DECLINED, e.errorCode)
        assertSame(failed, e.chargeState)
        assertEquals("Charge failed", e.message)
    }

    @Test
    fun `a charge that never leaves PROCESSING throws PAYMENT_007 at the initial timeout`() = runTest {
        val gateway = Gateway(this, *Array(100) { state(ChargeState.PROCESSING) })

        val e = assertThrowsSdk { manual(gateway).run() }

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

            assertEquals("$missing", GopayErrorCodes.PAYMENT_TIMEOUT, manual.errorCode)
            assertEquals("$missing", GopayErrorCodes.PAYMENT_TIMEOUT, automatic.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

        assertEquals(GopayErrorCodes.PAYMENT_TIMEOUT, e.errorCode)
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

    private fun state(state: ChargeState, failReason: String? = null) =
        ChargePaymentResponse(id = "ch-1", state = state, failReason = failReason)

    private fun actionRequired(redirectUrl: String?) = ChargePaymentResponse(
        id = "ch-1",
        state = ChargeState.ACTION_REQUIRED,
        action = ChargeAction(actionType = ChargeActionType.EMV3DS, redirectUrl = redirectUrl)
    )
}
