package com.gopay.sdk.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


/**
 * Displays either error or helper text based on the properties passed.
 */
@Composable
fun HelperText(error: String?, helperText: String?, theme: PaymentCardFormTheme) {
    when {
        error != null -> {
            BasicText(
                text = error,
                style = theme.errorTextStyle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        helperText != null -> {
            BasicText(
                text = helperText,
                style = theme.helperTextStyle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
} 