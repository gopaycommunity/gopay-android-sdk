package com.gopay.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import com.gopay.sdk.GopaySDK
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.model.PaymentMethodType
import kotlinx.coroutines.launch

@Composable
fun CheckoutScreen() {
    val sdk = GopaySDK.getInstance()
    val paymentMethods = sdk.getPaymentMethods()
    var selectedPaymentMethodId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Checkout",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Order summary section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Order Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Product")
                        Text(text = "GoPay SDK License")
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Amount")
                        Text(
                            text = "$99.99",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Select Payment Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Payment methods selection
            LazyColumn(
                modifier = Modifier
                    .selectableGroup()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(paymentMethods) { paymentMethod ->
                    PaymentMethodItem(
                        paymentMethod = paymentMethod,
                        selected = paymentMethod.id == selectedPaymentMethodId,
                        onSelect = { selectedPaymentMethodId = paymentMethod.id }
                    )
                }
            }
            
            Button(
                onClick = {
                    if (selectedPaymentMethodId != null) {
                        val success = sdk.processPayment(selectedPaymentMethodId!!, 99.99)
                        scope.launch {
                            if (success) {
                                snackbarHostState.showSnackbar("Payment successful!")
                            } else {
                                snackbarHostState.showSnackbar("Payment failed. Please try again.")
                            }
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please select a payment method")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pay $99.99")
            }
        }
    }
}

@Composable
fun PaymentMethodItem(
    paymentMethod: PaymentMethod,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Use our custom payment icons based on payment method type
            when (paymentMethod.type) {
                PaymentMethodType.CREDIT_CARD -> {
                    CreditCardIcon(modifier = Modifier.size(32.dp))
                }
                PaymentMethodType.BANK_TRANSFER -> {
                    BankTransferIcon(modifier = Modifier.size(32.dp))
                }
                PaymentMethodType.DIGITAL_WALLET -> {
                    DigitalWalletIcon(modifier = Modifier.size(32.dp))
                }
            }
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = paymentMethod.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = paymentMethod.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            RadioButton(
                selected = selected,
                onClick = null // null because we handle clicks on the entire card
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CheckoutScreenPreview() {
    ExampleAppTheme {
        CheckoutScreen()
    }
} 