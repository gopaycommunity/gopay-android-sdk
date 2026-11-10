package com.gopay.example.checkout

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A rotation recreates the checkout's Activity while a payment is under way. The checkout's state
 * and the payment's coroutine live in [CheckoutHolder], in the store an Activity hands to the one
 * that replaces it, so the payment runs on and its result is there for the new screen. Kept in
 * the composition they went with the old Activity: a payment ended paid and the demo never showed it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutHolderTest {

    /** What an Activity keeps over its recreation and clears when it is finished for good. */
    private val store = ViewModelStore()

    /** The holder as an Activity gets it: `by viewModels()` asks the store the same way. */
    private fun holder(): CheckoutHolder =
        ViewModelProvider(store, ViewModelProvider.NewInstanceFactory())[CheckoutHolder::class.java]

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `the recreated screen gets the same checkout, with what the shopper did to it`() {
        val before = holder().controller
        before.select(CheckoutController.Method.SAVED_CARD)
        before.banner = "Payment cancelled. Pick a method and try again."

        // The rotation: a new Activity asks the store it was handed.
        val after = holder().controller

        assertSame(before, after)
        assertEquals(CheckoutController.Method.SAVED_CARD, after.selectedMethod)
        assertEquals("Payment cancelled. Pick a method and try again.", after.banner)
    }

    @Test
    fun `a payment under way runs on over a rotation and its result reaches the new screen`() = runTest {
        val first = holder()
        val gateway = CompletableDeferred<ChargePaymentResponse>()
        // The payment, started from the first screen in the holder's scope, as CheckoutScreen does.
        val payment = first.scope.launch {
            first.controller.busyLabel = "Confirming payment…"
            val charge = gateway.await()
            first.controller.outcome = CheckoutController.Outcome(charge.state, charge, null)
            first.controller.busyLabel = null
        }

        // The rotation happens while the gateway is still deciding.
        val second = holder()
        assertTrue("still running, not cancelled with the old screen", payment.isActive)
        assertEquals("Confirming payment…", second.controller.busyLabel)

        gateway.complete(ChargePaymentResponse(id = "ch-1", state = ChargeState.SUCCEEDED))
        payment.join()

        assertEquals(ChargeState.SUCCEEDED, second.controller.outcome?.state)
        assertFalse(second.controller.isBusy)
    }

    @Test
    fun `leaving the checkout for good cancels the payment and starts the next checkout afresh`() = runTest {
        val left = holder()
        val payment = left.scope.launch { CompletableDeferred<Unit>().await() }
        left.controller.select(CheckoutController.Method.BANK_TRANSFER)

        // finish(): the Activity clears its store.
        store.clear()

        assertTrue(payment.isCancelled)
        val next = holder().controller
        assertNotSame(left.controller, next)
        assertEquals(CheckoutController.Method.CARD, next.selectedMethod)
    }
}
