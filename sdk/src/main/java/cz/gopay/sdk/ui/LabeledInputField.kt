package cz.gopay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex


/**
 * Configuration for LabeledInputField
 */
data class LabeledInputFieldConfig(
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
 */
@Composable
fun LabeledInputField(
    value: String,
    onValueChange: (String) -> Unit,
    config: LabeledInputFieldConfig,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    theme: PaymentCardFormTheme = PaymentCardFormTheme()
) {
    val shape = RoundedCornerShape(theme.inputBorderRadius.coerceAtLeast(0.dp))
    var isFocused by remember { mutableStateOf(false) }
    val hasError = config.error != null
    // A field in a state is lifted above its siblings, so the focus ring, which is drawn outside
    // the field, is never covered by the background of a neighbour drawn later.
    // Focus outranks an error: on a tie Compose paints in composition order, which would let an
    // invalid neighbour repaint the focused field's shared side in the error colour.
    Column(modifier = modifier.zIndex(if (isFocused) 2f else if (hasError) 1f else 0f)) {
        if (!theme.labelHidden) {
            BasicText(
                text = theme.renderedLabel(config.label),
                // The label keeps its own color in an error state; only the border and the
                // error line change, matching the hosted card form.
                style = theme.labelTextStyle(),
                modifier = Modifier.padding(bottom = theme.fieldSpacing.coerceAtLeast(0.dp))
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = config.keyboardOptions,
            visualTransformation = config.visualTransformation,
            modifier = config.textFieldModifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .semanticsLabel(theme, config.label)
                .background(color = theme.inputBackgroundColor, shape = shape)
                .focusRing(theme, isFocused)
                // A zero width means no border at all, as on the web; Compose would otherwise
                // draw a hairline.
                .let {
                    if (theme.inputBorderWidth > 0.dp) {
                        it.border(
                            width = theme.inputBorderWidth,
                            color = theme.borderColorFor(isFocused = isFocused, hasError = hasError),
                            shape = shape
                        )
                    } else {
                        it
                    }
                }
                .let {
                    // A minimum, not a fixed height: at a large font scale a hard height would
                    // leave the text drawing over the field below it. iOS keeps the value literal
                    // and says so; here growing is the safer reading of the same intent.
                    val height = theme.inputHeight?.coerceAtLeast(0.dp)
                    if (height != null) it.heightIn(min = height) else it
                }
                .padding(
                    horizontal = theme.inputPaddingHorizontal.coerceAtLeast(0.dp),
                    // A fixed height takes precedence over the vertical padding, as on the web.
                    vertical = if (theme.inputHeight != null) 0.dp else theme.inputPaddingVertical.coerceAtLeast(0.dp)
                ),
            singleLine = singleLine,
            textStyle = theme.inputTextStyle(),
            decorationBox = { innerTextField ->
                val content = @Composable {
                    if (value.isEmpty() && config.placeholder != null) {
                        // One line, like the field itself: at a large font scale a wrapping
                        // placeholder would make the empty field taller than a filled one.
                        BasicText(
                            text = config.placeholder,
                            style = theme.placeholderTextStyle(),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                    innerTextField()
                }
                if (theme.inputHeight != null) {
                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) { content() }
                } else {
                    content()
                }
            }
        )
        HelperText(error = config.error, helperText = config.helperText, theme = theme)
    }
}

/**
 * Keeps a hidden label available to screen readers: the field announces it as its content
 * description, so hiding the label costs no accessibility.
 */
private fun Modifier.semanticsLabel(theme: PaymentCardFormTheme, label: String): Modifier =
    if (theme.labelHidden) semantics { contentDescription = label } else this

/**
 * Border color of an input. A focused field shows the focus color even when it is invalid, so
 * the field the keyboard is on is always the one that stands out.
 */
internal fun PaymentCardFormTheme.borderColorFor(isFocused: Boolean, hasError: Boolean): Color = when {
    isFocused -> focusGradientStart
    hasError -> inputErrorBorderColor
    else -> inputBorderColor
}

/**
 * Draws the focus ring just outside the border of a focused field. It needs both a positive width
 * and a color, and it draws rather than measures, so it never shifts the surrounding layout.
 */
private fun Modifier.focusRing(theme: PaymentCardFormTheme, isFocused: Boolean): Modifier {
    val width = theme.focusRingWidth
    val color = theme.focusRingColor
    if (width == null || width <= 0.dp || color == null) return this
    return drawBehind {
        if (!isFocused) return@drawBehind
        val stroke = width.toPx()
        val outline = focusRingOutline(size, stroke, theme.inputBorderRadius.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(outline.left, outline.top),
            size = Size(outline.width, outline.height),
            cornerRadius = outline.topLeftCornerRadius,
            style = Stroke(width = stroke)
        )
    }
}

/**
 * The outline the focus ring is stroked along. It runs half a stroke outside the field on every
 * side, so the whole ring lies outside the field and follows its corners.
 */
internal fun focusRingOutline(fieldSize: Size, strokeWidth: Float, cornerRadius: Float): RoundRect {
    val inset = strokeWidth / 2f
    return RoundRect(
        left = -inset,
        top = -inset,
        right = fieldSize.width + inset,
        bottom = fieldSize.height + inset,
        cornerRadius = CornerRadius(cornerRadius + inset)
    )
}
