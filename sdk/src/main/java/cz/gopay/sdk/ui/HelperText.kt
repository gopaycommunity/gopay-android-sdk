package cz.gopay.sdk.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


/**
 * Displays either error or helper text below a field.
 *
 * The slot is shared by both texts. With [PaymentCardFormTheme.errorMinHeight] set, it is laid out
 * even while empty, so the form does not shift when a message appears.
 */
@Composable
fun HelperText(error: String?, helperText: String?, theme: PaymentCardFormTheme) {
    val text = error ?: helperText
    if (text == null && theme.errorMinHeight <= 0.dp) return
    Box(
        modifier = Modifier
            .padding(top = (theme.errorSpacing ?: theme.fieldSpacing).coerceAtLeast(0.dp))
            .defaultMinSize(minHeight = theme.reservedErrorHeight())
    ) {
        if (text != null) {
            BasicText(
                text = text,
                style = if (error != null) {
                    theme.errorTextStyle().copy(color = theme.resolvedErrorTextColor())
                } else {
                    theme.helperTextStyle().copy(color = theme.resolvedHelperTextColor())
                }
            )
        }
    }
}
