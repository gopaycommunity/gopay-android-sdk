package com.gopay.example

import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.locales.GopayLocales

/**
 * Which gateway the demo talks to. Decided by `gopay.demo.baseUrl` at build time and shown by
 * `MainActivity`'s environment badge.
 */
enum class DemoEnvironment {
    DEVELOPMENT,
    SANDBOX,
    PRODUCTION;

    /** The SDK [Environment] this maps to. */
    val sdkEnvironment: Environment
        get() = when (this) {
            DEVELOPMENT -> demoBaseUrlEnvironment(DemoConfig.developmentBaseUrl)
            SANDBOX -> Environment.SANDBOX
            PRODUCTION -> Environment.PRODUCTION
        }
}

/**
 * What a `gopay.demo.baseUrl` value resolves to: blank means the SDK's own sandbox host, one of the
 * SDK's built-in gateway URLs that environment, anything else a custom gateway.
 */
internal fun demoBaseUrlEnvironment(baseUrl: String): Environment {
    val normalized = baseUrl.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
    return when (normalized) {
        "", Environment.SANDBOX.apiBaseUrl -> Environment.SANDBOX
        Environment.PRODUCTION.apiBaseUrl -> Environment.PRODUCTION
        else -> Environment.DEVELOPMENT.create(baseUrl)
    }
}

/** The badge entry the demo starts on for a `gopay.demo.baseUrl` value. */
internal fun demoInitialEnvironment(baseUrl: String): DemoEnvironment =
    when (demoBaseUrlEnvironment(baseUrl)) {
        Environment.SANDBOX -> DemoEnvironment.SANDBOX
        Environment.PRODUCTION -> DemoEnvironment.PRODUCTION
        else -> DemoEnvironment.DEVELOPMENT
    }

/**
 * Merchant values used by both the SDK config (`clientId`/`shareableKey`) and the simulated
 * merchant backend (`clientSecret`/`goid`).
 */
data class DemoCredentials(
    val clientId: String,
    val shareableKey: String,
    val clientSecret: String,
    val goid: String
)

/**
 * DEMO ONLY.
 *
 * Holds the environment the demo runs against and builds the SDK config for it. The environment
 * is decided once, by `gopay.demo.baseUrl`, and never changes while the app runs: point the demo
 * somewhere else by editing `local.properties` and launching again.
 *
 * The gateway URL and merchant values come from `local.properties` in the repo root through
 * `BuildConfig`; see `app/README.md`.
 *
 * Mirrors the iOS example's `DemoConfig`.
 */
object DemoConfig {
    /** Gateway URL from `gopay.demo.baseUrl`. Blank when the key is not set. */
    val developmentBaseUrl: String = BuildConfig.DEMO_BASE_URL

    val credentials = DemoCredentials(
        clientId = BuildConfig.DEMO_CLIENT_ID,
        shareableKey = BuildConfig.DEMO_SHAREABLE_KEY,
        // Merchant secret. Used only by MerchantBackendSimulator, never by the SDK.
        clientSecret = BuildConfig.DEMO_CLIENT_SECRET,
        goid = BuildConfig.DEMO_GOID
    )

    /** Return URL the SDK's 3DS WebView intercepts to detect flow completion. */
    const val CHARGE_RETURN_URL = "cz.gopay.sdk://payment/return"

    /** What the badge reads: the built-in environment `gopay.demo.baseUrl` names, or Development. */
    val environment: DemoEnvironment = demoInitialEnvironment(developmentBaseUrl)

    /**
     * Builds the SDK config for [environment]: the custom locale, debug flag, timeout and error
     * callback the app registers at launch.
     */
    fun buildConfig(environment: DemoEnvironment): GopayConfig = GopayConfig(
        environment = environment.sdkEnvironment,
        clientId = credentials.clientId,
        shareableKey = credentials.shareableKey,
        debug = true,
        requestTimeoutMs = 5_000,
        // Register a custom locale (code "xx") the form can select alongside the built-ins, and
        // leave `locale = null` so the default follows the device language.
        customLocales = mapOf(
            "xx" to GopayLocales.EN.copy(
                panLabel = "Yer card number",
                expLabel = "Doom date",
                cvvLabel = "Secret code"
            )
        ),
        errorCallback = { error ->
            println("Global SDK Error: [${error.errorCode}] ${error.message}")
            error.httpContext?.let { http ->
                println("HTTP: ${http.statusCode} ${http.requestMethod} ${http.requestUrl}")
                http.responseBody?.let { body -> println("Body: ${body.take(200)}…") }
            }
            error.additionalData?.let { data -> println("Additional: $data") }
        }
    )
}
