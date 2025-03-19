package com.gopay.sdk

import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.service.PaymentService

/**
 * Main entry point for the Gopay SDK.
 */
class GopaySDK {
    
    private val paymentService = PaymentService()
    
    /**
     * Simple hello world function to test the connection between app and SDK.
     * 
     * @param name Optional name to include in the greeting
     * @return A greeting string
     */
    fun helloWorld(name: String = ""): String {
        return if (name.isBlank()) {
            "Hello from Gopay SDK!"
        } else {
            "Hello $name from Gopay SDK!"
        }
    }
    
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
         * Get the singleton instance of the SDK.
         */
        fun getInstance(): GopaySDK {
            return instance ?: synchronized(this) {
                instance ?: GopaySDK().also { instance = it }
            }
        }
    }
} 