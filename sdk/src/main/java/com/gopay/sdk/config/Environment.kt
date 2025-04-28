package com.gopay.sdk.config

/**
 * Defines the available environments for the Gopay SDK.
 */
enum class Environment(val apiBaseUrl: String) {
    /**
     * Production environment - use for live transactions.
     */
    PRODUCTION("https://api.gopay.com/v1"),

    /**
     * Sandbox environment - use for testing and integration.
     */
    SANDBOX("https://api.sandbox.gopay.com/v1"),

    /**
     * Development environment - use for local development.
     */
    DEVELOPMENT("https://api.dev.gopay.com/v1"),
    
    /**
     * Staging environment - use for pre-production testing.
     */
    STAGING("https://api.staging.gopay.com/v1");
} 