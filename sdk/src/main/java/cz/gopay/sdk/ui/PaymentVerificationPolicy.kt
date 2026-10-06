package cz.gopay.sdk.ui

import android.content.Intent
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.GopaySDK
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder

/**
 * Every decision the 3DS challenge WebView makes, kept apart from the WebView itself.
 *
 * [PaymentVerificationActivity] builds its `WebViewClient` inside `onCreate`, and a plain JVM
 * unit test can neither construct an `Activity` nor a `WebViewClient` against the stubbed
 * `android.jar`. These rules decide whether a payment is reported as authorised, abandoned or
 * failed, so they live here where they can be pinned by tests, and the client above is left as
 * the glue that calls the bridge.
 */
internal object PaymentVerificationPolicy {

    /**
     * Schemes that stay inside the WebView; everything else belongs to some other app.
     *
     * More than `http` and `https`, because the rest are ways for a page to address itself or
     * the browser it runs in. Sending `about:`, `data:`, `blob:`, `file:` or `javascript:` out
     * to the system would ask another application to open the challenge's own content. The same
     * seven as on iOS.
     */
    private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "file", "javascript")

    /** The `intent://` extra an ACS attaches for a device with no banking app installed. */
    private const val FALLBACK_EXTRA = "S.browser_fallback_url="

    enum class Navigation {
        /** The return URL came back: the challenge is answered. */
        COMPLETE,

        /** An ordinary page of the challenge; let the WebView load it. */
        LOAD,

        /**
         * A banking app or similar; hand it to the system and keep the challenge open. Only a
         * main frame navigation ever reaches this.
         */
        HAND_OFF
    }

    enum class LoadFailure {
        /** Not ours to report, e.g. a subresource or a scheme already handed over. */
        IGNORE,

        /** The challenge never drew, so the user never got to answer it. */
        REPORT_UNREACHABLE,

        /** It broke after the challenge drew; only the charge state can say what happened. */
        CANCEL
    }

    /**
     * The address whose arrival ends the challenge: [returnUrl] in its canonical form when it is
     * usable, otherwise [GopaySDK.CHARGE_RETURN_URL].
     *
     * Usable means an `http(s)` address that still has a host once parsed. Anything less is no
     * answer at all: a blank one, a bare `https://` or one like `https://user@/r` would match
     * every page of the challenge as a prefix and close it on its first navigation as answered.
     *
     * Canonical is what `HttpUrl` makes of it, which is also how the WebView reports a
     * navigation: scheme and host in lower case, an international host in punycode, surrounding
     * whitespace trimmed, the default port dropped and the path and query percent-encoded. A
     * merchant's `HTTPS://Shop.Example/return` would otherwise never match the
     * `https://shop.example/return` the ACS comes back to. iOS applies the same conditions
     * through `URLComponents`.
     */
    fun completionUrlFor(returnUrl: String?): String =
        returnUrl?.toHttpUrlOrNull()
            ?.takeIf { it.host.isNotEmpty() }
            ?.toString()
            ?: GopaySDK.CHARGE_RETURN_URL

    /**
     * @param url the navigation as a string. A navigation that starts with the completion URL
     *        (see [completionUrlFor]) is the ACS coming back, whatever the gateway appended to
     *        it, and is answered before the scheme is looked at: the return URL is an `https`
     *        address, and letting it reach the web branch would load it as a page of the
     *        challenge. The same prefix test as on iOS.
     * @param scheme the navigation's scheme as the WebView parsed it, or null when it has none.
     * @param isForMainFrame gates the hand-off. Leaving another application out of a challenge
     *        is the page navigating itself away, which only the main frame does; an iframe the
     *        ACS embedded would otherwise be able to throw the user out of the payment on its
     *        own. A subframe is left to the WebView, which quietly declines a scheme it cannot
     *        load.
     * @param returnUrl the `return_url` the charge response carried, the address the backend
     *        created the payment with. Null, or one [completionUrlFor] cannot use, falls back to
     *        [GopaySDK.CHARGE_RETURN_URL].
     */
    fun navigationFor(
        url: String,
        scheme: String?,
        isForMainFrame: Boolean,
        returnUrl: String? = null
    ): Navigation = when {
        url.startsWith(completionUrlFor(returnUrl)) -> Navigation.COMPLETE
        scheme?.lowercase() in WEB_SCHEMES -> Navigation.LOAD
        !isForMainFrame -> Navigation.LOAD
        else -> Navigation.HAND_OFF
    }

    /**
     * Whether a redirect URL can be loaded into the challenge WebView at all.
     *
     * `shouldOverrideUrlLoading` is never called for the URL handed to `loadUrl`, so a scheme
     * the WebView cannot load has no navigation decision behind it: it surfaces as a bare
     * `ERROR_UNSUPPORTED_SCHEME` the activity cannot tell from a hand-off nobody claimed.
     * Checked before the activity starts instead, where it can be reported straight to the
     * caller rather than leaving it suspended.
     */
    fun isLoadableChallengeUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)

    /**
     * The intent an ACS hand-off is sent as, or null when the URL is not something the system
     * can be asked to open at all.
     */
    fun handOffIntent(url: String): Intent? =
        runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }
            .getOrNull()
            ?.let(::sanitizedHandOff)

    /**
     * Reduces a parsed hand-off to a browsable VIEW with nothing of the page's choosing left on
     * it.
     *
     * The URL arrives from the network, and `parseUri` happily carries an explicit component or
     * a selector out of an `intent://` string, which would turn a challenge page into a way to
     * start an arbitrary activity of the host app under flags it picked itself.
     */
    @VisibleForTesting
    internal fun sanitizedHandOff(intent: Intent): Intent = intent.apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        component = null
        selector = null
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    /**
     * The web page an `intent://` URL names for a device where nothing claims its scheme, which
     * is the convention ACS use to keep a challenge answerable without the banking app.
     *
     * Only an `http(s)` fallback is returned: it goes back into the challenge WebView, and one
     * that started another hand-off would put us where we already were.
     */
    fun fallbackUrlFor(url: String): String? {
        val fragment = url.substringAfter('#', "")
        val encoded = fragment.split(';')
            .firstOrNull { it.startsWith(FALLBACK_EXTRA) }
            ?.removePrefix(FALLBACK_EXTRA)
            ?: return null
        val decoded = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: return null
        return decoded.takeIf(::isLoadableChallengeUrl)
    }

    /**
     * @param errorCode a `WebViewClient.ERROR_*` code.
     * @param hasHandedOffNavigation whether a navigation has already gone out to the system.
     * @param hasRenderedChallenge whether the challenge has drawn at least once.
     */
    fun loadFailureFor(
        isForMainFrame: Boolean,
        errorCode: Int,
        hasHandedOffNavigation: Boolean,
        hasRenderedChallenge: Boolean
    ): LoadFailure = when {
        // A scheme the system took is not a failed challenge, and the WebView can still report
        // it here. Before any hand-off, though, an unsupported scheme is the redirect URL itself
        // failing to load: ignoring that one left the caller suspended for the life of the
        // process and every later verification failing on PAYMENT_008.
        errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME &&
            (hasHandedOffNavigation || hasRenderedChallenge) -> LoadFailure.IGNORE
        else -> failureFor(isForMainFrame, hasRenderedChallenge)
    }

    /**
     * What an unclaimed hand-off means for the verification.
     *
     * Before the challenge draws, the page the ACS sent the user to is the challenge itself, so
     * a device that cannot open it leaves the user with nothing to answer. Afterwards the user
     * is looking at a page they can still answer or back out of, and the link was as likely a
     * `tel:` or `mailto:` off the ACS page as the challenge's next step; a support number
     * nothing on a tablet can dial must not end a payment. Mirrors the iOS hand-off, which
     * reports the same case only while the challenge has not been committed.
     */
    fun unclaimedHandOffFailure(
        isForMainFrame: Boolean,
        hasRenderedChallenge: Boolean
    ): LoadFailure =
        if (isForMainFrame && !hasRenderedChallenge) LoadFailure.REPORT_UNREACHABLE
        else LoadFailure.IGNORE

    /** The rule underneath, and the whole of it for an HTTP status, which carries no code. */
    fun failureFor(
        isForMainFrame: Boolean,
        hasRenderedChallenge: Boolean
    ): LoadFailure = when {
        !isForMainFrame -> LoadFailure.IGNORE
        hasRenderedChallenge -> LoadFailure.CANCEL
        else -> LoadFailure.REPORT_UNREACHABLE
    }
}
