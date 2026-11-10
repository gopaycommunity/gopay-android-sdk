package cz.gopay.sdk.session

import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentDetails
import cz.gopay.sdk.model.PaymentOutcome
import cz.gopay.sdk.model.PaymentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * [PaymentSessionApi] is what an app replaces the session with in its tests, so it has to carry
 * every call an integration makes on a [PaymentSession], and [PaymentOutcome] has to be
 * buildable from outside. `FakePaymentSessionTest` in the demo app implements the interface from
 * outside the module.
 */
class PaymentSessionApiTest {

    private fun Method.signature() = name to parameterTypes.toList()

    /** The session's public methods as a host sees them: no internal, synthetic or static ones. */
    private fun publicMethods(type: Class<*>) = type.declaredMethods.filter {
        Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && '$' !in it.name
    }

    @Test
    fun `PaymentSession is a PaymentSessionApi`() {
        assertTrue(PaymentSessionApi::class.java.isAssignableFrom(PaymentSession::class.java))
    }

    @Test
    fun `the interface has every public call of the session but the deprecated aliases`() {
        val api = publicMethods(PaymentSessionApi::class.java).map { it.signature() }.toSet()
        val session = publicMethods(PaymentSession::class.java)
        val (deprecated, current) = session.partition { it.isAnnotationPresent(Deprecated::class.java) }

        assertEquals(
            "a public method of PaymentSession is missing from PaymentSessionApi",
            emptyList<Any>(),
            current.map { it.signature() }.filterNot { it in api }
        )
        assertEquals(
            setOf("getGooglePayInfo", "chargeWithGooglePay"),
            deprecated.map { it.name }.toSet()
        )
        assertEquals(
            "a deprecated alias got into the interface",
            emptyList<Any>(),
            deprecated.map { it.signature() }.filter { it in api }
        )
    }

    @Test
    fun `the interface has nothing the session does not implement itself`() {
        val session = publicMethods(PaymentSession::class.java).map { it.signature() }.toSet()

        assertEquals(
            emptyList<Any>(),
            publicMethods(PaymentSessionApi::class.java).map { it.signature() }.filterNot { it in session }
        )
    }

    private fun payment(charge: ChargePaymentResponse?) = PaymentDetails(
        id = "1",
        orderNumber = "o-1",
        state = PaymentState.PAID,
        amount = 100,
        currency = Currency.CZK,
        customer = PaymentCustomer(email = "a@b.cz"),
        gwUrl = "https://gw.example/1",
        charge = charge
    )

    @Test
    fun `a PaymentOutcome built by a host carries what it was given, and its charge is the payment's`() {
        val charge = ChargePaymentResponse(id = "ch-1", state = ChargeState.SUCCEEDED)
        val payment = payment(charge)

        val outcome = PaymentOutcome(PaymentOutcome.Outcome.PAID, payment)

        assertEquals(PaymentOutcome.Outcome.PAID, outcome.outcome)
        assertSame(payment, outcome.payment)
        assertSame("still read from the payment, as in 2.7", charge, outcome.charge)
        assertNull(PaymentOutcome(PaymentOutcome.Outcome.NOT_CHARGED, payment(null)).charge)
    }

    @Test
    fun `a PaymentOutcome built by a host equals the one the SDK decides for the same payment`() {
        val payment = payment(ChargePaymentResponse(id = "ch-1", state = ChargeState.SUCCEEDED))

        assertEquals(PaymentOutcome.of(payment), PaymentOutcome(PaymentOutcome.Outcome.PAID, payment))
    }
}
