package cz.gopay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


/**
 * Configuration for LabeledInputField
 */
internal data class LabeledInputFieldConfig(
    val label: String,
    val error: String? = null,
    val helperText: String? = null,
    val placeholder: String? = null,
    val textFieldModifier: Modifier = Modifier,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

/**
 * A reusable input field with label, error/helper text, and a BasicTextField.
 *
 * Everything around the text is built from framework primitives: a background, a border and
 * padding. Nothing is painted onto a canvas, so the field has no underline of its own and does not
 * mark itself when it takes focus — neither does the iOS SDK, and a payment form is not the place
 * to invent either. The label above the field and the error line below it are laid out by the form,
 * because the theme places them with [PaymentCardFormTheme.fieldSpacing] and
 * [PaymentCardFormTheme.errorSpacing].
 */
@Composable
internal fun LabeledInputField(
    value: String,
    onValueChange: (String) -> Unit,
    config: LabeledInputFieldConfig,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    theme: PaymentCardFormTheme = PaymentCardFormTheme(),
    labelMinHeight: Dp = 0.dp
) {
    val hasError = config.error != null
    // The caret follows the entered text. The foundation default paints it black, which on a field
    // the host theme draws dark is a caret nobody can see.
    val textColor = theme.resolvedInputTextColor()

    Column(modifier = modifier) {
        FieldLabel(theme = theme, label = config.label, minHeight = labelMinHeight)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = config.keyboardOptions,
            visualTransformation = config.visualTransformation,
            modifier = config.textFieldModifier
                .fillMaxWidth()
                .semanticsLabel(theme, config.label),
            singleLine = singleLine,
            textStyle = theme.inputTextStyle().copy(color = textColor),
            cursorBrush = SolidColor(textColor),
            decorationBox = { innerTextField ->
                FieldBox(
                    theme = theme,
                    hasError = hasError,
                    isEmpty = value.isEmpty(),
                    placeholder = config.placeholder,
                    innerTextField = innerTextField
                )
            }
        )
        HelperText(error = config.error, helperText = config.helperText, theme = theme)
    }
}

/**
 * The label above a field, unless the theme hides it.
 *
 * [minHeight] is the floor the parent hands down so that two fields sharing a row keep their
 * inputs on one line even when one label wraps and the other does not; see [SharedLabelHeightRow].
 * The label itself is never clipped or shortened for it.
 */
@Composable
internal fun FieldLabel(theme: PaymentCardFormTheme, label: String, minHeight: Dp = 0.dp) {
    if (theme.labelHidden) return
    BasicText(
        text = theme.renderedLabel(label),
        // The label keeps its own color in an error state; only the field and the error
        // line change, matching the hosted card form.
        style = theme.labelTextStyle().copy(color = theme.resolvedLabelColor()),
        modifier = Modifier
            .heightIn(min = minHeight)
            .padding(bottom = theme.fieldSpacing.orZero())
    )
}

/**
 * What the field looks like around the text: the background, the border and the padding the theme
 * asks for, and the placeholder while the field is empty.
 *
 * [PaymentCardFormTheme.inputHeight] is a minimum rather than a fixed height, so a large font scale
 * grows the field instead of overflowing it, and it sits on this box rather than on the text field
 * so the border encloses the height it asks for.
 *
 * A zero border width means no border at all, the way a zero `errorMinHeight` reserves nothing:
 * a zero-width line is still a hairline to a canvas, so it has to be left out rather than drawn.
 */
@Composable
private fun FieldBox(
    theme: PaymentCardFormTheme,
    hasError: Boolean,
    isEmpty: Boolean,
    placeholder: String?,
    innerTextField: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(theme.inputBorderRadius.orZero())
    val borderWidth = theme.inputBorderWidth.orZero()
    val box = Modifier
        .fillMaxWidth()
        .minimumInputHeight(theme)
        .background(theme.inputBackgroundColor, shape)
        .then(
            if (borderWidth > 0.dp) {
                Modifier.border(borderWidth, theme.resolvedBorderColor(hasError), shape)
            } else {
                Modifier
            }
        )
        .padding(
            horizontal = theme.inputPaddingHorizontal.orZero(),
            vertical = theme.inputPaddingVertical.orZero()
        )

    Box(modifier = box, contentAlignment = Alignment.CenterStart) {
        if (isEmpty && placeholder != null) {
            // One line, like the field itself, so a large font scale cannot make an empty field
            // taller than a filled one.
            BasicText(
                text = placeholder,
                style = theme.placeholderTextStyle()
                    .copy(color = theme.resolvedPlaceholderColor()),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        innerTextField()
    }
}

/**
 * A minimum, not a fixed height: at a large font scale a hard height would leave the text drawing
 * over the field below it.
 */
private fun Modifier.minimumInputHeight(theme: PaymentCardFormTheme): Modifier {
    val height = theme.inputHeight?.orZero() ?: return this
    return heightIn(min = height)
}

/**
 * Keeps a hidden label available to screen readers: the field announces it as its content
 * description, so hiding the label costs no accessibility.
 */
private fun Modifier.semanticsLabel(theme: PaymentCardFormTheme, label: String): Modifier =
    if (theme.labelHidden) semantics { contentDescription = label } else this

/**
 * Lays out fields that share a row so their inputs stay on one line.
 *
 * Each field is its own column, so without this the taller label pushes only its own input down
 * and the pair ends up a line apart. That happens with an ordinary system font scale: the Spanish
 * expiry label wraps at 1.27x on a 320dp-wide screen. Clipping the label is not an option — the
 * text is what GoPay translates — so the row measures the labels first and hands every field the
 * height of the tallest one as a floor.
 *
 * Measuring the real [FieldLabel] rather than the raw string keeps the two in step: whatever the
 * theme does to a label, uppercase included, the probe sees it too.
 */
@Composable
internal fun SharedLabelHeightRow(
    theme: PaymentCardFormTheme,
    labels: List<String>,
    horizontalSpacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (labelMinHeight: Dp) -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val cellWidth = ((constraints.maxWidth - horizontalSpacing.roundToPx()) / labels.size)
            .coerceAtLeast(0)
        val cell = Constraints(maxWidth = cellWidth)
        val tallestLabel = subcompose(LabelProbe) {
            labels.forEach { FieldLabel(theme = theme, label = it) }
        }.maxOfOrNull { it.measure(cell).height } ?: 0

        val row = subcompose(RowContent) { content(tallestLabel.toDp()) }
            .first()
            .measure(constraints)
        layout(row.width, row.height) { row.place(0, 0) }
    }
}

private enum class SharedLabelSlot { LabelProbe, RowContent }

private val LabelProbe = SharedLabelSlot.LabelProbe
private val RowContent = SharedLabelSlot.RowContent
