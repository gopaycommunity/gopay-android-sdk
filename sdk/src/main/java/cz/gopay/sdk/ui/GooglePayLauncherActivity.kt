package cz.gopay.sdk.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants

internal class GooglePayLauncherActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PAYMENT_REQUEST_JSON = "payment_request_json"
        const val EXTRA_ENVIRONMENT = "environment"
        private const val GOOGLE_PAY_REQUEST_CODE = 991
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = intent.getStringExtra(EXTRA_PAYMENT_REQUEST_JSON) ?: run {
            GooglePayBridge.cancel(IllegalArgumentException("Missing payment request JSON"))
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
        if (requestCode == GOOGLE_PAY_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    val pd = PaymentData.getFromIntent(data!!)
                    if (pd != null) GooglePayBridge.complete(pd.toJson())
                    else GooglePayBridge.cancel(IllegalStateException("PaymentData is null"))
                }
                RESULT_CANCELED -> GooglePayBridge.cancel()
                AutoResolveHelper.RESULT_ERROR -> {
                    val status = AutoResolveHelper.getStatusFromIntent(data)
                    GooglePayBridge.cancel(Exception("Google Pay error: ${status?.statusMessage}"))
                }
            }
            finish()
        }
    }
}
