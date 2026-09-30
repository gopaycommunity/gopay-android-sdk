package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import kotlinx.coroutines.CompletableDeferred
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
class PaymentVerificationBridgeTest {

    private lateinit var owner: PaymentVerificationBridge.Owner

    @After
    fun tearDown() {
        if (::owner.isInitialized) PaymentVerificationBridge.clear(owner)
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
    fun `cancel surfaces user dismissal as cancellation`() {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.cancel(o)

        assertTrue(d.isCancelled)
    }

    @Test
    fun `failUnreachable reports a dead challenge as PAYMENT_VERIFICATION_UNREACHABLE`() = runTest {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!

        PaymentVerificationBridge.failUnreachable(o, "HTTP 404")

        // The host tells a dead link from a dismissal by what await() throws: a
        // GopaySDKException here, a CancellationException from cancel().
        try {
            d.await()
            fail("Expected GopaySDKException for a challenge that could not be loaded")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, e.errorCode)
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
            assertEquals(GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR, e.errorCode)
            assertTrue(e.message!!.contains("no redirect URL"))
        }
    }

    @Test
    fun `a report after the bridge is released is a no-op`() {
        val d = CompletableDeferred<Boolean>()
        val o = register(d)!!
        PaymentVerificationBridge.clear(o)

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
        PaymentVerificationBridge.clear(staleOwner)

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
        PaymentVerificationBridge.clear(staleOwner)

        assertSame(current, PaymentVerificationBridge.currentOwner())
    }
}
