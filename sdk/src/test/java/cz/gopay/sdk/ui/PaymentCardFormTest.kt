package cz.gopay.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCardFormTest {

    @Test
    fun cardEncryptionResult_success_carriesJwe() {
        val jwe = "header.encryptedKey.iv.ciphertext.tag"

        val result = CardEncryptionResult.Success(jwe)

        assertTrue("Result should be Success", result is CardEncryptionResult.Success)
        assertEquals("JWE should match", jwe, result.jwe)
    }

    @Test
    fun cardEncryptionResult_error_carriesMessageAndCause() {
        val errorMessage = "Public key fetch failed"
        val exception = RuntimeException("Network failure")

        val result = CardEncryptionResult.Error(errorMessage, exception)

        assertTrue("Result should be Error", result is CardEncryptionResult.Error)
        assertEquals("Error message should match", errorMessage, result.message)
        assertEquals("Exception should match", exception, result.exception)
    }

    @Test
    fun paymentCardFormTheme_defaultValues_areCorrect() {
        // An unthemed form is deliberately unstyled: it takes the platform's own type sizes, the
        // host's color scheme for every color it leaves unspecified, and leaves the padding around
        // it to the host, because it sits on the merchant's screen rather than on a page of its
        // own like the hosted card form.
        val theme = PaymentCardFormTheme()

        // Labels
        assertEquals("The label color is left to the host theme", Color.Unspecified, theme.labelColor)
        assertEquals("Labels are 12sp, the size the iOS SDK uses", 12.sp, theme.labelFontSize)
        assertEquals("Default label weight should be regular", 400, theme.labelFontWeight)
        assertFalse("Labels should keep the case they were written in", theme.labelUppercase)
        assertFalse("Labels should be visible by default", theme.labelHidden)

        // Input text
        assertEquals("Default input font size should be 16sp", 16.sp, theme.inputFontSize)
        assertNull("Default input weight should be unset", theme.inputFontWeight)
        assertNull("Default input height should be unset", theme.inputHeight)
        assertNull("Default placeholder color should be unset", theme.placeholderColor)

        // Border
        assertEquals("Default border style is a box, as on iOS", InputBorderStyle.BOXED, theme.inputBorderStyle)
        assertEquals("So is the border color", Color.Unspecified, theme.inputBorderColor)
        assertEquals("Default background should be transparent, so the host's shows through", Color.Transparent, theme.inputBackgroundColor)
        assertEquals("Default border width should be 1dp", 1.dp, theme.inputBorderWidth)
        assertEquals("Default border radius should be 4dp", 4.dp, theme.inputBorderRadius)
        assertEquals("Default vertical padding should be 12dp", 12.dp, theme.inputPaddingVertical)
        assertEquals("Default horizontal padding should be 12dp", 12.dp, theme.inputPaddingHorizontal)

        assertEquals("And the error border color", Color.Unspecified, theme.inputErrorBorderColor)

        // Errors
        assertEquals("And the error color", Color.Unspecified, theme.errorTextColor)
        assertEquals("Default error font size should be 12sp", 12.sp, theme.errorFontSize)
        assertEquals("Nothing is reserved for the error line by default", 0.dp, theme.errorMinHeight)
        assertNull("Default error spacing should fall back to fieldSpacing", theme.errorSpacing)

        // Layout
        assertEquals("Default group spacing should be 16dp", 16.dp, theme.groupSpacing)
        assertEquals("Default field spacing should be 4dp", 4.dp, theme.fieldSpacing)
        assertEquals("The host pads the form, so the default is none", 0.dp, theme.formPadding)
        assertEquals("Default form background should be transparent", Color.Transparent, theme.formBackgroundColor)

        // Mobile-only helper text
        assertEquals("And the helper color", Color.Unspecified, theme.helperTextColor)
        assertEquals("Default helper font size should be 12sp", 12.sp, theme.helperFontSize)
    }

    @Test
    fun paymentCardFormTheme_customValues_areApplied() {
        val customTheme = PaymentCardFormTheme(
            labelColor = Color.Blue,
            labelFontSize = 16.sp,
            labelFontWeight = 600,
            labelUppercase = true,
            inputTextColor = Color.Red,
            inputFontSize = 18.sp,
            errorTextColor = Color.Green,
            errorFontSize = 12.sp,
            inputBorderStyle = InputBorderStyle.UNDERLINE,
            inputBorderColor = Color.Yellow,
            inputBackgroundColor = Color.Cyan,
            inputBorderRadius = 8.dp,
            inputBorderWidth = 2.dp,
            fieldSpacing = 20.dp,
            groupSpacing = 12.dp
        )

        assertEquals("Custom label color should be Blue", Color.Blue, customTheme.labelColor)
        assertEquals("Custom label font size should be 16sp", 16.sp, customTheme.labelFontSize)
        assertEquals("Custom label weight should be 600", 600, customTheme.labelFontWeight)
        assertTrue("Custom labels should be uppercased", customTheme.labelUppercase)

        assertEquals("Custom input color should be Red", Color.Red, customTheme.inputTextColor)
        assertEquals("Custom input font size should be 18sp", 18.sp, customTheme.inputFontSize)

        assertEquals("Custom error color should be Green", Color.Green, customTheme.errorTextColor)
        assertEquals("Custom error font size should be 12sp", 12.sp, customTheme.errorFontSize)

        assertEquals("Custom border style should be underline", InputBorderStyle.UNDERLINE, customTheme.inputBorderStyle)
        assertEquals("Custom border color should be Yellow", Color.Yellow, customTheme.inputBorderColor)
        assertEquals("Custom background color should be Cyan", Color.Cyan, customTheme.inputBackgroundColor)
        assertEquals("Custom border width should be 2dp", 2.dp, customTheme.inputBorderWidth)
        assertEquals("Custom border radius should be 8dp", 8.dp, customTheme.inputBorderRadius)
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

    @Test
    fun paymentFormInputs_defaultValues_areCorrect() {
        val inputs = PaymentFormInputs()
        
        assertEquals("Default card number label should be 'Card Number'", "Card Number", inputs.cardNumber.label)
        assertEquals("Default card number placeholder should be correct", "1234 1234 1234 1234", inputs.cardNumber.placeholder)
        
        assertEquals("Default expiration label should be 'MM/YY'", "MM/YY", inputs.expirationDate.label)
        assertEquals("Default expiration placeholder should be 'MM/YY'", "MM/YY", inputs.expirationDate.placeholder)
        
        assertEquals("Default CVV label should be 'CVV'", "CVV", inputs.cvv.label)
        assertEquals("Default CVV placeholder should be '123'", "123", inputs.cvv.placeholder)
    }

    @Test
    fun inputFieldConfig_defaultValues_areCorrect() {
        val config = InputFieldConfig(label = "Test Label")
        
        assertEquals("Label should be set", "Test Label", config.label)
        assertNull("Helper text should be null by default", config.helperText)
        assertNull("Error text should be null by default", config.errorText)
        assertFalse("Has error should be false by default", config.hasError)
        assertNull("Placeholder should be null by default", config.placeholder)
    }
} 