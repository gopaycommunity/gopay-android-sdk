package cz.gopay.sdk.ui

import android.annotation.SuppressLint
import android.os.Bundle
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
import cz.gopay.sdk.util.SdkLog

internal class PaymentVerificationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_REDIRECT_URL = "redirect_url"
    }

    /** Set once the challenge has actually drawn; see [reportLoadFailure]. */
    private var hasRenderedChallenge = false

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
     * A failure degraded to a cancellation is logged rather than silent: to support, an HTTP
     * 404 in the middle of a challenge would otherwise be indistinguishable from a tap on Back.
     */
    private fun reportLoadFailure(failure: PaymentVerificationPolicy.LoadFailure, reason: String) {
        when (failure) {
            PaymentVerificationPolicy.LoadFailure.IGNORE -> return
            PaymentVerificationPolicy.LoadFailure.CANCEL -> {
                SdkLog.w(
                    "3DS verification failed after the challenge started rendering, reporting " +
                        "it as a cancellation: $reason"
                )
                PaymentVerificationBridge.cancel(owner)
            }
            PaymentVerificationPolicy.LoadFailure.REPORT_UNREACHABLE ->
                PaymentVerificationBridge.failUnreachable(owner, reason)
        }
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Which verification this activity belongs to. Nothing to serve means the caller is
        // already done with it, typically a system recreation after the verification ended, and
        // there is nobody left to report to.
        owner = PaymentVerificationBridge.currentOwner() ?: run {
            finish()
            return
        }

        val redirectUrl = intent.getStringExtra(EXTRA_REDIRECT_URL) ?: run {
            // The SDK never started a launch without it; this is our bug, not a dead link, and
            // the advice that goes with an unreachable challenge would send the host charging
            // again for no reason. GooglePayLauncherActivity reports the same case as its own
            // mistake too, though through a plain IllegalArgumentException rather than a code.
            PaymentVerificationBridge.failInternally(owner, "no redirect URL was supplied")
            finish()
            return
        }

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
                    return when (PaymentVerificationPolicy.navigationFor(request.url.scheme)) {
                        PaymentVerificationPolicy.Navigation.COMPLETE -> {
                            PaymentVerificationBridge.complete(owner)
                            finish()
                            true
                        }
                        PaymentVerificationPolicy.Navigation.LOAD -> false
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
                        PaymentVerificationPolicy.failureFor(
                            isForMainFrame = request.isForMainFrame,
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

        setContentView(container)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PaymentVerificationBridge.cancel(owner)
                finish()
            }
        })
    }

    /**
     * Last resort for a verification that ends without reporting anything, above all the task
     * being swiped away, which kills the activity without any of the paths above running.
     *
     * The bridge holds the caller's `CompletableDeferred`, so an unreported end leaves
     * `handle3dsVerification` suspended for the life of the process and every later
     * verification failing on [cz.gopay.sdk.exception.GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS].
     * Reporting a cancellation is right for it: the user walked away, and the charge state is
     * the only thing that can say what happened to the payment.
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
        if (isFinishing && ::owner.isInitialized) PaymentVerificationBridge.cancel(owner)
    }
}
