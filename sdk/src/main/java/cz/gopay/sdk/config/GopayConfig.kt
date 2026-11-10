package cz.gopay.sdk.config

import cz.gopay.sdk.exception.ErrorCallback
import cz.gopay.sdk.locales.GopayLocaleStrings

/**
 * Configuration class for the Gopay SDK.
 * Holds all environment-specific constants and settings.
 *
 * It reads and behaves as the data class it was in 2.x, `copy`, the components, equality and
 * `toString` included, and is written out by hand for one reason: [requestTimeoutMs] is put
 * right when the config is built, which a data class cannot do, so the value read back is the
 * one the SDK uses.
 */
class GopayConfig(
    /**
     * The environment to use for the SDK.
     */
    val environment: GopayEnvironment,

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
     * Timeout for API requests in milliseconds; connecting may take half of it. It is how long
     * a request waits for the next data, on both platforms; neither limits the whole call. A
     * value that is not positive, or is past
     * `Int.MAX_VALUE`, is replaced by the default, [DEFAULT_REQUEST_TIMEOUT_MS], here, when the
     * config is built, so [requestTimeoutMs] reads back the value in use. The iOS SDK does the
     * same with its `requestTimeout`, which is in seconds there.
     */
    requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,

    /**
     * Whether the debug log is on (`debugLoggingEnabled` on iOS too; 2.x called it `debug`): SDK
     * warnings and the HTTP traffic, bodies included, go to
     * Logcat. The `Authorization` header, `payment_secret`, the JWT, card tokens, the card JWE,
     * the Google Pay payment data and the customer's e-mail and phone number are masked in that
     * log. Keep it off in release builds.
     */
    val debugLoggingEnabled: Boolean = false,

    /**
     * Optional callback for error reporting.
     * When set, every [cz.gopay.sdk.exception.GopaySDKException] the SDK throws is reported to
     * this callback once, where it leaves the SDK, for analytics integration, except
     * [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_CANCELLED] and
     * [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_TIMEOUT], which are not errors, and a
     * coroutine cancellation, which is not an SDK error. Nothing else reaches it: not an
     * exception you construct yourself. The iOS SDK follows the same rule.
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
    /** The request timeout in use, in milliseconds; see the constructor parameter. */
    val requestTimeoutMs: Long =
        if (requestTimeoutMs in 1..Int.MAX_VALUE) requestTimeoutMs else DEFAULT_REQUEST_TIMEOUT_MS

    /** The timeout that was asked for and replaced by the default, for the debug log; else null. */
    internal val replacedRequestTimeoutMs: Long? =
        requestTimeoutMs.takeIf { it != this.requestTimeoutMs }

    /**
     * The 2.x constructor, with `debug` for [debugLoggingEnabled]. A call that names `debug`
     * lands here and is told the new name. `debug` sits second, where the primary constructor
     * takes a string, so the two never compete for a call with positional arguments.
     */
    @Deprecated(
        "debug was renamed to debugLoggingEnabled, the name the iOS SDK uses",
        ReplaceWith(
            "GopayConfig(environment = environment, clientId = clientId, " +
                "shareableKey = shareableKey, requestTimeoutMs = requestTimeoutMs, " +
                "debugLoggingEnabled = debug, errorCallback = errorCallback, locale = locale, " +
                "customLocales = customLocales)"
        )
    )
    constructor(
        environment: GopayEnvironment,
        debug: Boolean,
        clientId: String? = null,
        shareableKey: String? = null,
        requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
        errorCallback: ErrorCallback? = null,
        locale: String? = null,
        customLocales: Map<String, GopayLocaleStrings> = emptyMap()
    ) : this(
        environment = environment,
        clientId = clientId,
        shareableKey = shareableKey,
        requestTimeoutMs = requestTimeoutMs,
        debugLoggingEnabled = debug,
        errorCallback = errorCallback,
        locale = locale,
        customLocales = customLocales
    )

    /** The address of the gateway's API in [environment]; `baseUrl` on iOS too. */
    val baseUrl: String
        get() = environment.baseUrl

    /** The 2.x name of [baseUrl]. */
    @Deprecated("Renamed to baseUrl, the name the iOS SDK uses", ReplaceWith("baseUrl"))
    val apiBaseUrl: String
        get() = baseUrl

    /** The 2.x name of [debugLoggingEnabled]. */
    @Deprecated(
        "Renamed to debugLoggingEnabled, the name the iOS SDK uses",
        ReplaceWith("debugLoggingEnabled")
    )
    val debug: Boolean
        get() = debugLoggingEnabled

    /** A copy with the named values replaced, as the data class of 2.x had it. */
    fun copy(
        environment: GopayEnvironment = this.environment,
        clientId: String? = this.clientId,
        shareableKey: String? = this.shareableKey,
        requestTimeoutMs: Long = this.requestTimeoutMs,
        debugLoggingEnabled: Boolean = this.debugLoggingEnabled,
        errorCallback: ErrorCallback? = this.errorCallback,
        locale: String? = this.locale,
        customLocales: Map<String, GopayLocaleStrings> = this.customLocales
    ): GopayConfig = GopayConfig(
        environment = environment,
        clientId = clientId,
        shareableKey = shareableKey,
        requestTimeoutMs = requestTimeoutMs,
        debugLoggingEnabled = debugLoggingEnabled,
        errorCallback = errorCallback,
        locale = locale,
        customLocales = customLocales
    )

    operator fun component1(): GopayEnvironment = environment
    operator fun component2(): String? = clientId
    operator fun component3(): String? = shareableKey
    operator fun component4(): Long = requestTimeoutMs
    operator fun component5(): Boolean = debugLoggingEnabled
    operator fun component6(): ErrorCallback? = errorCallback
    operator fun component7(): String? = locale
    operator fun component8(): Map<String, GopayLocaleStrings> = customLocales

    override fun equals(other: Any?): Boolean = this === other || other is GopayConfig &&
        environment == other.environment &&
        clientId == other.clientId &&
        shareableKey == other.shareableKey &&
        requestTimeoutMs == other.requestTimeoutMs &&
        debugLoggingEnabled == other.debugLoggingEnabled &&
        errorCallback == other.errorCallback &&
        locale == other.locale &&
        customLocales == other.customLocales

    override fun hashCode(): Int = listOf(
        environment, clientId, shareableKey, requestTimeoutMs, debugLoggingEnabled, errorCallback, locale,
        customLocales
    ).hashCode()

    override fun toString(): String = "GopayConfig(environment=$environment, clientId=$clientId, " +
        "shareableKey=$shareableKey, requestTimeoutMs=$requestTimeoutMs, " +
        "debugLoggingEnabled=$debugLoggingEnabled, errorCallback=$errorCallback, locale=$locale, " +
        "customLocales=$customLocales)"

    companion object {
        /** The [requestTimeoutMs] the SDK uses when none, or one it cannot use, is given. */
        const val DEFAULT_REQUEST_TIMEOUT_MS: Long = 30_000
    }
} 