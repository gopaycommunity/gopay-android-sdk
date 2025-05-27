package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import com.gopay.sdk.internal.GopayContextProvider
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.model.AuthenticationResponse
import com.gopay.sdk.modules.network.NetworkManager
import com.gopay.sdk.service.PaymentService
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.JwtUtils
import com.gopay.sdk.exception.UnauthenticatedException
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
     * Network manager handling HTTP client and API service.
     * Uses automatically obtained Application context.
     */
    private val networkManager = NetworkManager(config, GopayContextProvider.getApplicationContext())
    
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
     * Gets the token storage instance for managing authentication tokens
     * 
     * @return TokenStorage instance
     */
    fun getTokenStorage(): TokenStorage = networkManager.getTokenStorage()
    
    /**
     * Sets authentication tokens from server-side authentication response.
     * This method validates JWT expiration and saves the tokens to storage.
     * 
     * @param authResponse The authentication response from server-side authentication
     * @throws UnauthenticatedException if the access token is expired or invalid
     */
    fun setAuthenticationResponse(authResponse: AuthenticationResponse) {
        val tokenStorage = getTokenStorage()
        
        // Validate that the access token is not expired
        if (JwtUtils.isTokenExpired(authResponse.accessToken)) {
            throw UnauthenticatedException("Access token is expired")
        }
        
        // Validate that the refresh token is not expired (if provided)
        if (JwtUtils.isTokenExpired(authResponse.refreshToken)) {
            throw UnauthenticatedException("Refresh token is expired")
        }
        
        // Save the tokens to storage
        tokenStorage.saveTokens(authResponse.accessToken, authResponse.refreshToken)
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
        // example:
        // val securityConfig = NetworkConfig(
        //     baseUrl = config.apiBaseUrl,
        //     readTimeoutSeconds = config.requestTimeoutMs / 1000,
        //     connectTimeoutSeconds = config.requestTimeoutMs / 2000,
        //     enableLogging = config.debugLoggingEnabled,
        //     sslSocketFactory = sslSocketFactory,
        //     trustManager = trustManager,
        //     certificatePinner = certificatePinner
        // )
        
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
         * Application context is obtained automatically.
         * Must be called before using any SDK features.
         *
         * @param config The SDK configuration
         */
        @JvmStatic
        fun initialize(config: GopayConfig) {
            instance = GopaySDK(config)
        }
        
        /**
         * Initialize the SDK with manual context (for special cases).
         * This is provided for backward compatibility and special use cases
         * where automatic context detection might not work.
         *
         * @param config The SDK configuration
         * @param context Android context (will use applicationContext)
         */
        @JvmStatic
        fun initialize(config: GopayConfig, context: Context) {
            // Set the context manually before creating the SDK instance
            GopayContextProvider.setApplicationContext(context.applicationContext)
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