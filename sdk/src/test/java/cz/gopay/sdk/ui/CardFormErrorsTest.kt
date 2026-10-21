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
    private val invalid = CardFormData(cardNumberDigits = "1234", expirationDigits = "1399", cvv = "1")
    private val valid = CardFormData(cardNumberDigits = "4532015112830366", expirationDigits = "1230", cvv = "123")

    private fun errors(
        data: CardFormData,
        mode: CardFormValidationDisplay,
        attempted: Boolean = false,
        edited: Boolean = true
    ) = CardFormErrors.of(
        data = data, strings = strings, mode = mode, submitAttempted = attempted,
        cardNumberEdited = edited, expirationEdited = edited, cvvEdited = edited
    )

    @Test
    fun `HIDDEN shows nothing whatever the fields hold`() {
        assertEquals(CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.HIDDEN, attempted = true))
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.HIDDEN, attempted = true))
    }

    @Test
    fun `LIVE shows the pattern message for edited, non-empty, invalid content`() {
        val e = errors(invalid, CardFormValidationDisplay.LIVE)

        assertEquals(strings.panErrorPattern, e.cardNumber)
        assertEquals(strings.expErrorPattern, e.expiration)
        assertEquals(strings.cvvErrorPattern, e.cvv)
    }

    @Test
    fun `LIVE never flags an empty field or one the user has not touched`() {
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.LIVE))
        assertEquals(CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.LIVE, edited = false))
        assertEquals(CardFormErrors.NONE, errors(valid, CardFormValidationDisplay.LIVE))
    }

    @Test
    fun `ON_SUBMIT shows nothing until a submit was attempted`() {
        assertEquals(CardFormErrors.NONE, errors(invalid, CardFormValidationDisplay.ON_SUBMIT))
        assertEquals(CardFormErrors.NONE, errors(CardFormData(), CardFormValidationDisplay.ON_SUBMIT))
    }

    @Test
    fun `ON_SUBMIT after a submit flags empty fields as required and invalid ones with the pattern`() {
        val empty = errors(CardFormData(), CardFormValidationDisplay.ON_SUBMIT, attempted = true, edited = false)
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
    fun `LIVE flags only the field the user has edited`() {
        val e = CardFormErrors.of(
            data = invalid, strings = strings, mode = CardFormValidationDisplay.LIVE, submitAttempted = false,
            cardNumberEdited = true, expirationEdited = false, cvvEdited = false
        )

        assertEquals(strings.panErrorPattern, e.cardNumber)
        assertNull(e.expiration)
        assertNull(e.cvv)
    }

    @Test
    fun `ON_SUBMIT after a submit flags only the fields that fail`() {
        val e = errors(valid.copy(cvv = "12"), CardFormValidationDisplay.ON_SUBMIT, attempted = true)

        assertNull(e.cardNumber)
        assertNull(e.expiration)
        assertEquals(strings.cvvErrorPattern, e.cvv)
    }
}
