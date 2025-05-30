package com.gopay.sdk.config

import com.gopay.sdk.exception.ErrorCallback
import com.gopay.sdk.util.UrlUtils

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
    val debugLoggingEnabled: Boolean = false,
    
    /**
     * Optional callback for error reporting.
     * When set, all SDK errors will be reported to this callback for analytics integration.
     */
    val errorCallback: ErrorCallback? = null
) {
    /**
     * Get the API base URL for the current environment.
     */
    val apiBaseUrl: String
        get() = environment.apiBaseUrl
    
    /**
     * Gets a properly formatted URL for the payments endpoint.
     * @return Full URL to the payments endpoint
     */
    fun getPaymentsUrl(): String {
        return UrlUtils.composeUrl(apiBaseUrl, PAYMENT_ENDPOINT)
    }
    
    /**
     * Gets a properly formatted URL for the customers endpoint.
     * @return Full URL to the customers endpoint
     */
    fun getCustomersUrl(): String {
        return UrlUtils.composeUrl(apiBaseUrl, CUSTOMER_ENDPOINT)
    }
    
    /**
     * Creates a properly formatted URL for any endpoint.
     * @param endpoint The endpoint to append to the base URL
     * @return The complete URL
     */
    fun createUrl(endpoint: String): String {
        return UrlUtils.composeUrl(apiBaseUrl, endpoint)
    }
    
    companion object {
        /**
         * Default API request timeout in milliseconds (30 seconds).
         */
        const val DEFAULT_REQUEST_TIMEOUT: Long = 30000
        
        /**
         * Payment API endpoints.
         */
        const val PAYMENT_ENDPOINT = "payments"
        
        /**
         * Customer API endpoints.
         */
        const val CUSTOMER_ENDPOINT = "customers"
    }
} 