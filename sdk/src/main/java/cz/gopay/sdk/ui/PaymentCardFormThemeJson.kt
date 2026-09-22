package cz.gopay.sdk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.util.JsonUtils
import cz.gopay.sdk.util.SdkLog
import java.util.Locale
import kotlin.math.abs

/**
 * JSON form of [PaymentCardFormTheme], shaped exactly like the theme document the GoPay hosted
 * card form accepts on the web.
 *
 * Every key is optional; an absent key keeps the value of the base theme it is applied to. Colors
 * are CSS-style hex strings (`#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`) or the literal `transparent`, sizes
 * and font sizes are plain numbers in pixels that map 1:1 to `dp` and `sp`.
 *
 * Keys the mobile SDK does not implement — the seven `submit*` keys and `errorHidden` — are
 * accepted and ignored, as is any other unknown key, so one theme document can be shared with the
 * web integration unchanged.
 *
 * The document is untrusted input, so it is read defensively, key by key: a value of the wrong
 * type drops only that key, and the rest of the document still applies. A document that cannot
 * be read at all yields an empty one, and an unusable value keeps the base theme's: a color that
 * cannot be parsed, or a size that is negative or large enough to break the layout pass. Letter
 * spacing is the one metric that keeps a negative value, because tight tracking is a legitimate
 * typographic choice. A key set to JSON `null` reads as absent, so a document adds to and overrides
 * a base theme but cannot clear one of its optional values back to unset. Font weights are CSS numbers; the keywords `bold` and `normal` are read as
 * 700 and 400. A key whose value cannot be read is reported as a warning in Logcat under the
 * `GopaySDK` tag while debug logging is on, so the integrator learns about it without the form
 * failing. A `fontFamily` the host's resolver returns nothing for is dropped without a warning,
 * because only the host knows which fonts it has.
 */
data class PaymentCardFormThemeJson(
    val fontFamily: String? = null,
    val labelColor: String? = null,
    val labelFontSize: Float? = null,
    val labelFontWeight: Int? = null,
    val labelLineHeight: Float? = null,
    val labelUppercase: Boolean? = null,
    val labelLetterSpacing: Float? = null,
    val labelHidden: Boolean? = null,
    val inputTextColor: String? = null,
    val inputFontSize: Float? = null,
    val inputFontWeight: Int? = null,
    val inputLineHeight: Float? = null,
    val inputLetterSpacing: Float? = null,
    val inputHeight: Float? = null,
    val placeholderColor: String? = null,
    val inputBorderStyle: String? = null,
    val inputBorderColor: String? = null,
    val inputBorderWidth: Float? = null,
    val inputBackgroundColor: String? = null,
    val inputPaddingVertical: Float? = null,
    val inputPaddingHorizontal: Float? = null,
    val inputBorderRadius: Float? = null,
    val inputErrorBorderColor: String? = null,
    val errorTextColor: String? = null,
    val errorFontSize: Float? = null,
    val errorMinHeight: Float? = null,
    val errorSpacing: Float? = null,
    val groupSpacing: Float? = null,
    val fieldSpacing: Float? = null,
    val formPadding: Float? = null,
    val formBackgroundColor: String? = null,
    val helperTextColor: String? = null,
    val helperFontSize: Float? = null
) {

    /**
     * Applies this document on top of [base].
     *
     * @param base Theme supplying the values for keys the document omits.
     * @param fontFamilyResolver Turns the document's [fontFamily] name into a font the host has
     *   registered. The default resolves nothing, so the platform font is kept: fonts live in the
     *   host application, never in the theme document.
     */
    fun toTheme(
        base: PaymentCardFormTheme = PaymentCardFormTheme(),
        fontFamilyResolver: (String) -> FontFamily? = { null }
    ): PaymentCardFormTheme = base.copy(
        fontFamily = fontFamily?.let(fontFamilyResolver) ?: base.fontFamily,
        labelColor = labelColor.toColorOr(base.labelColor, "labelColor"),
        labelFontSize = labelFontSize.toSizeOrNull() ?: base.labelFontSize,
        labelFontWeight = labelFontWeight ?: base.labelFontWeight,
        labelLineHeight = labelLineHeight.toSizeOrNull() ?: base.labelLineHeight,
        labelUppercase = labelUppercase ?: base.labelUppercase,
        labelLetterSpacing = labelLetterSpacing.toSpacingOrNull() ?: base.labelLetterSpacing,
        labelHidden = labelHidden ?: base.labelHidden,
        inputTextColor = inputTextColor.toColorOr(base.inputTextColor, "inputTextColor"),
        inputFontSize = inputFontSize.toSizeOrNull() ?: base.inputFontSize,
        inputFontWeight = inputFontWeight ?: base.inputFontWeight,
        inputLineHeight = inputLineHeight.toSizeOrNull() ?: base.inputLineHeight,
        inputLetterSpacing = inputLetterSpacing.toSpacingOrNull() ?: base.inputLetterSpacing,
        inputHeight = inputHeight.toLengthOrNull() ?: base.inputHeight,
        placeholderColor = placeholderColor.toOptionalColorOr(base.placeholderColor, "placeholderColor"),
        inputBorderStyle = inputBorderStyle?.toBorderStyle("inputBorderStyle") ?: base.inputBorderStyle,
        inputBorderColor = inputBorderColor.toColorOr(base.inputBorderColor, "inputBorderColor"),
        inputBorderWidth = inputBorderWidth.toLengthOrNull() ?: base.inputBorderWidth,
        inputBackgroundColor = inputBackgroundColor.toColorOr(base.inputBackgroundColor, "inputBackgroundColor"),
        inputPaddingVertical = inputPaddingVertical.toLengthOrNull() ?: base.inputPaddingVertical,
        inputPaddingHorizontal = inputPaddingHorizontal.toLengthOrNull() ?: base.inputPaddingHorizontal,
        inputBorderRadius = inputBorderRadius.toLengthOrNull() ?: base.inputBorderRadius,
        inputErrorBorderColor = inputErrorBorderColor.toColorOr(base.inputErrorBorderColor, "inputErrorBorderColor"),
        errorTextColor = errorTextColor.toColorOr(base.errorTextColor, "errorTextColor"),
        errorFontSize = errorFontSize.toSizeOrNull() ?: base.errorFontSize,
        errorMinHeight = errorMinHeight.toLengthOrNull() ?: base.errorMinHeight,
        errorSpacing = errorSpacing.toLengthOrNull() ?: base.errorSpacing,
        groupSpacing = groupSpacing.toLengthOrNull() ?: base.groupSpacing,
        fieldSpacing = fieldSpacing.toLengthOrNull() ?: base.fieldSpacing,
        formPadding = formPadding.toLengthOrNull() ?: base.formPadding,
        formBackgroundColor = formBackgroundColor.toColorOr(base.formBackgroundColor, "formBackgroundColor"),
        helperTextColor = helperTextColor.toColorOr(base.helperTextColor, "helperTextColor"),
        helperFontSize = helperFontSize.toSizeOrNull() ?: base.helperFontSize
    )

    /** Serializes this document to JSON, leaving out the keys it does not set. */
    fun toJson(): String = JsonUtils.toJson(this) ?: run {
        SdkLog.w("Theme document could not be encoded, an empty document is returned")
        EMPTY_DOCUMENT
    }

    companion object {
        private const val EMPTY_DOCUMENT = "{}"

        /**
         * Parses a theme document, ignoring keys this SDK does not implement.
         *
         * The document is read key by key. A value of the wrong type (`"labelFontSize": "big"`)
         * drops that key alone and the rest applies; a document that cannot be read at all
         * (malformed JSON, a literal `null`, an array) is read as an empty document. Either way a
         * bad document leaves the base theme untouched instead of failing the form, and a key
         * whose value cannot be read is reported as a warning under the `GopaySDK` Logcat tag.
         */
        fun parse(document: String): PaymentCardFormThemeJson {
            val raw = JsonUtils.fromJson<Map<String, Any?>>(document)
            if (raw == null) {
                SdkLog.w("Theme document could not be read, the base theme is kept")
                return PaymentCardFormThemeJson()
            }
            val values = ThemeDocumentValues(raw)
            return PaymentCardFormThemeJson(
                fontFamily = values.string("fontFamily"),
                labelColor = values.string("labelColor"),
                labelFontSize = values.number("labelFontSize"),
                labelFontWeight = values.fontWeight("labelFontWeight"),
                labelLineHeight = values.number("labelLineHeight"),
                labelUppercase = values.boolean("labelUppercase"),
                labelLetterSpacing = values.number("labelLetterSpacing", allowNegative = true),
                labelHidden = values.boolean("labelHidden"),
                inputTextColor = values.string("inputTextColor"),
                inputFontSize = values.number("inputFontSize"),
                inputFontWeight = values.fontWeight("inputFontWeight"),
                inputLineHeight = values.number("inputLineHeight"),
                inputLetterSpacing = values.number("inputLetterSpacing", allowNegative = true),
                inputHeight = values.number("inputHeight"),
                placeholderColor = values.string("placeholderColor"),
                inputBorderStyle = values.string("inputBorderStyle"),
                inputBorderColor = values.string("inputBorderColor"),
                inputBorderWidth = values.number("inputBorderWidth"),
                inputBackgroundColor = values.string("inputBackgroundColor"),
                inputPaddingVertical = values.number("inputPaddingVertical"),
                inputPaddingHorizontal = values.number("inputPaddingHorizontal"),
                inputBorderRadius = values.number("inputBorderRadius"),
                inputErrorBorderColor = values.string("inputErrorBorderColor"),
                errorTextColor = values.string("errorTextColor"),
                errorFontSize = values.number("errorFontSize"),
                errorMinHeight = values.number("errorMinHeight"),
                errorSpacing = values.number("errorSpacing"),
                groupSpacing = values.number("groupSpacing"),
                fieldSpacing = values.number("fieldSpacing"),
                formPadding = values.number("formPadding"),
                formBackgroundColor = values.string("formBackgroundColor"),
                helperTextColor = values.string("helperTextColor"),
                helperFontSize = values.number("helperFontSize")
            )
        }
    }
}

/**
 * Describes this theme as a JSON document, so it can be handed to the other GoPay channels.
 *
 * [PaymentCardFormTheme.fontFamily] is not represented: a Compose font family carries no name the
 * other channels could resolve. Set [PaymentCardFormThemeJson.fontFamily] explicitly if the
 * document needs one.
 */
fun PaymentCardFormTheme.toJsonModel(): PaymentCardFormThemeJson = PaymentCardFormThemeJson(
    labelColor = labelColor.toHexStringOrNull(),
    labelFontSize = labelFontSize.jsonValueOrNull(),
    labelFontWeight = labelFontWeight,
    labelLineHeight = labelLineHeight?.jsonValueOrNull(),
    labelUppercase = labelUppercase,
    labelLetterSpacing = labelLetterSpacing?.jsonValueOrNull(),
    labelHidden = labelHidden,
    inputTextColor = inputTextColor.toHexStringOrNull(),
    inputFontSize = inputFontSize.jsonValueOrNull(),
    inputFontWeight = inputFontWeight,
    inputLineHeight = inputLineHeight?.jsonValueOrNull(),
    inputLetterSpacing = inputLetterSpacing?.jsonValueOrNull(),
    inputHeight = inputHeight?.jsonValueOrNull(),
    placeholderColor = placeholderColor?.toHexStringOrNull(),
    inputBorderStyle = inputBorderStyle.name.lowercase(),
    inputBorderColor = inputBorderColor.toHexStringOrNull(),
    inputBorderWidth = inputBorderWidth.jsonValueOrNull(),
    inputBackgroundColor = inputBackgroundColor.toHexStringOrNull(),
    inputPaddingVertical = inputPaddingVertical.jsonValueOrNull(),
    inputPaddingHorizontal = inputPaddingHorizontal.jsonValueOrNull(),
    inputBorderRadius = inputBorderRadius.jsonValueOrNull(),
    inputErrorBorderColor = inputErrorBorderColor.toHexStringOrNull(),
    errorTextColor = errorTextColor.toHexStringOrNull(),
    errorFontSize = errorFontSize.jsonValueOrNull(),
    errorMinHeight = errorMinHeight.jsonValueOrNull(),
    errorSpacing = errorSpacing?.jsonValueOrNull(),
    groupSpacing = groupSpacing.jsonValueOrNull(),
    fieldSpacing = fieldSpacing.jsonValueOrNull(),
    formPadding = formPadding.jsonValueOrNull(),
    formBackgroundColor = formBackgroundColor.toHexStringOrNull(),
    helperTextColor = helperTextColor.toHexStringOrNull(),
    helperFontSize = helperFontSize.jsonValueOrNull()
)

/**
 * A number a JSON document can carry. `TextUnit.Unspecified` and `Dp.Unspecified` read back as
 * `NaN`, which no JSON writer accepts; emitting the key as absent keeps the rest of the document
 * intact instead of failing the whole serialization. An `em` value is left out too: the document
 * carries plain numbers the other channels read as sp, so writing one would silently change the
 * unit.
 */
private fun TextUnit.jsonValueOrNull(): Float? = takeIf { isSpecified && type == TextUnitType.Sp }
    ?.value
    ?.takeIf { it.isFinite() }

private fun Dp.jsonValueOrNull(): Float? = value.takeIf { it.isFinite() }

/**
 * Ceiling for any length or type metric read from a document, in dp or sp.
 *
 * Well past any real design value, but low enough that what the value feeds stays buildable: an
 * unbounded height throws out of the layout pass, an unbounded border width inverts the collapsed
 * outline. Mirrors the iOS `GopayCardFormTheme.lengthLimit`.
 */
private const val LENGTH_LIMIT = 10_000f

/** A usable length from a document: finite, not negative and within [LENGTH_LIMIT]. */
private fun Float?.toLengthOrNull(): Dp? = this?.takeIf { it in 0f..LENGTH_LIMIT }?.dp

/** A usable type metric from a document, for font sizes and line heights. */
private fun Float?.toSizeOrNull(): TextUnit? = this?.takeIf { it in 0f..LENGTH_LIMIT }?.sp

/** Letter spacing from a document, which may legitimately be negative. */
private fun Float?.toSpacingOrNull(): TextUnit? = this?.takeIf { abs(it) <= LENGTH_LIMIT }?.sp

/**
 * The raw key/value pairs of a theme document, read one key at a time so that a value of the wrong
 * type costs only its own key. Each dropped key is reported once through [SdkLog].
 */
private class ThemeDocumentValues(private val raw: Map<String, Any?>) {

    fun string(key: String): String? = read(key, "a string") { it as? String }

    /**
     * A number from the document, checked against the range the SDK can use. Lengths and type
     * metrics have to be positive; letter spacing may legitimately be negative, hence
     * [allowNegative]. A value outside the range is dropped and reported, the same as a value of
     * the wrong type: it is valid JSON and would otherwise disappear without a word.
     */
    fun number(key: String, allowNegative: Boolean = false): Float? {
        val value = read(key, "a number") { (it as? Number)?.toFloat() } ?: return null
        val usable = if (allowNegative) abs(value) <= LENGTH_LIMIT else value in 0f..LENGTH_LIMIT
        return if (usable) value else null.also { warnDropped(key, value.toString()) }
    }

    fun boolean(key: String): Boolean? = read(key, "true or false") { it as? Boolean }

    /** A CSS font weight: a number in the 100..900 range, or the keywords `bold` and `normal`. */
    fun fontWeight(key: String): Int? = read(key, "a number or the keyword bold or normal") {
        when (it) {
            is Number -> it.toInt()
            is String -> when (it.trim().lowercase()) {
                "bold" -> 700
                "normal" -> 400
                else -> null
            }

            else -> null
        }
    }

    private fun <T> read(key: String, expected: String, convert: (Any) -> T?): T? {
        val value = raw[key] ?: return null
        val converted = convert(value)
        if (converted == null) {
            SdkLog.w("Theme key \"$key\" was dropped: expected $expected, got $value")
        }
        return converted
    }
}

private fun String?.toOptionalColorOr(fallback: Color?, key: String): Color? =
    if (this == null) fallback else parseCssColor(this) ?: fallback.also { warnDropped(key, this) }

private fun String?.toColorOr(fallback: Color, key: String): Color =
    if (this == null) fallback else parseCssColor(this) ?: fallback.also { warnDropped(key, this) }

private fun String.toBorderStyle(key: String): InputBorderStyle? = when (lowercase()) {
    "boxed" -> InputBorderStyle.BOXED
    "underline" -> InputBorderStyle.UNDERLINE
    else -> null.also { warnDropped(key, this) }
}

private fun warnDropped(key: String, value: String) {
    SdkLog.w("Theme key \"$key\" was dropped: the value \"$value\" could not be read")
}

/**
 * Parses `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa` and the keyword `transparent`. Returns `null` for anything
 * else, so an unusable value falls back to the base theme instead of failing the whole document.
 */
internal fun parseCssColor(value: String): Color? {
    val trimmed = value.trim()
    if (trimmed.equals("transparent", ignoreCase = true)) return Color.Transparent
    if (!trimmed.startsWith("#")) return null
    val digits = trimmed.substring(1)
    // toLongOrNull honours a leading + or -, which would turn a typo into an arbitrary colour.
    if (!digits.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
    val expanded = when (digits.length) {
        3 -> digits.map { "$it$it" }.joinToString("") + "ff"
        4 -> digits.map { "$it$it" }.joinToString("")
        6 -> digits + "ff"
        8 -> digits
        else -> return null
    }
    val rgba = expanded.toLongOrNull(16) ?: return null
    val argb = (rgba ushr 8) or ((rgba and 0xffL) shl 24)
    return Color(argb.toInt())
}

/**
 * Renders a color as `#rrggbb`, or `#rrggbbaa` when it is not fully opaque. `Color.Unspecified`
 * has no hex form and is left out of the document, so the receiving side keeps its own default.
 */
internal fun Color.toHexStringOrNull(): String? {
    if (this == Color.Unspecified) return null
    val argb = toArgb()
    val alpha = (argb ushr 24) and 0xff
    val rgb = String.format(Locale.ROOT, "#%06x", argb and 0xffffff)
    return if (alpha == 0xff) rgb else rgb + String.format(Locale.ROOT, "%02x", alpha)
}
