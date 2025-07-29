package com.gopay.sdk.config

/**
 * Defines the available environments for the Gopay SDK.
 */
sealed class Environment(val apiBaseUrl: String) {
    /**
     * Production environment - use for live transactions.
     */
    object PRODUCTION : Environment("https://api.gopay.com/v1/")

    /**
     * Sandbox environment - use for testing and integration.
     */
    object SANDBOX : Environment("https://api.sandbox.gopay.com/v1/")

    /**
     * Development environment - use for local development with custom endpoint.
     * 
     * @param customUrl The custom development endpoint URL
     */
    class DEVELOPMENT(customUrl: String) : Environment(
        customUrl.let { 
            require(it.isNotBlank()) { "Development URL cannot be empty" }
            require(it.startsWith("http://") || it.startsWith("https://")) { 
                "Development URL must start with http:// or https://" 
            }
            if (!it.endsWith("/")) "$it/" else it
        }
    ) {
        companion object {
            /**
             * Creates a DEVELOPMENT environment with a custom endpoint URL.
             * 
             * @param customUrl The custom development endpoint URL
             * @return DEVELOPMENT environment with the specified URL
             * @throws IllegalArgumentException if the URL is empty or invalid
             */
            fun create(customUrl: String): DEVELOPMENT = DEVELOPMENT(customUrl)
        }
    }
} 