package com.gopay.example.checkout

import android.app.Activity
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.session.PaymentSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

/**
 * A second tap on Pay or Refresh can land before the button redraws disabled (GPMOB-184). The
 * busy label is the lock: a call that finds it held returns at once, sends nothing and leaves the
 * label to the call that took it.
 *
 * Nothing here reaches the network or the SDK. A call that got past the lock would hit the
 * uninitialised `GopaySDK`, the merchant backend or the mocked session, fail, and leave its trace in `outcome` and a
 * released label.
 */
class CheckoutControllerBusyTest {

    private val controller = CheckoutController()
    private val activity: Activity = mock(Activity::class.java)

    @Test
    fun `pay while busy returns at once for every method`() = runTest {
        CheckoutController.Method.entries.forEach { method ->
            controller.selectedMethod = method
            controller.busyLabel = HELD
            controller.banner = "Earlier banner"
            controller.cardFormError = "Earlier card error"

            controller.pay(activity)

            assertEquals("$method keeps the lock", HELD, controller.busyLabel)
            assertEquals("$method leaves the banner", "Earlier banner", controller.banner)
            assertEquals("$method leaves the card error", "Earlier card error", controller.cardFormError)
            assertNull("$method shows no outcome", controller.outcome)
            assertNull("$method creates no payment", controller.paymentId)
            assertNull("$method fetches no transfer details", controller.qrDetails)
        }
        verifyNoInteractions(activity)
    }

    @Test
    fun `refresh while busy returns at once`() = runTest {
        // Refresh needs a session to get as far as the lock.
        val session: PaymentSession = mock(PaymentSession::class.java)
        controller.setSessionForTest(session)
        val pending = CheckoutController.Outcome(ChargeState.PROCESSING, null, null)
        controller.outcome = pending
        controller.busyLabel = HELD

        controller.refreshChargeState(activity)

        assertEquals(HELD, controller.busyLabel)
        assertSame(pending, controller.outcome)
        verifyNoInteractions(session, activity)
    }

    private companion object {
        /** A label neither pay() nor refreshChargeState() sets, so only the first call holds it. */
        const val HELD = "Held by the first tap"
    }
}
