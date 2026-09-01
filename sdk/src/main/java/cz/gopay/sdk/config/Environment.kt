package cz.gopay.sdk.config

/**
 * Defines the available environments for the Gopay SDK.
 */
sealed class Environment(val apiBaseUrl: String) {
    /**
     * Production environment - use for live transactions.
     *
     * The Payments 4.0 production gateway, same constant as the iOS SDK's `.production`.
     */
    object PRODUCTION : Environment("https://gate.gopay.com/gp-gw/api/4.0/")

    /**
     * Sandbox environment - use for testing and integration.
     *
     * The Payments 4.0 sandbox gateway, same constant as the iOS SDK's `.sandbox`.
     */
    object SANDBOX : Environment("https://gw.sandbox.gopay.com/gp-gw/api/4.0/")

    /**
     * Development environment - use for local development with custom endpoint.
     *
     * @param customUrl The custom development endpoint URL
     */
    class DEVELOPMENT(customUrl: String) : Environment(
        customUrl.let {
            require(it.isNotBlank()) { "Development URL cannot be empty" }
            // Case-insensitive: URL schemes are, and iOS accepts "HTTPS://" too. Rejecting it
            // here would make the same launch override work on one platform and not the other.
            require(it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)) {
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
