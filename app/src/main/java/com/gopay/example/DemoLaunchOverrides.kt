package com.gopay.example

import cz.gopay.sdk.config.Environment
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Gateway settings handed to the demo at launch, so it can be pointed at a different environment
 * — sandbox, a branch deployment, a test double — without editing code or rebuilding.
 *
 * Read from the intent that starts [MainActivity]:
 * ```
 * adb shell am start -S -n com.gopay.example/.MainActivity \
 *   -e GOPAY_DEMO_BASE_URL https://gw.example.dev.gopay.com/gp-gw/api/4.0/ \
 *   -e GOPAY_DEMO_CLIENT_ID SDK \
 *   -e GOPAY_DEMO_SHAREABLE_KEY sk_… \
 *   -e GOPAY_DEMO_CLIENT_SECRET cs_… \
 *   -e GOPAY_DEMO_GOID 8761908826
 * ```
 * `-S` matters: it force-stops first, so the extras reach a cold start. Without it `am start`
 * only brings an already-running task to the front and the app keeps the gateway it had.
 *
 * Every extra is optional and anything omitted keeps its compiled-in default, so a plain launch
 * behaves exactly as before. The base URL alone can also be set at build time via the
 * `gopay.demo.baseUrl` Gradle property (`BuildConfig.DEMO_BASE_URL`); an extra wins over that.
 *
 * Credentials are intentionally runtime-only — a Gradle property would be compiled into the APK.
 * They are still visible in the `am start` command line and in logcat, which is fine for a demo
 * app pointed at a test gateway and is not a pattern to copy into a real one.
 *
 * The extra names are exactly the iOS example's `GOPAY_DEMO_*` launch-argument names
 * (`DemoOverrides.swift`), so one set of key strings drives both demo apps.
 */
data class DemoLaunchOverrides(
    val baseUrl: String? = null,
    val clientId: String? = null,
    val shareableKey: String? = null,
    val clientSecret: String? = null,
    val goid: String? = null
) {
    /** True when the launch named nothing at all — the normal case, and a fast path for callers. */
    val isEmpty: Boolean
        get() = baseUrl == null &&
            clientId == null &&
            shareableKey == null &&
            clientSecret == null &&
            goid == null

    companion object {
        const val EXTRA_BASE_URL = "GOPAY_DEMO_BASE_URL"
        const val EXTRA_CLIENT_ID = "GOPAY_DEMO_CLIENT_ID"
        const val EXTRA_SHAREABLE_KEY = "GOPAY_DEMO_SHAREABLE_KEY"
        const val EXTRA_CLIENT_SECRET = "GOPAY_DEMO_CLIENT_SECRET"
        const val EXTRA_GOID = "GOPAY_DEMO_GOID"

        /**
         * Collects the overrides from a raw string lookup — `intent::getStringExtra` in the app, a
         * map in tests, which is why this takes a function rather than an `Intent`.
         *
         * Blank values are treated as absent: `-e GOPAY_DEMO_BASE_URL ""` (or an extra the shell mangled into
         * nothing) means "keep the default" rather than "use an empty URL", which would only fail
         * later at the SDK's own validation.
         */
        fun from(extra: (String) -> String?): DemoLaunchOverrides = DemoLaunchOverrides(
            baseUrl = extra(EXTRA_BASE_URL).sanitized(),
            clientId = extra(EXTRA_CLIENT_ID).sanitized(),
            shareableKey = extra(EXTRA_SHAREABLE_KEY).sanitized(),
            clientSecret = extra(EXTRA_CLIENT_SECRET).sanitized(),
            goid = extra(EXTRA_GOID).sanitized()
        )

        private fun String?.sanitized(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Normalizes a demo gateway URL to exactly the form the SDK will use, or fails with the reason.
 *
 * The SDK's own [Environment.DEVELOPMENT.create] is the first gate — it rejects a blank or
 * non-`http(s)` URL and appends the trailing slash the API paths are concatenated onto. It stops
 * at the scheme, though: `"https://"`, `"https://:8080/"` and `"https://host:99999/"` all sail
 * through it and then blow up much later, inside `GopaySDK.initialize`, where `NetworkManager`
 * eagerly builds a Retrofit instance. That throw takes the whole app down on launch — and on the
 * Gradle-property channel it would do so on *every* launch of that build, leaving no way out but
 * a rebuild.
 *
 * So the second gate is OkHttp's own parser, the one Retrofit will actually hand the value to.
 * Deliberately not `java.net.URI`: it misses bad ports, and it rejects perfectly usable hosts
 * that merely contain an underscore or a non-ASCII character, which internal test gateways do.
 */
internal fun normalizeDemoBaseUrl(candidate: String): Result<String> = runCatching {
    val normalized = Environment.DEVELOPMENT.create(candidate).apiBaseUrl
    val parsed = normalized.toHttpUrlOrNull()
    requireNotNull(parsed) { "not a URL OkHttp can parse" }
    // The SDK itself accepts http://, but this app's manifest sets usesCleartextTraffic="false",
    // so the platform kills the request later with a CleartextNotPermittedException that names
    // neither the override nor the manifest. Refusing here reports the real cause.
    require(parsed.isHttps) {
        "must be https:// — the demo app sets usesCleartextTraffic=\"false\", so a cleartext " +
            "gateway is blocked by the platform"
    }
    // A base URL must end in "/" for Retrofit, and `create()` appends that slash to the whole
    // string — so on a URL carrying a query or fragment the slash lands after it, and Retrofit
    // rejects the result. A gateway base URL has no business carrying either anyway.
    require(parsed.query == null && parsed.fragment == null) {
        "a gateway base URL cannot carry a query or fragment"
    }
    normalized
}
