package cz.gopay.sdk.ui

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

    /** The scheme the ACS returns to when the challenge is done. */
    const val RETURN_URL_SCHEME: String = "cz.gopay.sdk"

    enum class Navigation {
        /** The return URL came back: the challenge is answered. */
        COMPLETE,

        /** Any other page; let the WebView load it. */
        LOAD
    }

    enum class LoadFailure {
        /** Not ours to report, e.g. a failure on a subresource. */
        IGNORE,

        /** The challenge never drew, so the user never got to answer it. */
        REPORT_UNREACHABLE,

        /** It broke after the challenge drew; only the charge state can say what happened. */
        CANCEL
    }

    fun navigationFor(scheme: String?): Navigation =
        if (scheme == RETURN_URL_SCHEME) Navigation.COMPLETE else Navigation.LOAD

    /** @param hasRenderedChallenge whether the challenge has drawn at least once. */
    fun failureFor(
        isForMainFrame: Boolean,
        hasRenderedChallenge: Boolean
    ): LoadFailure = when {
        !isForMainFrame -> LoadFailure.IGNORE
        hasRenderedChallenge -> LoadFailure.CANCEL
        else -> LoadFailure.REPORT_UNREACHABLE
    }
}
