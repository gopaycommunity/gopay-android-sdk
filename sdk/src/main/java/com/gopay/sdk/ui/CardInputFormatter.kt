package com.gopay.sdk.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * A visual transformation that formats card numbers while keeping the underlying value clean.
 * This approach separates input logic from display formatting for better UX.
 */
class CardNumberVisualTransformation : VisualTransformation {
    
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        
        // Don't allow more than 16 digits
        val cleanDigits = digits.take(16)
        
        // Format with spaces every 4 digits
        val formatted = cleanDigits.chunked(4).joinToString(" ")
        
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = CardNumberOffsetMapping(cleanDigits.length, formatted.length)
        )
    }
}

/**
 * Maps cursor positions between the clean digit input and formatted display
 */
private class CardNumberOffsetMapping(
    private val cleanLength: Int,
    private val formattedLength: Int
) : OffsetMapping {
    
    override fun originalToTransformed(offset: Int): Int {
        // Map position in clean digits to position in formatted text
        val clampedOffset = offset.coerceIn(0, cleanLength)
        
        // Calculate how many spaces should be before this position
        // Spaces appear after every 4 digits: positions 4, 8, 12
        val spacesBeforePosition = when {
            clampedOffset <= 4 -> 0
            clampedOffset <= 8 -> 1
            clampedOffset <= 12 -> 2
            else -> 3
        }
        
        // The transformed position is original position + number of spaces before it
        val transformedPos = clampedOffset + spacesBeforePosition
        
        // Make sure we don't exceed the actual formatted text length
        return transformedPos.coerceIn(0, formattedLength)
    }
    
    override fun transformedToOriginal(offset: Int): Int {
        // Map position in formatted text back to position in clean digits
        val clampedOffset = offset.coerceIn(0, formattedLength)
        
        // Count how many spaces are before this position
        val spacesCount = when {
            clampedOffset <= 4 -> 0    // Before first space
            clampedOffset <= 9 -> 1    // After first space (pos 5-9)
            clampedOffset <= 14 -> 2   // After second space (pos 10-14)
            else -> 3                  // After third space (pos 15+)
        }
        
        // Original position is transformed position minus the spaces
        val originalPos = clampedOffset - spacesCount
        
        return originalPos.coerceIn(0, cleanLength)
    }
}

/**
 * Simple input validator for card numbers that only allows digits
 */
object CardNumberInputValidator {
    
    /**
     * Filters input to only allow digits up to 16 characters
     */
    fun validateInput(input: String, maxLength: Int = 16): String {
        return input.filter { it.isDigit() }.take(maxLength)
    }
    
    /**
     * Checks if the input represents a complete card number
     */
    fun isComplete(input: String): Boolean {
        return input.filter { it.isDigit() }.length == 16
    }
    
    /**
     * Gets only digits from a string (removes spaces and other characters)
     */
    fun getDigitsOnly(input: String): String {
        return input.filter { it.isDigit() }
    }
    
    /**
     * Checks if the card number has valid length (16 digits)
     */
    fun isValidLength(input: String): Boolean {
        val digits = getDigitsOnly(input)
        return digits.length == 16
    }
    
    /**
     * Validates card number using Luhn algorithm
     */
    fun isValidLuhn(cardNumber: String): Boolean {
        val digits = getDigitsOnly(cardNumber)
        if (digits.length != 16) return false
        
        var sum = 0
        var alternate = false
        
        // Process digits from right to left
        for (i in digits.length - 1 downTo 0) {
            var digit = digits[i].digitToInt()
            
            if (alternate) {
                digit *= 2
                if (digit > 9) {
                    digit = (digit % 10) + 1
                }
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
}

/**
 * Visual transformation for expiration dates (MM/YY format)
 */
class ExpirationDateVisualTransformation : VisualTransformation {
    
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(4)
        
        val formatted = when {
            digits.length <= 2 -> digits
            else -> "${digits.substring(0, 2)}/${digits.substring(2)}"
        }
        
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = ExpirationDateOffsetMapping(digits.length, formatted.length)
        )
    }
}

/**
 * Maps cursor positions for expiration date formatting
 */
private class ExpirationDateOffsetMapping(
    private val cleanLength: Int,
    private val formattedLength: Int
) : OffsetMapping {
    
    override fun originalToTransformed(offset: Int): Int {
        val clampedOffset = offset.coerceIn(0, cleanLength)
        return when {
            clampedOffset <= 2 -> clampedOffset
            else -> clampedOffset + 1 // Add 1 for the slash
        }
    }
    
    override fun transformedToOriginal(offset: Int): Int {
        val clampedOffset = offset.coerceIn(0, formattedLength)
        return when {
            clampedOffset <= 2 -> clampedOffset
            clampedOffset == 3 -> 2 // The slash position maps to after 2nd digit
            else -> clampedOffset - 1 // Subtract 1 for the slash
        }
    }
}

/**
 * Input validator for expiration dates
 */
object ExpirationDateInputValidator {
    
    fun validateInput(input: String): String {
        return input.filter { it.isDigit() }.take(4)
    }
    
    fun isComplete(input: String): Boolean {
        return input.filter { it.isDigit() }.length == 4
    }
    
    /**
     * Checks if the expiration date has valid length (MM/YY = 5 chars or MMYY = 4 digits)
     */
    fun isValidLength(input: String): Boolean {
        val digits = input.filter { it.isDigit() }
        return digits.length == 4 || (input.contains("/") && input.length == 5)
    }
    
    /**
     * Validates if the expiration date is valid and in the future
     */
    fun isValidDate(input: String): Boolean {
        val digits = input.filter { it.isDigit() }
        if (digits.length != 4) return false
        
        val month = digits.substring(0, 2).toIntOrNull() ?: return false
        val year = digits.substring(2, 4).toIntOrNull() ?: return false
        
        // Check month is valid
        if (month < 1 || month > 12) return false
        
        // Check if date is in the future
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) % 100
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        
        return when {
            year > currentYear -> true
            year == currentYear -> month >= currentMonth
            else -> false
        }
    }
} 