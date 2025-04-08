package com.gopay.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple icon composables for payment methods that don't rely on Material Icons.
 */
@Composable
fun CreditCardIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    SimpleIconWithText(
        text = "CC",
        modifier = modifier,
        backgroundColor = tint.copy(alpha = 0.1f),
        textColor = tint
    )
}

@Composable
fun BankTransferIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    SimpleIconWithText(
        text = "BT",
        modifier = modifier,
        backgroundColor = tint.copy(alpha = 0.1f),
        textColor = tint
    )
}

@Composable
fun DigitalWalletIcon(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    SimpleIconWithText(
        text = "DW", 
        modifier = modifier,
        backgroundColor = tint.copy(alpha = 0.1f),
        textColor = tint
    )
}

@Composable
private fun SimpleIconWithText(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
    textColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
} 