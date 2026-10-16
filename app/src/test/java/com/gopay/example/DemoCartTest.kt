package com.gopay.example

import com.gopay.example.checkout.DemoCart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The demo charges real cards on production too, so the sample basket must come to 1 Kč, and the
 * rows the checkout shows must add up to the amount it charges.
 */
class DemoCartTest {

    private val cart = DemoCart.sample

    @Test
    fun `the sample basket charges 1 Kc`() {
        assertEquals(100, cart.total)
    }

    @Test
    fun `the shown rows add up to the total`() {
        assertEquals(351_900, cart.subtotal)
        assertEquals(9_900, cart.shipping)
        assertEquals(361_700, cart.discount)
        assertEquals(cart.subtotal + cart.shipping - cart.discount, cart.total)
    }
}
