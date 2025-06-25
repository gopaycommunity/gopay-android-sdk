package com.gopay.sdk.ui

/**
 * Comprehensive validator for payment card data
 * Combines validation logic for all card fields
 */
object CardValidator {
    
    /**
     * Validation result for a card field
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun valid() = ValidationResult(true)
            fun invalid(message: String) = ValidationResult(false, message)
        }
    }
    
    /**
     * Complete validation result for all card fields
     */
    data class CardValidationResult(
        val cardNumber: ValidationResult,
        val expirationDate: ValidationResult,
        val cvv: ValidationResult,
        val isAllValid: Boolean = cardNumber.isValid && expirationDate.isValid && cvv.isValid
    )
    
    /**
     * Validates a card number
     * 
     * @param cardNumber The card number to validate (with or without spaces)
     * @param requireLuhn Whether to perform Luhn algorithm validation
     * @return ValidationResult with validation status and error message if invalid
     */
    fun validateCardNumber(cardNumber: String, requireLuhn: Boolean = true): ValidationResult {
        val digitsOnly = CardNumberInputValidator.getDigitsOnly(cardNumber)
        
        return when {
            digitsOnly.isEmpty() -> ValidationResult.invalid("Card number is required")
            !CardNumberInputValidator.isValidLength(digitsOnly) -> ValidationResult.invalid("Card number must be 16 digits")
            requireLuhn && !CardNumberInputValidator.isValidLuhn(digitsOnly) -> ValidationResult.invalid("Invalid card number")
            else -> ValidationResult.valid()
        }
    }
    
    /**
     * Validates an expiration date
     * 
     * @param expirationDate The expiration date to validate (MM/YY format)
     * @param requireFutureDate Whether to check if the date is in the future
     * @return ValidationResult with validation status and error message if invalid
     */
    fun validateExpirationDate(expirationDate: String, requireFutureDate: Boolean = true): ValidationResult {
        return when {
            expirationDate.isEmpty() -> ValidationResult.invalid("Expiration date is required")
            !ExpirationDateInputValidator.isValidLength(expirationDate) -> ValidationResult.invalid("Expiration date must be MM/YY format")
            requireFutureDate && !ExpirationDateInputValidator.isValidDate(expirationDate) -> ValidationResult.invalid("Card has expired or invalid date")
            else -> ValidationResult.valid()
        }
    }
    
    /**
     * Validates a CVV
     * 
     * @param cvv The CVV to validate
     * @return ValidationResult with validation status and error message if invalid
     */
    fun validateCvv(cvv: String): ValidationResult {
        return when {
            cvv.isEmpty() -> ValidationResult.invalid("CVV is required")
            !CvvValidator.isNumeric(cvv) -> ValidationResult.invalid("CVV must contain only digits")
            !CvvValidator.isValidLength(cvv) -> ValidationResult.invalid("CVV must be 3-4 digits")
            else -> ValidationResult.valid()
        }
    }
    
    /**
     * Validates all card fields at once
     * 
     * @param cardNumber The card number to validate
     * @param expirationDate The expiration date to validate
     * @param cvv The CVV to validate
     * @param requireLuhn Whether to perform Luhn validation on card number
     * @param requireFutureDate Whether to check if expiration date is in the future
     * @return CardValidationResult with validation status for all fields
     */
    fun validateCard(
        cardNumber: String,
        expirationDate: String,
        cvv: String,
        requireLuhn: Boolean = true,
        requireFutureDate: Boolean = true
    ): CardValidationResult {
        return CardValidationResult(
            cardNumber = validateCardNumber(cardNumber, requireLuhn),
            expirationDate = validateExpirationDate(expirationDate, requireFutureDate),
            cvv = validateCvv(cvv)
        )
    }
    
    /**
     * Quick check if all basic fields are filled (not empty)
     * 
     * @param cardNumber The card number
     * @param expirationDate The expiration date
     * @param cvv The CVV
     * @return true if all fields have some content
     */
    fun areFieldsFilled(cardNumber: String, expirationDate: String, cvv: String): Boolean {
        return cardNumber.isNotBlank() && expirationDate.isNotBlank() && cvv.isNotBlank()
    }
    
    /**
     * Quick check if all fields appear to be complete (correct length)
     * 
     * @param cardNumber The card number
     * @param expirationDate The expiration date
     * @param cvv The CVV
     * @return true if all fields appear complete
     */
    fun areFieldsComplete(cardNumber: String, expirationDate: String, cvv: String): Boolean {
        return CardNumberInputValidator.isValidLength(cardNumber) &&
                ExpirationDateInputValidator.isValidLength(expirationDate) &&
                CvvValidator.isComplete(cvv)
    }
} 