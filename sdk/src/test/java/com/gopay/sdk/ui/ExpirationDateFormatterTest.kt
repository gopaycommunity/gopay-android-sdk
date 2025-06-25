package com.gopay.sdk.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Test
import org.junit.Assert.*
import java.util.Calendar

class ExpirationDateFormatterTest {

    @Test
    fun `formatExpirationDate should format digits as MM slash YY`() {
        val input = TextFieldValue("1225")
        val result = ExpirationDateFormatter.formatExpirationDate(input)
        
        assertEquals("12/25", result.text)
    }

    @Test
    fun `formatExpirationDate should preserve cursor position`() {
        val input = TextFieldValue("12", selection = TextRange(2))
        val result = ExpirationDateFormatter.formatExpirationDate(input)
        
        assertEquals("12", result.text)
        assertEquals(2, result.selection.start)
        
        // Test with slash
        val input2 = TextFieldValue("123", selection = TextRange(3))
        val result2 = ExpirationDateFormatter.formatExpirationDate(input2)
        
        assertEquals("12/3", result2.text)
        assertEquals(4, result2.selection.start) // Cursor should be after the slash
    }

    @Test
    fun `formatExpirationDate should not accept more than 4 digits`() {
        val input = TextFieldValue("123456")
        val result = ExpirationDateFormatter.formatExpirationDate(input)
        
        assertEquals(input, result) // Should return original unchanged
    }

    @Test
    fun `formatExpirationDate should filter out non-digits`() {
        val input = TextFieldValue("12ab34")
        val result = ExpirationDateFormatter.formatExpirationDate(input)
        
        assertEquals("12/34", result.text)
    }

    @Test
    fun `isValidLength should return true for 4 digits`() {
        assertTrue(ExpirationDateFormatter.isValidLength("1225"))
        assertTrue(ExpirationDateFormatter.isValidLength("12/25"))
    }

    @Test
    fun `isValidLength should return false for incorrect length`() {
        assertFalse(ExpirationDateFormatter.isValidLength("122"))
        assertFalse(ExpirationDateFormatter.isValidLength("12255"))
    }

    @Test
    fun `isValidDate should validate future dates`() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR) % 100
        
        // Future year should be valid
        val futureYear = (currentYear + 1).toString().padStart(2, '0')
        assertTrue(ExpirationDateFormatter.isValidDate("12/$futureYear"))
        
        // Past year should be invalid
        val pastYear = (currentYear - 1).toString().padStart(2, '0')
        assertFalse(ExpirationDateFormatter.isValidDate("12/$pastYear"))
    }

    @Test
    fun `isValidDate should validate month range`() {
        // Test with a future year that should always be valid to isolate month validation
        val futureYear = ((Calendar.getInstance().get(Calendar.YEAR) + 10) % 100).toString().padStart(2, '0')
        
        assertFalse(ExpirationDateFormatter.isValidDate("00/$futureYear"))
        assertFalse(ExpirationDateFormatter.isValidDate("13/$futureYear"))
        assertTrue(ExpirationDateFormatter.isValidDate("01/$futureYear"))
        assertTrue(ExpirationDateFormatter.isValidDate("12/$futureYear"))
    }

    @Test
    fun `parseExpirationDate should parse valid format`() {
        val result = ExpirationDateFormatter.parseExpirationDate("12/25")
        assertNotNull(result)
        assertEquals("12", result!!.first)
        assertEquals("25", result.second)
    }

    @Test
    fun `parseExpirationDate should return null for invalid format`() {
        assertNull(ExpirationDateFormatter.parseExpirationDate("1225"))
        assertNull(ExpirationDateFormatter.parseExpirationDate("12-25"))
        assertNull(ExpirationDateFormatter.parseExpirationDate(""))
    }

    @Test
    fun `parseExpirationDate should pad single digits`() {
        val result = ExpirationDateFormatter.parseExpirationDate("1/5")
        assertNotNull(result)
        assertEquals("01", result!!.first)
        assertEquals("05", result.second)
    }

    @Test
    fun `getDigitsOnly should extract only digits`() {
        assertEquals("1225", ExpirationDateFormatter.getDigitsOnly("12/25"))
        assertEquals("1225", ExpirationDateFormatter.getDigitsOnly("12-25"))
        assertEquals("1225", ExpirationDateFormatter.getDigitsOnly("12ab25"))
    }
} 