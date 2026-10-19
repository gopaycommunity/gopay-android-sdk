package cz.gopay.sdk.config

import cz.gopay.sdk.exception.ErrorCallback
import cz.gopay.sdk.locales.GopayLocaleStrings

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
     * endpoints (`GET /cards/public-key`, `GET /cards/card-form-url`, `GET /cards/browser-data`)
     * authenticated with `shareable_key` basic auth. Safe to embed in the mobile app — it is
     * paired with the shareable key, never with the merchant secret.
     */
    val clientId: String? = null,

    /**
     * Merchant `shareable_key`. Required together with [clientId] for public-resource
     * endpoints, and so for charging too: a charge first fetches the browser data under it,
     * unless the [cz.gopay.sdk.model.BrowserData] passed in already carries `ip` and
     * `acceptHeader`. Without it such a charge fails with
     * [cz.gopay.sdk.exception.GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING]. The card form and
     * `encryptCardData` need it as well.
     */
    val shareableKey: String? = null,

    /**
     * Timeout for API requests in milliseconds.
     */
    val requestTimeoutMs: Long = 30000,

    /**
     * Whether to enable debug mode: SDK warnings and the HTTP traffic, bodies included, go to
     * Logcat. The `Authorization` header, `payment_secret`, the JWT, card tokens, the card JWE and
     * the Google Pay payment data are masked in that log. Keep it off in release builds.
     */
    val debug: Boolean = false,

    /**
     * Optional callback for error reporting.
     * When set, every SDK error is reported to this callback for analytics integration, except
     * [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_CANCELLED], which is not an error.
     */
    val errorCallback: ErrorCallback? = null,

    /**
     * Preferred locale code (ISO 639-1, e.g. `"cs"`, `"de"`) for the payment card form labels.
     * When `null` (default) the SDK uses the device language, falling back to Czech. A
     * [cz.gopay.sdk.ui.PaymentCardForm] `locale` parameter overrides this per form.
     */
    val locale: String? = null,

    /**
     * Custom locale translations to register with the SDK, keyed by locale code. These are
     * available to the payment card form alongside the built-in locales and take priority over a
     * built-in of the same code. See [cz.gopay.sdk.locales.GopayLocaleStrings].
     */
    val customLocales: Map<String, GopayLocaleStrings> = emptyMap()
) {
    /**
     * Get the API base URL for the current environment.
     */
    val apiBaseUrl: String
        get() = environment.apiBaseUrl
} 