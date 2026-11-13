package com.gopay.example.checkout

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.ui.PaymentCardFormValidationDisplay
import cz.gopay.sdk.ui.PaymentCardForm
import kotlinx.coroutines.launch

/**
 * A deliberately ordinary-looking e-shop checkout. Everything payment-related is real: the amounts
 * come from [DemoCart], the payment is created through `MerchantBackendSimulator` when the
 * checkout opens, and each method drives the actual SDK call (see [CheckoutController]).
 */
@Composable
fun CheckoutScreen(holder: CheckoutHolder, onClose: () -> Unit) {
    // Both from the holder, not from the composition: a rotation recomposes this screen in a new
    // Activity, and the payment under way and its result have to be there when it does.
    val scope = holder.scope
    val model = holder.controller

    // Leaving closes the session, and a payment under way would be left with nobody to show its
    // result to: while one runs, neither the system's Back nor the arrow in the header leaves.
    // The wait for the bank's 3DS challenge is the exception. With the challenge on screen this
    // Back is out of reach anyway; it is reachable only once the system has destroyed that
    // screen in the background, and then the wait would never end and leaving is the way out.
    BackHandler(enabled = !model.canLeave) {}

    val outcome = model.outcome
    if (outcome != null) {
        CheckoutResultScreen(
            outcome = outcome,
            cart = model.cart,
            // The payment the result is of. After a decline the checkout already holds the next one.
            paymentId = outcome.paymentId,
            isRefreshing = model.isBusy,
            onRefresh = { scope.launch { model.refreshChargeState() } },
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
            // Edge to edge: the keyboard would cover the pay bar.
            .imePadding()
    ) {
        CheckoutHeader(
            canGoBack = model.canLeave,
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
            TrustFooter(paymentId = model.paymentId)
        }

        PayBar(
            model = model,
            onPay = { scope.launch { model.pay() } },
            onOpenAgain = { scope.launch { model.open() } }
        )
    }
}

@Composable
private fun CheckoutHeader(canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.canvas)
            .padding(horizontal = CheckoutTheme.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            enabled = canGoBack,
            modifier = Modifier
                .size(38.dp)
                .background(CheckoutTheme.surface, CircleShape)
                .border(1.dp, CheckoutTheme.hairline, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = if (canGoBack) CheckoutTheme.ink else CheckoutTheme.inkMuted,
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
        TotalRow("Demo discount", cart.formatted(-cart.discount), emphasized = false)
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

        // Google Pay is on the list only where the device can pay with it.
        model.methods.forEachIndexed { index, method ->
            if (index > 0) Divider(color = CheckoutTheme.hairline)
            PaymentMethodRow(
                method = method,
                isSelected = model.selectedMethod == method,
                enabled = !model.isBusy,
                onSelect = { model.select(method) }
            ) {
                if (method == CheckoutController.Method.CARD) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        LocaleMenu(locale = model.locale, onLocaleChange = { model.locale = it })
                    }
                    // The SDK's own component, themed to match the shop. Card data never leaves it:
                    // the form keeps it under CARD_FORM_ID and the pay button asks
                    // GopaySDK.submitCardForm(CARD_FORM_ID) for a JWE, not a PAN. The form shows
                    // its own localized validation errors as the user types.
                    key(model.locale) {
                        PaymentCardForm(
                            locale = model.locale,
                            theme = CheckoutTheme.cardForm,
                            validation = PaymentCardFormValidationDisplay.LIVE,
                            formId = CheckoutController.CARD_FORM_ID
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
private fun TrustFooter(paymentId: String?) {
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
        // The payment the checkout opened, there to be seen from the start: it stays the same
        // through an attempt that charged nothing and changes when a declined one is replaced.
        paymentId?.let {
            Text("Payment $it", fontSize = 11.5.sp, color = CheckoutTheme.inkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PayBar(model: CheckoutController, onPay: () -> Unit, onOpenAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CheckoutTheme.surface)
            .padding(horizontal = CheckoutTheme.gutter)
            .padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // The payment could not be created or its session started: nothing to pay until it is,
        // so the pay button below stays disabled and this is the way on.
        model.openError?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CheckoutTheme.danger.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = CheckoutTheme.danger,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(error, fontSize = 13.sp, color = CheckoutTheme.danger, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenAgain) {
                    Text("Try again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CheckoutTheme.danger)
                }
            }
        }

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

        // While the checkout's payment is being opened the button says so and waits; the form
        // above can be filled in the meantime.
        val busy = model.busyLabel
        PrimaryButton(
            text = busy ?: model.payButtonTitle,
            enabled = model.canPay,
            leading = if (busy != null || model.isOpening) {
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
