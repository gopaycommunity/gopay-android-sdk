package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Covers the three ways a 3DS verification can end, and above all that a challenge which never
 * loaded is distinguishable from a user who dismissed one, and that a report belonging to a
 * verification that is already over cannot end the one running now.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentVerificationBridgeTest {

    private lateinit var owner: PaymentVerificationBridge.Owner

    @After
    fun tearDown() {
        if (::owner.isInitialized) PaymentVerificationBridge.dismiss(owner)
    }

    /** Registers [d] and keeps its token for [tearDown]. */
    private fun register(d: CompletableDeferred<Boolean>): PaymentVerificationBridge.Owner? =
        PaymentVerificationBridge.register(d)?.also { owner = it }

    @Test
    fun `register hands out no token while a verification is in flight`() {
        assertNotNull(register(CompletableDeferred()))
        assertNull(register(CompletableDeferred()))
    }

    @Test
    fun `the current owner is the one the caller registered`() {
        val o = register(CompletableDeferred())

        assertSame(o, PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `complete resolves the verification`() = runTest {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.complete(o)

        assertTrue(d.await())
    }

    @Test
    fun `cancel surfaces a closed challenge as PAYMENT_CANCELLED, not as a cancellation`() = runTest {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.cancel(o)

        // A CancellationException would read as the host's own scope going away.
        assertFalse(d.getCompletionExceptionOrNull() is CancellationException)
        try {
            d.await()
            fail("Expected GopaySDKException for a closed challenge")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.PAYMENT_CANCELLED, e.code)
        }
    }

    @Test
    fun `failUnreachable reports a dead challenge as PAYMENT_VERIFICATION_UNREACHABLE`() = runTest {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.failUnreachable(o, "HTTP 404")

        // The host tells a dead link from a dismissal by the code: PAYMENT_010 here,
        // PAYMENT_011 from cancel().
        try {
            d.await()
            fail("Expected GopaySDKException for a challenge that could not be loaded")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.code)
            assertTrue(e.message!!.contains("HTTP 404"))
        }
    }

    @Test
    fun `failInternally reports an SDK bug as INTERNAL_UNEXPECTED_ERROR`() = runTest {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.failInternally(o, "no redirect URL was supplied")

        try {
            d.await()
            fail("Expected GopaySDKException for a verification that could not start")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR, e.code)
            assertTrue(e.message!!.contains("no redirect URL"))
        }
    }

    @Test
    fun `a report after the bridge is released is a no-op`() {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!
        PaymentVerificationBridge.dismiss(o)

        PaymentVerificationBridge.failUnreachable(o, "HTTP 404")

        assertFalse(d.isCompleted)
    }

    @Test
    fun `a finished verification cannot cancel the one that replaced it`() {
        // The activity of the first verification reports, finishes, and its onDestroy runs only
        // after the host has started the next charge. Without the owner that late cancel ends a
        // challenge the user has not even seen, and the host is told the user dismissed it.
        val first = CompletableDeferred<Boolean>()
        val staleOwner = PaymentVerificationBridge.register(first)!!
        PaymentVerificationBridge.complete(staleOwner)
        PaymentVerificationBridge.dismiss(staleOwner)

        val second = CompletableDeferred<Boolean>()
        register(second)

        PaymentVerificationBridge.cancel(staleOwner)

        assertFalse("the running verification must survive", second.isCompleted)
    }

    @Test
    fun `releasing a finished verification does not release the next one`() {
        val first = CompletableDeferred<Boolean>()
        val staleOwner = PaymentVerificationBridge.register(first)!!
        PaymentVerificationBridge.complete(staleOwner)

        val second = CompletableDeferred<Boolean>()
        val current = register(second)

        // The first caller's finally lands late; the bridge must stay claimed by the second.
        PaymentVerificationBridge.dismiss(staleOwner)

        assertSame(current, PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `cancelling the caller closes the challenge and frees the bridge`() = runBlocking {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        var closed = 0
        // What the activity does in onCreate.
        PaymentVerificationBridge.attach(token) { closed++ }

        // Runs up to the point where it waits for the result, as the real caller does.
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            PaymentVerificationBridge.await(token, deferred)
        }
        caller.cancelAndJoin()

        assertEquals("the challenge must be closed once", 1, closed)
        assertNull("the next verification must be able to start", PaymentVerificationBridge.currentOwner())
        // The closing activity's own onDestroy reaches nothing.
        PaymentVerificationBridge.cancel(token)
        assertTrue(caller.isCancelled)
        assertFalse("the caller's cancellation must not turn into PAYMENT_011", deferred.isCompleted)
    }

    @Test
    fun `a user dismissal is not closed a second time`() = runBlocking {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        var closed = 0
        PaymentVerificationBridge.attach(token) { closed++ }

        PaymentVerificationBridge.cancel(token)
        try {
            PaymentVerificationBridge.await(token, deferred)
            fail("Expected the dismissal as PAYMENT_CANCELLED")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.PAYMENT_CANCELLED, e.code)
        }

        assertEquals("the activity closes itself after a dismissal", 0, closed)
    }

    @Test
    fun `dismissing a finished verification does not touch the one that replaced it`() {
        val first = register(CompletableDeferred())!!
        PaymentVerificationBridge.dismiss(first)
        val second = register(CompletableDeferred())!!
        var closed = 0
        PaymentVerificationBridge.attach(second) { closed++ }
        // A stale activity of the first verification tries to attach too.
        PaymentVerificationBridge.attach(first) { fail("a stale activity must not attach") }

        PaymentVerificationBridge.dismiss(first)

        assertEquals(0, closed)
        assertSame(second, PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `a challenge screen that never appears ends as PAYMENT_010 after 30 seconds`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { runCatching { PaymentVerificationBridge.await(token, deferred) } }

        advanceTimeBy(29_999)
        runCurrent()
        assertFalse("still within the limit", caller.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        val e = caller.await().exceptionOrNull() as GopaySDKException
        assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.code)
        assertTrue(e.message!!.contains("did not appear within 30 s"))
        assertNull("the next verification can start", PaymentVerificationBridge.currentOwner())
        assertFalse("a screen that turns up late has nothing to serve", PaymentVerificationBridge.attach(token) {})
    }

    @Test
    fun `a challenge screen that appeared is waited for as long as the user takes`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { PaymentVerificationBridge.await(token, deferred) }

        advanceTimeBy(10_000)
        PaymentVerificationBridge.attach(token) {}
        advanceTimeBy(600_000)
        runCurrent()
        assertFalse("ten minutes in the bank's app is not a timeout", caller.isCompleted)

        PaymentVerificationBridge.complete(token)
        caller.await()
    }

    @Test
    fun `a screen recreated after it appeared does not start the limit again`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { PaymentVerificationBridge.await(token, deferred) }
        PaymentVerificationBridge.attach(token) {}

        // A rotation: the recreated activity attaches again.
        advanceTimeBy(45_000)
        assertTrue(PaymentVerificationBridge.attach(token) {})
        advanceTimeBy(45_000)
        runCurrent()

        assertFalse(caller.isCompleted)
        PaymentVerificationBridge.complete(token)
        caller.await()
    }

    @Test
    fun `a report that comes before the screen attached ends the wait at once`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { runCatching { PaymentVerificationBridge.await(token, deferred) } }
        runCurrent()

        PaymentVerificationBridge.failInternally(token, "no redirect URL was supplied")
        runCurrent()

        assertEquals(0L, currentTime)
        assertEquals(
            GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR,
            (caller.await().exceptionOrNull() as GopaySDKException).code
        )
    }

    @Test
    fun `a screen that takes the verification over in the instant the limit runs out counts as shown`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        var closed = false
        // Scheduled first, so at the 30th second the screen attaches before the limit fires:
        // the wait for the screen is cut off by the limit although the screen is there.
        launch {
            delay(30_000)
            assertTrue(PaymentVerificationBridge.attach(token) { closed = true })
        }
        val caller = async { runCatching { PaymentVerificationBridge.await(token, deferred) } }

        advanceTimeBy(30_000)
        runCurrent()

        assertFalse("the user is looking at the challenge, the call must go on", caller.isCompleted)
        assertFalse(closed)
        assertSame(token, PaymentVerificationBridge.currentOwner())
        PaymentVerificationBridge.complete(token)
        caller.await().getOrThrow()
    }

    @Test
    fun `a screen that attaches while the limit gives up is closed, never left without a caller`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { runCatching { PaymentVerificationBridge.await(token, deferred) } }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(
            GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE,
            (caller.await().exceptionOrNull() as GopaySDKException).code
        )

        // The screen turning up now is told to close itself.
        assertFalse(PaymentVerificationBridge.attach(token) { fail("attach already says so") })
    }

    @Test
    fun `a challenge finds only the verification its intent names`() {
        val first = register(CompletableDeferred())!!
        PaymentVerificationBridge.dismiss(first)
        val second = register(CompletableDeferred())!!

        // What a challenge started for the first one, and appearing only now, looks up.
        assertNull(PaymentVerificationBridge.currentOwner(first.id))
        assertSame(second, PaymentVerificationBridge.currentOwner(second.id))
        assertFalse("ids are never reused", first.id == second.id)
    }

    @Test
    fun `a verification whose challenge is not on screen yet can be ended, and the bridge is free`() = runTest {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        val caller = async { runCatching { PaymentVerificationBridge.await(token, deferred) } }
        runCurrent()
        val cause = GopaySDKException(code = GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, message = "closed")

        assertTrue(PaymentVerificationBridge.releaseUnshown(token, cause))
        runCurrent()

        assertSame(cause, caller.await().exceptionOrNull())
        assertEquals("ended at once, not at the limit", 0L, currentTime)
        assertNull(PaymentVerificationBridge.currentOwner())
        assertFalse("its challenge, turning up later, closes itself", PaymentVerificationBridge.attach(token) {})
    }

    @Test
    fun `a challenge the user already sees is not ended that way`() {
        val deferred = CompletableDeferred<Boolean>()
        val token = register(deferred)!!
        var closed = false
        PaymentVerificationBridge.attach(token) { closed = true }

        assertFalse(PaymentVerificationBridge.releaseUnshown(token, IllegalStateException()))

        assertFalse("the challenge stays", closed)
        assertFalse(deferred.isCompleted)
        assertSame(token, PaymentVerificationBridge.currentOwner())
    }

    @Test
    fun `an activity whose caller already gave up is told to close itself`() {
        val token = register(CompletableDeferred())!!
        PaymentVerificationBridge.dismiss(token)

        assertFalse(PaymentVerificationBridge.attach(token) { fail("nothing should close it") })
    }

    @Test
    fun `an activity attaching to its live verification may stay`() {
        val token = register(CompletableDeferred())!!

        assertTrue(PaymentVerificationBridge.attach(token) { })
    }
}
