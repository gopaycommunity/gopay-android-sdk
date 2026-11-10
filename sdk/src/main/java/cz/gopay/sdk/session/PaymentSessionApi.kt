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
 *
 * An implementation writes the members without a body and inherits the rest, the forms of 2.x
 * with the Activity first; a Java class does too, since those are JVM default methods. In Java
 * a `suspend` member takes a `Continuation` as its last parameter, as every Kotlin one does.
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
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null,
        activity: Activity? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithCardToken]. */
    suspend fun chargeWithCardToken(
        cardToken: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null,
        activity: Activity? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.chargeWithEncryptedCard]. */
    suspend fun chargeWithEncryptedCard(
        payload: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null,
        activity: Activity? = null
    ): ChargePaymentResponse

    /** See [PaymentSession.handle3dsVerification]. */
    suspend fun handle3dsVerification(redirectUrl: String, returnUrl: String? = null, activity: Activity? = null)

    // The forms of 2.x with the activity first. Each is the call above with the activity named,
    // implemented here, so an implementation of the interface has nothing to add for them, in
    // Kotlin or in Java: the module is compiled with -Xjvm-default=all, which makes each body a
    // JVM default method of the interface.

    /** [chargeWithWallet] with the [activity] first, as 2.x had it. */
    suspend fun chargeWithWallet(
        activity: Activity,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse = chargeWithWallet(browserData, challengePreference, activity)

    /** [chargeWithCardToken] with the [activity] first, as 2.x had it. */
    suspend fun chargeWithCardToken(
        activity: Activity,
        cardToken: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse = chargeWithCardToken(cardToken, browserData, challengePreference, activity)

    /** [chargeWithEncryptedCard] with the [activity] first, as 2.x had it. */
    suspend fun chargeWithEncryptedCard(
        activity: Activity,
        payload: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse = chargeWithEncryptedCard(payload, browserData, challengePreference, activity)

    /** [handle3dsVerification] with the [activity] first, as 2.x had it. */
    suspend fun handle3dsVerification(activity: Activity, redirectUrl: String, returnUrl: String? = null) =
        handle3dsVerification(redirectUrl, returnUrl, activity)

    /** See [PaymentSession.close]. */
    fun close()
}
