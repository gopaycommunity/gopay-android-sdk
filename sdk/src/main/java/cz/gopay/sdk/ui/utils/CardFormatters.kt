package cz.gopay.sdk.ui.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual transformation for card numbers (e.g., 1234 5678 9012 3456)
 */
internal class CardNumberVisualTransformation : VisualTransformation {
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
internal class ExpirationDateVisualTransformation : VisualTransformation {
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
 * Visual transformation for masked card numbers (e.g., **** **** **** 1234)
 * Only shows last 4 digits if the number is valid (16 digits and Luhn valid), otherwise shows formatted as usual
 */
internal class CardNumberMaskedVisualTransformation : VisualTransformation {
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
 *
 * How tall the CVV is next to the expiration field is settled in the row that composes the two,
 * on both platforms. Android needs nothing there: the mask is a plain ASCII asterisk, drawn from
 * the same font as the digits it hides, so the line keeps its metrics whether the field is masked
 * or not and the two fields measure the same on their own.
 */
internal class CvvMaskedVisualTransformation : VisualTransformation {
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