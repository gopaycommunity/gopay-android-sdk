package com.gopay.example.checkout

import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A second tap on Pay or Refresh can land before the button redraws disabled (GPMOB-184). The
 * busy label is the lock: a call that finds it held returns at once, sends nothing and leaves the
 * label to the call that took it.
 *
 * The checkout is open, so a call that got past the lock would charge its session, read the card
 * form or wait for the charge, and leave its trace in the session's calls, in `outcome` and in a
 * released label.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutControllerBusyTest {

    private val services = FakeCheckoutServices()
    private val controller = CheckoutController(services)

    @Test
    fun `pay while busy returns at once for every method`() = runTest {
        services.walletOnDevice = true
        services.cardForm = { throw AssertionError("the card form is not read") }
        controller.open()
        val opened = services.last.calls.toList()

        CheckoutController.Method.entries.forEach { method ->
            controller.selectedMethod = method
            controller.busyLabel = HELD
            controller.banner = "Earlier banner"
            controller.cardFormError = "Earlier card error"

            controller.pay()

            assertEquals("$method keeps the lock", HELD, controller.busyLabel)
            assertEquals("$method leaves the banner", "Earlier banner", controller.banner)
            assertEquals("$method leaves the card error", "Earlier card error", controller.cardFormError)
            assertNull("$method shows no outcome", controller.outcome)
            assertNull("$method fetches no transfer details", controller.qrDetails)
            assertEquals("$method sends nothing on the session", opened, services.last.calls)
        }
        assertEquals("no payment but the checkout's own", listOf("payment-1"), services.created)
        assertEquals("no saved card was tokenized", 0, services.savedCardTokens)
    }

    @Test
    fun `refresh while busy returns at once`() = runTest {
        controller.open()
        val opened = services.last.calls.toList()
        val pending = CheckoutController.Outcome(ChargeState.PROCESSING, null, null, "payment-1")
        controller.outcome = pending
        controller.busyLabel = HELD

        controller.refreshChargeState()

        assertEquals(HELD, controller.busyLabel)
        assertSame(pending, controller.outcome)
        assertEquals(opened, services.last.calls)
    }

    @Test
    fun `a payment under way holds the pay button and the way back`() = runTest {
        controller.open()
        controller.busyLabel = HELD

        assertFalse(controller.canPay)
        assertFalse("leaving would close the session under the payment", controller.canLeave)

        controller.busyLabel = null
        assertTrue(controller.canPay)
        assertTrue(controller.canLeave)
    }

    @Test
    fun `the checkout can be left while it is still opening, and the opening then keeps nothing`() = runTest {
        services.createGate = CompletableDeferred()
        val opening = launch { controller.open() }
        runCurrent()

        assertTrue(controller.isOpening)
        assertFalse("opening is not a payment under way", controller.isBusy)
        assertTrue(controller.canLeave)

        // Left: what the header's arrow does.
        controller.finish()
        services.createGate!!.complete(Unit)
        opening.join()

        assertEquals("no session was started for a checkout nobody is in", 0, services.sessions.size)
        assertFalse(controller.isOpening)
        assertFalse(controller.canPay)
    }

    private companion object {
        /** A label neither pay() nor refreshChargeState() sets, so only the first call holds it. */
        const val HELD = "Held by the first tap"
    }
}
