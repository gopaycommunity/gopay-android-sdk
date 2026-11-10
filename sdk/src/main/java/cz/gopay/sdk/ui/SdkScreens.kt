package cz.gopay.sdk.ui

import android.app.Activity
import android.content.Intent
import androidx.annotation.VisibleForTesting

/**
 * Starts the SDK's own screens from a screen of the host. Apart from the session so that a unit
 * test, which has no Android runtime to build an `Intent` with, can see what was started and
 * from where.
 */
internal object SdkScreens {

    /**
     * How long a screen of the SDK, the 3DS challenge or the Google Pay launcher, may take to
     * appear once it was started. The system can drop the start without a word, for an app that
     * went to the background in that instant above all, and the caller would then wait for a
     * screen that is not coming. One limit for both screens; only a test changes it.
     */
    @Volatile
    var showTimeoutMs: Long = 30_000L

    /** What both screens say when they did not appear within [showTimeoutMs]; [screen] names which. */
    fun notShown(screen: String): String =
        "$screen did not appear within ${showTimeoutMs / 1_000} s of being started"

    /**
     * Starts the 3DS challenge for `redirectUrl`, watching for `returnUrl`, from the screen, for
     * the verification `ownerId` names.
     */
    @VisibleForTesting
    @Volatile
    var startVerification: (screen: Activity, redirectUrl: String, returnUrl: String?, ownerId: Long) -> Unit =
        { screen, redirectUrl, returnUrl, ownerId ->
            screen.startActivity(
                Intent(screen, PaymentVerificationActivity::class.java)
                    .putExtra(PaymentVerificationActivity.EXTRA_REDIRECT_URL, redirectUrl)
                    .putExtra(PaymentVerificationActivity.EXTRA_RETURN_URL, returnUrl)
                    .putExtra(PaymentVerificationActivity.EXTRA_OWNER_ID, ownerId)
            )
        }

    /** Starts the Google Pay launcher for the request `ownerId` names, from the screen. */
    @VisibleForTesting
    @Volatile
    var startGooglePay: (screen: Activity, paymentRequestJson: String, environment: Int, ownerId: Long) -> Unit =
        { screen, paymentRequestJson, environment, ownerId ->
            screen.startActivity(
                Intent(screen, GooglePayLauncherActivity::class.java)
                    .putExtra(GooglePayLauncherActivity.EXTRA_PAYMENT_REQUEST_JSON, paymentRequestJson)
                    .putExtra(GooglePayLauncherActivity.EXTRA_ENVIRONMENT, environment)
                    .putExtra(GooglePayLauncherActivity.EXTRA_OWNER_ID, ownerId)
            )
        }
}
