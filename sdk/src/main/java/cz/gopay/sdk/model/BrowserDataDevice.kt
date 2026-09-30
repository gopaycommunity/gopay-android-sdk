package cz.gopay.sdk.model

import android.app.Activity
import android.os.Build
import android.webkit.WebSettings
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.util.SdkLog
import java.util.Locale
import java.util.TimeZone

// Standard `Accept` header sent by a modern mobile WebView. There's no device API to read this
// back at charge time (the ACS challenge WebView doesn't exist yet), so this mirrors the
// conventional value every mainstream mobile browser/3DS SDK reports.
private const val DEFAULT_ACCEPT_HEADER =
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"

/**
 * Best-effort [BrowserData] derived from an [activity]'s configuration. Mirrors the iOS
 * `BrowserData.deviceDefault()`.
 *
 * A top-level extension on the empty companion rather than a companion member, and the only
 * `android.*` import in `model/`: the price of the call reading `BrowserData.deviceDefault(...)`
 * on both platforms, and of keeping the model class itself free of platform types. Leave it here.
 *
 * The spec requires `browser_data` on every card charge but a Google Pay payment doesn't
 * naturally surface it; the device's locale + screen + timezone are reasonable defaults.
 * `colorDepth` has no real device API on Android; 24 is the universal value every mobile
 * browser reports regardless of hardware. `javascriptEnabled` reflects that the SDK's own 3DS
 * challenge ([cz.gopay.sdk.ui.PaymentVerificationActivity]) renders in a `WebView` with
 * `settings.javaScriptEnabled = true`.
 *
 * Every field can be overridden by constructing [BrowserData] directly if you collected more
 * accurate values elsewhere.
 */
fun BrowserData.Companion.deviceDefault(activity: Activity): BrowserData {
    val resources = activity.resources
    // An empty LocaleList is rare but legal, and indexing it throws. A charge is not worth
    // losing over the language field.
    val locales = resources.configuration.locales
    val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
    val metrics = resources.displayMetrics
    // Offset at the current instant, not rawOffset — the latter ignores daylight saving and
    // would report the wrong zone for half the year, which `Date.getTimezoneOffset()` (the
    // value the issuer expects) never does.
    val tzOffsetMillis = TimeZone.getDefault().getOffset(System.currentTimeMillis())
    return BrowserData(
        language = locale.toLanguageTag(),
        timezone = jsTimezoneOffsetMinutes(tzOffsetMillis),
        screenWidth = metrics.widthPixels,
        screenHeight = metrics.heightPixels,
        colorDepth = 24,
        userAgent = webViewUserAgent(activity),
        acceptHeader = DEFAULT_ACCEPT_HEADER,
        javascriptEnabled = true
    )
}

/**
 * A zone offset in milliseconds east of UTC as `browser_data.timezone` wants it: minutes *west*
 * of UTC, the JavaScript `Date.getTimezoneOffset()` convention the issuer reads it as. CET is
 * therefore -60 and CEST -120.
 *
 * Its own function because the sign flip is the whole of the field and reading the zone is not:
 * this way the convention can be pinned by a test without a device clock in it.
 */
@VisibleForTesting
internal fun jsTimezoneOffsetMinutes(offsetMillis: Int): Int = -(offsetMillis / 60_000)

/**
 * The User-Agent the SDK's own 3DS challenge WebView will send.
 *
 * EMV 3DS carries `browser_data.user_agent` in the AReq and the issuer weighs it when deciding
 * between a frictionless approval and a challenge, so it has to look like a browser and it has
 * to match what the challenge actually sends. When the lookup fails, which means a device with
 * the WebView package disabled or a unit-test JVM, [syntheticUserAgent] stands in, and the
 * omission is logged because the value going to the issuer is then a reconstruction.
 */
private fun webViewUserAgent(activity: Activity): String =
    runCatching { WebSettings.getDefaultUserAgent(activity) }
        .getOrNull()
        ?: syntheticUserAgent().also {
            SdkLog.w(
                "WebView User-Agent could not be read; browser_data.user_agent falls back to a " +
                    "synthesized one, which the issuer may score differently"
            )
        }

/**
 * Fallback User-Agent in the shape a WebView reports, used only when the real lookup fails.
 *
 * `System.getProperty("http.agent")` is the obvious candidate and the wrong one: it is the
 * java.net client's `Dalvik/…` string, which is the very value this fix removed for not looking
 * like a browser, and it can be null, which drops the field from the payload altogether.
 *
 * The Chrome and WebKit build tokens are fixed rather than read, because the package they would
 * be read from is the one that just failed. Mirrors the iOS `syntheticUserAgent()`.
 *
 * The device facts are parameters with the platform's values as defaults, so the shape of the
 * string can be pinned by a test: on a stubbed `android.jar` every [Build] field reads as null
 * or zero, which is the one case this function must not be trusted to get right by accident.
 *
 * @param release `Build.VERSION.RELEASE`, which is empty on some builds and null off-device.
 * @param sdkInt stands in for a release the device would not name.
 * @param model `Build.MODEL`, under the same caveat as [release].
 */
@VisibleForTesting
internal fun syntheticUserAgent(
    release: String? = Build.VERSION.RELEASE,
    sdkInt: Int = Build.VERSION.SDK_INT,
    model: String? = Build.MODEL
): String {
    val version = release?.takeIf { it.isNotBlank() } ?: sdkInt.toString()
    val device = model?.takeIf { it.isNotBlank() } ?: "Android"
    return "Mozilla/5.0 (Linux; Android $version; $device) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
