package cz.gopay.sdk.model

import com.squareup.moshi.JsonDataException
import cz.gopay.sdk.modules.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Which fields of the response models are always present and which are optional, as
 * `sdk/docs/API_3_0.md` lists them under "One shape on both platforms". The iOS SDK decodes the
 * same shape. Decoded by the Moshi instance every SDK call uses.
 */
class ResponseShapeTest {

    private val payments = NetworkModule.moshi.adapter(PaymentDetails::class.java)
    private val qr = NetworkModule.moshi.adapter(QrPaymentDetails::class.java)
    private val charges = NetworkModule.moshi.adapter(ChargePaymentResponse::class.java)

    private fun payment(without: String? = null): String = mapOf(
        "id" to "\"3310782449\"",
        "order_number" to "\"o-1\"",
        "state" to "\"CREATED\"",
        "amount" to "100",
        "currency" to "\"CZK\"",
        "customer" to """{"email": "a@b.cz"}""",
        "gw_url" to "\"https://gw.sandbox.gopay.com/gw/v3/x\""
    ).filterKeys { it != without }.entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\": ${it.value}" }

    private fun qrPayment(recipient: String? = RECIPIENT, qrCode: String? = """{"spayd": "SPD*1.0"}"""): String =
        listOfNotNull(
            "\"amount\": 100",
            "\"currency\": \"CZK\"",
            recipient?.let { "\"recipient\": $it" },
            qrCode?.let { "\"qr_code\": $it" }
        ).joinToString(prefix = "{", postfix = "}")

    @Test
    fun `a payment carries its order number, gateway URL and customer`() {
        val payment = payments.fromJson(payment())!!

        assertEquals("o-1", payment.orderNumber)
        assertEquals("https://gw.sandbox.gopay.com/gw/v3/x", payment.gatewayUrl)
        assertEquals("a@b.cz", payment.customer.email)
        assertNull(payment.charge)
        assertNull(payment.paymentSecret)
    }

    @Test
    fun `a payment without its order number, gateway URL or customer does not decode`() {
        for (field in listOf("order_number", "gw_url", "customer")) {
            assertThrows(field, JsonDataException::class.java) { payments.fromJson(payment(without = field)) }
        }
    }

    @Test
    fun `QR payment details carry the recipient and the codes`() {
        val details = qr.fromJson(qrPayment())!!

        assertEquals("GoPay Czech", details.recipient.name)
        assertEquals("2600000001", details.recipient.bankAccount!!.local!!.accountNumber)
        assertEquals("SPD*1.0", details.qrCode.spayd)
        assertNull(details.qrCode.mnbQr)
    }

    @Test
    fun `QR payment details without the recipient or the codes do not decode`() {
        assertThrows(JsonDataException::class.java) { qr.fromJson(qrPayment(recipient = null)) }
        assertThrows(JsonDataException::class.java) { qr.fromJson(qrPayment(qrCode = null)) }
    }

    @Test
    fun `a local account carries all four of its fields`() {
        for (field in listOf("prefix", "account_number", "bank_code", "variable_symbol")) {
            val local = LOCAL_FIELDS.filterKeys { it != field }.asJson()
            val recipient = """{"name": "GoPay Czech", "bank_account": {"local": $local}}"""

            assertThrows(field, JsonDataException::class.java) { qr.fromJson(qrPayment(recipient = recipient)) }
        }
    }

    @Test
    fun `a recipient may come without a name, an account or an address`() {
        val details = qr.fromJson(qrPayment(recipient = "{}"))!!

        assertNotNull(details.recipient)
        assertNull(details.recipient.name)
        assertNull(details.recipient.bankAccount)
        assertNull(details.recipient.address)
    }

    @Test
    fun `the instrument of a charge may come without details`() {
        val charge = charges.fromJson(
            """{"id": "ch-1", "state": "PROCESSING", "payment_instrument": {"payment_instrument": "PAYMENT_CARD"}}"""
        )!!

        assertEquals("PAYMENT_CARD", charge.paymentInstrument!!.paymentInstrument)
        assertNull(charge.paymentInstrument!!.details)
    }

    private companion object {
        val LOCAL_FIELDS = mapOf(
            "prefix" to "000000",
            "account_number" to "2600000001",
            "bank_code" to "2010",
            "variable_symbol" to "3309579338"
        )

        fun Map<String, String>.asJson(): String =
            entries.joinToString(prefix = "{", postfix = "}") { "\"${it.key}\": \"${it.value}\"" }

        val RECIPIENT = """{"name": "GoPay Czech", "bank_account": {"local": ${LOCAL_FIELDS.asJson()}}}"""
    }
}
