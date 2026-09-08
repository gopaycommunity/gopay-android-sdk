package com.gopay.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.locales.GopayLocales

/**
 * Which gateway the demo talks to. Selectable from `MainActivity`'s environment badge; the
 * selection is not persisted across launches.
 */
enum class DemoEnvironment(val title: String) {
    DEVELOPMENT("Development"),
    SANDBOX("Sandbox"),
    PRODUCTION("Production");

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
 * Holds the demo's currently-selected environment and builds the SDK config for it. Both
 * [ExampleApplication] (at launch) and the environment badge (at runtime) go through here, so the
 * SDK and the badge always agree on what is active.
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

    /** Where the badge starts: the built-in environment `gopay.demo.baseUrl` names, or Development. */
    private val initialEnvironment: DemoEnvironment = demoInitialEnvironment(developmentBaseUrl)

    /** Development is offered only when `gopay.demo.baseUrl` names a custom gateway. */
    val availableEnvironments: List<DemoEnvironment> = DemoEnvironment.entries.filter {
        it != DemoEnvironment.DEVELOPMENT || initialEnvironment == DemoEnvironment.DEVELOPMENT
    }

    /** Backed by Compose snapshot state, so the environment badge recomposes on [select]. */
    var environment by mutableStateOf(initialEnvironment)
        private set

    /**
     * Closes any live payment session, since each one captures its own API client at creation,
     * then re-initializes the SDK against [newEnvironment].
     */
    fun select(newEnvironment: DemoEnvironment) {
        if (newEnvironment == environment) return
        if (GopaySDK.isInitialized()) {
            GopaySDK.getInstance().closeAllPaymentSessions()
        }
        GopaySDK.initialize(buildConfig(newEnvironment))
        environment = newEnvironment
    }

    /**
     * Builds the SDK config for [environment], reproducing every setting from app launch exactly
     * (custom locale, debug flag, timeout, error callback).
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
