package com.gopay.sdk.config

/**
 * Configuration class for the Gopay SDK.
 * Holds all environment-specific constants and settings.
 */
data class GopayConfig(
    /**
     * The environment to use for the SDK.
     */
    val environment: Environment,
    
    /**
     * Timeout for API requests in milliseconds.
     */
    val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT,
    
    /**
     * Whether to enable debug logging.
     */
    val debugLoggingEnabled: Boolean = false
) {
    /**
     * Get the API base URL for the current environment.
     */
    val apiBaseUrl: String
        get() = environment.apiBaseUrl
    
    companion object {
        /**
         * Default API request timeout in milliseconds (30 seconds).
         */
        const val DEFAULT_REQUEST_TIMEOUT: Long = 30000
        
        /**
         * Payment API endpoints.
         */
        const val PAYMENT_ENDPOINT = "/payments"
        
        /**
         * Customer API endpoints.
         */
        const val CUSTOMER_ENDPOINT = "/customers"
    }
} 