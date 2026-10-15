package cz.gopay.sdk.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants

internal class GooglePayLauncherActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PAYMENT_REQUEST_JSON = "payment_request_json"
        const val EXTRA_ENVIRONMENT = "environment"
        /** [GooglePayBridge.Owner.id] of the request this launcher was started for. */
        const val EXTRA_OWNER_ID = "owner_id"
        private const val GOOGLE_PAY_REQUEST_CODE = 991
        private const val NO_OWNER = -1L
    }

    /**
     * The request this launcher serves, taken once in [onCreate]. Every report carries it, so a
     * launcher whose caller has already given up, or a sheet that answers late, cannot end the
     * payment that came after.
     */
    private lateinit var owner: GooglePayBridge.Owner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The request this launcher was started for, named in its intent, which a recreation and
        // a process restart both keep. Nothing to serve means the caller is done with it, or a
        // later payment holds the bridge now; nobody is left to report to either way. After a
        // process restart the ids start at a new random point, so an old id matches nothing.
        val ownerId = intent.getLongExtra(EXTRA_OWNER_ID, NO_OWNER)
        owner = GooglePayBridge.currentOwner(ownerId).takeIf { ownerId != NO_OWNER } ?: run {
            finish()
            return
        }

        // Back reaches the launcher only while no sheet covers it, before Google Pay shows one.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                GooglePayBridge.report(owner, GooglePayBridge.dismissed())
                finish()
            }
        })

        // A recreation, a rotation while the sheet is up above all, finds the sheet still open
        // and its result still on the way to onActivityResult. Asking again would put a second
        // sheet on top of the first.
        if (savedInstanceState != null) return
        val json = intent.getStringExtra(EXTRA_PAYMENT_REQUEST_JSON) ?: run {
            GooglePayBridge.report(
                owner,
                Result.failure(IllegalArgumentException("Missing payment request JSON"))
            )
            finish()
            return
        }
        val env = intent.getIntExtra(EXTRA_ENVIRONMENT, WalletConstants.ENVIRONMENT_TEST)
        val client = Wallet.getPaymentsClient(
            this,
            Wallet.WalletOptions.Builder().setEnvironment(env).build()
        )
        AutoResolveHelper.resolveTask(
            client.loadPaymentData(PaymentDataRequest.fromJson(json)),
            this,
            GOOGLE_PAY_REQUEST_CODE
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GOOGLE_PAY_REQUEST_CODE || !::owner.isInitialized) return
        val paymentData = data?.let { PaymentData.getFromIntent(it) }
        val status = if (resultCode == AutoResolveHelper.RESULT_ERROR) {
            AutoResolveHelper.getStatusFromIntent(data)
        } else {
            null
        }
        GooglePayBridge.report(
            owner,
            GooglePayBridge.resultFor(
                resultCode = resultCode,
                paymentDataJson = paymentData?.toJson(),
                statusCode = status?.statusCode,
                statusMessage = status?.statusMessage
            )
        )
        finish()
    }

    /**
     * Last resort for a launcher that ends without reporting, such as the task being swiped
     * away: the caller would otherwise wait for good. It reads as the user leaving. A launcher
     * that already reported reaches nothing, as does a recreation, which is not finishing.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && ::owner.isInitialized) {
            GooglePayBridge.report(owner, GooglePayBridge.dismissed())
        }
    }
}
