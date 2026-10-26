package cz.gopay.sdk

import android.app.Activity
import androidx.annotation.VisibleForTesting
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.Wallet
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
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import cz.gopay.sdk.telemetry.Telemetry
import cz.gopay.sdk.ui.CardEncryptionResult
import cz.gopay.sdk.ui.CardFormStore
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
 * Card collection: `GopaySDK.encryptCardData(cardData)`, or the `PaymentCardForm` composable
 * submitted with [submitCardForm], returns a JWE that the host app forwards to its backend; the
 * merchant backend calls `POST /cards/tokens`. The mobile SDK never touches that endpoint.
 *
 * The names shared with the iOS SDK are the main ones: [isWalletAvailable], [submitCardForm],
 * [clearCardFormData], and on the session `getWalletInfo`, `chargeWithWallet` and the card
 * helpers without an activity. The Google Pay names stay as aliases of the same bodies.
 */
class GopaySDK private constructor(
    val config: GopayConfig,
    val sslSocketFactory: SSLSocketFactory? = null,
    val trustManager: X509TrustManager? = null,
    val certificatePinner: CertificatePinner? = null
) {

    /** This instance's operational data; null where it has nowhere to go. See [Telemetry]. */
    internal val telemetry: GwLoggerTelemetry? = Telemetry.create(config)

    private val networkManager =
        NetworkManager(config, sslSocketFactory, trustManager, certificatePinner, telemetry)

    private val cardEncryption = CardEncryption(publicApi = { networkManager.publicApi })

    /** Registry of live payment sessions keyed by `payment_id`. Supports concurrent payments. */
    private val sessions: ConcurrentHashMap<String, PaymentSession> = ConcurrentHashMap()

    private var paymentSessionFactory: PaymentSession.Factory =
        PaymentSession.Factory(
            authApi = networkManager.authApi,
            paymentApiBuilder = { provider -> networkManager.buildPaymentApi(provider) },
            publicApi = { networkManager.publicApi },
            telemetry = telemetry
        )

    /** Sessions authenticated by [factory] instead of the gateway. Tests only. */
    @VisibleForTesting
    internal fun setPaymentSessionFactoryForTest(factory: PaymentSession.Factory) {
        paymentSessionFactory = factory
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

        // Attached before the token exchange, as in the browser SDK: the token call and the error
        // of a failed start then belong to the payment they were for. Detached again on failure.
        telemetry?.attach(paymentId)
        val session = try {
            // Reports a failed start under its payment. The exception then remembers it was
            // reported (a compare-and-set on it), so the outer reportErrors adds nothing.
            reportErrors(paymentId) {
                paymentSessionFactory.create(
                    paymentId = paymentId,
                    paymentSecret = paymentSecret,
                    scope = scope,
                    onClose = {
                        // Only the registered session: a duplicate closed below never started.
                        if (sessions.remove(it.paymentId, it)) {
                            telemetry?.leave(it.paymentId)
                            telemetry?.detach(it.paymentId)
                        }
                    }
                )
            }
        } catch (e: Throwable) {
            telemetry?.detach(paymentId)
            throw e
        }
        // putIfAbsent is the only check needed — concurrent callers either land here or in close()
        // below if they raced and lost.
        val existing = sessions.putIfAbsent(paymentId, session)
        if (existing != null) {
            session.close()
            telemetry?.detach(paymentId)
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_PAYMENT_SESSION_ALREADY_EXISTS,
                message = "A PaymentSession for $paymentId already exists; close it before starting a new one."
            )
        }
        telemetry?.navigate(paymentId)
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
     * Encrypts the card the [cz.gopay.sdk.ui.PaymentCardForm] on screen holds into a JWE for
     * server-side tokenization, the way the iOS `submitCardForm(formId:)` does. The PAN and CVV
     * never leave the SDK; only the JWE is returned.
     *
     * On success the form returns to its pristine state: the SDK's copy of the card is dropped
     * **and the visible fields are emptied**, so the user sees an empty card form and your own
     * UI should move on rather than wait for further input. A failed encryption leaves
     * everything as the user typed it, since nothing has been authorized yet. A second call
     * for the same form therefore fails with [CardEncryptionResult.NO_CARD_DATA_MESSAGE] until a
     * new card is typed.
     *
     * Like [encryptCardData], every call fetches the public key afresh and dates the payload by
     * the gateway's clock. A call cancelled while the key is on its way ends with the
     * `CancellationException`, not reported through `errorCallback`, and the card stays in the
     * form. Forward the returned JWE promptly and use it for a single charge: the gateway
     * accepts each JWE only once and its payload expires 10 minutes after creation.
     *
     * In [cz.gopay.sdk.ui.CardFormValidationDisplay.ON_SUBMIT] the form shows its validation
     * errors from this call on, whether or not it passes. Safe to call from any thread: the
     * form is updated on the main thread.
     *
     * @param formId the form to read, the `formId` it was composed with. When null, the most
     *        recently active form.
     * @return JWE compact serialization (RFC 7516) ready to send to the merchant backend.
     * @throws GopaySDKException [GopayErrorCodes.VALIDATION_INVALID_INPUT] with
     *         [CardEncryptionResult.NO_CARD_DATA_MESSAGE] when no form is on screen or the form
     *         is empty, and with [CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE] when what
     *         it holds fails validation; then whatever [encryptCardData] throws.
     *         [GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED] comes from [getInstance] before.
     */
    suspend fun submitCardForm(formId: String? = null): String = reportErrors {
        cz.gopay.sdk.ui.submitCardForm(formId) { encryptCardData(it) }
    }

    /**
     * Drops the SDK's copy of the card a [cz.gopay.sdk.ui.PaymentCardForm] holds and empties
     * its visible fields, for when the user abandons the checkout while the form is still on
     * screen. [submitCardForm] calls this itself after returning a JWE, and the form when it
     * leaves the screen. Safe to call from any thread: the form is updated on the main thread.
     * Mirrors the iOS `clearCardFormData`.
     *
     * @param formId the form to clear; when null, every form.
     */
    fun clearCardFormData(formId: String? = null): Unit = CardFormStore.clear(formId)

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
     * Whether the device's wallet, Google Pay, can pay: the name shared with the iOS SDK, where
     * it asks Apple Pay. Call this before showing a Google Pay button.
     *
     * With [info], the readiness check uses the card networks and auth methods the gateway
     * allows for that payment, in the environment it names. Without one, before a payment
     * exists, it asks for `PAN_ONLY` and `CRYPTOGRAM_3DS` with Visa and Mastercard, which is
     * what the gateway allows, in the environment [GopayConfig.environment] implies:
     * `PRODUCTION` for the production gateway, `TEST` otherwise. A `false` from Google Play
     * services is returned as such; the check never throws an SDK error.
     *
     * @param activity The current Activity (used to create a PaymentsClient).
     * @param info The [GooglePayInfoResponse] from [PaymentSession.getWalletInfo], or null.
     */
    suspend fun isWalletAvailable(activity: Activity, info: GooglePayInfoResponse? = null): Boolean {
        Telemetry.noteOrigin(activity)
        val startedAt = telemetry?.now()
        val client = Wallet.getPaymentsClient(
            activity,
            Wallet.WalletOptions.Builder()
                .setEnvironment(GooglePayHelper.walletEnvironment(info, config.environment))
                .build()
        )
        val requestJson = GooglePayHelper.isReadyToPayRequestJson(info)
        return suspendCoroutine { cont ->
            client.isReadyToPay(IsReadyToPayRequest.fromJson(requestJson))
                .addOnCompleteListener { task ->
                    val available = task.isSuccessful && task.result == true
                    telemetry?.walletAvailability(
                        GwLoggerTelemetry.Method.GOOGLE_PAY,
                        caller = "isWalletAvailable",
                        checkSucceeded = task.isSuccessful,
                        // Read only after a successful check: a failed task throws on result.
                        ready = available,
                        startedAt = startedAt
                    )
                    cont.resume(available)
                }
        }
    }

    /**
     * Checks whether Google Pay is available and ready on this device using the allowed payment
     * methods from a GoPay API response. The Android name of [isWalletAvailable], the same call.
     *
     * @param activity The current Activity (used to create a PaymentsClient).
     * @param info The [GooglePayInfoResponse] returned from [PaymentSession.getGooglePayInfo].
     */
    suspend fun isGooglePayAvailable(activity: Activity, info: GooglePayInfoResponse): Boolean =
        isWalletAvailable(activity, info)

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
            Telemetry.current = sdk.telemetry
            sdk.telemetry?.init()
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
