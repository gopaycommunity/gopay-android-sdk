package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.telemetry.GwLoggerSchema
import cz.gopay.sdk.telemetry.JsonText
import cz.gopay.sdk.testing.FakeGateway
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway's documented 404 and 409 on the payment's own routes get codes of their own:
 * `PAYMENT_012` for a payment or a charge the gateway does not have, `PAYMENT_013` for a second
 * charge. The status and the gateway's error stay on `httpContext`, `errorCallback` hears the
 * error once, and the telemetry is what it was: the `api_call` with the status, no error event.
 * Any other 4xx, and a 404 on any other route, stays `NETWORK_002`. The iOS SDK maps the same statuses on the same routes.
 */
class PaymentSessionGatewayCodesTest {

    private val gateway = FakeGateway()

    private val browserData = BrowserData(
        language = "cs-CZ", timezone = -60, screenWidth = 1080, screenHeight = 2400, colorDepth = 24,
        userAgent = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
        acceptHeader = "*/*", ip = "192.0.2.1"
    )

    @After
    fun tearDown() = gateway.close()

    @Test
    fun `a second charge is PAYMENT_013 with the gateway's 409 kept`() {
        gateway.answer = { 409 to CONFLICT }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.chargeWithEncryptedCard("jwe", browserData) }
        }

        assertEquals(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, e.errorCode)
        assertEquals("PAYMENT_013", e.errorCode)
        assertEquals(409, e.getHttpStatusCode())
        assertEquals("POST", e.httpContext?.requestMethod)
        assertEquals("Charge already exists for this payment", e.httpContext?.gatewayError?.message)
        assertTrue(e.message!!.endsWith("HTTP 409: Charge already exists for this payment"))
        assertEquals(listOf(e), gateway.reported)
        assertApiCall("/payments/{id}/charge", 409)
        assertNoErrorEvent()
    }

    @Test
    fun `every charge call reports the 409 the same way`() {
        gateway.answer = { 409 to CONFLICT }
        val session = gateway.start()
        val calls = listOf<suspend () -> Unit>(
            { session.charge(ChargePaymentRequest.cardToken("token", browserData)) },
            { session.chargeWithCardToken("token", browserData) },
            { session.chargeWithEncryptedCard("jwe", browserData) }
        )

        for ((i, call) in calls.withIndex()) {
            val e = assertThrows("call #$i", GopaySDKException::class.java) { runBlocking { call() } }
            assertEquals("call #$i", GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, e.errorCode)
        }
        assertEquals(3, gateway.reported.size)
    }

    @Test
    fun `a charge on a payment the gateway does not have is PAYMENT_012`() {
        gateway.answer = { 404 to NOT_FOUND }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.chargeWithCardToken("token", browserData) }
        }

        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.errorCode)
        assertEquals("PAYMENT_012", e.errorCode)
        assertEquals(404, e.getHttpStatusCode())
        assertEquals("Payment not found", e.httpContext?.gatewayError?.message)
        assertEquals(listOf(e), gateway.reported)
        assertApiCall("/payments/{id}/charge", 404)
        assertNoErrorEvent()
    }

    @Test
    fun `no charge yet is PAYMENT_012 from getChargeState`() {
        gateway.answer = { 404 to """{"error":"NOT_FOUND","message":"Charge not found"}""" }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getChargeState() } }

        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.errorCode)
        assertEquals(404, e.getHttpStatusCode())
        assertEquals("GET", e.httpContext?.requestMethod)
        assertEquals(listOf(e), gateway.reported)
        assertApiCall("/payments/{id}/charge", 404)
    }

    @Test
    fun `a 404 while awaitChargeState polls ends it with PAYMENT_012, reported once`() {
        gateway.answer = { 404 to NOT_FOUND }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.awaitChargeState(AwaitChargeOptions(threeDSMode = ThreeDSMode.MANUAL)) }
        }

        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.errorCode)
        assertEquals(404, e.getHttpStatusCode())
        assertEquals("the polling stops at the first 404", 1, gateway.requests.size)
        assertEquals(listOf(e), gateway.reported)
    }

    @Test
    fun `a payment the gateway does not have is PAYMENT_012 from getStatus`() {
        gateway.answer = { 404 to NOT_FOUND }
        val session = gateway.start()

        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getStatus() } }

        assertEquals(GopayErrorCodes.PAYMENT_NOT_FOUND, e.errorCode)
        assertEquals(404, e.getHttpStatusCode())
        assertApiCall("/payments/{id}", 404)
    }

    @Test
    fun `any other 4xx on those routes stays NETWORK_002`() {
        val session = gateway.start()
        val calls = listOf<Pair<String, suspend () -> Unit>>(
            "charge" to { session.chargeWithCardToken("token", browserData) },
            "getChargeState" to { session.getChargeState() },
            "getStatus" to { session.getStatus() }
        )
        for (status in listOf(400, 403, 422, 429)) {
            gateway.answer = { status to "{}" }
            for ((name, call) in calls) {
                val e = assertThrows("$name $status", GopaySDKException::class.java) { runBlocking { call() } }
                assertEquals("$name $status", GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
                assertEquals("$name $status", status, e.getHttpStatusCode())
            }
        }
        // getStatus and getChargeState never get a 409 from the gateway; if one came, it is not a charge.
        gateway.answer = { 409 to CONFLICT }
        for ((name, call) in calls.drop(1)) {
            val e = assertThrows(name, GopaySDKException::class.java) { runBlocking { call() } }
            assertEquals(name, GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        }
    }

    @Test
    fun `a 404 from a route that is not about the payment itself stays NETWORK_002`() {
        gateway.answer = { 404 to NOT_FOUND }
        val session = gateway.start()

        for ((name, call) in listOf<Pair<String, suspend () -> Unit>>(
            "getQrPaymentInfo" to { session.getQrPaymentInfo() },
            "getWalletInfo" to { session.getWalletInfo() }
        )) {
            val e = assertThrows(name, GopaySDKException::class.java) { runBlocking { call() } }
            assertEquals(name, GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
            assertEquals(name, 404, e.getHttpStatusCode())
        }
    }

    @Test
    fun `a closed session is still AUTH_013, before anything goes out`() {
        val session = gateway.start()
        session.close()

        val e = assertThrows(GopaySDKException::class.java) { runBlocking { session.getChargeState() } }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.errorCode)
        assertTrue(gateway.requests.isEmpty())
    }

    // --- Harness ---------------------------------------------------------------------------

    private fun events() = gateway.telemetry.events.onEach { GwLoggerSchema.assertValid(it) }

    /** The one `api_call` for [target], with the gateway's [status]: the same as before the codes. */
    private fun assertApiCall(target: String, status: Long) {
        val calls = events().filter { it["event_type"] == "api_call" && it["target"] == target }
        assertEquals("$calls", 1, calls.size)
        assertEquals(status, (calls.single()["status_code"] as JsonText.JsonNumber).literal.toLong())
    }

    /** An HTTP error goes out as its `api_call` only, under the new codes as before them. */
    private fun assertNoErrorEvent() {
        val errors = events().filter { it["event_type"] == "js_event" && it.containsKey("error_code") }
        assertEquals(emptyList<Any>(), errors)
    }

    private companion object {
        const val CONFLICT = """{"error":"CONFLICT","message":"Charge already exists for this payment"}"""
        const val NOT_FOUND = """{"error":"NOT_FOUND","message":"Payment not found"}"""
    }
}
