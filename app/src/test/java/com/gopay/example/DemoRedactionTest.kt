package com.gopay.example

import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentDetails
import cz.gopay.sdk.model.PaymentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The demo prints responses for developers; the secret and the customer's contacts stay out. */
class DemoRedactionTest {

    @Test
    fun `a payment printed as the demo prints it shows neither the e-mail nor the phone number`() {
        val payment = PaymentDetails(
            id = "3310782449",
            orderNumber = "order-1",
            state = PaymentState.PAID,
            amount = 100,
            currency = Currency.CZK,
            customer = PaymentCustomer(email = "customer@example.com", phoneNumber = "+420777123456"),
            gatewayUrl = "https://gw.sandbox.gopay.com/gw/v3/3310782449"
        )

        val shown = DemoRedaction.hide(payment.toString())

        assertFalse(shown, shown.contains("customer@example.com"))
        assertFalse(shown, shown.contains("777123456"))
        assertTrue(shown, shown.contains("email=(hidden)"))
        assertTrue(shown, shown.contains("phoneNumber=(hidden)"))
        assertTrue("the rest stays readable", shown.contains("id=3310782449") && shown.contains("state=PAID"))
    }

    @Test
    fun `a JSON body loses the secret and the contacts at any depth`() {
        val body = """{"id":"1","payment_secret":"s3cr3t","customer":{"email":"a@b.cz","phone_number":"+420 777 123 456","nested":[{"email":"c@d.cz"}]},"amount":100}"""

        assertEquals(
            """{"id":"1","payment_secret":(hidden),"customer":{"email":(hidden),"phone_number":(hidden),"nested":[{"email":(hidden)}]},"amount":100}""",
            DemoRedaction.hide(body)
        )
    }

    @Test
    fun `an absent value and an unrelated key are left as they are`() {
        assertEquals("PaymentCustomer(email=null, phoneNumber=null)", DemoRedaction.hide("PaymentCustomer(email=null, phoneNumber=null)"))
        assertEquals("payment_id: 3310782449\nreturn_url: https://shop.example/return", DemoRedaction.hide("payment_id: 3310782449\nreturn_url: https://shop.example/return"))
        assertEquals("We've emailed your receipt", DemoRedaction.hide("We've emailed your receipt"))
    }

    @Test
    fun `the console's own lines hide a secret that got into them`() {
        assertEquals("payment_secret: (hidden)", DemoRedaction.hide("payment_secret: abc123"))
        // Hiding twice changes nothing, so a line that already says so passes through.
        val line = "payment_secret: ${DemoRedaction.HIDDEN}, filled into the field below"
        assertEquals(line, DemoRedaction.hide(line))
        assertEquals(DemoRedaction.hide("email=a@b.cz"), DemoRedaction.hide(DemoRedaction.hide("email=a@b.cz")))
    }
}
