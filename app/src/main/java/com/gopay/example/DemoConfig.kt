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
     *
     * Development resolves [DemoConfig.developmentBaseUrl], not the constant, so a launch-time
     * override reaches every reader — including the environment badge, which recomposes on it.
     */
    val sdkEnvironment: Environment
        get() = when (this) {
            DEVELOPMENT -> Environment.DEVELOPMENT.create(DemoConfig.developmentBaseUrl)
            SANDBOX -> Environment.SANDBOX
            PRODUCTION -> Environment.PRODUCTION
        }

    /**
     * Merchant credentials for this environment. Sandbox and production are empty here and stay
     * empty: real values reach the demo only as launch overrides, which always land in
     * [DEVELOPMENT] — run `scripts/run-demo.sh` and read the URL, not the badge. An empty
     * `clientId` / `shareableKey` / `clientSecret` fails clearly at the gateway rather than
     * silently mixing environments.
     */
    val credentials: DemoCredentials
        get() = when (this) {
            DEVELOPMENT -> DemoConfig.developmentCredentials
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

/** What [DemoConfig.applyLaunchOverrides] did with the settings a launch supplied. */
sealed interface OverrideOutcome {
    /** Nothing was supplied at launch — the default, and the only outcome on a normal start. */
    object None : OverrideOutcome

    /** The demo is now on [baseUrl], normalized to the form the SDK uses. */
    data class Applied(val baseUrl: String) : OverrideOutcome

    /**
     * The supplied base URL was unusable, so the launch was ignored in full: the gateway, and
     * every credential that arrived with it, were all dropped. See
     * [DemoConfig.applyLaunchOverrides] for why it is all-or-nothing.
     */
    data class Rejected(val reason: String) : OverrideOutcome
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
    /** Compiled-in dev gateway URL — the fallback when nothing is supplied at build or launch
     * time. Replace with your own merchant's development host. */
    const val DEVELOPMENT_BASE_URL = "https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/"

    /** Placeholders on purpose — real values live in the gitignored `.env` and arrive as launch
     * overrides. See `.env.example` and `scripts/run-demo.sh`. */
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

    /**
     * The dev gateway values actually in use. The `DEVELOPMENT_*` constants above are only the
     * compiled-in defaults; these are what every reader resolves, and what a launch-time override
     * replaces. Backed by Compose snapshot state so the environment badge follows a change.
     *
     * [developmentBaseUrl] is seeded from the `gopay.demo.baseUrl` Gradle property when the build
     * set one, otherwise from the constant. Credentials have no build-time channel on purpose —
     * see [DemoLaunchOverrides].
     */
    var developmentBaseUrl by mutableStateOf(resolveSeedBaseUrl())
        private set

    var developmentCredentials by mutableStateOf(DEVELOPMENT_CREDENTIALS)
        private set

    /**
     * The build-time seed, put through the same validation a launch-time one gets. A bad
     * `gopay.demo.baseUrl` must fall back to the constant rather than be stored as-is: the value
     * reaches `GopaySDK.initialize` from `ExampleApplication.onCreate`, so an unusable one would
     * crash the app on every launch of that build, with no way out but a rebuild.
     *
     * Validating here also means [developmentBaseUrl] always holds the SDK's normalized form
     * (trailing slash), whatever channel it arrived through.
     */
    private fun resolveSeedBaseUrl(): String {
        val seed = BuildConfig.DEMO_BASE_URL.trim()
        if (seed.isEmpty()) return DEVELOPMENT_BASE_URL
        return normalizeDemoBaseUrl(seed).getOrElse { error ->
            println("⚠️ Ignoring gopay.demo.baseUrl \"$seed\" — ${error.message}")
            DEVELOPMENT_BASE_URL
        }
    }

    /**
     * Whether [applyLaunchOverrides] has already run in this process. Lets `MainActivity` tell an
     * activity recreation (skip — a rotation must not drag a hand-picked environment back to
     * development) from a restore after process death (apply — this object is back at its
     * defaults, so the override really is gone and has to be redone).
     */
    var launchOverridesApplied = false
        private set

    /** Backed by Compose's snapshot state, so reading it from a `@Composable` (e.g. the
     * environment badge) automatically recomposes on [select]. */
    var environment by mutableStateOf(DemoEnvironment.DEVELOPMENT)
        private set

    val credentials: DemoCredentials get() = environment.credentials

    /**
     * Applies the gateway settings a launch supplied (see [DemoLaunchOverrides]) and re-points the
     * SDK at them. Called by `MainActivity` before any screen can open a payment session, so no
     * session is ever created against the environment we're about to leave.
     *
     * Overrides describe the development environment, so applying any of them also selects
     * [DemoEnvironment.DEVELOPMENT] — pointing sandbox or production at a custom host would be
     * meaningless, and the badge would then lie about which one is live.
     *
     * A launch that names a base URL is all-or-nothing: if that URL does not survive
     * [normalizeDemoBaseUrl], the whole set is dropped, credentials included. Keeping the
     * credentials would send them to whichever gateway the build happens to carry, which is
     * exactly the environment mixing every other decision here is arranged to prevent, and a
     * single typo in the URL would be enough to cause it.
     *
     * Safe to call repeatedly with the same values: once the state matches, this is a no-op and
     * the SDK is left alone.
     */
    fun applyLaunchOverrides(overrides: DemoLaunchOverrides): OverrideOutcome {
        launchOverridesApplied = true
        if (overrides.isEmpty) return OverrideOutcome.None

        val newBaseUrl = if (overrides.baseUrl != null) {
            normalizeDemoBaseUrl(overrides.baseUrl).getOrElse { error ->
                return OverrideOutcome.Rejected("GOPAY_DEMO_BASE_URL \"${overrides.baseUrl}\" — ${error.message}")
            }
        } else {
            developmentBaseUrl
        }

        val newCredentials = developmentCredentials.copy(
            clientId = overrides.clientId ?: developmentCredentials.clientId,
            shareableKey = overrides.shareableKey ?: developmentCredentials.shareableKey,
            clientSecret = overrides.clientSecret ?: developmentCredentials.clientSecret,
            goid = overrides.goid ?: developmentCredentials.goid
        )

        val alreadyApplied = newBaseUrl == developmentBaseUrl &&
            newCredentials == developmentCredentials &&
            environment == DemoEnvironment.DEVELOPMENT
        if (!alreadyApplied) {
            developmentBaseUrl = newBaseUrl
            developmentCredentials = newCredentials
            reinitialize(DemoEnvironment.DEVELOPMENT)
        }
        return OverrideOutcome.Applied(newBaseUrl)
    }

    /**
     * Closes any live payment session — otherwise it would keep talking to the old gateway, since
     * each `PaymentSession` captures its own API client at creation — then re-initializes the SDK
     * against the new environment and updates the published selection. Only reachable from
     * `MainActivity`, which never holds a session itself.
     */
    fun select(newEnvironment: DemoEnvironment) {
        if (newEnvironment == environment) return
        reinitialize(newEnvironment)
    }

    /**
     * The re-point itself, shared by [select] and [applyLaunchOverrides]. Unlike [select] it has
     * no "same environment, nothing to do" guard — an override changes the URL *within*
     * development, which still has to reach the SDK.
     */
    private fun reinitialize(newEnvironment: DemoEnvironment) {
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
