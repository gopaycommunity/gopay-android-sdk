package cz.gopay.sdk.session

import android.app.Activity
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.PaymentDetails
import cz.gopay.sdk.model.PaymentOutcome
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails

/**
 * Every call an integration makes on a payment session. [PaymentSession] implements it, and
 * [cz.gopay.sdk.GopaySDK.startPaymentSession] keeps returning a [PaymentSession]; the interface
 * is there so that your own code can depend on it and take an implementation of your own in its
 * tests. The iOS SDK has the protocol of the same name.
 *
 * The response models have public constructors, [PaymentOutcome] included, so a fake can return
 * any answer, and it throws what the session throws by constructing a
 * [cz.gopay.sdk.exception.GopaySDKException] with the code.
 *
 * Each member is documented on [PaymentSession], where it is implemented. The deprecated names
 * of 2.x, `getGooglePayInfo` and `chargeWithGooglePay`, are not part of the interface.
 */
interface PaymentSessionApi {

    /** The `payment_id` the session was started for. */
    val paymentId: String

    /** See [PaymentSession.getStatus]. */
    suspend fun getStatus(): PaymentDetails

    /** See [PaymentSession.getPaymentOutcome]. */
    suspend fun getPaymentOutcome(): PaymentOutcome

    /** See [PaymentSession.charge]. */
    suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse

    /** See [PaymentSession.completeBrowserData]. */
    suspend fun completeBrowserData(browserData: BrowserData): BrowserData

    /** See [PaymentSession.getChargeState]. */
    suspend fun getChargeState(): ChargePaymentResponse

    /** See [PaymentSession.awaitChargeState]. */
    suspend fun awaitChargeState(options: AwaitChargeOptions = AwaitChargeOptions()): ChargePaymentResponse

    /** See [PaymentSession.getQrPaymentInfo]. */
    suspend fun getQrPaymentInfo(format: QrCodeFormat? = null): QrPaymentDetails

    /** See [PaymentSession.getWalletInfo]. */
    suspend fun getWalletInfo(): GooglePayInfoResponse

    /** See [PaymentSession.chargeWithWallet]. */
    suspend fun chargeWithWallet(
        activity: Activity,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithCardToken], the form that derives the browser data. */
    suspend fun chargeWithCardToken(
        activity: Activity,
        cardToken: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithCardToken], the form that takes the browser data. */
    suspend fun chargeWithCardToken(
        cardToken: String,
        browserData: BrowserData,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithEncryptedCard], the form that derives the browser data. */
    suspend fun chargeWithEncryptedCard(
        activity: Activity,
        payload: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithEncryptedCard], the form that takes the browser data. */
    suspend fun chargeWithEncryptedCard(
        payload: String,
        browserData: BrowserData,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.handle3dsVerification]. */
    suspend fun handle3dsVerification(activity: Activity, redirectUrl: String, returnUrl: String? = null)

    /** See [PaymentSession.close]. */
    fun close()
}
