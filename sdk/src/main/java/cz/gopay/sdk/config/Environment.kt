package cz.gopay.sdk.config

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException

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
     * An empty [customUrl], or one that is not an `http(s)` address, is
     * [GopayErrorCodes.CONFIG_INVALID_BASE_URL], the code the iOS SDK reports for a base URL it
     * cannot use. A well-formed address the HTTP client still refuses, say one with a space in
     * the host, is the same code from `GopaySDK.initialize`.
     *
     * @param customUrl The custom development endpoint URL
     * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] when [customUrl] is
     *         empty or does not start with `http://` or `https://`.
     */
    class DEVELOPMENT(customUrl: String) : Environment(validated(customUrl)) {
        companion object {
            /**
             * Creates a DEVELOPMENT environment with a custom endpoint URL.
             *
             * @param customUrl The custom development endpoint URL
             * @return DEVELOPMENT environment with the specified URL
             * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] if the URL is
             *         empty or invalid
             */
            fun create(customUrl: String): DEVELOPMENT = DEVELOPMENT(customUrl)

            private fun validated(customUrl: String): String {
                if (customUrl.isBlank()) {
                    throw invalidBaseUrl("Development URL cannot be empty")
                }
                // Case-insensitive: URL schemes are, and iOS accepts "HTTPS://" too. Rejecting it
                // here would make the same demo base URL work on one platform and not the other.
                if (!customUrl.startsWith("http://", ignoreCase = true) &&
                    !customUrl.startsWith("https://", ignoreCase = true)
                ) {
                    throw invalidBaseUrl("Development URL must start with http:// or https://")
                }
                return if (customUrl.endsWith("/")) customUrl else "$customUrl/"
            }

            private fun invalidBaseUrl(message: String) = GopaySDKException(
                errorCode = GopayErrorCodes.CONFIG_INVALID_BASE_URL,
                message = message
            )
        }
    }
}
