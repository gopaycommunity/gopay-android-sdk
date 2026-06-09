package cz.gopay.sdk.config

import cz.gopay.sdk.exception.ErrorCallback

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
     * Merchant `client_id`. Required together with [shareableKey] to call the public-resource
     * endpoints (`GET /cards/public-key`, `GET /cards/card-form-url`) authenticated with
     * `shareable_key` basic auth. Safe to embed in the mobile app — it is paired with the
     * shareable key, never with the merchant secret.
     */
    val clientId: String? = null,

    /**
     * Merchant `shareable_key`. Required together with [clientId] for public-resource
     * endpoints. Optional if the SDK is only used for charging existing payments via
     * [cz.gopay.sdk.session.PaymentSession].
     */
    val shareableKey: String? = null,

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