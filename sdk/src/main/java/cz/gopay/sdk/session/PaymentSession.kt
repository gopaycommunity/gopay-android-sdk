package cz.gopay.sdk.session

import android.app.Activity
import android.content.Intent
import com.google.android.gms.wallet.WalletConstants
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.PaymentChargeInstrument
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.model.deviceDefault
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.SessionTokenProvider
import cz.gopay.sdk.modules.network.apiCall
import cz.gopay.sdk.service.GooglePayHelper
import cz.gopay.sdk.ui.GooglePayBridge
import cz.gopay.sdk.ui.GooglePayLauncherActivity
import cz.gopay.sdk.ui.PaymentVerificationActivity
import cz.gopay.sdk.ui.PaymentVerificationBridge
import cz.gopay.sdk.ui.PaymentVerificationPolicy
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JwtUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A live, per-payment authentication context.
 *
 * Created via [cz.gopay.sdk.GopaySDK.startPaymentSession] with the `payment_id` /
 * `payment_secret` pair the merchant backend issued when it created the payment. Holds the
 * secret in memory (never persisted) and exchanges it at `POST /oauth2/token` with
 * `grant_type=payment_credentials` to obtain a payment-scoped JWT. The JWT is attached to every
 * outbound request on this session's [PaymentApi]; on a 401 the session re-authenticates exactly
 * once using the cached secret.
 *
 * Sessions are concurrent-safe and independent — two PaymentSession instances for two distinct
 * payments never share credentials or tokens. Always [close] a session when the payment flow
 * terminates so the in-memory secret is wiped.
 *
 * Construction is via the suspending [Factory.create] (eager auth) so that bad credentials
 * surface at the start of the flow rather than on first API call.
 */
class PaymentSession internal constructor(
    val paymentId: String,
    private val scope: String,
    private val authApi: AuthApi,
    paymentApiBuilder: (SessionTokenProvider) -> PaymentApi,
    private val onClose: (PaymentSession) -> Unit
) : SessionTokenProvider {

    /** In-memory only; nulled by [close], which is the canonical "closed" signal. */
    @Volatile
    private var paymentSecret: String? = null

    @Volatile
    private var token: String? = null

    /** Unix seconds. 0 means "unknown" (no exp claim); checked alongside [token]. */
    @Volatile
    private var tokenExpiresAt: Long = 0

    private val authMutex = Mutex()

    private val paymentApi: PaymentApi = paymentApiBuilder(this)

    /**
     * Returns the cached JWT if it's still valid, otherwise null. Lets
     * [cz.gopay.sdk.modules.network.SessionAuthInterceptor] avoid re-parsing the JWT on every
     * request — expiry is captured once at re-auth time.
     */
    override fun currentToken(): String? {
        val t = token ?: return null
        val exp = tokenExpiresAt
        if (exp != 0L && (System.currentTimeMillis() / 1000) >= exp) return null
        return t
    }

    override fun invalidateToken() {
        token = null
        tokenExpiresAt = 0
    }

    override suspend fun reauthenticate(): String {
        return authMutex.withLock {
            // Another caller may have refreshed the token while we were waiting.
            currentToken()?.let { return@withLock it }
            val secret = paymentSecret ?: throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED,
                message = "PaymentSession for $paymentId is closed"
            )
            val response = try {
                apiCall("acquire payment-scoped token") {
                    authApi.token(
                        authorization = Base64Utils.basicAuthHeader(paymentId, secret),
                        grantType = GRANT_TYPE_PAYMENT_CREDENTIALS,
                        scope = scope
                    )
                }
            } catch (e: GopaySDKException) {
                // Retag NETWORK_CLIENT_ERROR from unwrap as AUTH_PAYMENT_CREDENTIALS_INVALID,
                // keeping the HttpErrorContext for diagnostics.
                if (e.errorCode == GopayErrorCodes.NETWORK_CLIENT_ERROR) {
                    throw GopaySDKException(
                        errorCode = GopayErrorCodes.AUTH_PAYMENT_CREDENTIALS_INVALID,
                        message = "payment_credentials rejected: HTTP ${e.httpContext?.statusCode}",
                        cause = e.cause,
                        httpContext = e.httpContext
                    )
                }
                throw e
            } catch (e: Exception) {
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_PAYMENT_CREDENTIALS_INVALID,
                    message = "Failed to acquire payment-scoped token: ${e.message}",
                    cause = e
                )
            }
            token = response.accessToken
            tokenExpiresAt = JwtUtils.expirationSecondsOrZero(response.accessToken)
            response.accessToken
        }
    }

    /** GET /payments/{payment_id} */
    suspend fun getStatus(): PaymentCreateResponse =
        apiCall("get payment status") { paymentApi.getPaymentStatus(paymentId) }

    /** POST /payments/{payment_id}/charge */
    suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse =
        apiCall("charge payment") { paymentApi.chargePayment(paymentId, request) }

    /** GET /payments/{payment_id}/charge */
    suspend fun getChargeState(): ChargePaymentResponse =
        apiCall("get charge state") { paymentApi.getChargeState(paymentId) }

    /** GET /payments/{payment_id}/qr-payment/info */
    suspend fun getQrPaymentInfo(format: QrCodeFormat? = null): QrPaymentDetails =
        apiCall("get QR payment info") { paymentApi.getQrPaymentInfo(paymentId, format?.name?.lowercase()) }

    /** GET /payments/{payment_id}/google-pay/info */
    suspend fun getGooglePayInfo(): GooglePayInfoResponse =
        apiCall("get Google Pay info") { paymentApi.getGooglePayInfo(paymentId) }

    /**
     * Managed Google Pay flow: fetches Google Pay config for this payment, launches the Google
     * Pay sheet, parses the resulting token, and submits the charge in one call.
     *
     * Suspends while the Google Pay sheet is visible. Returns the [ChargePaymentResponse] on
     * success — inspect `action.redirectUrl` and call [handle3dsVerification] if 3DS is
     * required, then [getChargeState] for the final result.
     *
     * Only one Google Pay sheet can be visible per process; if another session has one in
     * progress this throws [GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS]. User dismissal is
     * reported as [kotlinx.coroutines.CancellationException].
     *
     * @param browserData Optional 3DS browser data. Required by the spec on every card charge;
     *                    when null, the SDK derives it from the [activity]'s resources. Override
     *                    if you collected more accurate values elsewhere.
     * @param challengePreference 3DS challenge preference forwarded to the gateway.
     */
    suspend fun chargeWithGooglePay(
        activity: Activity,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null
    ): ChargePaymentResponse {
        val info = getGooglePayInfo()
        val paymentRequestJson = GooglePayHelper.buildPaymentDataRequestJson(info)
        val env = if (info.environment == "PRODUCTION") WalletConstants.ENVIRONMENT_PRODUCTION
                  else WalletConstants.ENVIRONMENT_TEST

        val deferred = CompletableDeferred<Result<String>>()
        if (!GooglePayBridge.register(deferred)) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS,
                message = "A Google Pay payment is already in progress"
            )
        }
        try {
            withContext(Dispatchers.Main) {
                activity.startActivity(
                    Intent(activity, GooglePayLauncherActivity::class.java)
                        .putExtra(GooglePayLauncherActivity.EXTRA_PAYMENT_REQUEST_JSON, paymentRequestJson)
                        .putExtra(GooglePayLauncherActivity.EXTRA_ENVIRONMENT, env)
                )
            }
            val tokenJson = deferred.await().getOrElse { cause ->
                if (cause is GooglePayBridge.UserCancelledThrowable) {
                    throw kotlinx.coroutines.CancellationException("Google Pay sheet was dismissed")
                }
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS,
                    message = "Google Pay failed: ${cause.message}",
                    cause = cause
                )
            }
            return charge(
                ChargePaymentRequest(
                    paymentInstrument = PaymentChargeInstrument(
                        input = GooglePayHelper.parseGooglePayToken(tokenJson),
                        browserData = browserData ?: BrowserData.deviceDefault(activity),
                        challengePreference = challengePreference
                    )
                )
            )
        } finally {
            GooglePayBridge.clear()
        }
    }

    /**
     * Charges a previously tokenized card (`POST /cards/tokens`). Convenience wrapper over
     * [charge] + [ChargePaymentRequest.cardToken] that fills in real device-derived [BrowserData]
     * when the caller doesn't supply one.
     *
     * @param browserData Optional 3DS browser data. Required by the spec on every card charge;
     *                    when null, the SDK derives it from the [activity]'s resources. Override
     *                    if you collected more accurate values elsewhere.
     * @param challengePreference 3DS challenge preference forwarded to the gateway.
     */
    suspend fun chargeWithCardToken(
        activity: Activity,
        cardToken: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null,
        returnUrl: String? = null
    ): ChargePaymentResponse = charge(
        ChargePaymentRequest.cardToken(
            cardToken = cardToken,
            browserData = browserData ?: BrowserData.deviceDefault(activity),
            challengePreference = challengePreference,
            returnUrl = returnUrl
        )
    )

    /**
     * Charges directly with a JWE-encrypted card, skipping the server-side `POST /cards/tokens`
     * round-trip. Convenience wrapper over [charge] + [ChargePaymentRequest.encryptedCard] that
     * fills in real device-derived [BrowserData] when the caller doesn't supply one.
     *
     * @param payload The JWE compact string from [cz.gopay.sdk.GopaySDK.encryptCardData].
     * @param browserData Optional 3DS browser data. Required by the spec on every card charge;
     *                    when null, the SDK derives it from the [activity]'s resources. Override
     *                    if you collected more accurate values elsewhere.
     * @param challengePreference 3DS challenge preference forwarded to the gateway.
     */
    suspend fun chargeWithEncryptedCard(
        activity: Activity,
        payload: String,
        browserData: BrowserData? = null,
        challengePreference: ChallengePreference? = null,
        returnUrl: String? = null
    ): ChargePaymentResponse = charge(
        ChargePaymentRequest.encryptedCard(
            payload = payload,
            browserData = browserData ?: BrowserData.deviceDefault(activity),
            challengePreference = challengePreference,
            returnUrl = returnUrl
        )
    )

    /**
     * Launches a managed WebView to complete a 3DS challenge and suspends until the user finishes
     * or cancels. Call this whenever [charge] or [getChargeState] returns a response with a
     * non-null `action.redirectUrl`. After this returns, call [getChargeState] to read the
     * final charge result.
     *
     * Only one verification can run per process at a time;
     * [GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS] is thrown if another is in flight.
     * User dismissal surfaces as [kotlinx.coroutines.CancellationException]. A challenge page
     * that never drew at all, such as a redirect URL the gateway has already retired, throws
     * [GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE] rather than reading as a dismissal, so
     * the host can tell the two apart and report the payment accordingly. Anything that goes
     * wrong after the user has seen the challenge ends as a cancellation instead, because by
     * then only [getChargeState] can say whether the payment was authorised.
     *
     * [redirectUrl] has to be an `http(s)` address; anything else, including an empty string,
     * throws [GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE] before the challenge is opened.
     */
    suspend fun handle3dsVerification(activity: Activity, redirectUrl: String) {
        // Checked here rather than in the activity: the WebView is never asked to decide about
        // the URL it is handed, so a scheme it cannot load would surface only as an error with
        // nothing to attribute it to, and the caller would wait for a challenge that never
        // opened. An empty redirect_url from the gateway lands here too.
        if (!PaymentVerificationPolicy.isLoadableChallengeUrl(redirectUrl)) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE,
                message = "3DS verification could not be loaded: the redirect URL is not an " +
                    "http(s) address the challenge WebView can open"
            )
        }
        val deferred = CompletableDeferred<Boolean>()
        val owner = PaymentVerificationBridge.register(deferred)
            ?: throw GopaySDKException(
                errorCode = GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS,
                message = "A payment verification is already in progress"
            )
        try {
            withContext(Dispatchers.Main) {
                activity.startActivity(
                    Intent(activity, PaymentVerificationActivity::class.java)
                        .putExtra(PaymentVerificationActivity.EXTRA_REDIRECT_URL, redirectUrl)
                )
            }
            deferred.await()
        } finally {
            // Scoped to this verification: the release can land after the next one has already
            // claimed the bridge, and clearing that one would strand its caller.
            PaymentVerificationBridge.clear(owner)
        }
    }

    /**
     * Wipes the in-memory `payment_secret` and JWT, and unregisters this session from the SDK
     * registry. Idempotent — once the secret is nulled, [reauthenticate] starts throwing
     * [GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED].
     */
    fun close() {
        if (paymentSecret == null) return
        paymentSecret = null
        token = null
        tokenExpiresAt = 0
        onClose(this)
    }

    internal class Factory(
        private val authApi: AuthApi,
        private val paymentApiBuilder: (SessionTokenProvider) -> PaymentApi
    ) {
        suspend fun create(
            paymentId: String,
            paymentSecret: String,
            scope: String,
            onClose: (PaymentSession) -> Unit
        ): PaymentSession {
            val session = PaymentSession(
                paymentId = paymentId,
                scope = scope,
                authApi = authApi,
                paymentApiBuilder = paymentApiBuilder,
                onClose = onClose
            )
            session.paymentSecret = paymentSecret
            // Eager auth — surface bad credentials immediately.
            session.reauthenticate()
            return session
        }
    }

    companion object {
        /**
         * Minimal scope sufficient to read a payment and charge it. Override by passing a
         * different `scope` to [cz.gopay.sdk.GopaySDK.startPaymentSession] when extra scopes
         * are required.
         */
        const val DEFAULT_SCOPE: String = "payment:charge payment:read"

        // Custom GoPay grant type for the payment-credentials flow (Payments.yaml,
        // components.schemas.Payment-Credentials-Request).
        private const val GRANT_TYPE_PAYMENT_CREDENTIALS = "payment_credentials"
    }
}
