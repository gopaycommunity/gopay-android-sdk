package com.gopay.sdk.ui

/**
 * Utility class for validating CVV (Card Verification Value) codes
 */
object CvvValidator {
    
    private const val MIN_CVV_LENGTH = 3
    private const val MAX_CVV_LENGTH = 4
    
    /**
     * Validates CVV input - allows only digits and enforces length constraints
     * 
     * @param input The input string to validate
     * @param currentCvv The current CVV value
     * @return The validated CVV string or the current CVV if input is invalid
     */
    fun validateInput(input: String, currentCvv: String): String {
        return if (input.length <= MAX_CVV_LENGTH && input.all { it.isDigit() }) {
            input
        } else {
            currentCvv
        }
    }
    
    /**
     * Checks if a CVV has a valid length (3 or 4 digits)
     * 
     * @param cvv The CVV string to validate
     * @return true if the CVV length is valid
     */
    fun isValidLength(cvv: String): Boolean {
        return cvv.length in MIN_CVV_LENGTH..MAX_CVV_LENGTH
    }
    
    /**
     * Checks if a CVV is complete (3 digits for most cards, 4 for Amex)
     * This is a basic check - for more accurate validation, card type detection would be needed
     * 
     * @param cvv The CVV string to validate
     * @return true if the CVV appears to be complete
     */
    fun isComplete(cvv: String): Boolean {
        return cvv.length >= MIN_CVV_LENGTH
    }
    
    /**
     * Checks if a CVV contains only digits
     * 
     * @param cvv The CVV string to validate
     * @return true if the CVV contains only digits
     */
    fun isNumeric(cvv: String): Boolean {
        return cvv.all { it.isDigit() }
    }
} 