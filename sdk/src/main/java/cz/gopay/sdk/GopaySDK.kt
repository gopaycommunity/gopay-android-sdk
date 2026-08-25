package cz.gopay.sdk

import android.app.Activity
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.NetworkManager
import cz.gopay.sdk.service.EncryptionService
import cz.gopay.sdk.service.GooglePayHelper
import cz.gopay.sdk.service.PublicKeyCache
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

    private val encryptionService = EncryptionService()

    /** Registry of live payment sessions keyed by `payment_id`. Supports concurrent payments. */
    private val sessions: ConcurrentHashMap<String, PaymentSession> = ConcurrentHashMap()

    private val paymentSessionFactory: PaymentSession.Factory =
        PaymentSession.Factory(
            authApi = networkManager.authApi,
            paymentApiBuilder = { provider -> networkManager.buildPaymentApi(provider) }
        )

    private val publicKeyCache: PublicKeyCache by lazy {
        PublicKeyCache(networkManager.publicApi)
    }

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
     *              (`payment:charge payment:read`). Override only when a wider scope is needed.
     */
    suspend fun startPaymentSession(
        paymentId: String,
        paymentSecret: String,
        scope: String = PaymentSession.DEFAULT_SCOPE
    ): PaymentSession {
        require(paymentId.isNotBlank()) { "paymentId must not be blank" }
        require(paymentSecret.isNotBlank()) { "paymentSecret must not be blank" }

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
        return session
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
     * Fetches the merchant's encryption JWK from `GET /cards/public-key` using the
     * `shareable_key` basic auth scheme. Requires `clientId` and `shareableKey` to be set on
     * [GopayConfig]. The key is cached in memory only.
     *
     * @param forceRefresh Bypass the in-memory cache and fetch fresh.
     */
    suspend fun getPublicEncryptionKey(forceRefresh: Boolean = false): Jwk =
        publicKeyCache.get(forceRefresh)

    /**
     * Encrypts card data into a JWE for server-side tokenization.
     *
     * The mobile SDK never calls `POST /cards/tokens` itself (that endpoint requires merchant
     * credentials). Instead, the merchant backend submits the returned JWE on the device's
     * behalf. The public key is fetched via [getPublicEncryptionKey] and cached in memory; card
     * data is never persisted.
     *
     * @return JWE compact serialization (RFC 7516) ready to send to the merchant backend.
     * @throws IllegalArgumentException if [cardData] fails basic validation.
     * @throws GopaySDKException if `clientId`/`shareableKey` are missing or the public key fetch fails.
     */
    suspend fun encryptCardData(cardData: CardData): String {
        validateCardData(cardData)
        val jwk = publicKeyCache.get()
        val clientId = requireNotNull(config.clientId) {
            "GopayConfig.clientId must be set to encrypt card data"
        }
        return encryptionService.createJweEncryptedPayload(cardData, jwk, clientId)
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

    private fun validateCardData(cardData: CardData) {
        require(cardData.cardPan.isNotBlank()) { "Card PAN cannot be empty" }
        require(cardData.cardPan.length in 13..19) { "Card PAN must be 13-19 digits" }
        require(cardData.cardPan.all { it.isDigit() }) { "Card PAN must contain only digits" }
        require(cardData.expMonth.matches(Regex("^(0[1-9]|1[0-2])$"))) { "Expiration month must be 01-12" }
        require(cardData.expYear.matches(Regex("^[0-9]{2,4}$"))) { "Expiration year must be 2-4 digits" }
        require(cardData.cvv.matches(Regex("^[0-9]{3,4}$"))) { "CVV must be 3-4 digits" }
    }

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
         * Initialize the SDK with the given configuration. Must be called before any other SDK
         * operation. The SDK no longer requires an Android `Context` — all credentials live in
         * memory inside [GopayConfig] and per-payment [PaymentSession] instances.
         */
        @JvmStatic
        fun initialize(config: GopayConfig) {
            ErrorReporter.setErrorCallback(config.errorCallback)
            GopayLocales.registerAll(config.customLocales)
            GopayLocales.setDefaultLocale(config.locale)
            instance = GopaySDK(config)
        }

        /**
         * Get the singleton instance of the SDK.
         *
         * @throws GopaySDKException if SDK hasn't been initialized
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
