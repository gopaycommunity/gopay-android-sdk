package com.gopay.sdk.ui.utils

/**
 * Simple input validator for card numbers that only allows digits
 */
object CardNumberInputValidator {
    fun validateInput(input: String, maxLength: Int = 16): String {
        return input.filter { it.isDigit() }.take(maxLength)
    }
    fun isComplete(input: String): Boolean {
        return input.filter { it.isDigit() }.length == 16
    }
    fun getDigitsOnly(input: String): String {
        return input.filter { it.isDigit() }
    }
    fun isValidLength(input: String): Boolean {
        val digits = getDigitsOnly(input)
        return digits.length == 16
    }
    fun isValidLuhn(cardNumber: String): Boolean {
        val digits = getDigitsOnly(cardNumber)
        if (digits.length != 16) return false
        var sum = 0
        var alternate = false
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

object ExpirationDateInputValidator {
    fun validateInput(input: String): String {
        return input.filter { it.isDigit() }.take(4)
    }
    fun isComplete(input: String): Boolean {
        return input.filter { it.isDigit() }.length == 4
    }
    fun isValidLength(expirationDate: String): Boolean {
        return CardValidator.isValidLength(expirationDate)
    }
    fun isValidDate(expirationDate: String): Boolean {
        return CardValidator.isValidDate(expirationDate)
    }
}

object CvvValidator {
    private const val MIN_CVV_LENGTH = 3
    private const val MAX_CVV_LENGTH = 3
    fun validateInput(input: String, currentCvv: String): String {
        return if (input.length <= MAX_CVV_LENGTH && input.all { it.isDigit() }) {
            input
        } else {
            currentCvv
        }
    }
    fun isValidLength(cvv: String): Boolean {
        return cvv.length in MIN_CVV_LENGTH..MAX_CVV_LENGTH
    }
    fun isComplete(cvv: String): Boolean {
        return cvv.length >= MIN_CVV_LENGTH
    }
    fun isNumeric(cvv: String): Boolean {
        return cvv.all { it.isDigit() }
    }
}

object CardValidator {
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        companion object {
            fun valid() = ValidationResult(true)
            fun invalid(message: String) = ValidationResult(false, message)
        }
    }
    data class CardValidationResult(
        val cardNumber: ValidationResult,
        val expirationDate: ValidationResult,
        val cvv: ValidationResult,
        val isAllValid: Boolean = cardNumber.isValid && expirationDate.isValid && cvv.isValid
    )
    fun validateCardNumber(cardNumber: String, requireLuhn: Boolean = true): ValidationResult {
        val digitsOnly = CardNumberInputValidator.getDigitsOnly(cardNumber)
        return when {
            digitsOnly.isEmpty() -> ValidationResult.invalid("Card number is required")
            !CardNumberInputValidator.isValidLength(digitsOnly) -> ValidationResult.invalid("Card number must be 16 digits")
            requireLuhn && !CardNumberInputValidator.isValidLuhn(digitsOnly) -> ValidationResult.invalid(
                "Invalid card number"
            )
            else -> ValidationResult.valid()
        }
    }
    // --- Expiration date validation helpers ---
    fun isValidLength(expirationDate: String): Boolean {
        val digitsOnly = expirationDate.filter { it.isDigit() }
        return digitsOnly.length == 4
    }
    fun isValidDate(expirationDate: String): Boolean {
        val parts = expirationDate.split("/")
        if (parts.size != 2) return false
        val month = parts[0].toIntOrNull() ?: return false
        val year = parts[1].toIntOrNull() ?: return false
        if (month < 1 || month > 12) return false
        val fullYear = if (year < 50) 2000 + year else 1900 + year
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val maxYear = currentYear + 10
        val isNotPast = when {
            fullYear > currentYear -> true
            fullYear == currentYear -> month >= currentMonth
            else -> false
        }
        val isNotTooFar = when {
            fullYear < maxYear -> true
            fullYear == maxYear -> month <= currentMonth
            else -> false
        }
        return isNotPast && isNotTooFar
    }
    fun parseExpirationDate(expirationDate: String): Pair<String, String>? {
        val parts = expirationDate.split("/")
        if (parts.size != 2) return null
        val month = parts[0].padStart(2, '0')
        val year = parts[1].padStart(2, '0')
        return Pair(month, year)
    }
    fun getDigitsOnly(formattedDate: String): String {
        return formattedDate.filter { it.isDigit() }
    }
    fun validateExpirationDate(expirationDate: String, requireFutureDate: Boolean = true): ValidationResult {
        return when {
            expirationDate.isEmpty() -> ValidationResult.invalid("Expiration date is required")
            !isValidLength(expirationDate) -> ValidationResult.invalid(
                "Expiration date must be MM/YY format"
            )
            requireFutureDate && !isValidDate(expirationDate) -> ValidationResult.invalid(
                "Card has expired or invalid date"
            )
            else -> ValidationResult.valid()
        }
    }
    fun validateCvv(cvv: String): ValidationResult {
        return when {
            cvv.isEmpty() -> ValidationResult.invalid("CVV is required")
            !CvvValidator.isNumeric(cvv) -> ValidationResult.invalid("CVV must contain only digits")
            !CvvValidator.isValidLength(cvv) -> ValidationResult.invalid("CVV must be 3-4 digits")
            else -> ValidationResult.valid()
        }
    }
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
} 