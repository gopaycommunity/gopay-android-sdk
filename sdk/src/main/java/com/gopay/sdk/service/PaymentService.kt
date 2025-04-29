package com.gopay.sdk.service

import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.model.PaymentMethodType

/**
 * Service for handling payment-related operations.
 */
class PaymentService {
    
    /**
     * Returns a list of available payment methods.
     */
    fun getAvailablePaymentMethods(): List<PaymentMethod> {
        // In a real SDK, this would fetch from an API
        // Example of how we would use the configuration:
        // val apiBaseUrl = GopaySDK.getInstance().config.apiBaseUrl
        // val paymentEndpoint = GopaySDK.getInstance().config.apiBaseUrl + GopayConfig.PAYMENT_ENDPOINT + "/methods"
        
        return listOf(
            PaymentMethod(
                id = "card",
                name = "Credit Card",
                description = "Pay with Visa, Mastercard, or American Express",
                type = PaymentMethodType.CREDIT_CARD
            ),
            PaymentMethod(
                id = "bank",
                name = "Bank Transfer",
                description = "Direct transfer from your bank account",
                type = PaymentMethodType.BANK_TRANSFER
            ),
            PaymentMethod(
                id = "wallet",
                name = "Digital Wallet",
                description = "Pay with your digital wallet",
                type = PaymentMethodType.DIGITAL_WALLET
            )
        )
    }
    
    /**
     * Processes a payment with the selected payment method.
     * 
     * @param paymentMethodId The ID of the selected payment method
     * @param amount The amount to charge
     * @return True if payment was successful, false otherwise
     */
    fun processPayment(paymentMethodId: String, amount: Double): Boolean {
        // In a real SDK, this would make an API call to process the payment
        // For demonstration, handle differently based on environment
        val config = GopaySDK.getInstance().config
        
        // In a real implementation, we would make HTTP requests to the appropriate endpoint
        // using the config.apiBaseUrl
        
        // Just for demonstration, let's simulate environment-specific behavior
        return when (config.environment) {
            Environment.SANDBOX -> true  // Always succeed in sandbox
            Environment.DEVELOPMENT -> paymentMethodId != "bank" // Fail bank transfers in dev
            else -> amount > 0 // In production/staging, only succeed if amount is positive
        }
    }
} 