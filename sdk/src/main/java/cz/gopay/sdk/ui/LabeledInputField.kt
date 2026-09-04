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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


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
    Column(modifier = modifier) {
        if (!theme.labelHidden) {
            BasicText(
                text = theme.renderedLabel(config.label),
                style = if (config.error != null) {
                    theme.labelTextStyle().copy(color = theme.errorTextColor)
                } else {
                    theme.labelTextStyle()
                },
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
                .semanticsLabel(theme, config.label)
                .background(color = theme.inputBackgroundColor, shape = shape)
                // A zero width means no border at all, as on the web; Compose would otherwise
                // draw a hairline.
                .let {
                    if (theme.inputBorderWidth > 0.dp) {
                        it.border(
                            width = theme.inputBorderWidth,
                            color = if (config.error != null) theme.inputErrorBorderColor else theme.inputBorderColor,
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
