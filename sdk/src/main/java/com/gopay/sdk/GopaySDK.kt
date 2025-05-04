package com.gopay.sdk

import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.modules.network.NetworkManager
import com.gopay.sdk.service.PaymentService
import okhttp3.CertificatePinner
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

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
     * Network manager handling HTTP client and API service
     */
    private val networkManager = NetworkManager(config)
    
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
    
    /**
     * Configure advanced security settings for the SDK's network client.
     * Use this to set up certificate pinning or custom certificates.
     *
     * @param sslSocketFactory Custom SSL socket factory (optional)
     * @param trustManager Custom X509TrustManager (required if sslSocketFactory is provided)
     * @param certificatePinner Certificate pinner for public key pinning (optional)
     * @return The same SDK instance for chaining
     */
    fun configureSecuritySettings(
        sslSocketFactory: SSLSocketFactory? = null,
        trustManager: X509TrustManager? = null,
        certificatePinner: CertificatePinner? = null
    ): GopaySDK {
        // Create a NetworkConfig with the security settings
        val securityConfig = NetworkConfig(
            baseUrl = config.apiBaseUrl,
            readTimeoutSeconds = config.requestTimeoutMs / 1000,
            connectTimeoutSeconds = config.requestTimeoutMs / 2000,
            enableLogging = config.debugLoggingEnabled,
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            certificatePinner = certificatePinner
        )
        
        // Use the withSecuritySettings method when implemented
        // networkManager.withSecuritySettings(securityConfig)
        
        return this
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