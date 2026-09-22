package cz.gopay.sdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Border treatment of the input fields.
 *
 * Mirrors the `inputBorderStyle` key of the GoPay hosted card form, [UNDERLINE] included: that is
 * what all three channels use when a theme does not say otherwise.
 */
enum class InputBorderStyle {
    /** Full border around the field, rounded by [PaymentCardFormTheme.inputBorderRadius]. */
    BOXED,

    /** Bottom line only. */
    UNDERLINE
}

/**
 * Appearance of [PaymentCardForm].
 *
 * The parameters are atomic and named 1:1 after the GoPay hosted card form (cc-v4) theme keys, so
 * the same design tokens describe the form on the web, on iOS and on Android. Values map from the
 * web's pixels 1:1 to `dp` (sizes) and `sp` (font sizes, spacing, line heights); font weights are
 * CSS numbers in the 100..900 range.
 *
 * **Nothing is styled by default.** A form the host does not theme looks like any other form on
 * the screen it sits in: the platform's type sizes and colors, an ordinary field, labels in the
 * case they were written in, and the host's own padding around it. That is the difference from the
 * hosted card form, which is a page of its own and can afford a look; here the form is one part of
 * the merchant's screen. Theming is fully available, it is just a choice rather than the starting
 * point. The README carries the parity table and the 1.x to 2.0 migration map.
 *
 * The mobile theme is a subset of the hosted form's: it carries what a native input and the layout
 * around it can be told to do, and nothing that would mean painting the field by hand. Fifteen of
 * the hosted form's keys are therefore absent. The seven `submit*` keys, because the mobile SDK
 * never renders a submit button — it is the permanent equivalent of the web's
 * `submitMode: 'external'`, where the host supplies the button. `errorHidden`, because the mobile
 * form only ever draws errors the host passes in through [InputFieldConfig.errorText]. And seven
 * that the web can only express by drawing: `inputBorderCollapse`, `focusRingWidth`,
 * `focusRingColor`, `focusGradientStart`, `focusGradientEnd`, `inputLetterSpacing` and
 * `inputLineHeight`. All of them are accepted and ignored when a theme arrives as JSON, so one
 * theme document can still drive all three channels.
 *
 * @property fontFamily Font used for labels, input text, placeholders and error text. `null` uses
 *   the platform font. Fonts are resolved by the host application; the theme carries no font files.
 * @property labelColor Color of the field labels.
 * @property labelFontSize Font size of the field labels.
 * @property labelFontWeight CSS font weight of the field labels, 100..900.
 * @property labelLineHeight Line height of the field labels. `null` uses the font metrics.
 * @property labelUppercase Whether the labels are uppercased before rendering.
 * @property labelLetterSpacing Letter spacing of the field labels. `null` means none.
 * @property labelHidden Hides the labels visually. The label text stays available to screen
 *   readers as the content description of the field, and takes up no vertical space.
 * @property inputTextColor Color of the entered text.
 * @property inputFontSize Font size of the entered text.
 * @property inputFontWeight CSS font weight of the entered text, 100..900. `null` means regular.
 * @property inputHeight Height of the input, taken as a minimum so a large font scale can still
 *   grow the field rather than overflow it. Takes precedence over the vertical padding.
 *   `null` derives the height from the font and the padding.
 * @property placeholderColor Color of the placeholder text. `null` keeps the historical default,
 *   the input text tinted `LightGray`, which reads as real input on a dark form — set it there.
 * @property inputBorderStyle Border treatment of the inputs, see [InputBorderStyle].
 * @property inputBorderColor Border color of an unfocused, valid input.
 * @property inputBorderWidth Border width of the inputs.
 * @property inputBackgroundColor Background color of the input area.
 * @property inputPaddingVertical Vertical padding inside the inputs.
 * @property inputPaddingHorizontal Horizontal padding inside the inputs.
 * @property inputBorderRadius Corner radius of the inputs. Replaces the arbitrary `Shape` of 1.x;
 *   the hosted form has no equivalent of a general shape either.
 * @property inputErrorBorderColor Border color of an input in an error state.
 * @property errorTextColor Color of the error text below an input.
 * @property errorFontSize Font size of the error text.
 * @property errorMinHeight Vertical space reserved for the error line, so the layout does not
 *   shift when a message appears. The slot is shared with the helper text. Zero, the default,
 *   reserves nothing and lets the form grow when a message appears, as an unstyled form does.
 * @property errorSpacing Distance from the input to the error line. `null` uses [fieldSpacing].
 * @property groupSpacing Gap between the field rows, and between the expiry and CVV fields.
 *   In 1.x this parameter meant the horizontal gap only, and the gap between rows was
 *   `fieldSpacing`; see the migration map in the README.
 * @property fieldSpacing Gap between a label and its input. In 1.x this parameter meant the gap
 *   between the field rows.
 * @property formPadding Padding around the whole form. Zero by default, because the host lays the
 *   form out on its own screen; the hosted card form uses 16 inside its iframe.
 * @property formBackgroundColor Background color of the form container.
 * @property helperTextColor Color of the helper text. Mobile-only extension: the hosted form has
 *   no helper text.
 * @property helperFontSize Font size of the helper text. Mobile-only extension.
 */
data class PaymentCardFormTheme(
    // Typography
    val fontFamily: FontFamily? = null,

    // Labels
    val labelColor: Color = Color.Gray,
    val labelFontSize: TextUnit = 14.sp,
    val labelFontWeight: Int = 400,
    val labelLineHeight: TextUnit? = null,
    val labelUppercase: Boolean = false,
    val labelLetterSpacing: TextUnit? = null,
    val labelHidden: Boolean = false,

    // Input text
    val inputTextColor: Color = Color.Unspecified,
    val inputFontSize: TextUnit = 16.sp,
    val inputFontWeight: Int? = null,
    val inputHeight: Dp? = null,
    val placeholderColor: Color? = null,

    // Input border
    val inputBorderStyle: InputBorderStyle = InputBorderStyle.UNDERLINE,
    val inputBorderColor: Color = Color.Gray,
    val inputBorderWidth: Dp = 1.dp,
    val inputBackgroundColor: Color = Color.Transparent,
    val inputPaddingVertical: Dp = 12.dp,
    val inputPaddingHorizontal: Dp = 12.dp,
    val inputBorderRadius: Dp = 4.dp,

    // Validation errors
    val inputErrorBorderColor: Color = Color.Red,
    val errorTextColor: Color = Color.Red,
    val errorFontSize: TextUnit = 12.sp,
    val errorMinHeight: Dp = 0.dp,
    val errorSpacing: Dp? = null,

    // Layout
    val groupSpacing: Dp = 16.dp,
    val fieldSpacing: Dp = 4.dp,
    val formPadding: Dp = 0.dp,
    val formBackgroundColor: Color = Color.Transparent,

    // Mobile-only extensions
    val helperTextColor: Color = Color.Gray,
    val helperFontSize: TextUnit = 12.sp
)

/** Label text as rendered, with [PaymentCardFormTheme.labelUppercase] applied. */
internal fun PaymentCardFormTheme.renderedLabel(label: String): String =
    if (labelUppercase) label.uppercase(Locale.getDefault()) else label

/**
 * Maps a CSS font weight (100..900) to a Compose [FontWeight], clamping out-of-range values.
 */
internal fun cssFontWeight(weight: Int): FontWeight = FontWeight(weight.coerceIn(100, 900))

/**
 * Text style of the field labels. A themed line height keeps the whole line box: Compose trims
 * the extra height above the first and below the last line by default, which on a one-line label
 * would swallow the setting entirely, so the box is kept and the text centered in it, the way
 * CSS line-height and the iOS label behave.
 */
internal fun PaymentCardFormTheme.labelTextStyle(): TextStyle = TextStyle(
    color = labelColor,
    fontSize = labelFontSize,
    fontWeight = cssFontWeight(labelFontWeight),
    fontFamily = fontFamily,
    lineHeight = labelLineHeight ?: TextUnit.Unspecified,
    lineHeightStyle = labelLineHeight?.let {
        LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
    },
    letterSpacing = labelLetterSpacing ?: TextUnit.Unspecified
)

/**
 * Text style of the entered text. Every field of the form holds a number, so the text is laid out
 * left to right even in a right-to-left layout: the bidi algorithm would otherwise reorder the
 * groups of a formatted card number ("3456 9012 5678 1234"), for the placeholder and the masked
 * value alike.
 */
internal fun PaymentCardFormTheme.inputTextStyle(): TextStyle = TextStyle(
    color = inputTextColor,
    fontSize = inputFontSize,
    fontWeight = inputFontWeight?.let(::cssFontWeight),
    fontFamily = fontFamily,
    textDirection = TextDirection.Ltr
)

/** Text style of the placeholder shown in an empty field. */
internal fun PaymentCardFormTheme.placeholderTextStyle(): TextStyle =
    inputTextStyle().copy(color = placeholderColor ?: Color.LightGray)

/** How much taller a rendered line is than the font size that names it, for the default family. */
private const val ERROR_LINE_HEIGHT_FACTOR = 1.2f

/**
 * Height reserved below an input so the form does not jump when an error appears.
 *
 * [PaymentCardFormTheme.errorMinHeight] is what the theme asked for. Zero means no reserve at all
 * and the slot disappears; above zero, a reserve shorter than one rendered line of the error font
 * would not hold the message it exists for, so the taller of the two wins. Mirrors the iOS `reservedErrorHeight(for:)`, which reads the line height from the font
 * itself; Compose has no equivalent outside a measuring pass, so this uses the usual factor.
 */
@Composable
internal fun PaymentCardFormTheme.reservedErrorHeight(): Dp {
    val requested = errorMinHeight.coerceAtLeast(0.dp)
    if (requested <= 0.dp) return 0.dp
    // A rendered line is about a fifth taller than the font size that names it, so reserving the
    // bare size still lets the form jump when a message appears. `toDp` accepts only `sp`, and
    // `errorFontSize` is a public parameter a host can leave unset or carry over from a text
    // style in `em`, which would otherwise take the whole form down.
    val oneLine = if (errorFontSize.isSp) {
        with(LocalDensity.current) { errorFontSize.toDp() } * ERROR_LINE_HEIGHT_FACTOR
    } else {
        0.dp
    }
    return maxOf(requested, oneLine)
}

/** Text style of the error line below a field. Prose, so its direction follows its content. */
internal fun PaymentCardFormTheme.errorTextStyle(): TextStyle = TextStyle(
    color = errorTextColor,
    fontSize = errorFontSize,
    fontFamily = fontFamily,
    textDirection = TextDirection.Content
)

/** Text style of the helper line below a field. */
internal fun PaymentCardFormTheme.helperTextStyle(): TextStyle = TextStyle(
    color = helperTextColor,
    fontSize = helperFontSize,
    fontFamily = fontFamily
)
