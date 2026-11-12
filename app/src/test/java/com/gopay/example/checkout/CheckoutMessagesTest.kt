package com.gopay.example.checkout

import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the checkout says, and for how long: the texts are the same in the iOS demo. */
class CheckoutMessagesTest {

    private val controller = CheckoutController()

    @Test
    fun `the banner of a cancelled Google Pay goes when the shopper picks another method`() {
        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.banner = "Payment cancelled. Pick a method and try again."

        controller.select(CheckoutController.Method.CARD)

        assertNull(controller.banner)
        assertEquals(CheckoutController.Method.CARD, controller.selectedMethod)
    }

    @Test
    fun `the card form's message goes with the card when the shopper picks another method`() {
        controller.select(CheckoutController.Method.CARD)
        controller.cardFormError = "Please enter your card details and try again."

        controller.select(CheckoutController.Method.SAVED_CARD)

        assertNull(controller.cardFormError)
    }

    @Test
    fun `tapping the method already picked keeps its banner`() {
        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.banner = "Google Pay isn't available on this device."

        controller.select(CheckoutController.Method.GOOGLE_PAY)

        assertEquals("Google Pay isn't available on this device.", controller.banner)
    }

    @Test
    fun `a charge whose state could not be read is not shown as one the bank has not confirmed`() {
        val unread = CheckoutController.Outcome(
            state = ChargeState.UNKNOWN,
            response = null,
            message = "Couldn't check the payment: [NETWORK_004] No internet connection",
            paymentId = "payment-1",
            unread = true
        )

        assertTrue(unread.isUnread)
        assertEquals("Couldn't check the payment", resultTitle(unread))
        assertFalse(resultSubtitle(unread), resultSubtitle(unread).contains("hasn't confirmed"))
        assertTrue(resultSubtitle(unread).contains("couldn't read its state"))
    }

    @Test
    fun `an error that ended the wait for a charge whose state was read is not a state that could not be read`() {
        // A challenge that could not be shown, a session that lost its token: the state is known.
        val unfinished = CheckoutController.Outcome(
            state = ChargeState.ACTION_REQUIRED,
            response = ChargePaymentResponse(id = "ch-1", state = ChargeState.ACTION_REQUIRED),
            message = "[PAYMENT_010] 3DS verification could not be loaded",
            paymentId = "payment-1"
        )

        assertFalse(unfinished.isUnread)
        assertTrue(unfinished.isUnfinished)
        assertEquals("Payment not finished", resultTitle(unfinished))
        assertEquals(
            "The payment was sent, but it isn't finished yet. Refresh before paying again.",
            resultSubtitle(unfinished)
        )
    }

    @Test
    fun `a charge still being decided, a paid one and a failed one keep their texts`() {
        val pending = CheckoutController.Outcome(
            ChargeState.PROCESSING, ChargePaymentResponse(id = "ch-1", state = ChargeState.PROCESSING), null, "payment-1"
        )
        val paid = CheckoutController.Outcome(ChargeState.SUCCEEDED, null, null, "payment-1")
        val failed = CheckoutController.Outcome(ChargeState.FAILED, null, "Insufficient funds", "payment-1")

        assertFalse(pending.isUnread)
        assertEquals("Payment pending", resultTitle(pending))
        assertTrue(resultSubtitle(pending).contains("the bank hasn't confirmed yet"))
        assertEquals("Payment complete", resultTitle(paid))
        assertEquals("Payment failed", resultTitle(failed))
        assertEquals("Insufficient funds", resultSubtitle(failed))
        assertFalse("a failure with a reason is a failure", failed.isUnread)
    }
}
