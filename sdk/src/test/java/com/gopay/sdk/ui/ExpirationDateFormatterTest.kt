package com.gopay.sdk.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gopay.sdk.ui.utils.formatExpirationDate
import com.gopay.sdk.ui.utils.CardValidator
import com.gopay.sdk.ui.utils.ExpirationDateInputValidator
import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class ExpirationDateFormatterTest {

    @Test
    fun `formatExpirationDate should format digits as MM slash YY`() {
        val input = TextFieldValue("1225")
        val result = formatExpirationDate(input)
        
        assertEquals("12/25", result.text)
    }

    @Test
    fun `formatExpirationDate should preserve cursor position`() {
        val input = TextFieldValue("12", selection = TextRange(2))
        val result = formatExpirationDate(input)
        
        assertEquals("12", result.text)
        assertEquals(2, result.selection.start)
        
        // Test with slash
        val input2 = TextFieldValue("123", selection = TextRange(3))
        val result2 = formatExpirationDate(input2)
        
        assertEquals("12/3", result2.text)
        assertEquals(4, result2.selection.start) // Cursor should be after the slash
    }

    @Test
    fun `formatExpirationDate should not accept more than 4 digits`() {
        val input = TextFieldValue("123456")
        val result = formatExpirationDate(input)
        
        assertEquals(input, result) // Should return original unchanged
    }

    @Test
    fun `formatExpirationDate should filter out non-digits`() {
        val input = TextFieldValue("12ab34")
        val result = formatExpirationDate(input)
        
        assertEquals("12/34", result.text)
    }

    @Test
    fun `isValidLength should return true for 4 digits`() {
        assertTrue(ExpirationDateInputValidator.isValidLength("1225"))
        assertTrue(ExpirationDateInputValidator.isValidLength("12/25"))
    }

    @Test
    fun `isValidLength should return false for incorrect length`() {
        assertFalse(ExpirationDateInputValidator.isValidLength("122"))
        assertFalse(ExpirationDateInputValidator.isValidLength("12255"))
    }

    @Test
    fun `isValidDate should validate future dates`() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR) % 100
        // Future year should be valid
        val futureYear = (currentYear + 1).toString().padStart(2, '0')
        assertTrue(ExpirationDateInputValidator.isValidDate("12/$futureYear"))
        // Past year should be invalid
        val pastYear = (currentYear - 1).toString().padStart(2, '0')
        assertFalse(ExpirationDateInputValidator.isValidDate("12/$pastYear"))
    }

    @Test
    fun `isValidDate should validate month range`() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val futureYear = ((currentYear + 10) % 100).toString().padStart(2, '0')
        // Invalid months
        assertFalse(ExpirationDateInputValidator.isValidDate("00/$futureYear"))
        assertFalse(ExpirationDateInputValidator.isValidDate("13/$futureYear"))
        // Valid months: 1 to currentMonth
        for (month in 1..currentMonth) {
            val monthStr = month.toString().padStart(2, '0')
            assertTrue(
                "Month $monthStr/$futureYear should be valid", 
                ExpirationDateInputValidator.isValidDate("$monthStr/$futureYear")
            )
        }
        // Invalid months: currentMonth+1 to 12
        for (month in (currentMonth+1)..12) {
            val monthStr = month.toString().padStart(2, '0')
            assertFalse(
                "Month $monthStr/$futureYear should be invalid", 
                ExpirationDateInputValidator.isValidDate("$monthStr/$futureYear")
            )
        }
    }

    @Test
    fun `isValidDate should not allow dates more than 10 years in the future`() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        // Date exactly 10 years in the future (should be valid)
        val validYear = (currentYear + 10) % 100
        val validMonth = currentMonth
        val validDate = String.format("%02d/%02d", validMonth, validYear)
        assertTrue(ExpirationDateInputValidator.isValidDate(validDate))
        // Date 10 years and 1 month in the future (should be invalid)
        var invalidMonth = validMonth + 1
        var invalidYear = validYear
        if (invalidMonth > 12) {
            invalidMonth = 1
            invalidYear = (validYear + 1) % 100
        }
        val invalidDate1 = String.format("%02d/%02d", invalidMonth, invalidYear)
        assertFalse(ExpirationDateInputValidator.isValidDate(invalidDate1))
        // Date 11 years in the future (should be invalid)
        val tooFarYear = (currentYear + 11) % 100
        val tooFarDate = String.format("%02d/%02d", currentMonth, tooFarYear)
        assertFalse(ExpirationDateInputValidator.isValidDate(tooFarDate))
    }

    @Test
    fun `parseExpirationDate should parse valid format`() {
        val result = CardValidator.parseExpirationDate("12/25")
        assertNotNull(result)
        assertEquals("12", result!!.first)
        assertEquals("25", result.second)
    }

    @Test
    fun `parseExpirationDate should return null for invalid format`() {
        assertNull(CardValidator.parseExpirationDate("1225"))
        assertNull(CardValidator.parseExpirationDate("12-25"))
        assertNull(CardValidator.parseExpirationDate(""))
    }

    @Test
    fun `parseExpirationDate should pad single digits`() {
        val result = CardValidator.parseExpirationDate("1/5")
        assertNotNull(result)
        assertEquals("01", result!!.first)
        assertEquals("05", result.second)
    }

    @Test
    fun `getDigitsOnly should extract only digits`() {
        assertEquals("1225", CardValidator.getDigitsOnly("12/25"))
        assertEquals("1225", CardValidator.getDigitsOnly("12-25"))
        assertEquals("1225", CardValidator.getDigitsOnly("12ab25"))
    }
} 