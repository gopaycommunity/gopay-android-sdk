package cz.gopay.sdk.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import cz.gopay.sdk.util.SdkLog

internal class PaymentVerificationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REDIRECT_URL = "redirect_url"
        const val EXTRA_RETURN_URL = "return_url"

        /** The navigation bar scrim `enableEdgeToEdge` uses where dark icons are not possible. */
        private const val DARK_NAVIGATION_SCRIM = 0x801b1b1b.toInt()
    }

    /** The view behind the system bars, painted by [applySystemBars]. */
    private var container: View? = null

    /** Set once the challenge has actually drawn; see [reportLoadFailure]. */
    private var hasRenderedChallenge = false

    /**
     * Set once a navigation has gone out to the system. An unsupported scheme reported after
     * that belongs to the hand-off; before it, to the challenge itself. See
     * [PaymentVerificationPolicy.loadFailureFor].
     */
    private var hasHandedOffNavigation = false

    /**
     * The verification this activity serves, taken once in [onCreate].
     *
     * Every report to the bridge carries it, so that this activity finishing long after it has
     * reported cannot end whatever verification is running by then.
     */
    private lateinit var owner: PaymentVerificationBridge.Owner

    /**
     * Carries out what [PaymentVerificationPolicy] decided about a load failure.
     *
     * A failure degraded to [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_CANCELLED] is logged
     * rather than silent: to support, an HTTP 404 in the middle of a challenge would otherwise be
     * indistinguishable from a tap on Back.
     */
    private fun reportLoadFailure(failure: PaymentVerificationPolicy.LoadFailure, reason: String) {
        when (failure) {
            PaymentVerificationPolicy.LoadFailure.IGNORE -> return
            PaymentVerificationPolicy.LoadFailure.CANCEL -> {
                SdkLog.w(
                    "3DS verification failed after the challenge started rendering, reporting " +
                        "it as PAYMENT_011: $reason"
                )
                PaymentVerificationBridge.cancel(owner, "the page failed after it started: $reason")
            }
            PaymentVerificationPolicy.LoadFailure.REPORT_UNREACHABLE ->
                PaymentVerificationBridge.failUnreachable(owner, reason)
        }
        finish()
    }

    /**
     * Hands a navigation the WebView cannot load to the system, typically a banking app an ACS
     * redirected to, and deals with a device on which nothing takes it.
     *
     * The intent is built and stripped down by [PaymentVerificationPolicy.handOffIntent], so
     * what stays here is starting it and answering for the three outcomes.
     *
     * An `intent://` names the web page to use when the app is missing, and that page is the
     * whole point of the convention: it keeps the challenge answerable, so it goes back into
     * this WebView. Without one there is nothing left to try, which is logged rather than
     * passed over in silence and, while the challenge has not drawn yet, reported;
     * [PaymentVerificationPolicy.unclaimedHandOffFailure] says why only then.
     *
     * Always returns true: every outcome is decided here, and letting the WebView try the
     * navigation afterwards only produces an error nobody can attribute.
     */
    private fun handOff(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        val intent = PaymentVerificationPolicy.handOffIntent(url)
        if (intent != null && runCatching { startActivity(intent) }.isSuccess) {
            hasHandedOffNavigation = true
            return true
        }

        PaymentVerificationPolicy.fallbackUrlFor(url)?.let { fallback ->
            view.loadUrl(fallback)
            return true
        }

        val reason = "no application could open the ${request.url.scheme} link and the page " +
            "named no browser_fallback_url"
        SdkLog.w("The 3DS challenge asked for a link this device cannot open: $reason.")
        reportLoadFailure(
            PaymentVerificationPolicy.unclaimedHandOffFailure(
                isForMainFrame = request.isForMainFrame,
                hasRenderedChallenge = hasRenderedChallenge
            ),
            reason
        )
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge on every version, not only where the host's targetSdk forces it (35 and up
        // on Android 15), so the challenge lays out the same everywhere; the insets below keep it
        // clear of the bars and the keyboard, and applySystemBars makes the bars see-through.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Which verification this activity belongs to. Nothing to serve means the caller is
        // already done with it, typically a system recreation after the verification ended, and
        // there is nobody left to report to.
        owner = PaymentVerificationBridge.currentOwner() ?: run {
            finish()
            return
        }

        // A caller whose coroutine is cancelled closes the challenge it no longer waits for, and
        // one that gave up before this point leaves nothing to serve.
        if (!PaymentVerificationBridge.attach(owner) { runOnUiThread { finish() } }) {
            finish()
            return
        }

        val redirectUrl = intent.getStringExtra(EXTRA_REDIRECT_URL) ?: run {
            // The SDK never started a launch without it; this is our bug, not a dead link, and
            // the advice that goes with an unreachable challenge would send the host charging
            // again for no reason. GooglePayLauncherActivity reports the same case as a failed
            // payment, PAYMENT_PROCESSING_FAILED, which carries no such advice either.
            PaymentVerificationBridge.failInternally(owner, "no redirect URL was supplied")
            finish()
            return
        }

        // Optional: without it the policy watches for GopaySDK.CHARGE_RETURN_URL.
        val returnUrl = intent.getStringExtra(EXTRA_RETURN_URL)

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    return when (
                        PaymentVerificationPolicy.navigationFor(
                            url = url.toString(),
                            scheme = url.scheme,
                            isForMainFrame = request.isForMainFrame,
                            returnUrl = returnUrl
                        )
                    ) {
                        PaymentVerificationPolicy.Navigation.COMPLETE -> {
                            PaymentVerificationBridge.complete(owner)
                            finish()
                            true
                        }
                        PaymentVerificationPolicy.Navigation.LOAD -> false
                        // European ACS routinely hand the main frame to a banking app. A WebView
                        // cannot load those schemes, so the navigation has to go to the system;
                        // the verification stays open and the user comes back to it. Only the
                        // main frame gets here, so an embedded iframe cannot send the user out.
                        PaymentVerificationPolicy.Navigation.HAND_OFF -> handOff(view, request)
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    progressBar.visibility = View.GONE
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    hasRenderedChallenge = true
                }

                // A challenge that never loads is reported, not cancelled. The host used to
                // see the same CancellationException as a user dismissal, tick the verification
                // off as done, and let the payment lapse without ever saying why.
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    reportLoadFailure(
                        PaymentVerificationPolicy.loadFailureFor(
                            isForMainFrame = request.isForMainFrame,
                            errorCode = error.errorCode,
                            hasHandedOffNavigation = hasHandedOffNavigation,
                            hasRenderedChallenge = hasRenderedChallenge
                        ),
                        "${error.description} (${error.errorCode})"
                    )
                }

                // A dead redirect URL answers with an HTTP status, not a transport error, so it
                // never reaches onReceivedError; the challenge would sit on the gateway's 404
                // page until the user backed out of it.
                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse
                ) {
                    reportLoadFailure(
                        PaymentVerificationPolicy.failureFor(
                            isForMainFrame = request.isForMainFrame,
                            hasRenderedChallenge = hasRenderedChallenge
                        ),
                        "HTTP ${errorResponse.statusCode}"
                    )
                }
            }

            loadUrl(redirectUrl)
        }

        val container = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(progressBar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            })
        }

        // Edge to edge, the window no longer shrinks for the system bars or the keyboard, so the
        // challenge would sit under the status bar and its submit button under the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setContentView(container)
        this.container = container
        applySystemBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PaymentVerificationBridge.cancel(owner)
                finish()
            }
        })
    }

    /**
     * Makes the system bars see-through over the challenge and picks icons that read on what is
     * behind them. `androidx.activity`'s `enableEdgeToEdge` does the same, but the SDK depends on
     * an older version that lacks it, so this is done by hand.
     *
     * Below Android 15 a window that does not fit the system windows still draws the bar colors of
     * the host's theme, which can be anything, so both are cleared here. What shows through is the
     * container, painted with the theme's `colorBackground`, and the icons follow that color's
     * luminance rather than the night mode, so they stay readable on a host theme that ignores it.
     * Below Android 8 the navigation bar cannot have dark icons and keeps a dark scrim instead.
     */
    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        val container = container ?: return
        val value = TypedValue()
        val resolved = theme.resolveAttribute(android.R.attr.colorBackground, value, true) &&
            value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val background = when {
            resolved -> value.data
            night -> Color.BLACK
            else -> Color.WHITE
        }
        container.setBackgroundColor(background)
        val lightBackground = ColorUtils.calculateLuminance(background) > 0.5

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> Color.TRANSPARENT
            else -> DARK_NAVIGATION_SCRIM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars =
                lightBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        }
    }

    /**
     * `uiMode` is among the activity's `configChanges`, so a switch to or from dark mode lands here
     * rather than recreating the challenge, and the bars are worked out again.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
    }

    /**
     * Last resort for a verification that ends without reporting anything, above all the task
     * being swiped away, which kills the activity without any of the paths above running.
     *
     * The bridge holds the caller's `CompletableDeferred`, so an unreported end leaves
     * `handle3dsVerification` suspended for the life of the process and every later
     * verification failing on [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS].
     * Reporting [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_CANCELLED] is right for it: the
     * user walked away, and the charge state is the only thing that can say what happened to the
     * payment. When the caller gave up first, the bridge is already released and this reaches
     * nothing.
     *
     * Only a destruction this activity asked for reports anything. [isFinishing] is what says
     * so: a configuration change recreates the activity rather than ending it, and the system
     * reclaiming the task in the background does not end the verification either, least of all
     * as a dismissal the user never made. Both come back to a recreated activity, and the
     * redirect URL being single use is its own problem. Reporting is also owner-scoped, so an
     * onDestroy landing after a later verification has started reaches nothing.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::owner.isInitialized) {
            PaymentVerificationBridge.cancel(owner, "the activity was closed")
        }
    }
}
