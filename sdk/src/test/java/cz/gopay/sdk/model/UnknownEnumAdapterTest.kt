package cz.gopay.sdk.model

import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.util.SdkLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A value the gateway added after this SDK shipped is read as `UNKNOWN`, the rest of the
 * response still decodes, and the raw value lands in the debug log.
 */
class UnknownEnumAdapterTest {

    private val moshi = NetworkModule.moshi
    private val defaultWarnSink = SdkLog.warnSink
    private val warnings = mutableListOf<String>()

    @Before
    fun collectWarnings() {
        SdkLog.warnSink = { warnings.add(it) }
    }

    @After
    fun restoreLogSink() {
        SdkLog.warnSink = defaultWarnSink
    }

    private fun charge(
        state: String = "SUCCEEDED",
        actionType: String = "EMV3DS",
        actionState: String = "CHALLENGE_REQUIRED",
        scheme: String = "VISA"
    ): ChargePaymentResponse = moshi.adapter(ChargePaymentResponse::class.java).fromJson(
        """{"id":"c1","state":"$state","return_url":"https://shop.example/r",
           "payment_instrument":{"payment_instrument":"PAYMENT_CARD",
             "details":{"input_type":"ENCRYPTED_CARD","masked_pan":"4444********1111","scheme":"$scheme"}},
           "action":{"action_type":"$actionType","state":"$actionState","redirect_url":"https://acs.example/c"}}"""
    )!!

    private fun payment(state: String = "CREATED", currency: String = "CZK"): PaymentCreateResponse =
        moshi.adapter(PaymentCreateResponse::class.java).fromJson(
            """{"id":"p1","order_number":"o1","state":"$state","amount":100,"currency":"$currency",
               "customer":{"email":"a@b.cz"},"gw_url":"https://gw.example"}"""
        )!!

    @Test
    fun `an unknown charge state is UNKNOWN and the rest of the charge still reads`() {
        val charge = charge(state = "PARTIALLY_CAPTURED")

        assertEquals(ChargeState.UNKNOWN, charge.state)
        assertEquals("c1", charge.id)
        assertEquals("https://acs.example/c", charge.action?.redirectUrl)
        assertTrue(warnings.single().contains("\"PARTIALLY_CAPTURED\""))
        assertTrue(warnings.single().contains("ChargeState"))
    }

    @Test
    fun `an unknown action type is UNKNOWN`() {
        val charge = charge(actionType = "REDIRECT")

        assertEquals(ChargeActionType.UNKNOWN, charge.action?.actionType)
        assertEquals(ChargeState.SUCCEEDED, charge.state)
        assertTrue(warnings.single().contains("\"REDIRECT\""))
    }

    @Test
    fun `an unknown 3DS state is UNKNOWN`() {
        val charge = charge(actionState = "DECOUPLED_PENDING")

        assertEquals(Emv3dsState.UNKNOWN, charge.action?.state)
        assertTrue(warnings.single().contains("\"DECOUPLED_PENDING\""))
    }

    @Test
    fun `an unknown card scheme is UNKNOWN`() {
        val charge = charge(scheme = "MAESTRO")

        assertEquals(CardScheme.UNKNOWN, charge.paymentInstrument?.details?.scheme)
        assertEquals("4444********1111", charge.paymentInstrument?.details?.maskedPan)
        assertTrue(warnings.single().contains("\"MAESTRO\""))
    }

    @Test
    fun `an unknown payment state is UNKNOWN`() {
        val payment = payment(state = "AUTHORIZATION_PENDING")

        assertEquals(PaymentState.UNKNOWN, payment.state)
        assertEquals(Currency.CZK, payment.currency)
        assertTrue(warnings.single().contains("\"AUTHORIZATION_PENDING\""))
    }

    @Test
    fun `an unknown currency is UNKNOWN`() {
        val payment = payment(currency = "CHF")

        assertEquals(Currency.UNKNOWN, payment.currency)
        assertEquals(PaymentState.CREATED, payment.state)
        assertTrue(warnings.single().contains("\"CHF\""))
    }

    @Test
    fun `an unknown currency in the QR payment details is UNKNOWN`() {
        val details = moshi.adapter(QrPaymentDetails::class.java).fromJson(
            """{"amount":100,"currency":"CHF","qr_code":{}}"""
        )!!

        assertEquals(Currency.UNKNOWN, details.currency)
    }

    @Test
    fun `known values decode as before and log nothing`() {
        val charge = charge(state = "PROCESSING", scheme = "MASTERCARD")

        assertEquals(ChargeState.PROCESSING, charge.state)
        assertEquals(CardScheme.MASTERCARD, charge.paymentInstrument?.details?.scheme)
        assertEquals(Emv3dsState.CHALLENGE_REQUIRED, charge.action?.state)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a null optional enum stays null`() {
        val action = moshi.adapter(ChargeAction::class.java)
            .fromJson("""{"action_type":"EMV3DS","state":null}""")!!

        assertNull(action.state)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `encoding an enum value is unchanged`() {
        val json = moshi.adapter(ChargeState::class.java).toJson(ChargeState.SUCCEEDED)

        assertEquals("\"SUCCEEDED\"", json)
    }
}
