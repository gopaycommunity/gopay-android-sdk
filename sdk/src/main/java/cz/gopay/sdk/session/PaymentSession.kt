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
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.SessionTokenProvider
import cz.gopay.sdk.modules.network.unwrap
import cz.gopay.sdk.service.GooglePayHelper
import cz.gopay.sdk.ui.GooglePayBridge
import cz.gopay.sdk.ui.GooglePayLauncherActivity
import cz.gopay.sdk.ui.PaymentVerificationActivity
import cz.gopay.sdk.ui.PaymentVerificationBridge
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JwtUtils
import java.util.TimeZone
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
                authApi.token(
                    authorization = Base64Utils.basicAuthHeader(paymentId, secret),
                    grantType = GRANT_TYPE_PAYMENT_CREDENTIALS,
                    scope = scope
                ).unwrap("acquire payment-scoped token")
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
        paymentApi.getPaymentStatus(paymentId).unwrap("get payment status")

    /** POST /payments/{payment_id}/charge */
    suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse =
        paymentApi.chargePayment(paymentId, request).unwrap("charge payment")

    /** GET /payments/{payment_id}/charge */
    suspend fun getChargeState(): ChargePaymentResponse =
        paymentApi.getChargeState(paymentId).unwrap("get charge state")

    /** GET /payments/{payment_id}/qr-payment/info */
    suspend fun getQrPaymentInfo(format: QrCodeFormat? = null): QrPaymentDetails =
        paymentApi.getQrPaymentInfo(paymentId, format?.name?.lowercase()).unwrap("get QR payment info")

    /** GET /payments/{payment_id}/google-pay/info */
    suspend fun getGooglePayInfo(): GooglePayInfoResponse =
        paymentApi.getGooglePayInfo(paymentId).unwrap("get Google Pay info")

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
                        browserData = browserData ?: browserDataFromActivity(activity),
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
            browserData = browserData ?: browserDataFromActivity(activity),
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
            browserData = browserData ?: browserDataFromActivity(activity),
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
     * User dismissal surfaces as [kotlinx.coroutines.CancellationException].
     */
    suspend fun handle3dsVerification(activity: Activity, redirectUrl: String) {
        val deferred = CompletableDeferred<Boolean>()
        if (!PaymentVerificationBridge.register(deferred)) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.PAYMENT_VERIFICATION_IN_PROGRESS,
                message = "A payment verification is already in progress"
            )
        }
        try {
            withContext(Dispatchers.Main) {
                activity.startActivity(
                    Intent(activity, PaymentVerificationActivity::class.java)
                        .putExtra(PaymentVerificationActivity.EXTRA_REDIRECT_URL, redirectUrl)
                )
            }
            deferred.await()
        } finally {
            PaymentVerificationBridge.clear()
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

        // Standard `Accept` header sent by a modern mobile WebView. There's no device API to
        // read this back at charge time (the ACS challenge WebView doesn't exist yet), so this
        // mirrors the conventional value every mainstream mobile browser/3DS SDK reports.
        private const val DEFAULT_ACCEPT_HEADER =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"

        /**
         * Best-effort [BrowserData] derived from an [Activity]'s configuration. The spec
         * requires `browser_data` on every card charge but a Google Pay payment doesn't
         * naturally surface it; the device's locale + screen + timezone are reasonable defaults.
         * `colorDepth` has no real device API on Android; 24 is the universal value every mobile
         * browser reports regardless of hardware. `javascriptEnabled` reflects that the SDK's own
         * 3DS challenge ([cz.gopay.sdk.ui.PaymentVerificationActivity]) renders in a `WebView`
         * with `settings.javaScriptEnabled = true`.
         */
        private fun browserDataFromActivity(activity: Activity): BrowserData {
            val resources = activity.resources
            val locale = resources.configuration.locales[0]
            val metrics = resources.displayMetrics
            // JavaScript convention: minutes west of UTC (CET = -60, CEST = -120).
            // Offset at the current instant, not rawOffset — the latter ignores daylight saving
            // and would report the wrong zone for half the year, which `Date.getTimezoneOffset()`
            // (the value the issuer expects) never does.
            val tzOffsetMinutes = -(TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000)
            return BrowserData(
                language = locale.toLanguageTag(),
                timezone = tzOffsetMinutes,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                colorDepth = 24,
                userAgent = System.getProperty("http.agent"),
                acceptHeader = DEFAULT_ACCEPT_HEADER,
                javascriptEnabled = true
            )
        }
    }
}
