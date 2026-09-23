package cz.gopay.sdk.ui

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cz.gopay.sdk.util.SdkLog
import java.util.Locale

/**
 * Appearance of [PaymentCardForm].
 *
 * The parameters are atomic and named 1:1 after the GoPay hosted card form (cc-v4) theme keys, so
 * the same design tokens describe the form on the web, on iOS and on Android. Values map from the
 * web's pixels 1:1 to `dp` (sizes) and `sp` (font sizes, spacing, line heights); font weights are
 * CSS numbers in the 100..900 range.
 *
 * **Nothing is styled by default.** A form the host does not theme looks like any other form on
 * the screen it sits in: the platform's type sizes, an ordinary field, labels in the case they
 * were written in, and the host's own padding around it. Colors the theme leaves unset come from
 * the host's own theme attributes — `textColorPrimary` and its siblings, the ones an ordinary
 * Android widget reads — so the form follows the host's palette and its dark variant. The iOS SDK
 * resolves the same parameters to the equivalent system colors. That is the difference from the
 * hosted card form, which is a page of its own and can afford a look; here the form is one part of
 * the merchant's screen. Theming is fully available, it is just a choice rather than the starting
 * point. The README carries the parity table and the 1.x to 2.0 migration map.
 *
 * The mobile theme is a subset of the hosted form's: it carries what a native input and the layout
 * around it can be told to do, and nothing that would mean painting the field by hand. Sixteen of
 * the hosted form's keys are therefore absent. The seven `submit*` keys, because the mobile SDK
 * never renders a submit button — it is the permanent equivalent of the web's
 * `submitMode: 'external'`, where the host supplies the button. `errorHidden`, because the mobile
 * form only ever draws errors the host passes in through [InputFieldConfig.errorText]. And eight
 * that the web can only express by drawing: `inputBorderStyle`, `inputBorderCollapse`,
 * `focusRingWidth`, `focusRingColor`, `focusGradientStart`, `focusGradientEnd`,
 * `inputLetterSpacing` and
 * `inputLineHeight`. The theme is a typed Kotlin object, so none of them is something a call site
 * can state in the first place.
 *
 * @property fontFamily Font used for labels, input text, placeholders and error text. `null` uses
 *   the platform font. Fonts are resolved by the host application; the theme carries no font files.
 * @property labelColor Color of the field labels. Unset takes the host theme's `textColorPrimary`.
 * @property labelFontSize Font size of the field labels. The default is `12.sp`, the same size the
 *   iOS SDK gives a label. It is a literal, not a lookup: a host with its own type scale still gets
 *   12 unless it says otherwise.
 * @property labelFontWeight CSS font weight of the field labels, 100..900.
 * @property labelLineHeight Line height of the field labels. `null` uses the font metrics.
 * @property labelUppercase Whether the labels are uppercased before rendering.
 * @property labelLetterSpacing Letter spacing of the field labels. `null` means none.
 * @property labelHidden Hides the labels visually. The label text stays available to screen
 *   readers as the content description of the field, and takes up no vertical space.
 * @property inputTextColor Color of the entered text. Unset takes the host theme's
 *   `textColorPrimary`, which is also what the caret follows.
 * @property inputFontSize Font size of the entered text.
 * @property inputFontWeight CSS font weight of the entered text, 100..900. `null` means regular.
 * @property inputHeight Smallest height of the input, with the vertical padding inside it rather
 *   than on top of it. It is a minimum, not a fixed height, so a large font scale can still grow
 *   the field rather than overflow it. `null` derives the height from the font and the padding
 *   alone. The iOS SDK reads it the same way.
 * @property placeholderColor Color of the placeholder text. `null` takes the host theme's
 *   `textColorHint`, so an unstyled placeholder is the same muted color the host gives its own
 *   fields. A theme that paints the field a color of its own should state this as well.
 * @property inputBorderColor Border color of a valid input. Unset takes the host theme's
 *   `colorControlNormal`, the color the platform gives an ordinary control.
 * @property inputBorderWidth Border width of the inputs. Zero draws no border. It is the only
 *   width there is: the field does not change when it takes focus, on either platform.
 * @property inputBackgroundColor Background color of the input area.
 * @property inputPaddingVertical Vertical padding inside the inputs.
 * @property inputPaddingHorizontal Horizontal padding inside the inputs.
 * @property inputBorderRadius Corner radius of the inputs. Replaces the arbitrary `Shape` of 1.x;
 *   the hosted form has no equivalent of a general shape either.
 * @property inputErrorBorderColor Border color of an input in an error state. Unset takes the
 *   same fixed red the iOS SDK uses.
 * @property errorTextColor Color of the error text below an input. Unset takes that same red.
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
 * @property helperTextColor Color of the helper text, the host theme's `textColorSecondary` when
 *   unset. Android-only extension: the hosted form has no helper text.
 * @property helperFontSize Font size of the helper text. Android-only extension.
 */
data class PaymentCardFormTheme(
    // Typography
    val fontFamily: FontFamily? = null,

    // Labels
    val labelColor: Color = Color.Unspecified,
    val labelFontSize: TextUnit = 12.sp,
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
    val inputBorderColor: Color = Color.Unspecified,
    val inputBorderWidth: Dp = 1.dp,
    val inputBackgroundColor: Color = Color.Transparent,
    val inputPaddingVertical: Dp = 12.dp,
    val inputPaddingHorizontal: Dp = 12.dp,
    val inputBorderRadius: Dp = 4.dp,

    // Validation errors
    val inputErrorBorderColor: Color = Color.Unspecified,
    val errorTextColor: Color = Color.Unspecified,
    val errorFontSize: TextUnit = 12.sp,
    val errorMinHeight: Dp = 0.dp,
    val errorSpacing: Dp? = null,

    // Layout
    val groupSpacing: Dp = 16.dp,
    val fieldSpacing: Dp = 4.dp,
    val formPadding: Dp = 0.dp,
    val formBackgroundColor: Color = Color.Transparent,

    // Android-only extensions
    val helperTextColor: Color = Color.Unspecified,
    val helperFontSize: TextUnit = 12.sp
)

/**
 * A length the theme states, read as none when it is negative or unspecified.
 *
 * `Dp.Unspecified` is `Dp(NaN)` and slips through `coerceAtLeast`, so a theme that leaves a length
 * unspecified would reach the layout pass and fail it — "Padding must be non-negative", or a round
 * of NaN. The typed theme is the only way in now, so it is the place to catch it.
 */
internal fun Dp.orZero(): Dp = if (isSpecified) coerceAtLeast(0.dp) else 0.dp

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

/**
 * Text style of the placeholder shown in an empty field. An unset color is left unspecified on
 * purpose: the platform then colors the placeholder the way it colors every other one.
 */
internal fun PaymentCardFormTheme.placeholderTextStyle(): TextStyle =
    inputTextStyle().copy(color = placeholderColor ?: Color.Unspecified)

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
    val requested = errorMinHeight.orZero()
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

/**
 * A color from the host's own theme, or `null` when the theme does not carry it.
 *
 * These are the platform's theming attributes, not Material's: `textColorPrimary` and its siblings
 * are what an ordinary Android widget reads, they have existed far below `minSdk = 24`, and they
 * follow the host's theme, its dark variant included. It is the closest Android has to the semantic
 * system colors the iOS SDK resolves to, and the form reads them for the same reason: a theme that
 * states nothing should look like the screen it sits in.
 *
 * An attribute can be a literal color or a color state list, and a host theme need not define it at
 * all, so all three cases are handled and the caller decides what an absent one falls back to.
 */
@Composable
private fun hostThemeColor(@AttrRes attr: Int): Color? {
    val context = LocalContext.current
    return remember(context.theme, attr) {
        val resolved = TypedValue()
        if (!context.theme.resolveAttribute(attr, resolved, true)) {
            null
        } else if (resolved.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            Color(resolved.data)
        } else if (resolved.resourceId != 0) {
            ContextCompat.getColorStateList(context, resolved.resourceId)?.defaultColor?.let(::Color)
        } else {
            null
        }
    }
}

/**
 * The host attribute, or a neutral of the right lightness when the host theme does not carry it.
 *
 * The fallback reads the system setting rather than the host's theme, which is why it is only a
 * fallback: a host whose theme is light while the system is dark would get the system's answer.
 */
@Composable
private fun hostColor(@AttrRes attr: Int, dark: Color, light: Color): Color =
    hostThemeColor(attr) ?: if (isSystemInDarkTheme()) dark else light

/** Red of an error, fixed rather than themed, and the same red the iOS SDK uses. */
private val ErrorRed = Color(0xFFFF3B30)

private val NeutralTextDark = Color(0xFFE6E6E6)
private val NeutralTextLight = Color(0xFF1B1B1B)
private val NeutralMutedDark = Color(0xFF9E9E9E)
private val NeutralMutedLight = Color(0xFF6B6B6B)

/**
 * Color of the field labels, with an unset one taken from the host's theme.
 *
 * `BasicText` paints an unspecified color black rather than asking anything, so every text the form
 * draws resolves its color here first.
 */
@Composable
internal fun PaymentCardFormTheme.resolvedLabelColor(): Color = labelColor.takeOrElse {
    hostColor(android.R.attr.textColorPrimary, NeutralTextDark, NeutralTextLight)
}

/** Color of the entered text, the host's primary text color when unset. */
@Composable
internal fun PaymentCardFormTheme.resolvedInputTextColor(): Color = inputTextColor.takeOrElse {
    hostColor(android.R.attr.textColorPrimary, NeutralTextDark, NeutralTextLight)
}

/** Color of the placeholder in an empty field, the host's hint color when unset. */
@Composable
internal fun PaymentCardFormTheme.resolvedPlaceholderColor(): Color = placeholderColor
    ?: hostColor(android.R.attr.textColorHint, NeutralMutedDark, NeutralMutedLight)

/**
 * Border color of a field. An error wins over the resting color, the way the hosted form draws it.
 */
@Composable
internal fun PaymentCardFormTheme.resolvedBorderColor(hasError: Boolean): Color = if (hasError) {
    inputErrorBorderColor.takeOrElse { ErrorRed }
} else {
    inputBorderColor.takeOrElse {
        hostColor(android.R.attr.colorControlNormal, NeutralMutedDark, NeutralMutedLight)
    }
}

/** Color of the error line. */
@Composable
internal fun PaymentCardFormTheme.resolvedErrorTextColor(): Color =
    errorTextColor.takeOrElse { ErrorRed }

/** Color of the helper line, the host's secondary text color when unset. */
@Composable
internal fun PaymentCardFormTheme.resolvedHelperTextColor(): Color = helperTextColor.takeOrElse {
    hostColor(android.R.attr.textColorSecondary, NeutralMutedDark, NeutralMutedLight)
}

/**
 * What the SDK says when the host's theme and the system disagree about dark mode.
 *
 * Both ways out are named, because they suit different hosts: a host that themes its screens in XML
 * adds the night variant, while a Compose-only host may never touch its XML theme and is better off
 * stating the colors it wants.
 */
internal const val HOST_THEME_DARK_MODE_WARNING =
    "PaymentCardForm: the system is in dark mode, but the host theme gives the card form dark " +
        "text, so its labels and fields will be hard to read. Either give the host theme a night " +
        "variant (res/values-night/themes.xml), or state labelColor and inputTextColor in " +
        "PaymentCardFormTheme."

/** Above this relative luminance a text color reads as light. */
private const val LIGHT_TEXT_LUMINANCE = 0.5f

/**
 * Whether a resolved text color contradicts the system's dark mode.
 *
 * A color the theme states is the integrator's decision and never warns, however dark it is. Only
 * an unset one, resolved from the host theme's attributes, can be the mismatch this warns about:
 * the host draws its own screen dark while its theme still answers with a dark text color.
 *
 * Pure on purpose, so the rule can be tested without a device.
 */
internal fun contradictsDarkMode(
    systemInDarkTheme: Boolean,
    statedColor: Color,
    resolvedColor: Color
): Boolean = systemInDarkTheme &&
    !statedColor.isSpecified &&
    resolvedColor.isSpecified &&
    resolvedColor.luminance() < LIGHT_TEXT_LUMINANCE

/**
 * Warns once, in a debug build, when the host theme leaves the form unreadable on a dark screen.
 *
 * The SDK does not second-guess the host by overriding the color: following the host's theme is the
 * point, and a host whose theme is light on purpose is a legitimate host. It only says so out loud,
 * because the alternative is an integrator staring at a form they cannot read.
 */
@Composable
internal fun PaymentCardFormTheme.WarnWhenTheHostThemeFightsDarkMode() {
    val systemInDarkTheme = isSystemInDarkTheme()
    val contradicts =
        contradictsDarkMode(systemInDarkTheme, labelColor, resolvedLabelColor()) ||
            contradictsDarkMode(systemInDarkTheme, inputTextColor, resolvedInputTextColor())
    // Keyed on the answer, so recomposition alone does not repeat the line.
    LaunchedEffect(contradicts) {
        if (contradicts) SdkLog.w(HOST_THEME_DARK_MODE_WARNING)
    }
}
