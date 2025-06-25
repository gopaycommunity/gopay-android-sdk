package com.gopay.sdk.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Test
import org.junit.Assert.*

class CardInputFormatterTest {

    @Test
    fun `CardNumberVisualTransformation should format digits with spaces`() {
        val transformation = CardNumberVisualTransformation()
        val input = AnnotatedString("1234567890123456")
        val result = transformation.filter(input)
        
        assertEquals("1234 5678 9012 3456", result.text.text)
    }

    @Test
    fun `CardNumberVisualTransformation should handle partial input`() {
        val transformation = CardNumberVisualTransformation()
        val input = AnnotatedString("12345")
        val result = transformation.filter(input)
        
        assertEquals("1234 5", result.text.text)
    }

    @Test
    fun `CardNumberVisualTransformation should limit to 16 digits`() {
        val transformation = CardNumberVisualTransformation()
        val input = AnnotatedString("12345678901234567890")
        val result = transformation.filter(input)
        
        assertEquals("1234 5678 9012 3456", result.text.text)
    }

    @Test
    fun `CardNumberVisualTransformation should have working offset mapping`() {
        val transformation = CardNumberVisualTransformation()
        
        // Test with 4 digits (no spaces yet)
        val input4 = AnnotatedString("1234")
        val result4 = transformation.filter(input4)
        assertEquals("1234", result4.text.text)
        
        // Test edge case that was causing crashes
        assertEquals(4, result4.offsetMapping.originalToTransformed(4)) // End position
        assertEquals(4, result4.offsetMapping.transformedToOriginal(4)) // End position
        
        // Test with 8 digits (one space)
        val input8 = AnnotatedString("12345678")
        val result8 = transformation.filter(input8)
        assertEquals("1234 5678", result8.text.text)
        
        // Test offset mapping for 8 digits
        assertEquals(0, result8.offsetMapping.originalToTransformed(0)) // Start
        assertEquals(4, result8.offsetMapping.originalToTransformed(4)) // Before space
        assertEquals(6, result8.offsetMapping.originalToTransformed(5)) // After space
        assertEquals(9, result8.offsetMapping.originalToTransformed(8)) // End
        
        // Test reverse mapping
        assertEquals(0, result8.offsetMapping.transformedToOriginal(0)) // Start
        assertEquals(4, result8.offsetMapping.transformedToOriginal(4)) // Before space
        assertEquals(4, result8.offsetMapping.transformedToOriginal(5)) // On space
        assertEquals(5, result8.offsetMapping.transformedToOriginal(6)) // After space
        assertEquals(8, result8.offsetMapping.transformedToOriginal(9)) // End
    }

    @Test
    fun `ExpirationDateVisualTransformation should format as MM slash YY`() {
        val transformation = ExpirationDateVisualTransformation()
        val input = AnnotatedString("1225")
        val result = transformation.filter(input)
        
        assertEquals("12/25", result.text.text)
    }

    @Test
    fun `ExpirationDateVisualTransformation should handle partial input`() {
        val transformation = ExpirationDateVisualTransformation()
        val input = AnnotatedString("12")
        val result = transformation.filter(input)
        
        assertEquals("12", result.text.text)
    }

    @Test
    fun `CardNumberInputValidator should filter non-digits`() {
        val result = CardNumberInputValidator.validateInput("1234abc5678")
        assertEquals("12345678", result)
    }

    @Test
    fun `CardNumberInputValidator should limit length`() {
        val result = CardNumberInputValidator.validateInput("12345678901234567890", 10)
        assertEquals("1234567890", result)
    }

    @Test
    fun `CardNumberInputValidator should detect complete card numbers`() {
        assertTrue(CardNumberInputValidator.isComplete("1234567890123456"))
        assertFalse(CardNumberInputValidator.isComplete("123456789012345"))
        assertFalse(CardNumberInputValidator.isComplete("12345678901234567"))
    }

    @Test
    fun `ExpirationDateInputValidator should validate input correctly`() {
        assertEquals("1225", ExpirationDateInputValidator.validateInput("12/25"))
        assertEquals("1225", ExpirationDateInputValidator.validateInput("12ab25cd"))
        assertEquals("1234", ExpirationDateInputValidator.validateInput("123456"))
    }

    @Test
    fun `ExpirationDateInputValidator should detect complete dates`() {
        assertTrue(ExpirationDateInputValidator.isComplete("1225"))
        assertFalse(ExpirationDateInputValidator.isComplete("122"))
        assertFalse(ExpirationDateInputValidator.isComplete("12255"))
    }

    @Test
    fun `CardNumberInputValidator should validate Luhn algorithm`() {
        // Valid test card numbers
        assertTrue(CardNumberInputValidator.isValidLuhn("4532015112830366")) // Visa test card
        assertTrue(CardNumberInputValidator.isValidLuhn("5555555555554444")) // Mastercard test card
        
        // Invalid Luhn
        assertFalse(CardNumberInputValidator.isValidLuhn("1234567890123456"))
        assertFalse(CardNumberInputValidator.isValidLuhn("4532015112830365")) // Last digit changed
    }

    @Test
    fun `ExpirationDateInputValidator should validate future dates`() {
        // These tests may need updating when the current date changes
        // Valid future dates (assuming current date)
        assertTrue(ExpirationDateInputValidator.isValidDate("1230")) // December 2030
        
        // Invalid past dates
        assertFalse(ExpirationDateInputValidator.isValidDate("0120")) // January 2020
        
        // Invalid months
        assertFalse(ExpirationDateInputValidator.isValidDate("1330")) // Month 13
        assertFalse(ExpirationDateInputValidator.isValidDate("0030")) // Month 00
    }
} 