package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.PaymentOutcome.Outcome
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.telemetry.GwLoggerSchema
import cz.gopay.sdk.telemetry.JsonText
import cz.gopay.sdk.testing.FakeGateway
import cz.gopay.sdk.testing.SharedSdkState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `getPaymentOutcome()` reads `GET /payments/{id}` once and decides the outcome from the
 * payment's state and its optional charge, without polling. The responses go through the SDK's
 * real decoding, so a state the gateway spells differently fails here. The iOS SDK has the same
 * table.
 */
class PaymentSessionOutcomeTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val gateway = FakeGateway()

    @After
    fun tearDown() = gateway.close()

    @Test
    fun `decides the outcome for every payment state against every charge`() {
        val session = gateway.start()
        var reads = 0

        for ((paymentState, row) in TABLE) {
            val outcomes = row.split(" ").map { OUTCOME.getValue(it) }
            assertEquals("row $paymentState", CHARGES.size, outcomes.size)
            for ((charge, expected) in CHARGES.zip(outcomes)) {
                gateway.answer = { 200 to payment(paymentState, charge) }
                val result = runBlocking { session.getPaymentOutcome() }
                reads++
                assertEquals("$paymentState / ${charge ?: "no charge"}", expected, result.outcome)
            }
        }
        assertEquals(72, reads)
        assertEquals("one read per call, no polling", reads, gateway.requests.size)
        assertTrue(gateway.requests.all { it.method == "GET" && it.url.encodedPath == "/api/payments/$PAYMENT" })
    }

    @Test
    fun `hands over the payment and the charge in it, which carries only its id and state`() {
        gateway.answer = { 200 to payment("CANCELED", "FAILED") }
        val session = gateway.start()

        val result = runBlocking { session.getPaymentOutcome() }

        assertEquals(Outcome.DECLINED, result.outcome)
        assertEquals(PAYMENT, result.payment.id)
        assertEquals(PaymentState.CANCELED, result.payment.state)
        assertEquals("c1", result.charge?.id)
        assertEquals(ChargeState.FAILED, result.charge?.state)
        assertNull("the payment's charge has no fail_reason", result.charge?.failReason)
        assertTrue(result.charge === result.payment.charge)
    }

    @Test
    fun `a charge in the payment without a state is UNKNOWN, a charge read on its own still fails`() {
        val session = gateway.start()
        for (charge in listOf(NO_STATE, """{"id":"c1","state":null}""")) {
            gateway.answer = { 200 to payment("PAYMENT_METHOD_CHOSEN", null, rawCharge = charge) }
            val result = runBlocking { session.getPaymentOutcome() }
            assertEquals(charge, ChargeState.UNKNOWN, result.charge?.state)
            assertEquals(charge, Outcome.UNDECIDED, result.outcome)
            assertEquals(charge, ChargeState.UNKNOWN, runBlocking { session.getStatus() }.charge?.state)
        }

        gateway.answer = { 200 to NO_STATE }
        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getChargeState() } }
        assertEquals(GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR, e.code)
    }

    @Test
    fun `a payment without a charge has none`() {
        gateway.answer = { 200 to payment("CREATED", null) }
        val session = gateway.start()

        val result = runBlocking { session.getPaymentOutcome() }

        assertEquals(Outcome.NOT_CHARGED, result.outcome)
        assertNull(result.charge)
    }

    @Test
    fun `a payment the gateway does not have is PAYMENT_012, reported once`() {
        gateway.answer = { 404 to """{"error":"NOT_FOUND","message":"Payment not found"}""" }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getPaymentOutcome() } }

        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.code)
        assertEquals(404, e.httpStatus)
        assertEquals(listOf(e), gateway.reported)
    }

    @Test
    fun `any other failure is what getStatus throws`() {
        val session = gateway.start()
        for ((status, code) in listOf(
            400 to GopayErrorCodes.NETWORK_CLIENT_ERROR,
            403 to GopayErrorCodes.NETWORK_CLIENT_ERROR,
            500 to GopayErrorCodes.NETWORK_SERVER_ERROR
        )) {
            gateway.answer = { status to "{}" }
            val outcome = assertThrows(GopaySDKException::class.java) { runBlocking { session.getPaymentOutcome() } }
            val statusError = assertThrows(GopaySDKException::class.java) { runBlocking { session.getStatus() } }
            assertEquals("$status", code, outcome.code)
            assertEquals("$status", statusError.code, outcome.code)
        }
        gateway.answer = { 200 to "<html>captive portal</html>" }
        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getPaymentOutcome() } }
        assertEquals(GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR, e.code)
    }

    @Test
    fun `a closed session is AUTH_013 before anything goes out`() {
        val session = gateway.start()
        session.close()

        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getPaymentOutcome() } }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.code)
        assertTrue(gateway.requests.isEmpty())
        assertEquals(listOf(e), gateway.reported)
    }

    @Test
    fun `goes out in the telemetry as the same api_call as getStatus, and nothing else`() {
        gateway.answer = { 200 to payment("PAID", "SUCCEEDED") }
        val session = gateway.start()

        runBlocking { session.getPaymentOutcome() }
        runBlocking { session.getStatus() }

        val calls = events().filter { it["target"] == "/payments/{id}" }
        assertEquals(2, calls.size)
        assertEquals(
            "the outcome's api_call and getStatus's look alike",
            calls[1].filterKeys { it in COMPARED },
            calls[0].filterKeys { it in COMPARED }
        )
        assertEquals(listOf(200L, 200L), calls.map { (it["status_code"] as JsonText.JsonNumber).literal.toLong() })
        assertTrue(
            "no other event",
            events().all { it["event_type"] == "api_call" || it["event_type"] == "navigation" }
        )
    }

    // --- Harness ---------------------------------------------------------------------------

    private fun events() = gateway.telemetry.events.onEach { GwLoggerSchema.assertValid(it) }

    /** A payment in [state] with a charge in [chargeState], or with [rawCharge] as it is. */
    private fun payment(state: String, chargeState: String?, rawCharge: String? = null): String {
        val charge = rawCharge ?: chargeState?.let {
            if (it == NO_STATE_COLUMN) NO_STATE
            else """{"id":"c1","state":"$it","href":"https://gw.example/api/payments/$PAYMENT/charge"}"""
        }
        return """{"id":"$PAYMENT","order_number":"o-1","state":"$state","amount":10000,""" +
            """"currency":"CZK","customer":{"email":"a@example.com"},""" +
            """"gw_url":"https://gw.example/gw/$PAYMENT"""" + (charge?.let { ""","charge":$it""" } ?: "") + "}"
    }

    private companion object {
        const val PAYMENT = FakeGateway.PAYMENT
        const val NO_STATE_COLUMN = "(no state)"
        const val NO_STATE = """{"id":"c1","href":"https://gw.example/api/payments/3000000001/charge"}"""

        /** The charge columns: none, every state, one this SDK does not know, and one without a state. */
        val CHARGES = listOf(
            null, "REQUESTED", "PROCESSING", "ACTION_REQUIRED", "SUCCEEDED", "FAILED", "SETTLING", NO_STATE_COLUMN
        )

        val OUTCOME = mapOf(
            "P" to Outcome.PAID, "D" to Outcome.DECLINED, "N" to Outcome.NOT_CHARGED, "U" to Outcome.UNDECIDED
        )

        /**
         * Every payment state, `ON_HOLD` standing for one this SDK does not know, against [CHARGES]:
         * P paid, D declined, N not charged, U undecided. The iOS SDK tests the same table.
         */
        val TABLE = listOf(
            //                        none REQ PROC ACT SUCC FAIL ?   no state
            "PAID" to                 "P    P   P    P   P    P    P   P",
            "AUTHORIZED" to           "P    P   P    P   P    P    P   P",
            "REFUNDED" to             "P    P   P    P   P    P    P   P",
            "PARTIALLY_REFUNDED" to   "P    P   P    P   P    P    P   P",
            "CANCELED" to             "N    N   N    N   N    D    N   N",
            "TIMEOUTED" to            "N    N   N    N   N    D    N   N",
            "CREATED" to              "N    U   U    U   P    D    U   U",
            "PAYMENT_METHOD_CHOSEN" to "N   U   U    U   P    D    U   U",
            "ON_HOLD" to              "U    U   U    U   P    D    U   U"
        ).map { (state, row) -> state to row.trim().split(Regex("\\s+")).joinToString(" ") }

        val COMPARED = setOf("event_type", "action", "http_method", "target", "res_body")
    }
}
