package cz.gopay.sdk.ui

import cz.gopay.sdk.ui.utils.CardValidator
import cz.gopay.sdk.ui.utils.CvvValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Covers the high-level [CardValidator] entry points that return [CardValidator.ValidationResult]
 * (card number / expiry / cvv / combined) and the [CvvValidator] helpers. The lower-level
 * digit/Luhn/date helpers are already exercised by CardInputFormatterTest and
 * ExpirationDateFormatterTest, so this focuses on the result-producing branches.
 */
class CardValidatorTest {

    // A date guaranteed to be in the valid window (current year + 2), formatted MM/YY.
    private val futureDate: String = run {
        val cal = Calendar.getInstance()
        val yy = (cal.get(Calendar.YEAR) + 2) % 100
        "12/%02d".format(yy)
    }

    // ---- validateCardNumber ----

    @Test
    fun `validateCardNumber rejects empty input`() {
        val result = CardValidator.validateCardNumber("")
        assertFalse(result.isValid)
        assertEquals("Card number is required", result.errorMessage)
    }

    @Test
    fun `validateCardNumber rejects wrong length`() {
        val result = CardValidator.validateCardNumber("4532 0151")
        assertFalse(result.isValid)
        assertEquals("Card number must be 16 digits", result.errorMessage)
    }

    @Test
    fun `validateCardNumber rejects Luhn failure`() {
        val result = CardValidator.validateCardNumber("4532015112830365") // last digit altered
        assertFalse(result.isValid)
        assertEquals("Invalid card number", result.errorMessage)
    }

    @Test
    fun `validateCardNumber accepts a valid Luhn number with formatting`() {
        val result = CardValidator.validateCardNumber("4532 0151 1283 0366")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateCardNumber skips Luhn when not required`() {
        val result = CardValidator.validateCardNumber("1234567890123456", requireLuhn = false)
        assertTrue(result.isValid)
    }

    // ---- validateExpirationDate ----

    @Test
    fun `validateExpirationDate rejects empty input`() {
        val result = CardValidator.validateExpirationDate("")
        assertFalse(result.isValid)
        assertEquals("Expiration date is required", result.errorMessage)
    }

    @Test
    fun `validateExpirationDate rejects wrong length`() {
        val result = CardValidator.validateExpirationDate("1/2")
        assertFalse(result.isValid)
        assertEquals("Expiration date must be MM/YY format", result.errorMessage)
    }

    @Test
    fun `validateExpirationDate rejects an expired date`() {
        val result = CardValidator.validateExpirationDate("01/20")
        assertFalse(result.isValid)
        assertEquals("Card has expired or invalid date", result.errorMessage)
    }

    @Test
    fun `validateExpirationDate accepts a future date`() {
        val result = CardValidator.validateExpirationDate(futureDate)
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `validateExpirationDate skips date check when future not required`() {
        val result = CardValidator.validateExpirationDate("01/20", requireFutureDate = false)
        assertTrue(result.isValid)
    }

    // ---- validateCvv ----

    @Test
    fun `validateCvv rejects empty input`() {
        val result = CardValidator.validateCvv("")
        assertFalse(result.isValid)
        assertEquals("CVV is required", result.errorMessage)
    }

    @Test
    fun `validateCvv rejects non-numeric input`() {
        val result = CardValidator.validateCvv("12a")
        assertFalse(result.isValid)
        assertEquals("CVV must contain only digits", result.errorMessage)
    }

    @Test
    fun `validateCvv rejects wrong length`() {
        val result = CardValidator.validateCvv("12")
        assertFalse(result.isValid)
        assertEquals("CVV must be 3-4 digits", result.errorMessage)
    }

    @Test
    fun `validateCvv accepts three digits`() {
        val result = CardValidator.validateCvv("123")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    // ---- validateCard (combined) ----

    @Test
    fun `validateCard reports all valid for a good card`() {
        val result = CardValidator.validateCard("4532015112830366", futureDate, "123")
        assertTrue(result.cardNumber.isValid)
        assertTrue(result.expirationDate.isValid)
        assertTrue(result.cvv.isValid)
        assertTrue(result.isAllValid)
    }

    @Test
    fun `validateCard reports not all valid when one field fails`() {
        val result = CardValidator.validateCard("4532015112830366", futureDate, "")
        assertTrue(result.cardNumber.isValid)
        assertTrue(result.expirationDate.isValid)
        assertFalse(result.cvv.isValid)
        assertFalse(result.isAllValid)
    }

    @Test
    fun `ValidationResult factory helpers set fields`() {
        val valid = CardValidator.ValidationResult.valid()
        assertTrue(valid.isValid)
        assertNull(valid.errorMessage)

        val invalid = CardValidator.ValidationResult.invalid("boom")
        assertFalse(invalid.isValid)
        assertEquals("boom", invalid.errorMessage)
    }

    // ---- CvvValidator ----

    @Test
    fun `CvvValidator validateInput accepts up to three digits`() {
        assertEquals("12", CvvValidator.validateInput("12", currentCvv = "1"))
        assertEquals("123", CvvValidator.validateInput("123", currentCvv = "12"))
    }

    @Test
    fun `CvvValidator validateInput keeps current value on overflow or non-digit`() {
        assertEquals("123", CvvValidator.validateInput("1234", currentCvv = "123"))
        assertEquals("12", CvvValidator.validateInput("1a", currentCvv = "12"))
    }

    @Test
    fun `CvvValidator length and completeness checks`() {
        assertTrue(CvvValidator.isValidLength("123"))
        assertFalse(CvvValidator.isValidLength("12"))
        assertFalse(CvvValidator.isValidLength("1234"))

        assertTrue(CvvValidator.isComplete("123"))
        assertFalse(CvvValidator.isComplete("12"))
    }

    @Test
    fun `CvvValidator isNumeric distinguishes digits`() {
        assertTrue(CvvValidator.isNumeric("123"))
        assertFalse(CvvValidator.isNumeric("12a"))
    }
}
