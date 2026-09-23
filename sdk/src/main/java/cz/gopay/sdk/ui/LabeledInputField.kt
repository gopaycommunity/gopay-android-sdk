package cz.gopay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
 * The field itself is decorated by Material, through the same decoration box Material's own text
 * fields are built from, so the underline, the outline and the way they react to focus and to an
 * error are the platform's. The SDK paints no part of it. The label above the field and the error
 * line below it stay the form's own, because the theme places them with [PaymentCardFormTheme
 * .fieldSpacing] and [PaymentCardFormTheme.errorSpacing], which Material's built-in slots have no
 * equivalent for.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hasError = config.error != null
    val colors = theme.fieldColors()
    val decoration = FieldDecoration(
        theme = theme,
        colors = colors,
        interactionSource = interactionSource,
        hasError = hasError,
        singleLine = singleLine,
        visualTransformation = config.visualTransformation,
        placeholder = placeholderSlot(theme, config.placeholder)
    )

    Column(modifier = modifier) {
        FieldLabel(theme = theme, label = config.label, minHeight = labelMinHeight)
        CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = config.keyboardOptions,
                visualTransformation = config.visualTransformation,
                interactionSource = interactionSource,
                modifier = config.textFieldModifier
                    .fillMaxWidth()
                    .semanticsLabel(theme, config.label)
                    .minimumInputHeight(theme),
                singleLine = singleLine,
                textStyle = theme.inputTextStyle().copy(
                    color = theme.inputTextColor(colors, isFocused = isFocused, hasError = hasError)
                ),
                cursorBrush = SolidColor(colors.cursorFor(hasError)),
                decorationBox = { innerTextField ->
                    InputDecoration(decoration, value, innerTextField)
                }
            )
        }
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
 * A minimum, not a fixed height: at a large font scale a hard height would leave the text drawing
 * over the field below it.
 */
private fun Modifier.minimumInputHeight(theme: PaymentCardFormTheme): Modifier {
    val height = theme.inputHeight?.orZero() ?: return this
    return heightIn(min = height)
}

/**
 * The placeholder slot Material's decoration expects, or `null` when the field has no placeholder.
 *
 * Material's [Text], not [BasicText]: with no color in the theme the style leaves the color
 * unspecified, and only [Text] falls back to the placeholder color the decoration box provides.
 * One line, like the field itself, so a large font scale cannot make an empty field taller than a
 * filled one.
 */
private fun placeholderSlot(
    theme: PaymentCardFormTheme,
    placeholder: String?
): (@Composable () -> Unit)? {
    if (placeholder == null) return null
    return {
        Text(
            text = placeholder,
            style = theme.placeholderTextStyle(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/** The Material color set that goes with the border style the theme asks for. */
@Composable
private fun PaymentCardFormTheme.fieldColors(): TextFieldColors = when (inputBorderStyle) {
    InputBorderStyle.UNDERLINE -> filledFieldColors()
    InputBorderStyle.BOXED -> outlinedFieldColors()
}

/** Caret color, which follows the error state like the rest of the field. */
private fun TextFieldColors.cursorFor(hasError: Boolean): Color =
    if (hasError) errorCursorColor else cursorColor

/**
 * Everything Material's decoration needs about the field that does not come from the text itself.
 *
 * Gathered in one place so the decoration can be read without the call that builds it: the
 * geometry the theme states, the color set, and the slots Material fills in.
 */
private class FieldDecoration(
    val theme: PaymentCardFormTheme,
    val colors: TextFieldColors,
    val interactionSource: MutableInteractionSource,
    val hasError: Boolean,
    val singleLine: Boolean,
    val visualTransformation: VisualTransformation,
    val placeholder: (@Composable () -> Unit)?
) {
    val shape = RoundedCornerShape(theme.inputBorderRadius.orZero())
    val borderWidth = theme.inputBorderWidth.orZero()

    /**
     * Zero means no border at all, the way a zero errorMinHeight means no reserved line: Material
     * would draw a hairline for it, because that is what a zero-width line is to a canvas.
     */
    val drawsBorder = borderWidth > 0.dp

    val contentPadding = PaddingValues(
        horizontal = theme.inputPaddingHorizontal.orZero(),
        vertical = theme.inputPaddingVertical.orZero()
    )
}

/**
 * Dresses the field in the border style the theme asks for.
 *
 * Clipped to the field, so an oversized border width stays inside it instead of spilling over
 * whatever sits above the form. It still paints over the field's own content; that is what an
 * oversized border does.
 *
 * The minimum constraints have to be passed on. A Box drops them by default, and Material's
 * decoration then sizes itself to its content, which left the underline as wide as the text
 * instead of as wide as the field.
 */
@Composable
private fun InputDecoration(
    decoration: FieldDecoration,
    value: String,
    innerTextField: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        propagateMinConstraints = true
    ) {
        when (decoration.theme.inputBorderStyle) {
            InputBorderStyle.UNDERLINE -> UnderlinedField(decoration, value, innerTextField)
            InputBorderStyle.BOXED -> BoxedField(decoration, value, innerTextField)
        }
    }
}

/** A Material filled field, the one with the indicator line under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnderlinedField(
    decoration: FieldDecoration,
    value: String,
    innerTextField: @Composable () -> Unit
) {
    TextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = innerTextField,
        enabled = true,
        singleLine = decoration.singleLine,
        visualTransformation = decoration.visualTransformation,
        interactionSource = decoration.interactionSource,
        isError = decoration.hasError,
        placeholder = decoration.placeholder,
        shape = decoration.shape,
        colors = decoration.colors,
        contentPadding = decoration.contentPadding,
        container = {
            if (decoration.drawsBorder) {
                TextFieldDefaults.Container(
                    enabled = true,
                    isError = decoration.hasError,
                    interactionSource = decoration.interactionSource,
                    colors = decoration.colors,
                    shape = decoration.shape,
                    // Material swaps the thickness on focus rather than taking the larger of
                    // the two, so a theme asking for a thick resting line would see it get
                    // thinner when the field is focused. The focused line is never the thinner.
                    focusedIndicatorLineThickness = maxOf(
                        decoration.borderWidth,
                        TextFieldDefaults.FocusedIndicatorThickness
                    ),
                    unfocusedIndicatorLineThickness = decoration.borderWidth
                )
            } else {
                BareBackground(decoration)
            }
        }
    )
}

/** A Material outlined field, the one with a border all the way round. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxedField(
    decoration: FieldDecoration,
    value: String,
    innerTextField: @Composable () -> Unit
) {
    OutlinedTextFieldDefaults.DecorationBox(
        value = value,
        innerTextField = innerTextField,
        enabled = true,
        singleLine = decoration.singleLine,
        visualTransformation = decoration.visualTransformation,
        interactionSource = decoration.interactionSource,
        isError = decoration.hasError,
        placeholder = decoration.placeholder,
        colors = decoration.colors,
        contentPadding = decoration.contentPadding,
        container = {
            if (decoration.drawsBorder) {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = decoration.hasError,
                    interactionSource = decoration.interactionSource,
                    colors = decoration.colors,
                    shape = decoration.shape,
                    // Never the thinner when focused, as above.
                    focusedBorderThickness = maxOf(
                        decoration.borderWidth,
                        OutlinedTextFieldDefaults.FocusedBorderThickness
                    ),
                    unfocusedBorderThickness = decoration.borderWidth
                )
            } else {
                BareBackground(decoration)
            }
        }
    )
}

/** The container of a field the theme asked to draw without a border: background and nothing else. */
@Composable
private fun BareBackground(decoration: FieldDecoration) {
    Box(
        Modifier
            .fillMaxSize()
            .background(decoration.theme.inputBackgroundColor, decoration.shape)
    )
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
