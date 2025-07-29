package com.gopay.sdk.config

import com.gopay.sdk.exception.ErrorCallback

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
    val requestTimeoutMs: Long = 30000,
    
    /**
     * Whether to enable debug mode.
     */
    val debug: Boolean = false,
    
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
} 