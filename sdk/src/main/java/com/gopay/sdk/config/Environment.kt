package com.gopay.sdk.config

/**
 * Defines the available environments for the Gopay SDK.
 */
enum class Environment(val apiBaseUrl: String) {
    /**
     * Production environment - use for live transactions.
     */
    PRODUCTION("https://api.gopay.com/v1/"),

    /**
     * Sandbox environment - use for testing and integration.
     */
    SANDBOX("https://api.sandbox.gopay.com/v1/"),

    /**
     * Development environment - use for local development.
     */
    DEVELOPMENT("https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/"),
    
    /**
     * Staging environment - use for pre-production testing.
     */
    STAGING("https://api.staging.gopay.com/v1/");
} 