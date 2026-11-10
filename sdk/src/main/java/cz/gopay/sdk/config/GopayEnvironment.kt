package cz.gopay.sdk.config

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Defines the available environments for the Gopay SDK. `GopayEnvironment` on iOS too; 2.x called
 * it `Environment`.
 */
sealed class GopayEnvironment(
    /** The address of the gateway's API, ending with a slash; `baseUrl` on iOS too. */
    val baseUrl: String
) {
    /** The 2.x name of [baseUrl]. */
    @Deprecated("Renamed to baseUrl, the name the iOS SDK uses", ReplaceWith("baseUrl"))
    val apiBaseUrl: String get() = baseUrl

    /**
     * Where the SDK's operational data goes in this environment (see the README, "Operational
     * data"). Its own host beside the gateway, never derived from [baseUrl]; null for
     * [DEVELOPMENT], which has nowhere to send it.
     */
    internal open val telemetryUrl: String? = null

    /**
     * Production environment - use for live transactions.
     *
     * The Payments 4.0 production gateway, same constant as the iOS SDK's `.production`.
     */
    object PRODUCTION : GopayEnvironment("https://gate.gopay.com/gp-gw/api/4.0/") {
        override val telemetryUrl: String = "https://lx.gopay.com/events"
        override fun toString(): String = "PRODUCTION"
    }

    /**
     * Sandbox environment - use for testing and integration.
     *
     * The Payments 4.0 sandbox gateway, same constant as the iOS SDK's `.sandbox`.
     */
    object SANDBOX : GopayEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0/") {
        override val telemetryUrl: String = "https://lx.sandbox.gopay.com/events"
        override fun toString(): String = "SANDBOX"
    }

    /**
     * Development environment - use for local development with custom endpoint.
     *
     * An empty [baseUrl], one that is not an `http(s)` address, or one that carries credentials
     * (`https://user:secret@host/`), is [GopayErrorCodes.CONFIG_INVALID_BASE_URL], the code the iOS
     * SDK reports for a base URL it
     * cannot use. A well-formed address the HTTP client still refuses, say one with a space in
     * the host, is the same code from `GopaySDK.initialize`. Thrown by the constructor, before
     * `initialize`, so it is not reported through `errorCallback`; from `initialize` it is.
     *
     * @param baseUrl The custom development endpoint URL
     * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] when [baseUrl] is
     *         empty, does not start with `http://` or `https://`, or carries credentials.
     */
    class DEVELOPMENT(baseUrl: String) : GopayEnvironment(validated(baseUrl)) {
        /** The name and the address, which the constructor has refused credentials in. */
        override fun toString(): String = "DEVELOPMENT($baseUrl)"

        companion object {
            /**
             * Creates a DEVELOPMENT environment with a custom endpoint URL.
             *
             * @param baseUrl The custom development endpoint URL
             * @return DEVELOPMENT environment with the specified URL
             * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] if the URL is
             *         empty or invalid
             */
            fun create(baseUrl: String): DEVELOPMENT = DEVELOPMENT(baseUrl)

            private fun validated(baseUrl: String): String {
                if (baseUrl.isBlank()) {
                    throw invalidBaseUrl("Development URL cannot be empty")
                }
                // Case-insensitive: URL schemes are, and iOS accepts "HTTPS://" too. Rejecting it
                // here would make the same demo base URL work on one platform and not the other.
                if (!baseUrl.startsWith("http://", ignoreCase = true) &&
                    !baseUrl.startsWith("https://", ignoreCase = true)
                ) {
                    throw invalidBaseUrl("Development URL must start with http:// or https://")
                }
                // Credentials in the address would go out with every request and into the logs,
                // and `https://gate.gopay.com@evil.example/` reads as the gateway while it is not.
                // The iOS SDK refuses the same. An address OkHttp cannot parse at all is left to
                // `GopaySDK.initialize`, which reports it.
                val parsed = baseUrl.toHttpUrlOrNull()
                if (parsed != null && (parsed.username.isNotEmpty() || parsed.password.isNotEmpty())) {
                    throw invalidBaseUrl("Development URL must not carry credentials")
                }
                return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            }

            private fun invalidBaseUrl(message: String) = GopaySDKException(
                code = GopayErrorCodes.CONFIG_INVALID_BASE_URL,
                message = message
            )
        }
    }
}

/**
 * The 2.x name of [GopayEnvironment]. It stands for the type only: Kotlin does not reach nested
 * objects through an alias, so `Environment.SANDBOX` has to become `GopayEnvironment.SANDBOX`.
 */
@Deprecated("Renamed to GopayEnvironment, the name the iOS SDK uses", ReplaceWith("GopayEnvironment"))
typealias Environment = GopayEnvironment
