package com.gopay.sdk.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Visual transformation for card numbers (e.g., 1234 5678 9012 3456)
 */
class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val cleanDigits = digits.take(16)
        val formatted = cleanDigits.chunked(4).joinToString(" ")
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = CardNumberOffsetMapping(cleanDigits.length, formatted.length)
        )
    }
}

private class CardNumberOffsetMapping(
    private val cleanLength: Int,
    private val formattedLength: Int
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val clampedOffset = offset.coerceIn(0, cleanLength)
        val spacesBeforePosition = when {
            clampedOffset <= 4 -> 0
            clampedOffset <= 8 -> 1
            clampedOffset <= 12 -> 2
            else -> 3
        }
        val transformedPos = clampedOffset + spacesBeforePosition
        return transformedPos.coerceIn(0, formattedLength)
    }
    override fun transformedToOriginal(offset: Int): Int {
        val clampedOffset = offset.coerceIn(0, formattedLength)
        val spacesCount = when {
            clampedOffset <= 4 -> 0
            clampedOffset <= 9 -> 1
            clampedOffset <= 14 -> 2
            else -> 3
        }
        val originalPos = clampedOffset - spacesCount
        return originalPos.coerceIn(0, cleanLength)
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
 * Formats an expiration date as MM/YY while preserving cursor position
 */
fun formatExpirationDate(current: TextFieldValue): TextFieldValue {
    val digitsOnly = current.text.filter { it.isDigit() }
    if (digitsOnly.length > 4) {
        return current
    }
    val formatted = when {
        digitsOnly.length <= 2 -> digitsOnly
        digitsOnly.length <= 4 -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2)}"
        else -> "${digitsOnly.substring(0, 2)}/${digitsOnly.substring(2, 4)}"
    }
    val originalCursor = current.selection.start
    val digitsBefore = current.text.substring(0, minOf(originalCursor, current.text.length)).filter { it.isDigit() }.length
    var newCursor = 0
    var digitCount = 0
    for ((index, char) in formatted.withIndex()) {
        if (char.isDigit()) {
            digitCount++
            if (digitCount == digitsBefore) {
                newCursor = index + 1
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
 * Visual transformation for masked card numbers (e.g., **** **** **** 1234)
 * Only shows last 4 digits if the number is valid (16 digits and Luhn valid), otherwise shows formatted as usual
 */
class CardNumberMaskedVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val cleanDigits = digits.take(16)
        val numMasked = (cleanDigits.length - 4).coerceAtLeast(0)
        val maskedPart = if (numMasked > 0) "*".repeat(numMasked) else ""
        val visiblePart = cleanDigits.takeLast(4)
        // Insert spaces every 4 chars for the masked part
        val maskedGroups = maskedPart.chunked(4).joinToString(" ")
        val formatted =
            (if (maskedGroups.isNotEmpty()) maskedGroups + " " else "") + visiblePart
        return TransformedText(
            text = AnnotatedString(formatted.trim()),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    return formatted.length
                }
                override fun transformedToOriginal(offset: Int): Int {
                    return cleanDigits.length
                }
            }
        )
    }
}

/**
 * Visual transformation for masking CVV (e.g., 123 -> ***)
 */
class CvvMaskedVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val masked = "*".repeat(digits.length)
        return TransformedText(
            text = AnnotatedString(masked),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = masked.length
                override fun transformedToOriginal(offset: Int): Int = digits.length
            }
        )
    }
} 