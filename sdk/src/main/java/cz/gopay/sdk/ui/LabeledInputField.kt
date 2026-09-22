package cz.gopay.sdk.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabeledInputField(
    value: String,
    onValueChange: (String) -> Unit,
    config: LabeledInputFieldConfig,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    theme: PaymentCardFormTheme = PaymentCardFormTheme()
) {
    val shape = RoundedCornerShape(theme.inputBorderRadius.coerceAtLeast(0.dp))
    val hasError = config.error != null
    val interactionSource = remember { MutableInteractionSource() }
    val borderWidth = theme.inputBorderWidth.coerceAtLeast(0.dp)
    val contentPadding = PaddingValues(
        horizontal = theme.inputPaddingHorizontal.coerceAtLeast(0.dp),
        vertical = theme.inputPaddingVertical.coerceAtLeast(0.dp)
    )
    val placeholder: (@Composable () -> Unit)? = config.placeholder?.let { text ->
        {
            // Material's Text, not BasicText: with no color in the theme the style leaves the
            // color unspecified, and only Text falls back to the placeholder color the decoration
            // box provides. One line, like the field itself, so a large font scale cannot make an
            // empty field taller than a filled one.
            Text(
                text = text,
                style = theme.placeholderTextStyle(),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }

    Column(modifier = modifier) {
        if (!theme.labelHidden) {
            BasicText(
                text = theme.renderedLabel(config.label),
                // The label keeps its own color in an error state; only the field and the error
                // line change, matching the hosted card form.
                style = theme.labelTextStyle().copy(color = theme.resolvedLabelColor()),
                modifier = Modifier.padding(bottom = theme.fieldSpacing.coerceAtLeast(0.dp))
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = config.keyboardOptions,
            visualTransformation = config.visualTransformation,
            interactionSource = interactionSource,
            modifier = config.textFieldModifier
                .fillMaxWidth()
                .semanticsLabel(theme, config.label)
                .let {
                    // A minimum, not a fixed height: at a large font scale a hard height would
                    // leave the text drawing over the field below it.
                    val height = theme.inputHeight?.coerceAtLeast(0.dp)
                    if (height != null) it.heightIn(min = height) else it
                },
            singleLine = singleLine,
            textStyle = theme.inputTextStyle(),
            decorationBox = { innerTextField ->
                when (theme.inputBorderStyle) {
                    InputBorderStyle.UNDERLINE -> TextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = singleLine,
                        visualTransformation = config.visualTransformation,
                        interactionSource = interactionSource,
                        isError = hasError,
                        placeholder = placeholder,
                        shape = shape,
                        colors = theme.filledFieldColors(),
                        contentPadding = contentPadding,
                        container = {
                            TextFieldDefaults.Container(
                                enabled = true,
                                isError = hasError,
                                interactionSource = interactionSource,
                                colors = theme.filledFieldColors(),
                                shape = shape,
                                // Only the resting thickness: Material thickens the line of
                                // the focused field on its own, and that is the focus mark.
                                unfocusedIndicatorLineThickness = borderWidth
                            )
                        }
                    )

                    InputBorderStyle.BOXED -> OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = true,
                        singleLine = singleLine,
                        visualTransformation = config.visualTransformation,
                        interactionSource = interactionSource,
                        isError = hasError,
                        placeholder = placeholder,
                        colors = theme.outlinedFieldColors(),
                        contentPadding = contentPadding,
                        container = {
                            OutlinedTextFieldDefaults.Container(
                                enabled = true,
                                isError = hasError,
                                interactionSource = interactionSource,
                                colors = theme.outlinedFieldColors(),
                                shape = shape,
                                // Resting only, as above.
                                unfocusedBorderThickness = borderWidth
                            )
                        }
                    )
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
