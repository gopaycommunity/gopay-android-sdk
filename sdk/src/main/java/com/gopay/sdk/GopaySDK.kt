package com.gopay.sdk

import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.service.PaymentService

/**
 * Main entry point for the Gopay SDK.
 */
class GopaySDK private constructor(
    /**
     * The configuration for this SDK instance.
     */
    val config: GopayConfig
) {
    
    private val paymentService = PaymentService()
    
    /**
     * Get available payment methods.
     * 
     * @return List of available payment methods
     */
    fun getPaymentMethods(): List<PaymentMethod> {
        return paymentService.getAvailablePaymentMethods()
    }
    
    /**
     * Process a payment.
     * 
     * @param paymentMethodId ID of the selected payment method
     * @param amount Amount to charge
     * @return True if payment was successful, false otherwise
     */
    fun processPayment(paymentMethodId: String, amount: Double): Boolean {
        return paymentService.processPayment(paymentMethodId, amount)
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var instance: GopaySDK? = null
        
        /**
         * Initialize the SDK with the given configuration.
         * Must be called before using any SDK features.
         *
         * @param config The SDK configuration
         */
        @JvmStatic
        fun initialize(config: GopayConfig) {
            instance = GopaySDK(config)
        }
        
        /**
         * Get the singleton instance of the SDK.
         * 
         * @throws IllegalStateException if SDK hasn't been initialized
         * @return The SDK instance
         */
        @JvmStatic
        fun getInstance(): GopaySDK {
            return instance ?: throw IllegalStateException(
                "GopaySDK has not been initialized. Call GopaySDK.initialize(config) first."
            )
        }
        
        /**
         * Check if the SDK has been initialized.
         *
         * @return True if initialized, false otherwise
         */
        @JvmStatic
        fun isInitialized(): Boolean {
            return instance != null
        }
    }
} 