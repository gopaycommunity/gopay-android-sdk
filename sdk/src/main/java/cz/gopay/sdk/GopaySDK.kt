package cz.gopay.sdk

import android.app.Activity
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.reportErrors
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.modules.network.NetworkManager
import cz.gopay.sdk.service.CardEncryption
import cz.gopay.sdk.service.GooglePayHelper
import cz.gopay.sdk.session.PaymentSession
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import okhttp3.CertificatePinner
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Main entry point for the Gopay SDK.
 *
 * Usage:
 * 1. `GopaySDK.initialize(GopayConfig(environment, clientId, shareableKey))` once on app start.
 * 2. Merchant backend creates a payment and returns `payment_id` + `payment_secret` to the device.
 * 3. `GopaySDK.getInstance().startPaymentSession(paymentId, paymentSecret)` — returns a
 *    [PaymentSession] scoped to that single payment.
 * 4. All charge/status/Google Pay/3DS operations run through `session.*`. Multiple sessions can
 *    run concurrently — sessions are keyed by `payment_id` and never share credentials.
 * 5. `session.close()` when done; the `payment_secret` and JWT are wiped from memory.
 *
 * Card collection: `GopaySDK.encryptCardData(cardData)` (or the `PaymentCardForm` composable)
 * returns a JWE that the host app forwards to its backend; the merchant backend calls
 * `POST /cards/tokens`. The mobile SDK never touches that endpoint.
 */
class GopaySDK private constructor(
    val config: GopayConfig,
    val sslSocketFactory: SSLSocketFactory? = null,
    val trustManager: X509TrustManager? = null,
    val certificatePinner: CertificatePinner? = null
) {

    private val networkManager =
        NetworkManager(config, sslSocketFactory, trustManager, certificatePinner)

    private val cardEncryption = CardEncryption(publicApi = { networkManager.publicApi })

    /** Registry of live payment sessions keyed by `payment_id`. Supports concurrent payments. */
    private val sessions: ConcurrentHashMap<String, PaymentSession> = ConcurrentHashMap()

    private val paymentSessionFactory: PaymentSession.Factory =
        PaymentSession.Factory(
            authApi = networkManager.authApi,
            paymentApiBuilder = { provider -> networkManager.buildPaymentApi(provider) },
            publicApi = { networkManager.publicApi }
        )

    /** Whether the SDK was initialized in debug mode. */
    fun isDebugEnabled(): Boolean = config.debug

    /**
     * Starts a payment-scoped session.
     *
     * Performs the `POST /oauth2/token` call with `grant_type=payment_credentials` and basic auth
     * `paymentId:paymentSecret` eagerly, so bad credentials surface at the start of the flow
     * rather than on first API call. The resulting JWT is held in memory only — neither the
     * secret nor the token is persisted.
     *
     * Each [paymentId] may have at most one live session at a time; call [PaymentSession.close]
     * before starting another for the same payment, or use [getPaymentSession] to reuse an
     * existing one.
     *
     * @param scope OAuth scopes to request. Defaults to [PaymentSession.DEFAULT_SCOPE]
     *              (`payment:charge payment:read`), which are the only two scopes the gateway
     *              grants for payment credentials; any other answers with
     *              [cz.gopay.sdk.exception.GopayErrorCodes.AUTH_PAYMENT_CREDENTIALS_INVALID].
     *              Pass a narrower one, e.g. `payment:read` alone, to only read the payment.
     * @throws GopaySDKException [GopayErrorCodes.VALIDATION_INVALID_INPUT] for an empty
     *         [paymentId] or [paymentSecret], before anything is sent, as on iOS.
     */
    suspend fun startPaymentSession(
        paymentId: String,
        paymentSecret: String,
        scope: String = PaymentSession.DEFAULT_SCOPE
    ): PaymentSession = reportErrors {
        if (paymentId.isBlank()) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.VALIDATION_INVALID_INPUT,
                message = "paymentId must not be empty"
            )
        }
        if (paymentSecret.isBlank()) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.VALIDATION_INVALID_INPUT,
                message = "paymentSecret must not be empty"
            )
        }

        val session = paymentSessionFactory.create(
            paymentId = paymentId,
            paymentSecret = paymentSecret,
            scope = scope,
            onClose = { sessions.remove(it.paymentId, it) }
        )
        // putIfAbsent is the only check needed — concurrent callers either land here or in close()
        // below if they raced and lost.
        val existing = sessions.putIfAbsent(paymentId, session)
        if (existing != null) {
            session.close()
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_PAYMENT_SESSION_ALREADY_EXISTS,
                message = "A PaymentSession for $paymentId already exists; close it before starting a new one."
            )
        }
        session
    }

    /**
     * Looks up an in-progress PaymentSession by `payment_id`. Returns null if none is registered
     * (never started, or already closed).
     */
    fun getPaymentSession(paymentId: String): PaymentSession? = sessions[paymentId]

    /**
     * Closes and unregisters every live PaymentSession, wiping in-memory secrets and tokens.
     * Intended for global teardown (e.g. user signs out of the host app).
     */
    fun closeAllPaymentSessions() {
        sessions.values.toList().forEach { it.close() }
    }

    /**
     * Encrypts card data into a JWE for server-side tokenization.
     *
     * The mobile SDK never calls `POST /cards/tokens` itself (that endpoint requires merchant
     * credentials). Instead, the merchant backend submits the returned JWE on the device's
     * behalf. Every call fetches the public key from `GET /cards/public-key` again, and the
     * JWE's `iat` and `exp` follow the gateway's clock, read from that response's `Date` header,
     * not the device's. Without a usable header the device clock is used, with a warning in
     * Logcat while [GopayConfig.debug] is on. Neither the key nor card data is persisted.
     *
     * @return JWE compact serialization (RFC 7516) ready to send to the merchant backend.
     * @throws GopaySDKException [GopayErrorCodes.VALIDATION_INVALID_INPUT] when [cardData] fails
     *         basic validation (PAN of 13 to 19 digits, month `01` to `12`, year of 2 to 4 digits,
     *         CVV of 3 or 4 digits), before anything is sent;
     *         [GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING] without `clientId`/`shareableKey`; a
     *         `NETWORK_*` code when the public key fetch fails;
     *         [GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR] when the key or the encryption itself
     *         fails, with the original exception as the cause. The iOS SDK throws the same codes.
     */
    suspend fun encryptCardData(cardData: CardData): String = reportErrors {
        validateCardData(cardData)
        cardEncryption.encrypt(cardData, config.clientId)
    }

    /**
     * Resolves the [GopayLocaleStrings] the payment card form uses for its labels.
     *
     * Resolution order: [preferred] (if given and known) -> the SDK-wide [GopayConfig.locale] ->
     * the device language -> Czech. Handy for reading the localized error / pay strings from host
     * code (e.g. to display inline validation messages).
     */
    fun currentLocaleStrings(preferred: String? = null): GopayLocaleStrings =
        GopayLocales.resolve(preferred)

    /** The same rules and messages as the iOS SDK's `validateCardData`. */
    private fun validateCardData(cardData: CardData) {
        if (cardData.cardPan.isBlank()) throw invalidCard("Card PAN cannot be empty")
        if (cardData.cardPan.length !in 13..19) throw invalidCard("Card PAN must be 13-19 digits")
        if (!cardData.cardPan.all { it.isDigit() }) throw invalidCard("Card PAN must contain only digits")
        if (!cardData.expMonth.matches(Regex("^(0[1-9]|1[0-2])$"))) throw invalidCard("Expiration month must be 01-12")
        if (!cardData.expYear.matches(Regex("^[0-9]{2,4}$"))) throw invalidCard("Expiration year must be 2-4 digits")
        if (!cardData.cvv.matches(Regex("^[0-9]{3,4}$"))) throw invalidCard("CVV must be 3-4 digits")
    }

    private fun invalidCard(message: String) = GopaySDKException(
        errorCode = GopayErrorCodes.VALIDATION_INVALID_INPUT,
        message = message
    )

    /**
     * Checks whether Google Pay is available and ready on this device using the allowed payment
     * methods from a GoPay API response. Call this before showing a Google Pay button.
     *
     * @param activity The current Activity (used to create a PaymentsClient).
     * @param info The [GooglePayInfoResponse] returned from [PaymentSession.getGooglePayInfo].
     */
    suspend fun isGooglePayAvailable(activity: Activity, info: GooglePayInfoResponse): Boolean {
        val env = if (info.environment == "PRODUCTION") WalletConstants.ENVIRONMENT_PRODUCTION
                  else WalletConstants.ENVIRONMENT_TEST
        val client = Wallet.getPaymentsClient(
            activity,
            Wallet.WalletOptions.Builder().setEnvironment(env).build()
        )
        val requestJson = GooglePayHelper.buildIsReadyToPayRequestJson(
            info.paymentDataRequest.allowedPaymentMethods
        )
        return suspendCoroutine { cont ->
            client.isReadyToPay(IsReadyToPayRequest.fromJson(requestJson))
                .addOnCompleteListener { task ->
                    cont.resume(task.isSuccessful && task.result == true)
                }
        }
    }

    companion object {
        @Volatile
        private var instance: GopaySDK? = null

        /**
         * The current version of the SDK, e.g. `"1.11.0"`.
         *
         * Sourced from the `sdk.version` Gradle property via `BuildConfig`, which the release
         * pipeline rewrites (`scripts/set-version.sh`). Mirrors `GopaySDK.version` on iOS so both
         * SDKs can report themselves the same way.
         */
        @JvmStatic
        val version: String get() = BuildConfig.VERSION_NAME

        /**
         * Fallback return URL for charge verification. [PaymentSession.handle3dsVerification]
         * watches for the `returnUrl` it is given: the address your backend created the payment
         * with as `callback.return_url`, which the app gets from its backend, not from the charge
         * response. Only without a usable one does it wait for this address instead, and then
         * your backend has to pass it as the payment's `callback.return_url`; the gateway
         * accepts only an absolute `http(s)` address there. The 3DS WebView intercepts any
         * navigation that starts with the address it watches for and never loads it. Mirrors
         * `GopaySDK.chargeReturnURL` on iOS.
         */
        const val CHARGE_RETURN_URL: String = "https://gopay.com/sdk/charge-return"

        /**
         * Initialize the SDK with the given configuration. Must be called before any other SDK
         * operation. The SDK no longer requires an Android `Context` — all credentials live in
         * memory inside [GopayConfig] and per-payment [PaymentSession] instances.
         *
         * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] for a base URL the
         *         HTTP client refuses, reported once to [GopayConfig.errorCallback] of [config];
         *         the SDK then stays as an earlier `initialize` left it, callback included.
         */
        @JvmStatic
        fun initialize(config: GopayConfig) {
            // Built first, so a config the SDK cannot use changes nothing: the instance, the
            // callback and the locales of an earlier initialize all stay.
            val sdk = try {
                GopaySDK(config)
            } catch (e: GopaySDKException) {
                ErrorReporter.reportTo(config.errorCallback, e)
                throw e
            }
            ErrorReporter.setErrorCallback(config.errorCallback)
            GopayLocales.registerAll(config.customLocales)
            GopayLocales.setDefaultLocale(config.locale)
            instance = sdk
        }

        /**
         * Get the singleton instance of the SDK.
         *
         * @throws GopaySDKException [GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED] if SDK hasn't
         *         been initialized. Thrown before `initialize`, when no callback is installed
         *         yet, so it is never reported through `errorCallback`.
         */
        @JvmStatic
        fun getInstance(): GopaySDK = instance ?: throw GopaySDKException(
            errorCode = GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED,
            message = "GopaySDK has not been initialized. Call GopaySDK.initialize(config) first."
        )

        /** Check if the SDK has been initialized. */
        @JvmStatic
        fun isInitialized(): Boolean = instance != null
    }
}
