package cz.gopay.sdk.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

internal class PaymentVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REDIRECT_URL = "redirect_url"
        private const val RETURN_URL_SCHEME = "cz.gopay.sdk"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val redirectUrl = intent.getStringExtra(EXTRA_REDIRECT_URL) ?: run {
            PaymentVerificationBridge.cancel()
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
                    if (request.url.scheme == RETURN_URL_SCHEME) {
                        PaymentVerificationBridge.complete()
                        finish()
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    progressBar.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        PaymentVerificationBridge.cancel()
                        finish()
                    }
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
                PaymentVerificationBridge.cancel()
                finish()
            }
        })
    }
}
