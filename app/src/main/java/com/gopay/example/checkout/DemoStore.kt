package com.gopay.example.checkout

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A hardcoded basket so the checkout has something real to charge for. [DemoCart.total] is what
 * gets passed to `MerchantBackendSimulator.createPayment`, so the gateway amount always matches
 * what the shopper sees.
 */
data class CartItem(
    val emoji: String,
    val name: String,
    val variant: String,
    /** Unit price in minor units (haléře). */
    val unitPrice: Int,
    val quantity: Int
) {
    val lineTotal: Int get() = unitPrice * quantity
}

data class DemoCart(
    val items: List<CartItem>,
    /** Minor units. */
    val shipping: Int,
    val currency: String = "CZK"
) {
    val subtotal: Int get() = items.sumOf { it.lineTotal }
    val total: Int get() = subtotal + shipping

    /** Formats minor units as a localized currency amount (`1 234 Kč`). */
    fun formatted(minorUnits: Long): String {
        val format = NumberFormat.getCurrencyInstance(Locale("cs", "CZ")).apply {
            currency = Currency.getInstance(this@DemoCart.currency)
            maximumFractionDigits = if (minorUnits % 100 == 0L) 0 else 2
        }
        return format.format(minorUnits / 100.0)
    }

    fun formatted(minorUnits: Int): String = formatted(minorUnits.toLong())

    companion object {
        val sample = DemoCart(
            items = listOf(
                CartItem("🎧", "Studio headphones", "Over-ear · Graphite", 249_000, 1),
                CartItem("☕️", "Single-origin beans", "Ethiopia · 500 g", 39_000, 2),
                CartItem("📓", "Dotted notebook", "A5 · Sage", 24_900, 1)
            ),
            shipping = 9_900
        )
    }
}
