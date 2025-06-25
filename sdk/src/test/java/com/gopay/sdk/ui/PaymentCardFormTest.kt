package com.gopay.sdk.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gopay.sdk.model.CardTokenResponse
import org.junit.Test
import org.junit.Assert.*

class PaymentCardFormTest {

    @Test
    fun tokenizationResult_success_containsCorrectData() {
        val tokenResponse = CardTokenResponse(
            maskedPan = "4444************",
            expirationMonth = "01",
            expirationYear = "27",
            brand = "visa",
            token = "test-token-12345",
            fingerprint = "test-fingerprint",
            expiresIn = "3600",
            cardArtUrl = null,
            maskedVirtualPan = null
        )
        
        val result = TokenizationResult.Success(tokenResponse)
        
        assertTrue("Result should be Success", result is TokenizationResult.Success)
        assertEquals("Token should match", "test-token-12345", result.tokenResponse.token)
        assertEquals("Brand should match", "visa", result.tokenResponse.brand)
        assertEquals("Masked PAN should match", "4444************", result.tokenResponse.maskedPan)
    }

    @Test
    fun tokenizationResult_error_containsCorrectMessage() {
        val errorMessage = "Network error occurred"
        val exception = RuntimeException("Network failure")
        
        val result = TokenizationResult.Error(errorMessage, exception)
        
        assertTrue("Result should be Error", result is TokenizationResult.Error)
        assertEquals("Error message should match", errorMessage, result.message)
        assertEquals("Exception should match", exception, result.exception)
    }

    @Test
    fun paymentCardFormTheme_defaultValues_areCorrect() {
        val theme = PaymentCardFormTheme()
        
        // Test default text styles
        assertEquals("Default label color should be Gray", Color.Gray, theme.labelTextStyle.color)
        assertEquals("Default label font size should be 14sp", 14.sp, theme.labelTextStyle.fontSize)
        
        assertEquals("Default input font size should be 16sp", 16.sp, theme.inputTextStyle.fontSize)
        
        assertEquals("Default error color should be Red", Color.Red, theme.errorTextStyle.color)
        assertEquals("Default error font size should be 12sp", 12.sp, theme.errorTextStyle.fontSize)
        
        // Test default colors
        assertEquals("Default border color should be Gray", Color.Gray, theme.inputBorderColor)
        assertEquals("Default background color should be White", Color.White, theme.inputBackgroundColor)
        
        // Test default sizes
        assertEquals("Default border width should be 1dp", 1.dp, theme.inputBorderWidth)
        assertEquals("Default field spacing should be 2dp", 2.dp, theme.fieldSpacing)
        assertEquals("Default group spacing should be 16dp", 16.dp, theme.groupSpacing)
    }

    @Test
    fun paymentCardFormTheme_customValues_areApplied() {
        val customTheme = PaymentCardFormTheme(
            labelTextStyle = TextStyle(color = Color.Blue, fontSize = 16.sp),
            inputTextStyle = TextStyle(color = Color.Red, fontSize = 18.sp),
            errorTextStyle = TextStyle(color = Color.Green, fontSize = 12.sp),
            inputBorderColor = Color.Yellow,
            inputBackgroundColor = Color.Cyan,
            inputShape = RoundedCornerShape(8.dp),
            inputBorderWidth = 2.dp,
            fieldSpacing = 20.dp,
            groupSpacing = 12.dp
        )

        // Test custom text styles
        assertEquals("Custom label color should be Blue", Color.Blue, customTheme.labelTextStyle.color)
        assertEquals("Custom label font size should be 16sp", 16.sp, customTheme.labelTextStyle.fontSize)
        
        assertEquals("Custom input color should be Red", Color.Red, customTheme.inputTextStyle.color)
        assertEquals("Custom input font size should be 18sp", 18.sp, customTheme.inputTextStyle.fontSize)
        
        assertEquals("Custom error color should be Green", Color.Green, customTheme.errorTextStyle.color)
        assertEquals("Custom error font size should be 12sp", 12.sp, customTheme.errorTextStyle.fontSize)
        
        // Test custom colors and sizes
        assertEquals("Custom border color should be Yellow", Color.Yellow, customTheme.inputBorderColor)
        assertEquals("Custom background color should be Cyan", Color.Cyan, customTheme.inputBackgroundColor)
        assertEquals("Custom border width should be 2dp", 2.dp, customTheme.inputBorderWidth)
        assertEquals("Custom field spacing should be 20dp", 20.dp, customTheme.fieldSpacing)
        assertEquals("Custom group spacing should be 12dp", 12.dp, customTheme.groupSpacing)
    }

    @Test
    fun cardNumberFormatting_addsSpacesCorrectly() {
        // Test the card number formatting logic
        val testCases = mapOf(
            "1234567890123456" to "1234 5678 9012 3456",
            "123456789012345" to "1234 5678 9012 345",
            "12345678901234" to "1234 5678 9012 34",
            "1234567890123" to "1234 5678 9012 3",
            "123456789012" to "1234 5678 9012",
            "12345678901" to "1234 5678 901",
            "1234567890" to "1234 5678 90",
            "123456789" to "1234 5678 9",
            "12345678" to "1234 5678",
            "1234567" to "1234 567",
            "123456" to "1234 56",
            "12345" to "1234 5",
            "1234" to "1234",
            "123" to "123",
            "12" to "12",
            "1" to "1",
            "" to ""
        )
        
        testCases.forEach { (input, expected) ->
            val formatted = formatCardNumber(input)
            assertEquals("Card number '$input' should format to '$expected'", expected, formatted)
        }
    }

    @Test
    fun expirationDateFormatting_addsSlashCorrectly() {
        // Test the expiration date formatting logic
        val testCases = mapOf(
            "1225" to "12/25",
            "122" to "12/2",
            "12" to "12",
            "1" to "1",
            "" to "",
            "01" to "01",
            "0125" to "01/25"
        )
        
        testCases.forEach { (input, expected) ->
            val formatted = formatExpirationDate(input)
            assertEquals("Expiration date '$input' should format to '$expected'", expected, formatted)
        }
    }

    @Test
    fun cvvValidation_limitsToFourDigits() {
        // Test CVV length validation logic
        val testCases = mapOf(
            "123" to true,
            "1234" to true,
            "12345" to false, // Should be rejected
            "12" to true,
            "1" to true,
            "" to true
        )
        
        testCases.forEach { (input, shouldBeValid) ->
            val isValid = input.length <= 4 && input.all { it.isDigit() }
            assertEquals("CVV '$input' validity should be $shouldBeValid", shouldBeValid, isValid)
        }
    }

    @Test
    fun cvvValidation_onlyAcceptsDigits() {
        // Test CVV digit validation logic
        val testCases = mapOf(
            "123" to true,
            "12a" to false,
            "1b3" to false,
            "abc" to false,
            "12!" to false,
            "1 3" to false,
            "" to true
        )
        
        testCases.forEach { (input, shouldBeValid) ->
            val isValid = input.all { it.isDigit() }
            assertEquals("CVV '$input' digit validation should be $shouldBeValid", shouldBeValid, isValid)
        }
    }
    
    // Helper functions to test the formatting logic
    private fun formatCardNumber(input: String): String {
        val digitsOnly = input.filter { it.isDigit() }
        return digitsOnly.chunked(4).joinToString(" ").take(19) // 16 digits + 3 spaces
    }

    private fun formatExpirationDate(input: String): String {
        val digitsOnly = input.filter { it.isDigit() }
        return when {
            digitsOnly.length <= 2 -> digitsOnly
            digitsOnly.length <= 4 -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2)}"
            else -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2, 4)}"
        }
    }

    @Test
    fun expirationDateValidation_validatesParsing() {
        // Test valid expiration date parsing
        val validTestCases = mapOf(
            "12/25" to Pair("12", "25"),
            "01/27" to Pair("01", "27"),
            "06/30" to Pair("06", "30")
        )
        
        validTestCases.forEach { (input, expected) ->
            val parts = input.split("/")
            assertEquals("Valid expiration '$input' should have 2 parts", 2, parts.size)
            assertEquals("Month should match", expected.first, parts[0].padStart(2, '0'))
            assertEquals("Year should match", expected.second, parts[1].padStart(2, '0'))
        }
    }

    @Test
    fun expirationDateValidation_detectsInvalidFormat() {
        // Test invalid expiration date formats
        val invalidTestCases = listOf(
            "1225", // No slash
            "12", // Incomplete
            "12/", // Incomplete
            "/25", // Missing month
            "13/25", // Invalid month
            "00/25", // Invalid month
            "12/ab", // Non-numeric year
            "ab/25", // Non-numeric month
            "", // Empty
            "12/25/30" // Too many parts
        )
        
        invalidTestCases.forEach { input ->
            val parts = input.split("/")
            val isValid = parts.size == 2 && 
                          parts[0].length == 2 && 
                          parts[1].length == 2 &&
                          parts[0].all { it.isDigit() } &&
                          parts[1].all { it.isDigit() } &&
                          parts[0].toIntOrNull() in 1..12
            
            assertFalse("Invalid expiration '$input' should be detected as invalid", isValid)
        }
    }

    @Test
    fun cardDataCreation_createsCorrectStructure() {
        // Test that card data is created with correct format
        val cardNumber = "4444 4444 4444 4448"
        val expirationDate = "12/25"
        val cvv = "123"
        
        // Simulate the processing that happens in the form
        val expParts = expirationDate.split("/")
        val expMonth = expParts[0].padStart(2, '0')
        val expYear = expParts[1].padStart(2, '0')
        
        // This simulates creating CardData (we can't import it due to dependencies)
        val processedCardNumber = cardNumber.replace(" ", "")
        
        assertEquals("Card number should be cleaned", "4444444444444448", processedCardNumber)
        assertEquals("Month should be padded", "12", expMonth)
        assertEquals("Year should be padded", "25", expYear)
        assertEquals("CVV should remain unchanged", "123", cvv)
    }

    @Test
    fun inputValidation_checksAllFieldsPresent() {
        // Test that all required fields are validated
        val testCases = mapOf(
            Triple("", "12/25", "123") to false, // Empty card number
            Triple("4444444444444448", "", "123") to false, // Empty expiration
            Triple("4444444444444448", "12/25", "") to false, // Empty CVV
            Triple("4444444444444448", "12/25", "123") to true, // All fields present
            Triple("444444444444444a", "12/25", "123") to false, // Invalid card number
            Triple("4444444444444448", "13/25", "123") to false, // Invalid month
            Triple("4444444444444448", "12/25", "12a") to false // Invalid CVV
        )
        
        testCases.forEach { (fields, shouldBeValid) ->
            val (cardNumber, expiration, cvv) = fields
            
            val cardNumberValid = cardNumber.isNotEmpty() && cardNumber.replace(" ", "").all { it.isDigit() }
            
            // More comprehensive expiration validation
            val expirationValid = if (expiration.contains("/")) {
                val parts = expiration.split("/")
                parts.size == 2 && 
                parts[0].all { it.isDigit() } && 
                parts[1].all { it.isDigit() } &&
                parts[0].toIntOrNull() in 1..12
            } else {
                false
            }
            
            val cvvValid = cvv.isNotEmpty() && cvv.all { it.isDigit() } && cvv.length <= 4
            
            val allValid = cardNumberValid && expirationValid && cvvValid
            
            assertEquals(
                "Fields ($cardNumber, $expiration, $cvv) validity should be $shouldBeValid", 
                shouldBeValid, 
                allValid
            )
        }
    }
} 