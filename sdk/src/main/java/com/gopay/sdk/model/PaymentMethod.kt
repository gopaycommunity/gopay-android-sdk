package com.gopay.sdk.model

/**
 * Represents a payment method type available in the SDK.
 */
enum class PaymentMethodType {
    CREDIT_CARD,
    BANK_TRANSFER,
    DIGITAL_WALLET
}

/**
 * Model representing a payment method.
 */
data class PaymentMethod(
    val id: String,
    val name: String,
    val description: String,
    val type: PaymentMethodType,
    val iconResId: Int? = null
) 