package com.gopay.example.checkout

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.ui.PaymentCardForm
import kotlinx.coroutines.launch

/**
 * A deliberately ordinary-looking e-shop checkout. Everything payment-related is real: the amounts
 * come from [DemoCart], the payment is created through `MerchantBackendSimulator`, and each method
 * drives the actual SDK call (see [CheckoutController]).
 */
@Composable
fun CheckoutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val model = remember { CheckoutController() }

    val outcome = model.outcome
    if (outcome != null) {
        CheckoutResultScreen(
            outcome = outcome,
            cart = model.cart,
            paymentId = model.paymentId,
            isRefreshing = model.isBusy,
            onRefresh = { scope.launch { model.refreshChargeState(activity) } },
            onRetry = { model.resumeShopping() },
            onDone = {
                model.resumeShopping()
                model.finish()
                onClose()
            }
        )
        return
    }

    model.qrDetails?.let { details ->
        BankTransferSheet(
            details = details,
            cart = model.cart,
            onDismiss = { model.dismissQr() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CheckoutTheme.canvas)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        CheckoutHeader(
            onBack = {
                model.finish()
                onClose()
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CheckoutTheme.gutter)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OrderSummary(model.cart)
            PaymentMethods(model)
            TrustFooter()
        }

        PayBar(model = model, onPay = { scope.launch { model.pay(activity) } })
    }
}

@Composable
private fun CheckoutHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.canvas)
            .padding(horizontal = CheckoutTheme.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .background(CheckoutTheme.surface, CircleShape)
                .border(1.dp, CheckoutTheme.hairline, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CheckoutTheme.ink,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Checkout", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CheckoutTheme.ink)
            Text("Northline Supply", fontSize = 12.5.sp, color = CheckoutTheme.inkMuted)
        }
    }
}

/**
 * Demonstrates `GopayLocales` — the card form ships with 20 built-in languages, plus any custom
 * locale registered at init time (`"xx"` in `ExampleApplication`). Lives next to the card form
 * rather than in the header, since it's the only thing it actually localizes.
 */
@Composable
private fun LocaleMenu(locale: String?, onLocaleChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .background(CheckoutTheme.surface, CircleShape)
                .border(1.dp, CheckoutTheme.hairline, CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Language,
                contentDescription = null,
                tint = CheckoutTheme.ink,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = locale?.uppercase() ?: "AUTO",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = CheckoutTheme.ink
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("System default") },
                onClick = { onLocaleChange(null); expanded = false }
            )
            GopayLocales.availableCodes().forEach { code ->
                DropdownMenuItem(
                    text = { Text(code.uppercase()) },
                    onClick = { onLocaleChange(code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun OrderSummary(cart: DemoCart) {
    SurfaceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your order", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CheckoutTheme.ink)
            Spacer(Modifier.weight(1f))
            Text("${cart.items.size} items", fontSize = 13.sp, color = CheckoutTheme.inkMuted)
        }

        cart.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CheckoutTheme.surfaceSunken, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = CheckoutTheme.ink)
                    Text(
                        text = if (item.quantity > 1) "${item.variant} · ×${item.quantity}" else item.variant,
                        fontSize = 12.5.sp,
                        color = CheckoutTheme.inkMuted
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    cart.formatted(item.lineTotal),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = CheckoutTheme.ink
                )
            }
        }

        Divider(color = CheckoutTheme.hairline)

        TotalRow("Subtotal", cart.formatted(cart.subtotal), emphasized = false)
        TotalRow("Shipping", cart.formatted(cart.shipping), emphasized = false)
        TotalRow("Total", cart.formatted(cart.total), emphasized = true)
    }
}

@Composable
private fun TotalRow(label: String, value: String, emphasized: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = if (emphasized) 16.sp else 14.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) CheckoutTheme.ink else CheckoutTheme.inkMuted
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontSize = if (emphasized) 19.sp else 14.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = CheckoutTheme.ink
        )
    }
}

@Composable
private fun PaymentMethods(model: CheckoutController) {
    SurfaceCard(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = "Payment method",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = CheckoutTheme.ink,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        CheckoutController.Method.entries.forEachIndexed { index, method ->
            if (index > 0) Divider(color = CheckoutTheme.hairline)
            PaymentMethodRow(
                method = method,
                isSelected = model.selectedMethod == method,
                enabled = !model.isBusy,
                onSelect = { model.selectedMethod = method }
            ) {
                if (method == CheckoutController.Method.CARD) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        LocaleMenu(locale = model.locale, onLocaleChange = { model.locale = it })
                    }
                    // The SDK's own component, themed to match the shop. Card data never leaves it —
                    // the submit lambda hands back a JWE, not a PAN.
                    key(model.locale) {
                        PaymentCardForm(
                            onEncryptionComplete = { },
                            onFormReady = { submit -> model.onCardFormReady(submit) },
                            locale = model.locale,
                            theme = CheckoutTheme.cardForm
                        )
                    }
                    model.cardFormError?.let { error ->
                        Text(text = error, fontSize = 12.5.sp, color = CheckoutTheme.danger)
                    }
                }
                SdkNote(method.sdkNote)
            }
        }
    }
}

@Composable
private fun TrustFooter() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = CheckoutTheme.inkMuted,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Secured by GoPay · card details never touch this app",
                fontSize = 11.5.sp,
                color = CheckoutTheme.inkMuted
            )
        }
        Text(
            "GopaySDK ${GopaySDK.version}",
            fontSize = 11.5.sp,
            color = CheckoutTheme.inkMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PayBar(model: CheckoutController, onPay: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.surface)
            .padding(horizontal = CheckoutTheme.gutter)
            .padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        model.banner?.let { banner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CheckoutTheme.warning.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = CheckoutTheme.warning,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(banner, fontSize = 13.sp, color = CheckoutTheme.warning)
            }
        }

        val busy = model.busyLabel
        PrimaryButton(
            text = busy ?: model.payButtonTitle,
            enabled = !model.isBusy,
            leading = if (busy != null) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = CheckoutTheme.onAccent,
                        strokeWidth = 2.dp
                    )
                }
            } else null,
            onClick = onPay
        )
    }
}
