package com.gopay.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.ui.InputBorderStyle
import cz.gopay.sdk.ui.PaymentCardFormTheme

/**
 * The themes the demo's picker offers, in the order it shows them.
 *
 * `Default` is the SDK's own theme, which styles nothing and takes the host's Material colors.
 * `Dark` and `Red` are the two the GoPay web card form ships with, written out parameter for
 * parameter; the iOS demo carries the same two with the same values, so a parameter can be read
 * off both. The rendering still differs: `inputBorderStyle` is `UNDERLINE` in both, which draws
 * an underline here and a box on iOS, where the native field has no underline.
 */
object ThemeShowcase {

    private val dark = PaymentCardFormTheme(
        labelColor = Color(0xFF94A3B8),
        labelFontSize = 11.sp,
        labelFontWeight = 600,
        labelUppercase = true,
        inputTextColor = Color(0xFFE2E8F0),
        inputFontSize = 14.sp,
        placeholderColor = Color(0xFF64748B),
        inputBorderStyle = InputBorderStyle.UNDERLINE,
        inputBorderColor = Color(0xFF334155),
        inputBorderWidth = 1.dp,
        inputBackgroundColor = Color.Transparent,
        inputPaddingVertical = 6.dp,
        inputPaddingHorizontal = 12.dp,
        inputBorderRadius = 0.dp,
        inputErrorBorderColor = Color(0xFFEA3C55),
        errorTextColor = Color(0xFFF87171),
        errorFontSize = 11.sp,
        errorMinHeight = 14.dp,
        groupSpacing = 16.dp,
        fieldSpacing = 4.dp,
        formPadding = 16.dp,
        formBackgroundColor = Color(0xFF1A1F2E)
    )

    private val red = PaymentCardFormTheme(
        labelColor = Color(0xFFC8102E),
        labelFontSize = 11.sp,
        labelFontWeight = 600,
        labelUppercase = true,
        inputTextColor = Color(0xFF4B5E68),
        inputFontSize = 14.sp,
        placeholderColor = Color(0xFF64748B),
        inputBorderStyle = InputBorderStyle.UNDERLINE,
        inputBorderColor = Color(0xFFC8102E),
        inputBorderWidth = 1.dp,
        inputBackgroundColor = Color.Transparent,
        inputPaddingVertical = 6.dp,
        inputPaddingHorizontal = 12.dp,
        inputBorderRadius = 0.dp,
        inputErrorBorderColor = Color(0xFFEA3C55),
        errorTextColor = Color(0xFFCC0000),
        errorFontSize = 11.sp,
        errorMinHeight = 14.dp,
        groupSpacing = 16.dp,
        fieldSpacing = 4.dp,
        formBackgroundColor = Color.Transparent,
        formPadding = 16.dp
    )

    private val themes = linkedMapOf(
        "Default" to PaymentCardFormTheme(),
        "Dark" to dark,
        "Red" to red
    )

    /** The picker's order, matching the iOS demo. */
    val names: List<String> = themes.keys.toList()

    /** The named theme, or the SDK defaults when the name is unknown. */
    fun theme(name: String): PaymentCardFormTheme = themes[name] ?: PaymentCardFormTheme()
}
