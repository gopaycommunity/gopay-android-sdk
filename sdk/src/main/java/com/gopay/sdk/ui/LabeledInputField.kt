package com.gopay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
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
    Column(modifier = modifier) {
        BasicText(
            text = config.label,
            style = if (config.error != null) {
                theme.labelTextStyle.copy(color = theme.errorTextStyle.color)
            } else {
                theme.labelTextStyle
            },
            modifier = Modifier.padding(bottom = 4.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = config.keyboardOptions,
            visualTransformation = config.visualTransformation,
            modifier = config.textFieldModifier
                .fillMaxWidth()
                .background(color = theme.inputBackgroundColor, shape = theme.inputShape)
                .border(
                    width = theme.inputBorderWidth,
                    color = if (config.error != null) theme.inputErrorBorderColor else theme.inputBorderColor,
                    shape = theme.inputShape
                )
                .padding(theme.inputPadding),
            singleLine = singleLine,
            textStyle = theme.inputTextStyle,
            decorationBox = { innerTextField ->
                if (value.isEmpty() && config.placeholder != null) {
                    BasicText(
                        text = config.placeholder,
                        style = theme.inputTextStyle.copy(color = Color.LightGray)
                    )
                }
                innerTextField()
            }
        )
        HelperText(error = config.error, helperText = config.helperText, theme = theme)
    }
} 