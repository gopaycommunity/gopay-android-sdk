package com.gopay.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.locales.GopayLocales

/**
 * Which gateway environment the demo currently talks to. Selectable from `MainActivity`'s
 * environment badge; the app always starts on [DEVELOPMENT] — the choice is not persisted across
 * launches.
 */
enum class DemoEnvironment(val title: String) {
    DEVELOPMENT("Development"),
    SANDBOX("Sandbox"),
    PRODUCTION("Production");

    /**
     * The SDK [Environment] this maps to. Sandbox and production reuse the SDK's own built-in
     * hosts ([Environment.SANDBOX], [Environment.PRODUCTION]) rather than duplicating a URL here,
     * so the demo can never drift from what the SDK itself resolves.
     */
    val sdkEnvironment: Environment
        get() = when (this) {
            DEVELOPMENT -> Environment.DEVELOPMENT.create(DemoConfig.DEVELOPMENT_BASE_URL)
            SANDBOX -> Environment.SANDBOX
            PRODUCTION -> Environment.PRODUCTION
        }

    /**
     * Merchant credentials for this environment. Sandbox and production ship as empty
     * placeholders — fill them in before selecting those environments. An empty `clientId` /
     * `shareableKey` / `clientSecret` fails clearly at the gateway rather than silently mixing
     * environments.
     */
    val credentials: DemoCredentials
        get() = when (this) {
            DEVELOPMENT -> DemoConfig.DEVELOPMENT_CREDENTIALS
            SANDBOX, PRODUCTION -> DemoCredentials.PLACEHOLDER
        }
}

/**
 * Merchant credentials used by both the SDK config (`clientId`/`shareableKey`) and the simulated
 * merchant backend (`clientSecret`/`goid`, for the `client_credentials` grant that only ever runs
 * on your real server).
 */
data class DemoCredentials(
    val clientId: String,
    val shareableKey: String,
    val clientSecret: String,
    val goid: String
) {
    companion object {
        val PLACEHOLDER = DemoCredentials(clientId = "", shareableKey = "", clientSecret = "", goid = "")
    }
}

/**
 * DEMO ONLY — replace with your own merchant values.
 *
 * Holds the demo's currently-selected environment and builds the SDK config for it. This is the
 * single path both [ExampleApplication] (at launch) and the environment picker (at runtime) go
 * through, so a switch can never leave the SDK and the picker disagreeing about what's active.
 *
 * Mirrors the iOS example's `DemoConfig`.
 */
object DemoConfig {
    /** Dev gateway URL — the only per-environment value that's actually app-editable. Replace
     * with your own merchant's development host. */
    const val DEVELOPMENT_BASE_URL = "https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/"

    val DEVELOPMENT_CREDENTIALS = DemoCredentials(
        clientId = "your_client_id",
        // Public shareable key — safe to ship in the app.
        shareableKey = "your_shareable_key",
        // Merchant secret — NEVER ship this in a real app. Used only by MerchantBackendSimulator.
        clientSecret = "your_client_secret",
        goid = "8761908826"
    )

    /** Return URL intercepted by the SDK's 3DS WebView to detect flow completion. Environment-
     * independent — it's the SDK's own deep-link scheme, not a gateway host. */
    const val CHARGE_RETURN_URL = "cz.gopay.sdk://payment/return"

    /** Backed by Compose's snapshot state, so reading it from a `@Composable` (e.g. the
     * environment badge) automatically recomposes on [select]. */
    var environment by mutableStateOf(DemoEnvironment.DEVELOPMENT)
        private set

    val credentials: DemoCredentials get() = environment.credentials

    /**
     * Closes any live payment session — otherwise it would keep talking to the old gateway, since
     * each `PaymentSession` captures its own API client at creation — then re-initializes the SDK
     * against the new environment and updates the published selection. Only reachable from
     * `MainActivity`, which never holds a session itself.
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
     * (custom locale, debug flag, timeout, error callback) so a runtime switch behaves identically
     * to a cold start.
     */
    fun buildConfig(environment: DemoEnvironment): GopayConfig {
        val credentials = environment.credentials
        return GopayConfig(
            environment = environment.sdkEnvironment,
            clientId = credentials.clientId,
            shareableKey = credentials.shareableKey,
            debug = true,
            requestTimeoutMs = 5_000,
            // Register a custom locale (code "xx") the form can select alongside the built-ins,
            // and leave `locale = null` so the default follows the device language (falling back
            // to cs).
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
}
