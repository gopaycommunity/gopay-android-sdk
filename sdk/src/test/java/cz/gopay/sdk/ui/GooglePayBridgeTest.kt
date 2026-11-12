package cz.gopay.sdk.ui

import android.app.Activity
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.AutoResolveHelper
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.testing.SharedSdkState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The bridge between `chargeWithWallet` and its launcher activity: one request at a time, and
 * no report from a request that is already over can reach the one after it.
 */
class GooglePayBridgeTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val owners = mutableListOf<GooglePayBridge.Owner>()

    @After
    fun tearDown() {
        owners.forEach(GooglePayBridge::clear)
    }

    private fun register(d: CompletableDeferred<Result<String>>): GooglePayBridge.Owner? =
        GooglePayBridge.register(d)?.also { owners += it }

    @Test
    fun `register hands out no owner while a sheet is up`() {
        assertNotNull(register(CompletableDeferred()))
        assertNull(register(CompletableDeferred()))
    }

    @Test
    fun `a report reaches the caller once`() {
        val d = CompletableDeferred<Result<String>>()
        val owner = register(d)!!

        GooglePayBridge.report(owner, Result.success("""{"protocolVersion":"ECv2"}"""))
        GooglePayBridge.report(owner, GooglePayBridge.dismissed())

        assertEquals("""{"protocolVersion":"ECv2"}""", d.getCompleted().getOrNull())
        assertNull(GooglePayBridge.currentOwner(owner.id))
    }

    @Test
    fun `a late report of a finished request does not reach the next one`() {
        val first = register(CompletableDeferred())!!
        GooglePayBridge.clear(first)
        val next = CompletableDeferred<Result<String>>()
        val second = register(next)!!

        // The old sheet answers, then the old launcher finishes.
        GooglePayBridge.report(first, Result.success("token of the first payment"))
        GooglePayBridge.report(first, GooglePayBridge.dismissed())
        GooglePayBridge.clear(first)

        assertFalse(next.isCompleted)
        assertSame(second, GooglePayBridge.currentOwner(second.id))
    }

    @Test
    fun `a launcher finds only the request its intent names`() {
        val first = register(CompletableDeferred())!!
        GooglePayBridge.clear(first)
        val second = register(CompletableDeferred())!!

        // A launcher started for the first payment, arriving late, must not take the second.
        assertNull(GooglePayBridge.currentOwner(first.id))
        assertSame(second, GooglePayBridge.currentOwner(second.id))
    }

    @Test
    fun `a launcher that never appears is given up on after 30 seconds`() = runTest {
        val owner = GooglePayBridge.register(CompletableDeferred())!!
        val shown = async { GooglePayBridge.awaitShown(owner) }

        advanceTimeBy(29_999)
        runCurrent()
        assertFalse(shown.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        assertFalse("it never appeared", shown.await())
        assertNull("given up on in the same step", GooglePayBridge.currentOwner(owner.id))
        assertFalse("a launcher that turns up late closes itself", GooglePayBridge.attach(owner) {})
    }

    @Test
    fun `a launcher that takes the request over in the instant the limit runs out counts as shown`() = runTest {
        val owner = register(CompletableDeferred())!!
        var closed = false
        // Scheduled first, so at the 30th second the launcher attaches before the limit fires.
        launch {
            delay(30_000)
            assertTrue(GooglePayBridge.attach(owner) { closed = true })
        }
        val shown = async { GooglePayBridge.awaitShown(owner) }

        advanceTimeBy(30_000)
        runCurrent()

        assertTrue("the sheet is on its way up, the call must go on", shown.await())
        assertFalse(closed)
        assertSame(owner, GooglePayBridge.currentOwner(owner.id))
    }

    @Test
    fun `a launcher that attaches while the call gives up on it is closed, never left with no caller`() {
        // Two real threads, the limit cut to a millisecond. Whatever the order, a call that gave
        // up must not leave a launcher that was told it may stay and was not closed: that one
        // would go on to show a sheet nobody waits for.
        //
        // How long the launcher takes is steered by the round before, not set against the
        // clock: one that made it takes a little longer next time, one that was given up on is
        // quicker. So the two keep crossing on a machine of any speed, and the limit does run
        // out in some rounds wherever a millisecond's limit really ends here; a fixed time for
        // the launcher made it every round on a machine whose timers fire late.
        val limit = SdkScreens.showTimeoutMs
        SdkScreens.showTimeoutMs = 1
        var launcherNanos = 1_000_000L
        var gaveUp = 0
        try {
            repeat(3_000) { round ->
                val owner = GooglePayBridge.register(CompletableDeferred())!!
                val closed = AtomicBoolean(false)
                val mayStay = AtomicBoolean(false)
                val takes = launcherNanos
                val launcher = Thread {
                    val until = System.nanoTime() + takes
                    while (System.nanoTime() < until) Thread.onSpinWait()
                    mayStay.set(GooglePayBridge.attach(owner) { closed.set(true) })
                }
                launcher.start()
                val shown = runBlocking { GooglePayBridge.awaitShown(owner) }
                launcher.join()

                if (!shown) {
                    gaveUp++
                    assertTrue("round $round: left on screen with no caller", !mayStay.get() || closed.get())
                    assertNull(GooglePayBridge.currentOwner(owner.id))
                    launcherNanos = takes - takes / 10
                } else {
                    assertFalse("round $round: closed under a caller that goes on", closed.get())
                    launcherNanos = minOf(takes + takes / 10 + 20_000, SLOWEST_LAUNCHER_NANOS)
                }
                GooglePayBridge.clear(owner)
            }
        } finally {
            SdkScreens.showTimeoutMs = limit
        }
        assertTrue("the limit did run out in some rounds", gaveUp > 0)
    }

    @Test
    fun `a launcher that appeared, or a request that ended, is not waited for`() = runTest {
        val attached = GooglePayBridge.register(CompletableDeferred())!!
        val shown = async { GooglePayBridge.awaitShown(attached) }
        runCurrent()
        assertTrue(GooglePayBridge.attach(attached) {})
        assertTrue(shown.await())
        // The sheet may then stay up for as long as the user takes.
        advanceTimeBy(600_000)
        GooglePayBridge.clear(attached)

        val reported = GooglePayBridge.register(CompletableDeferred())!!
        val ended = async { GooglePayBridge.awaitShown(reported) }
        runCurrent()
        GooglePayBridge.report(reported, GooglePayBridge.dismissed())
        assertTrue(ended.await())
        assertEquals("no virtual time was waited out for either", 600_000L, currentTime)
    }

    @Test
    fun `a sheet that returns payment data hands over its JSON`() {
        val result = GooglePayBridge.resultFor(Activity.RESULT_OK, "{\"x\":1}", null, null)

        assertEquals("{\"x\":1}", result.getOrNull())
    }

    @Test
    fun `RESULT_OK without payment data is a failure, not a crash`() {
        val failure = GooglePayBridge.resultFor(Activity.RESULT_OK, null, null, null).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(
            GopayErrorCodes.PAYMENT_PROCESSING_FAILED,
            GooglePayBridge.failureFor(failure!!).code
        )
    }

    @Test
    fun `a closed sheet and Back on the launcher both read as the user leaving`() {
        val closed = GooglePayBridge.resultFor(Activity.RESULT_CANCELED, null, null, null)

        assertTrue(closed.exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
        assertTrue(GooglePayBridge.dismissed().exceptionOrNull() is GooglePayBridge.UserCancelledThrowable)
    }

    @Test
    fun `a Google Pay error tells an unavailable sheet from a failed one`() {
        val unavailable = GooglePayBridge.resultFor(
            AutoResolveHelper.RESULT_ERROR, null, CommonStatusCodes.SERVICE_DISABLED, "disabled"
        )
        val failed = GooglePayBridge.resultFor(
            AutoResolveHelper.RESULT_ERROR, null, CommonStatusCodes.DEVELOPER_ERROR, "merchant"
        )

        assertTrue(unavailable.exceptionOrNull() is GooglePayBridge.UnavailableThrowable)
        assertFalse(failed.exceptionOrNull() is GooglePayBridge.UnavailableThrowable)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("merchant"))
    }

    @Test
    fun `an undocumented result code is a failure rather than no report`() {
        val failure = GooglePayBridge.resultFor(42, null, null, null).exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("42"))
    }

    @Test
    fun `a dismissed sheet surfaces as PAYMENT_CANCELLED, not as a cancellation`() {
        // A GopaySDKException by type, so never a CancellationException.
        val failure = GooglePayBridge.failureFor(GooglePayBridge.UserCancelledThrowable())

        assertEquals(GopayErrorCodes.PAYMENT_CANCELLED, failure.code)
    }

    @Test
    fun `a sheet Google Pay cannot show is PAYMENT_004`() {
        val cause = GooglePayBridge.UnavailableThrowable("Google Play services disabled")

        val failure = GooglePayBridge.failureFor(cause)

        assertEquals(GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE, failure.code)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `any other Google Pay failure is PAYMENT_003, never PAYMENT_009`() {
        val causes = listOf(
            Exception("Google Pay error: merchant account error (409)"),
            IllegalStateException("PaymentData is null"),
            IllegalArgumentException("Missing signature in Google Pay token")
        )
        for (cause in causes) {
            val failure = GooglePayBridge.failureFor(cause)

            assertEquals("$cause", GopayErrorCodes.PAYMENT_PROCESSING_FAILED, failure.code)
            assertSame(cause, failure.cause)
        }
    }

    @Test
    fun `only a missing or outdated Google Play service counts as unavailable`() {
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.API_NOT_CONNECTED))
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.SERVICE_DISABLED))
        assertTrue(GooglePayBridge.isUnavailable(CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED))
        assertFalse(GooglePayBridge.isUnavailable(CommonStatusCodes.DEVELOPER_ERROR))
        assertFalse(GooglePayBridge.isUnavailable(CommonStatusCodes.INTERNAL_ERROR))
        assertFalse(GooglePayBridge.isUnavailable(null))
    }

    private companion object {
        /**
         * As slow as the steered launcher gets, a thousand times the limit: reached only where
         * the limit never ran out before, and by then it does.
         */
        const val SLOWEST_LAUNCHER_NANOS = 1_000_000_000L
    }
}
