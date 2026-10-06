package cz.gopay.sdk.session

import android.app.Activity
import android.content.Intent
import com.google.android.gms.wallet.WalletConstants
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.PaymentChargeInstrument
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.model.deviceDefault
import cz.gopay.sdk.model.syntheticUserAgent
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.PublicApi
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
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

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
    /**
     * Resolved at charge time, not here: building it throws when `clientId` and `shareableKey`
     * are not configured, and that belongs to the charge it stops, not to opening the session.
     */
    private val publicApi: () -> PublicApi,
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
            val secret = paymentSecret ?: throw sessionClosed()
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

    /**
     * `POST /payments/{payment_id}/charge`.
     *
     * The gateway rejects a charge whose `browser_data` lacks `ip`, and the device cannot know
     * its own public address, so the request's [BrowserData] first goes through
     * [completeBrowserData]. Every charge on this session ends here, the wrappers included, so
     * a caller who builds the request by hand gets the same treatment. When the gateway cannot
     * be asked for the data, the charge is not sent and the failure surfaces as a charge error
     * with the underlying one as its cause.
     *
     * A closed session is refused first, with [GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED]:
     * the charge could not be sent anyway, and asking for the data before saying so would
     * report the wrong thing, [GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING] without the key.
     */
    suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse {
        if (paymentSecret == null) throw sessionClosed()
        val instrument = request.paymentInstrument
        val completed = request.copy(
            paymentInstrument = instrument.copy(
                browserData = completeBrowserData(instrument.browserData)
            )
        )
        return apiCall("charge payment") { paymentApi.chargePayment(paymentId, completed) }
    }

    /**
     * Fills in the [BrowserData] fields only the gateway can supply: `ip`, and `accept_header`
     * from the same request, which is what the issuer expects. Fetches them from
     * `GET /cards/browser-data` under the shareable key, the same authorization as the public
     * encryption key, and returns a copy with every null among them set; `javascript_enabled`
     * becomes `true` when null, because the SDK's challenge WebView runs JavaScript. `clientId`
     * and `shareableKey` therefore have to be set on [cz.gopay.sdk.config.GopayConfig] to
     * charge at all.
     *
     * The request goes out with `User-Agent` set to [BrowserData.userAgent], so the gateway
     * echoes the challenge WebView's User-Agent rather than the SDK's HTTP client, and the
     * issuer sees the same value in the AReq and in the challenge. When [browserData] carries
     * no `user_agent`, a synthesized WebView-shaped one stands in, on the request and in the
     * charge, and a warning is logged: the session holds no `Context`, so it cannot read the
     * WebView's own, and [deviceDefault] is the way to send that one. The header carries the
     * value reduced to printable ASCII, which is all the HTTP client lets through; the charge
     * keeps it as given. A value the caller set is never replaced, and when [browserData]
     * already carries `ip` and `accept_header` the gateway is not asked at all.
     *
     * [charge] calls this itself; call it directly when you want to see or log the values
     * before they go out, and pass the result to the charge unchanged.
     *
     * @throws GopaySDKException when the fetch fails, with the underlying error's code
     *         ([GopayErrorCodes.NETWORK_CLIENT_ERROR] for an HTTP error,
     *         [GopayErrorCodes.NETWORK_IO_ERROR] for a transport failure,
     *         [GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING] without the key) and the original
     *         exception as its cause. A transport failure is mapped to a code only on this
     *         step; the charge request itself lets an [IOException] through as before, like
     *         every other call on the session.
     */
    suspend fun completeBrowserData(browserData: BrowserData): BrowserData {
        val userAgent = browserData.userAgent ?: syntheticUserAgent().also {
            SdkLog.w(
                "browser_data has no user_agent, filling in a synthesized WebView User-Agent, " +
                    "which the issuer may score differently; build BrowserData with " +
                    "deviceDefault(activity) to send the WebView's own"
            )
        }
        if (browserData.ip != null && browserData.acceptHeader != null) {
            return browserData.copy(
                userAgent = userAgent,
                javascriptEnabled = browserData.javascriptEnabled ?: true
            )
        }
        val detected = fetchBrowserData(userAgent)
        return browserData.copy(
            ip = browserData.ip ?: detected.ip,
            userAgent = userAgent,
            acceptHeader = browserData.acceptHeader ?: detected.acceptHeader,
            javascriptEnabled = browserData.javascriptEnabled ?: true
        )
    }

    private suspend fun fetchBrowserData(userAgent: String): BrowserDataDetected = try {
        val api = publicApi()
        apiCall("fetch browser data") { api.getBrowserData(headerValue(userAgent)) }
    } catch (e: GopaySDKException) {
        // Charging without the address would fail on the gateway anyway, and reporting that as
        // the charge's own 400 would hide where it went wrong.
        throw GopaySDKException(
            errorCode = e.errorCode,
            message = "Failed to charge payment: browser data could not be fetched " +
                "(${e.message})",
            cause = e,
            httpContext = e.httpContext
        )
    } catch (e: IOException) {
        throw GopaySDKException(
            errorCode = GopayErrorCodes.NETWORK_IO_ERROR,
            message = "Failed to charge payment: browser data could not be fetched " +
                "(${e.message})",
            cause = e
        )
    }

    /**
     * [userAgent] as an HTTP header can carry it. OkHttp throws an `IllegalArgumentException`,
     * outside the SDK's error contract, for a header value with a character outside printable
     * ASCII, which a device model with an accent puts into the synthesized User-Agent and a
     * host can put into its own. Line breaks are dropped and the rest is replaced.
     */
    private fun headerValue(userAgent: String): String = buildString(userAgent.length) {
        for (c in userAgent) {
            when {
                c == '\r' || c == '\n' -> Unit
                c.code in 0x20..0x7E -> append(c)
                else -> append('?')
            }
        }
    }

    private fun sessionClosed() = GopaySDKException(
        errorCode = GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED,
        message = "PaymentSession for $paymentId is closed"
    )

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
     *
     * @param returnUrl the [ChargePaymentResponse.returnUrl] of the response [redirectUrl] came
     *        from: the address your backend created the payment with, as `callback.return_url`.
     *        The challenge counts as answered once the WebView navigates to an address starting
     *        with it. When it is null, blank or not an `http(s)` address with a host, the SDK
     *        waits for [cz.gopay.sdk.GopaySDK.CHARGE_RETURN_URL] instead, and then the payment
     *        has to have been created with that address. It should carry no fragment (`#…`):
     *        what the gateway appends lands in front of it, and the address no longer matches.
     */
    suspend fun handle3dsVerification(
        activity: Activity,
        redirectUrl: String,
        returnUrl: String? = null
    ) {
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
                        .putExtra(PaymentVerificationActivity.EXTRA_RETURN_URL, returnUrl)
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
     * [GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED], and [charge] throws it outright, before
     * asking the gateway for the browser data.
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
        private val paymentApiBuilder: (SessionTokenProvider) -> PaymentApi,
        private val publicApi: () -> PublicApi
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
                publicApi = publicApi,
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
