package cz.gopay.sdk.ui

import cz.gopay.sdk.locales.GopayLocales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the form shows which localized error, per [CardFormValidationDisplay]; the same rules as
 * the iOS `GopayCardFormErrors`. The messages are the locale's, never the English literals
 * `CardValidator` carries.
 */
class CardFormErrorsTest {

    private val strings = GopayLocales.CS
    // Each field invalid and short of its full length: still being typed, as LIVE sees it.
    private val invalid = CardFormData(cardNumberDigits = "1234", expirationDigits = "13", cvv = "1")
    private val valid = CardFormData(cardNumberDigits = "4532015112830366", expirationDigits = "1230", cvv = "123")

    private fun errors(
        data: CardFormData,
        mode: CardFormValidationDisplay,
        attempted: Boolean = false,
        left: Boolean = false
    ) = CardFormErrors.of(
        data = data, strings = strings, mode = mode, submitAttempted = attempted,
        cardNumberLeft = left, expirationLeft = left, cvvLeft = left
    )

    @Test
    fun `HIDDEN shows nothing whatever the fields hold`() {
        assertEquals(CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.HIDDEN, attempted = true, left = true))
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.HIDDEN, attempted = true))
    }

    @Test
    fun `LIVE keeps quiet while a field is still being typed`() {
        // Half a card number, a month on its own, one CVV digit: invalid, but not done.
        val typing = CardFormData(cardNumberDigits = "45320151", expirationDigits = "1", cvv = "1")

        assertEquals(CardFormErrors.NONE, errors(typing, CardFormValidationDisplay.LIVE))
        assertEquals("an impossible month, not yet a date", CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.LIVE))
    }

    @Test
    fun `LIVE flags a field once it holds its full length and still fails`() {
        // Sixteen digits that fail the check, a date in the past; the CVV is never full and wrong.
        val done = CardFormData(cardNumberDigits = "4532015112830367", expirationDigits = "1220", cvv = "12")

        val e = errors(done, CardFormValidationDisplay.LIVE)

        assertEquals(strings.panErrorPattern, e.cardNumber)
        assertEquals(strings.expErrorPattern, e.expiration)
        assertNull(e.cvv)
    }

    @Test
    fun `LIVE flags a field once the user has left it`() {
        val e = errors(invalid, CardFormValidationDisplay.LIVE, left = true)

        assertEquals(strings.panErrorPattern, e.cardNumber)
        assertEquals(strings.expErrorPattern, e.expiration)
        assertEquals(strings.cvvErrorPattern, e.cvv)
        // And only the field that was left.
        val one = CardFormErrors.of(
            data = invalid, strings = strings, mode = CardFormValidationDisplay.LIVE, submitAttempted = false,
            cardNumberLeft = true, expirationLeft = false, cvvLeft = false
        )
        assertEquals(strings.panErrorPattern, one.cardNumber)
        assertNull(one.expiration)
        assertNull(one.cvv)
    }

    @Test
    fun `LIVE flags every edited field once a submit ran, never an empty one`() {
        val e = errors(invalid.copy(cvv = ""), CardFormValidationDisplay.LIVE, attempted = true)

        assertEquals(strings.panErrorPattern, e.cardNumber)
        assertEquals(strings.expErrorPattern, e.expiration)
        assertNull(e.cvv)
    }

    @Test
    fun `LIVE never flags an empty field or a valid one`() {
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.LIVE, attempted = true, left = true))
        assertEquals(CardFormErrors.NONE, errors(valid, CardFormValidationDisplay.LIVE, attempted = true, left = true))
    }

    @Test
    fun `ON_SUBMIT shows nothing until a submit was attempted`() {
        assertEquals(CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.ON_SUBMIT, left = true))
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.ON_SUBMIT))
    }

    @Test
    fun `ON_SUBMIT after a submit flags empty fields as required and invalid ones with the pattern`() {
        val empty = errors(CardFormData(), CardFormValidationDisplay.ON_SUBMIT, attempted = true)
        assertEquals(strings.requiredErrorMessage, empty.cardNumber)
        assertEquals(strings.requiredErrorMessage, empty.expiration)
        assertEquals(strings.requiredErrorMessage, empty.cvv)

        val mixed = errors(invalid.copy(cvv = ""), CardFormValidationDisplay.ON_SUBMIT, attempted = true)
        assertEquals(strings.panErrorPattern, mixed.cardNumber)
        assertEquals(strings.expErrorPattern, mixed.expiration)
        assertEquals(strings.requiredErrorMessage, mixed.cvv)

        assertEquals(CardFormErrors.NONE, errors(valid, CardFormValidationDisplay.ON_SUBMIT, attempted = true))
    }

    @Test
    fun `ON_SUBMIT after a submit flags only the fields that fail`() {
        val e = errors(valid.copy(cvv = "12"), CardFormValidationDisplay.ON_SUBMIT, attempted = true)

        assertNull(e.cardNumber)
        assertNull(e.expiration)
        assertEquals(strings.cvvErrorPattern, e.cvv)
    }
}
