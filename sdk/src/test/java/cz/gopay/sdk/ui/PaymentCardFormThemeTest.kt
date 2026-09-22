package cz.gopay.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.util.SdkLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentCardFormThemeTest {

    /** Warnings the decoder reports to the host; unit tests run without Logcat. */
    private val warnings = mutableListOf<String>()
    private lateinit var previousSink: (String) -> Unit

    @Before
    fun collectWarnings() {
        previousSink = SdkLog.warnSink
        SdkLog.warnSink = { warnings += it }
    }

    @After
    fun restoreWarnings() {
        SdkLog.warnSink = previousSink
    }

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
    fun parseCssColor_acceptsTheCssForms() {
        assertEquals("Short hex should expand", Color(0xFFAABBCC), parseCssColor("#abc"))
        assertEquals("Four digit hex should expand and carry alpha", Color(0x88AABBCC), parseCssColor("#abc8"))
        assertEquals("Six digit hex should parse", Color(0xFF4B5E68), parseCssColor("#4b5e68"))
        assertEquals("Eight digit hex should carry alpha", Color(0x804B5E68), parseCssColor("#4b5e6880"))
        assertEquals("Keyword should parse", Color.Transparent, parseCssColor("transparent"))
        assertNull("Unusable values should be rejected", parseCssColor("rgb(1, 2, 3)"))
    }

    @Test
    fun colorHex_roundTripsThroughTheDocument() {
        assertEquals("Opaque colors drop the alpha", "#4b5e68", Color(0xFF4B5E68).toHexStringOrNull())
        assertEquals("Translucent colors keep it", "#4b5e6880", Color(0x804B5E68).toHexStringOrNull())
        assertNull("Unspecified has no hex form", Color.Unspecified.toHexStringOrNull())
        assertEquals(
            "Parsing the rendered hex should give the color back",
            Color(0x804B5E68),
            parseCssColor(Color(0x804B5E68).toHexStringOrNull()!!)
        )
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
    fun themeDocument_roundTripsThroughJson() {
        // Every parameter the document can carry is set to a non-default value on purpose: the
        // two mappers are hand-written, one line per parameter, and a forgotten line would
        // otherwise pass unnoticed. fontFamily is the only parameter left out — a font family
        // carries no name the other channels could resolve, so the document never states one.
        val theme = PaymentCardFormTheme(
            labelColor = Color(0xFF4B5E68),
            labelFontSize = 11.sp,
            labelFontWeight = 600,
            labelLineHeight = 16.sp,
            labelUppercase = true,
            labelLetterSpacing = 0.66.sp,
            labelHidden = true,
            inputTextColor = Color(0xFF223344),
            inputFontSize = 14.sp,
            inputFontWeight = 500,
            inputHeight = 44.dp,
            placeholderColor = Color(0xFF99AABB),
            inputBorderStyle = InputBorderStyle.UNDERLINE,
            inputBorderColor = Color(0xFF698492),
            inputBorderWidth = 2.dp,
            inputBackgroundColor = Color(0x80FFFFFF),
            inputPaddingVertical = 6.dp,
            inputPaddingHorizontal = 3.dp,
            inputBorderRadius = 7.dp,
            inputErrorBorderColor = Color(0xFFEA3C55),
            errorTextColor = Color(0xFFCC0000),
            errorFontSize = 11.sp,
            errorMinHeight = 14.dp,
            errorSpacing = 3.dp,
            groupSpacing = 18.dp,
            fieldSpacing = 5.dp,
            formPadding = 16.dp,
            formBackgroundColor = Color(0xFF1A1F2E),
            helperTextColor = Color(0xFF556677),
            helperFontSize = 13.sp
        )

        val restored = PaymentCardFormThemeJson.parse(theme.toJsonModel().toJson()).toTheme()

        assertEquals("The theme should survive the round trip", theme, restored)
    }

    @Test
    fun themeDocument_ignoresKeysTheSdkDoesNotImplement() {
        val webDocument = """
            {
              "labelColor": "#4b5e68",
              "labelFontSize": 11,
              "labelFontWeight": 600,
              "labelUppercase": true,
              "inputBackgroundColor": "transparent",
              "inputBorderStyle": "underline",
              "errorHidden": false,
              "submitBackgroundColor": "#1899d6",
              "submitHoverBackgroundColor": "#1482ba",
              "submitBorderRadius": 4,
              "inputBorderCollapse": true,
              "focusRingWidth": 2,
              "focusRingColor": "#19c7d6",
              "focusGradientStart": "#19c7d6",
              "focusGradientEnd": "#1899d6",
              "inputLetterSpacing": 0.5,
              "inputLineHeight": 18,
              "aKeyFromSomeFutureRelease": 42
            }
        """.trimIndent()

        val theme = PaymentCardFormThemeJson.parse(webDocument).toTheme()

        assertEquals("Known keys should apply", Color(0xFF4B5E68), theme.labelColor)
        assertEquals("Sizes map 1:1 to sp", 11.sp, theme.labelFontSize)
        assertEquals("Weights arrive as CSS numbers", 600, theme.labelFontWeight)
        assertTrue("Booleans should apply", theme.labelUppercase)
        assertEquals("The transparent keyword should apply", Color.Transparent, theme.inputBackgroundColor)
        assertEquals("The border style should apply", InputBorderStyle.UNDERLINE, theme.inputBorderStyle)
        assertEquals(
            "Omitted keys should keep the base theme value",
            PaymentCardFormTheme().inputBorderColor,
            theme.inputBorderColor
        )
        assertEquals(
            "The keys the form would have to paint by hand are ignored like any other",
            PaymentCardFormTheme(
                labelColor = Color(0xFF4B5E68),
                labelFontSize = 11.sp,
                labelFontWeight = 600,
                labelUppercase = true,
                inputBackgroundColor = Color.Transparent,
                inputBorderStyle = InputBorderStyle.UNDERLINE
            ),
            theme
        )
    }

    @Test
    fun borderStyle_readsBothOfTheWebsValues() {
        assertEquals(
            InputBorderStyle.BOXED,
            PaymentCardFormThemeJson.parse("""{"inputBorderStyle": "boxed"}""").toTheme().inputBorderStyle
        )
        assertEquals(
            "The keyword is matched regardless of case",
            InputBorderStyle.BOXED,
            PaymentCardFormThemeJson.parse("""{"inputBorderStyle": "BOXED"}""").toTheme().inputBorderStyle
        )
        assertEquals(
            InputBorderStyle.UNDERLINE,
            PaymentCardFormThemeJson.parse("""{"inputBorderStyle": "underline"}""").toTheme().inputBorderStyle
        )
        assertEquals(
            "An unknown style keeps the base theme's",
            PaymentCardFormTheme().inputBorderStyle,
            PaymentCardFormThemeJson.parse("""{"inputBorderStyle": "dotted"}""").toTheme().inputBorderStyle
        )
    }

    @Test
    fun fontWeight_outsideTheCssRange_dropsAndIsReported() {
        val theme = PaymentCardFormThemeJson
            .parse("""{"labelFontWeight": 1200, "inputFontWeight": 0}""")
            .toTheme()

        assertEquals("The base weight is kept", PaymentCardFormTheme().labelFontWeight, theme.labelFontWeight)
        assertNull("And an unset one stays unset", theme.inputFontWeight)
        assertEquals("Both are reported", 2, warnings.count { it.contains("FontWeight") })
    }

    @Test
    fun keysThisPlatformCannotRender_areReportedNotSwallowed() {
        val document = """
            {
              "labelColor": "#4b5e68",
              "inputBorderCollapse": true,
              "focusRingWidth": 2,
              "focusGradientStart": "#19c7d6",
              "inputLetterSpacing": 0.5,
              "inputLineHeight": 18,
              "submitBorderRadius": 4,
              "aKeyFromSomeFutureRelease": 42
            }
        """.trimIndent()

        PaymentCardFormThemeJson.parse(document)

        val reported = warnings.filter { it.contains("not supported") }
        assertEquals("One warning per unsupported key", 5, reported.size)
        listOf(
            "inputBorderCollapse",
            "focusRingWidth",
            "focusGradientStart",
            "inputLetterSpacing",
            "inputLineHeight"
        ).forEach { key ->
            assertTrue("$key should be reported", reported.any { it.contains(key) })
        }
        assertTrue(
            "A web-only or unknown key stays silent",
            reported.none { it.contains("submit") || it.contains("FutureRelease") }
        )
    }

    @Test
    fun themeDocument_appliesOnTopOfABaseTheme() {
        val base = PaymentCardFormTheme(inputBorderColor = Color.Magenta, groupSpacing = 24.dp)

        val theme = PaymentCardFormThemeJson.parse("""{"groupSpacing": 8}""").toTheme(base)

        assertEquals("The stated key should win", 8.dp, theme.groupSpacing)
        assertEquals("The rest should come from the base", Color.Magenta, theme.inputBorderColor)
    }

    @Test
    fun encodedDocument_leavesOutTheKeysItDoesNotSet() {
        val document = PaymentCardFormThemeJson(labelColor = "#4b5e68", labelFontSize = 11f).toJson()

        assertTrue("The stated keys should be written", document.contains("\"labelColor\""))
        assertFalse("Unset keys should be left out", document.contains("\"labelLineHeight\""))
        assertFalse("Unspecified colors have no hex form", document.contains("\"inputTextColor\""))
    }

    @Test
    fun emptyDocuments_leaveTheBaseThemeUntouched() {
        val base = PaymentCardFormTheme(inputBorderColor = Color.Magenta)

        assertEquals("An empty object changes nothing", base, PaymentCardFormThemeJson.parse("{}").toTheme(base))
        assertEquals("A null document changes nothing", base, PaymentCardFormThemeJson.parse("null").toTheme(base))
    }

    @Test
    fun malformedDocuments_leaveTheBaseThemeUntouched() {
        val base = PaymentCardFormTheme(inputBorderColor = Color.Magenta, groupSpacing = 24.dp)

        val malformed = listOf(
            "not json at all",
            "{",
            """{"groupSpacing": }""",
            """{"groupSpacing": "sixteen"}""",
            """{"labelUppercase": 7}""",
            "[]"
        )

        malformed.forEach { document ->
            assertEquals(
                "A document the SDK cannot read should not change the theme: $document",
                base,
                PaymentCardFormThemeJson.parse(document).toTheme(base)
            )
        }
    }

    @Test
    fun hostileSizes_areDroppedAndTheBaseValueIsKept() {
        val document = """
            {
              "labelFontSize": -11,
              "inputHeight": -44,
              "inputBorderWidth": -2,
              "groupSpacing": -16,
              "inputFontSize": 1e9,
              "inputBorderRadius": 1e12,
              "formPadding": 24
            }
        """.trimIndent()

        val base = PaymentCardFormTheme()
        val theme = PaymentCardFormThemeJson.parse(document).toTheme(base)

        // A value that cannot be used drops its own key, the way every other unusable value does,
        // rather than inventing a zero the document never asked for. Mirrors iOS.
        assertEquals(base.labelFontSize, theme.labelFontSize)
        assertEquals(base.inputHeight, theme.inputHeight)
        assertEquals(base.inputBorderWidth, theme.inputBorderWidth)
        assertEquals(base.groupSpacing, theme.groupSpacing)

        // Beyond the ceiling the value is not a design decision any more and the layout it feeds
        // throws, so those keys drop too.
        assertEquals(base.inputFontSize, theme.inputFontSize)
        assertEquals(base.inputBorderRadius, theme.inputBorderRadius)

        // A usable key in the same document still applies.
        assertEquals(24.dp, theme.formPadding)
    }

    @Test
    fun negativeLetterSpacing_isKeptAsATypographicChoice() {
        val theme = PaymentCardFormThemeJson
            .parse("""{"labelLetterSpacing": -0.5}""")
            .toTheme()

        assertEquals((-0.5).sp, theme.labelLetterSpacing)
    }

    @Test
    fun unusableValues_fallBackInsteadOfFailingTheDocument() {
        val base = PaymentCardFormTheme(labelColor = Color.Magenta)

        val theme = PaymentCardFormThemeJson
            .parse("""{"labelColor": "rebeccapurple", "inputBorderStyle": "dashed", "groupSpacing": 8}""")
            .toTheme(base)

        assertEquals("An unreadable color keeps the base value", Color.Magenta, theme.labelColor)
        assertEquals("An unknown border style keeps the base value", base.inputBorderStyle, theme.inputBorderStyle)
        assertEquals("The rest of the document still applies", 8.dp, theme.groupSpacing)
        assertTrue("The color is reported", warnings.any { "labelColor" in it && "rebeccapurple" in it })
        assertTrue("The border style is reported", warnings.any { "inputBorderStyle" in it && "dashed" in it })
    }

    @Test
    fun wrongTypedValue_dropsOnlyItsOwnKey() {
        val base = PaymentCardFormTheme(labelFontSize = 13.sp, labelUppercase = false, inputHeight = null)

        val theme = PaymentCardFormThemeJson
            .parse(
                """
                {
                  "labelFontSize": "big",
                  "labelUppercase": 7,
                  "inputHeight": true,
                  "inputBorderColor": 42,
                  "labelColor": "#4b5e68",
                  "groupSpacing": 8
                }
                """.trimIndent()
            )
            .toTheme(base)

        assertEquals("A string where a number is expected keeps the base value", 13.sp, theme.labelFontSize)
        assertFalse("A number where a boolean is expected keeps the base value", theme.labelUppercase)
        assertNull("A boolean where a number is expected keeps the base value", theme.inputHeight)
        assertEquals("A number where a color is expected keeps the base value", base.inputBorderColor, theme.inputBorderColor)
        assertEquals("The readable keys still apply", Color(0xFF4B5E68), theme.labelColor)
        assertEquals("The readable keys still apply", 8.dp, theme.groupSpacing)
        assertEquals(
            "Every dropped key is reported once, in document order",
            listOf("labelFontSize", "labelUppercase", "inputHeight", "inputBorderColor"),
            warnings.map { it.substringAfter('"').substringBefore('"') }
        )
    }

    @Test
    fun fontWeightKeywords_areReadAsCssNumbers() {
        val theme = PaymentCardFormThemeJson
            .parse("""{"labelFontWeight": "bold", "inputFontWeight": "Normal"}""")
            .toTheme()

        assertEquals("bold reads as 700", 700, theme.labelFontWeight)
        assertEquals("normal reads as 400, whatever the case", 400, theme.inputFontWeight)
        assertTrue("Keywords are not a fault", warnings.isEmpty())

        val other = PaymentCardFormThemeJson.parse("""{"labelFontWeight": "bolder"}""").toTheme()

        assertEquals("Other keywords drop the key", PaymentCardFormTheme().labelFontWeight, other.labelFontWeight)
        assertTrue(warnings.any { "labelFontWeight" in it && "bolder" in it })
    }

    @Test
    fun unreadableDocument_isReportedOnce() {
        PaymentCardFormThemeJson.parse("not json at all")

        assertEquals(1, warnings.size)
        assertTrue(warnings.single().contains("could not be read"))
    }
}
