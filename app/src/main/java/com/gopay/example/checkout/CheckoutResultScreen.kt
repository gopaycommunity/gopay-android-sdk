package com.gopay.example.checkout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.model.ChargeState

/**
 * Terminal screen of the checkout: succeeded, failed, or still pending. The pending case is real —
 * a charge can sit in PROCESSING after 3DS, so the screen offers `getChargeState()` on demand.
 */
@Composable
fun CheckoutResultScreen(
    outcome: CheckoutController.Outcome,
    cart: DemoCart,
    paymentId: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit
) {
    var showsDeveloperDetails by remember { mutableStateOf(false) }

    val tint = when {
        outcome.isSuccess -> CheckoutTheme.accent
        outcome.isFailure -> CheckoutTheme.danger
        else -> CheckoutTheme.warning
    }
    val icon = when {
        outcome.isSuccess -> Icons.Filled.CheckCircle
        outcome.isFailure -> Icons.Filled.Warning
        else -> Icons.Filled.Schedule
    }
    val title = when {
        outcome.isSuccess -> "Payment complete"
        outcome.isFailure -> "Payment failed"
        else -> "Payment pending"
    }
    val subtitle = when {
        outcome.isSuccess -> "Thanks! We've emailed your receipt and the order is on its way."
        outcome.isFailure -> outcome.message ?: "The bank declined the charge. No money was taken."
        else -> "We polled the gateway and the bank hasn't confirmed yet. A real shop would also " +
            "get the final state on its notification URL."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CheckoutTheme.canvas)
            .verticalScroll(rememberScrollState())
            .padding(CheckoutTheme.gutter)
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CheckoutTheme.ink)
            Spacer(Modifier.size(6.dp))
            Text(
                text = subtitle,
                fontSize = 15.sp,
                color = CheckoutTheme.inkMuted,
                textAlign = TextAlign.Center
            )
        }

        SurfaceCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            ReceiptRow("Amount", cart.formatted(cart.total))
            outcome.response?.paymentInstrument?.details?.let { details ->
                details.maskedPan?.let { ReceiptRow("Card", it) }
                details.scheme?.let { ReceiptRow("Scheme", it.name.lowercase().replaceFirstChar(Char::uppercase)) }
                ReceiptRow("Input", friendlyInputType(details.inputType))
            }
            paymentId?.let { ReceiptRow("Payment", it) }
            ReceiptRow("Charge state", outcome.state.name)
            outcome.message?.takeIf { it.isNotBlank() && !outcome.isSuccess }?.let {
                ReceiptRow("Reason", it, valueColor = CheckoutTheme.danger)
            }
        }

        outcome.response?.let { response ->
            SurfaceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showsDeveloperDetails = !showsDeveloperDetails },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Code,
                        contentDescription = null,
                        tint = CheckoutTheme.ink,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Developer details",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CheckoutTheme.ink
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (showsDeveloperDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = CheckoutTheme.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                AnimatedVisibility(visible = showsDeveloperDetails) {
                    Text(
                        text = prettyPrint(response),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CheckoutTheme.inkMuted
                    )
                }
            }
        }

        if (outcome.isPending) {
            PrimaryButton(
                text = if (isRefreshing) "Refreshing…" else "Refresh status",
                enabled = !isRefreshing,
                leading = if (isRefreshing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = CheckoutTheme.onAccent,
                            strokeWidth = 2.dp
                        )
                    }
                } else null,
                onClick = onRefresh
            )
        }

        if (outcome.isFailure) {
            PrimaryButton(text = "Try another method", onClick = onRetry)
        }

        if (outcome.isSuccess) {
            PrimaryButton(text = "Back to shop", onClick = onDone)
        } else {
            SecondaryButton(text = "Cancel order", onClick = onDone)
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String, valueColor: Color = CheckoutTheme.ink) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 13.sp, color = CheckoutTheme.inkMuted)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

private fun friendlyInputType(raw: String): String = when (raw) {
    "ENCRYPTED_CARD" -> "Card entered in app"
    "CARD_TOKEN" -> "Saved card"
    "GOOGLE_PAY" -> "Google Pay"
    else -> raw
}

/**
 * The SDK's models are Moshi data classes, not something the app can serialize on its own — Moshi
 * isn't on the app's compile classpath. Breaking the generated `toString()` across lines is enough
 * to correlate what the shopper saw with what the API returned.
 */
private fun prettyPrint(value: Any): String =
    value.toString()
        .replace(", ", ",\n")
        .replace("(", "(\n  ")
        .replace(")", "\n)")
