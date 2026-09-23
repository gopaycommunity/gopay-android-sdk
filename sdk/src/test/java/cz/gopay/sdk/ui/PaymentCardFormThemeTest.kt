package cz.gopay.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentCardFormThemeTest {

    @Test
    fun labelLineHeight_keepsTheWholeLineBoxOnASingleLine() {
        val themed = PaymentCardFormTheme(labelLineHeight = 36.sp).labelTextStyle()
        assertEquals(36.sp, themed.lineHeight)
        assertEquals(androidx.compose.ui.text.style.LineHeightStyle.Trim.None, themed.lineHeightStyle?.trim)
        assertEquals(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, themed.lineHeightStyle?.alignment)

        val plain = PaymentCardFormTheme().labelTextStyle()
        assertEquals(null, plain.lineHeightStyle)
    }

    @Test
    fun cssFontWeight_mapsTheFullRange() {
        val expected = mapOf(
            100 to FontWeight.Thin,
            200 to FontWeight.ExtraLight,
            300 to FontWeight.Light,
            400 to FontWeight.Normal,
            500 to FontWeight.Medium,
            600 to FontWeight.SemiBold,
            700 to FontWeight.Bold,
            800 to FontWeight.ExtraBold,
            900 to FontWeight.Black
        )

        expected.forEach { (weight, fontWeight) ->
            assertEquals("Weight $weight should map to $fontWeight", fontWeight, cssFontWeight(weight))
        }
    }

    @Test
    fun cssFontWeight_clampsOutOfRangeValues() {
        assertEquals("Below 100 should clamp to Thin", FontWeight.Thin, cssFontWeight(0))
        assertEquals("Above 900 should clamp to Black", FontWeight.Black, cssFontWeight(1200))
    }

    @Test
    fun renderedLabel_appliesUppercaseOnlyWhenAsked() {
        assertEquals(
            "Card number",
            PaymentCardFormTheme(labelUppercase = false).renderedLabel("Card number")
        )
        assertEquals(
            "CARD NUMBER",
            PaymentCardFormTheme(labelUppercase = true).renderedLabel("Card number")
        )
    }

    @Test
    fun textStyles_carryTheAtomicTypographyParams() {
        val theme = PaymentCardFormTheme(
            fontFamily = FontFamily.Monospace,
            labelFontWeight = 600,
            labelLineHeight = 16.sp,
            labelLetterSpacing = 0.66.sp,
            inputFontWeight = 500,
            placeholderColor = Color.Magenta
        )

        val label = theme.labelTextStyle()
        assertEquals(FontWeight.SemiBold, label.fontWeight)
        assertEquals(FontFamily.Monospace, label.fontFamily)
        assertEquals(16.sp, label.lineHeight)
        assertEquals(0.66.sp, label.letterSpacing)

        val input = theme.inputTextStyle()
        assertEquals(FontWeight.Medium, input.fontWeight)

        assertEquals(Color.Magenta, theme.placeholderTextStyle().color)
        assertEquals(FontFamily.Monospace, theme.errorTextStyle().fontFamily)
        assertEquals(TextDirection.Ltr, input.textDirection)
        assertEquals(TextDirection.Ltr, theme.placeholderTextStyle().textDirection)
        assertEquals(TextDirection.Content, theme.errorTextStyle().textDirection)
        assertEquals(FontFamily.Monospace, theme.helperTextStyle().fontFamily)
    }

    @Test
    fun textStyles_leaveUnsetTypographyToThePlatform() {
        val theme = PaymentCardFormTheme()

        assertEquals(TextUnit.Unspecified, theme.labelTextStyle().lineHeight)
        assertEquals(TextUnit.Unspecified, theme.labelTextStyle().letterSpacing)
        assertNull("An unset weight stays unset", theme.inputTextStyle().fontWeight)
        assertEquals(
            "An unset placeholder color is left to the platform",
            Color.Unspecified,
            theme.placeholderTextStyle().color
        )
    }

    @Test
    fun unspecifiedLengths_readAsNone() {
        // Dp.Unspecified is Dp(NaN) and slips through a plain coerceAtLeast, which would reach
        // the layout pass as a negative padding or a round of NaN.
        assertEquals(0.dp, Dp.Unspecified.orZero())
        assertEquals(0.dp, (-8).dp.orZero())
        assertEquals(8.dp, 8.dp.orZero())
        assertEquals(0.dp, 0.dp.orZero())
    }

    @Test
    fun darkModeWarning_firesOnlyForAColorTheThemeDidNotState() {
        val dark = Color(0xFF1D1D1E)
        val light = Color.White

        assertTrue(
            "A dark text color resolved from the host theme under a dark system is the mismatch",
            contradictsDarkMode(systemInDarkTheme = true, statedColor = Color.Unspecified, resolvedColor = dark)
        )
        assertFalse(
            "A light color under a dark system is what we want, not a mismatch",
            contradictsDarkMode(systemInDarkTheme = true, statedColor = Color.Unspecified, resolvedColor = light)
        )
        assertFalse(
            "A dark color under a light system is ordinary",
            contradictsDarkMode(systemInDarkTheme = false, statedColor = Color.Unspecified, resolvedColor = dark)
        )
        assertFalse(
            "A color the integrator stated is their decision, however dark",
            contradictsDarkMode(systemInDarkTheme = true, statedColor = dark, resolvedColor = dark)
        )
        assertFalse(
            "Nothing to judge when the color did not resolve at all",
            contradictsDarkMode(systemInDarkTheme = true, statedColor = Color.Unspecified, resolvedColor = Color.Unspecified)
        )
    }

    @Test
    fun darkModeWarning_namesBothWaysOut() {
        assertTrue(
            "The warning has to say which form it is about",
            HOST_THEME_DARK_MODE_WARNING.contains("PaymentCardForm")
        )
        assertTrue(
            "and point at the night resource folder",
            HOST_THEME_DARK_MODE_WARNING.contains("res/values-night/themes.xml")
        )
        assertTrue(
            "and at stating the colors instead",
            HOST_THEME_DARK_MODE_WARNING.contains("PaymentCardFormTheme")
        )
    }
}
