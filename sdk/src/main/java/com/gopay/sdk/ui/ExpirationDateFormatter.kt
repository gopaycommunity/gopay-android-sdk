package com.gopay.sdk.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.Calendar

/**
 * Utility class for formatting and validating card expiration dates
 */
object ExpirationDateFormatter {
    
    private const val MAX_EXPIRATION_DIGITS = 4
    private const val EXPIRATION_FORMAT_LENGTH = 5 // MM/YY
    
    /**
     * Formats an expiration date as MM/YY while preserving cursor position
     * 
     * @param current The current TextFieldValue containing text and cursor position
     * @return A new TextFieldValue with formatted text and adjusted cursor position
     */
    fun formatExpirationDate(current: TextFieldValue): TextFieldValue {
        val digitsOnly = current.text.filter { it.isDigit() }
        
        // Don't allow more than 4 digits
        if (digitsOnly.length > MAX_EXPIRATION_DIGITS) {
            return current
        }
        
        val formatted = when {
            digitsOnly.length <= 2 -> digitsOnly
            digitsOnly.length <= 4 -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2)}"
            else -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2, 4)}"
        }
        
        // Calculate new cursor position
        val originalCursor = current.selection.start
        val digitsBefore = current.text.substring(0, minOf(originalCursor, current.text.length))
            .filter { it.isDigit() }.length
        
        // Find cursor position in formatted text
        // We want to position the cursor after the digitsBefore-th digit, including any separators that follow
        var newCursor = 0
        var digitCount = 0
        for ((index, char) in formatted.withIndex()) {
            if (char.isDigit()) {
                digitCount++
                if (digitCount == digitsBefore) {
                    // Position cursor after this digit
                    newCursor = index + 1
                    // Continue to include any non-digit characters (slashes) immediately after
                    while (newCursor < formatted.length && !formatted[newCursor].isDigit()) {
                        newCursor++
                    }
                    break
                }
            }
        }
        
        return TextFieldValue(
            text = formatted,
            selection = TextRange(newCursor)
        )
    }
    
    /**
     * Validates if an expiration date is complete (has 4 digits)
     * 
     * @param expirationDate The expiration date string (may contain slash)
     * @return true if the expiration date has 4 digits
     */
    fun isValidLength(expirationDate: String): Boolean {
        val digitsOnly = expirationDate.filter { it.isDigit() }
        return digitsOnly.length == MAX_EXPIRATION_DIGITS
    }
    
    /**
     * Validates if an expiration date is in the future
     * 
     * @param expirationDate The expiration date in MM/YY format
     * @return true if the date is in the future
     */
    fun isValidDate(expirationDate: String): Boolean {
        val parts = expirationDate.split("/")
        if (parts.size != 2) return false
        
        val month = parts[0].toIntOrNull() ?: return false
        val year = parts[1].toIntOrNull() ?: return false
        
        // Validate month range
        if (month < 1 || month > 12) return false
        
        // Convert 2-digit year to 4-digit year (assuming 20XX)
        val fullYear = if (year < 50) 2000 + year else 1900 + year
        
        // Check if date is in the future
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        
        return when {
            fullYear > currentYear -> true
            fullYear == currentYear -> month >= currentMonth
            else -> false
        }
    }
    
    /**
     * Parses expiration date into month and year components
     * 
     * @param expirationDate The expiration date in MM/YY format
     * @return Pair of (month, year) as strings, or null if invalid format
     */
    fun parseExpirationDate(expirationDate: String): Pair<String, String>? {
        val parts = expirationDate.split("/")
        if (parts.size != 2) return null
        
        val month = parts[0].padStart(2, '0')
        val year = parts[1].padStart(2, '0')
        
        return Pair(month, year)
    }
    
    /**
     * Gets just the digits from a formatted expiration date
     * 
     * @param formattedDate Expiration date that may contain slash
     * @return String containing only the digits
     */
    fun getDigitsOnly(formattedDate: String): String {
        return formattedDate.filter { it.isDigit() }
    }
} 