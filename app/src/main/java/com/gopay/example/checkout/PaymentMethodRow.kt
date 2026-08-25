package com.gopay.example.checkout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CheckoutController.Method.icon: ImageVector
    get() = when (this) {
        CheckoutController.Method.CARD -> Icons.Filled.CreditCard
        CheckoutController.Method.GOOGLE_PAY -> Icons.Filled.Wallet
        CheckoutController.Method.SAVED_CARD -> Icons.Filled.VerifiedUser
        CheckoutController.Method.BANK_TRANSFER -> Icons.Filled.QrCode2
    }

/**
 * One selectable payment method. Selecting a row expands it to reveal the method's own content —
 * for cards that's the SDK's `PaymentCardForm`, for everything else a short note about which SDK
 * call the row runs.
 */
@Composable
fun PaymentMethodRow(
    method: CheckoutController.Method,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    expanded: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onSelect)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isSelected) CheckoutTheme.accent.copy(alpha = 0.14f) else CheckoutTheme.surfaceSunken,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = method.icon,
                    contentDescription = null,
                    tint = if (isSelected) CheckoutTheme.accent else CheckoutTheme.inkMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CheckoutTheme.ink
                )
                Text(
                    text = method.subtitle,
                    fontSize = 13.sp,
                    color = CheckoutTheme.inkMuted
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = if (isSelected) 6.dp else 1.5.dp,
                        color = if (isSelected) CheckoutTheme.accent else CheckoutTheme.hairline,
                        shape = CircleShape
                    )
            )
        }

        AnimatedVisibility(visible = isSelected) {
            Column(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = expanded
            )
        }
    }
}

/** The short "what this row does in SDK terms" note shown under a selected method. */
@Composable
fun SdkNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.surfaceSunken, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AccountBalance,
            contentDescription = null,
            tint = CheckoutTheme.inkMuted,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = CheckoutTheme.inkMuted
        )
    }
}
