package com.gopay.example.checkout

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gopay.example.MerchantBackendSimulator
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.ui.CardEncryptionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Owns the whole checkout flow. This is the file to read if you want to see, in one place, how an
 * integration wires the SDK together:
 *
 *     create payment (your server) -> startPaymentSession -> charge(...) -> 3DS -> final state
 *
 * Every payment method funnels into the same [runCharge] tail, because 3DS and result handling are
 * identical regardless of how the card data arrived.
 *
 * Each tap of the pay button starts that sequence from the top with a brand-new payment — a
 * payment is single-use, so a retry after a decline needs a new one.
 *
 * Plain Compose state holder rather than an AAC `ViewModel`: the app module has no
 * `lifecycle-viewmodel-compose` dependency, and `SDKTestActivity` uses the same `remember`-scoped
 * pattern. State is therefore lost on configuration change, exactly like the existing console.
 */
@Stable
class CheckoutController {

    enum class Method(
        val title: String,
        val subtitle: String,
        /** One line explaining which part of the SDK this row exercises. */
        val sdkNote: String
    ) {
        CARD(
            title = "Credit or debit card",
            subtitle = "Visa, Mastercard",
            sdkNote = "PaymentCardForm collects the card and encrypts it into a JWE on device, then " +
                "chargeWithEncryptedCard() sends it. The PAN never touches this app's code."
        ),
        GOOGLE_PAY(
            title = "Google Pay",
            subtitle = "Pay with your saved Google account",
            sdkNote = "chargeWithGooglePay() presents the Google Pay sheet configured from the " +
                "gateway's /google-pay/info and charges the resulting token."
        ),
        SAVED_CARD(
            title = "Saved card",
            subtitle = "•••• 4448 · exp 12/28",
            sdkNote = "A returning customer. encryptCardData() → your server's POST /cards/tokens → " +
                "chargeWithCardToken(). Tokenization is simulated here by MerchantBackendSimulator."
        ),
        BANK_TRANSFER(
            title = "Bank transfer",
            subtitle = "QR code and account details",
            sdkNote = "getQrPaymentInfo(QrCodeFormat.PNG) returns the recipient account and QR " +
                "payloads to render for a manual transfer."
        )
    }

    data class Outcome(
        val state: ChargeState,
        val response: ChargePaymentResponse?,
        /** Populated when the flow failed before the gateway returned a charge. */
        val message: String?
    ) {
        val isSuccess: Boolean get() = state == ChargeState.SUCCEEDED
        val isFailure: Boolean get() = state == ChargeState.FAILED
        val isPending: Boolean get() = !isSuccess && !isFailure
    }

    val cart = DemoCart.sample

    var selectedMethod by mutableStateOf(Method.CARD)
    /** `null` follows the SDK/device default locale. */
    var locale by mutableStateOf<String?>(null)
    /** Non-null while a network call or a presented SDK sheet is in flight; carries the status. */
    var busyLabel by mutableStateOf<String?>(null)
    /**
     * Transient, non-fatal message: a payment window closed before anything was charged, or
     * Google Pay reported unavailable before its sheet was offered. A `PAYMENT_004` from the sheet
     * itself is a failed payment and goes to the result screen instead.
     */
    var banner by mutableStateOf<String?>(null)
    /** Inline validation / encryption error shown under the card form. */
    var cardFormError by mutableStateOf<String?>(null)
    var outcome by mutableStateOf<Outcome?>(null)
    var qrDetails by mutableStateOf<QrPaymentDetails?>(null)
        private set
    var paymentId by mutableStateOf<String?>(null)
        private set

    private var session: PaymentSession? = null

    /** The `callback.return_url` the current payment was created with, from the backend. */
    private var paymentReturnUrl: String? = null

    /** Whether the current attempt's charge has gone out, which decides what a closed window means. */
    private var chargeSent = false

    /**
     * The SDK's card form has no pay button of its own — it hands back a submit lambda through
     * `onFormReady`, which the checkout's own pay button drives.
     */
    private var submitCardForm: (suspend () -> CardEncryptionResult)? = null

    val isBusy: Boolean get() = busyLabel != null

    val payButtonTitle: String
        get() = when (selectedMethod) {
            Method.BANK_TRANSFER -> "Show transfer details"
            Method.GOOGLE_PAY -> "Pay with Google Pay"
            else -> "Pay ${cart.formatted(cart.total)}"
        }

    fun onCardFormReady(submit: suspend () -> CardEncryptionResult) {
        submitCardForm = submit
    }

    fun dismissQr() {
        qrDetails = null
    }

    // region Actions

    suspend fun pay(activity: Activity) {
        banner = null
        cardFormError = null
        chargeSent = false
        try {
            when (selectedMethod) {
                Method.CARD -> payWithNewCard(activity)
                Method.GOOGLE_PAY -> payWithGooglePay(activity)
                Method.SAVED_CARD -> payWithSavedCard(activity)
                Method.BANK_TRANSFER -> showBankTransfer()
            }
        } catch (e: CancellationException) {
            // The checkout's own scope going away, not the user: nothing left to show it on.
            throw e
        } catch (e: GopaySDKException) {
            if (e.errorCode == GopayErrorCodes.PAYMENT_CANCELLED) {
                afterClosedWindow(activity, session)
                return
            }
            fail("[${e.errorCode}] ${e.message}")
        } catch (e: Exception) {
            fail(e.message ?: "Something went wrong")
        } finally {
            busyLabel = null
        }
    }

    /** Called from the result screen's "Try another method". */
    fun resumeShopping() {
        outcome = null
        cardFormError = null
    }

    /** Called when the checkout is dismissed for good. */
    fun finish() {
        session?.close()
        session = null
        paymentId = null
        paymentReturnUrl = null
    }

    /** Re-reads the charge and resumes polling — used by the pending result screen. */
    suspend fun refreshChargeState(activity: Activity) {
        val current = session ?: return
        busyLabel = "Refreshing…"
        try {
            val charge = current.getChargeState()
            val settled = settle(activity, current, charge)
            outcome = Outcome(settled.state, settled, settled.failReason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GopaySDKException) {
            if (e.errorCode == GopayErrorCodes.PAYMENT_CANCELLED) {
                afterClosedWindow(activity, current)
            } else {
                banner = "[${e.errorCode}] ${e.message}"
            }
        } catch (e: Exception) {
            banner = e.message ?: "Something went wrong"
        } finally {
            busyLabel = null
        }
    }

    // endregion

    // region Flows

    private suspend fun payWithNewCard(activity: Activity) {
        val submit = submitCardForm ?: run {
            cardFormError = "The card form isn't ready yet."
            return
        }

        // Submit validates locally before it encrypts, so an invalid card costs no payment.
        busyLabel = "Checking your card…"
        val jwe = when (val result = submit()) {
            is CardEncryptionResult.Success -> result.jwe
            is CardEncryptionResult.Error -> {
                // An empty form right after a successful encryption is the SDK's cleanup at work
                // (GPMOB-140). The gateway accepts each JWE only once, so a retry can't replay
                // the previous one — the user has to enter the card again.
                cardFormError = if (result.message == CardEncryptionResult.NO_CARD_DATA_MESSAGE) {
                    "Please re-enter your card details and try again."
                } else {
                    result.message
                }
                return
            }
        }

        val current = startNewSession()
        busyLabel = "Authorizing…"
        runCharge(activity, current) {
            current.chargeWithEncryptedCard(
                activity = activity,
                payload = jwe,
                challengePreference = ChallengePreference.AUTO
            )
        }
    }

    private suspend fun payWithGooglePay(activity: Activity) {
        // Unlike Apple Pay's static availability check, Google Pay readiness is answered from the
        // gateway's per-payment info — so the payment has to exist before we can ask.
        val current = startNewSession()

        busyLabel = "Checking Google Pay…"
        val info = current.getGooglePayInfo()
        if (!GopaySDK.getInstance().isGooglePayAvailable(activity, info)) {
            banner = "Google Pay isn't available on this device."
            return
        }

        busyLabel = "Waiting for Google Pay…"
        runCharge(activity, current) { current.chargeWithGooglePay(activity) }
    }

    private suspend fun payWithSavedCard(activity: Activity) {
        // Stands in for a card the shopper saved on a previous order. In a real integration your
        // server holds the token; here we mint one on the fly from a known test card.
        busyLabel = "Loading saved card…"
        val jwe = GopaySDK.getInstance().encryptCardData(
            CardData(cardPan = "4444444444444448", expMonth = "12", expYear = "28", cvv = "123")
        )
        val token = MerchantBackendSimulator.tokenizeCard(jwe)

        val current = startNewSession()
        busyLabel = "Authorizing…"
        runCharge(activity, current) {
            current.chargeWithCardToken(
                activity = activity,
                cardToken = token,
                challengePreference = ChallengePreference.AUTO
            )
        }
    }

    private suspend fun showBankTransfer() {
        val current = startNewSession()
        busyLabel = "Fetching transfer details…"
        qrDetails = current.getQrPaymentInfo(QrCodeFormat.PNG)
    }

    /** Charge, then settle. Shared by every card-based method. */
    private suspend fun runCharge(
        activity: Activity,
        session: PaymentSession,
        charge: suspend () -> ChargePaymentResponse
    ) {
        val response = charge()
        chargeSent = true
        val settled = settle(activity, session, response)
        outcome = Outcome(settled.state, settled, settled.failReason)
    }

    /**
     * Drives a charge to a terminal state, exactly the way a merchant backend would: poll
     * `GET /payments/{id}/charge` on an interval until the gateway reports SUCCEEDED or FAILED.
     *
     * `ACTION_REQUIRED` is a normal step of that loop, not a failure — whenever the gateway hands
     * back a 3DS `redirectUrl` we haven't seen yet, the SDK's verification WebView is presented
     * inline and polling continues afterwards. Each redirect is run once so a gateway that keeps
     * echoing the same action can't loop the shopper through the challenge repeatedly.
     *
     * If the charge is still in flight when the budget runs out we return the last response as-is;
     * the result screen then shows "pending" with a Refresh button that re-enters this loop.
     */
    private suspend fun settle(
        activity: Activity,
        session: PaymentSession,
        initial: ChargePaymentResponse
    ): ChargePaymentResponse {
        var response = initial
        val handledRedirects = mutableSetOf<String>()
        var attempts = 0

        while (true) {
            val redirect = response.action?.redirectUrl
            if (redirect != null && handledRedirects.add(redirect)) {
                busyLabel = "Verifying with your bank…"
                // The address the payment was created with, not the charge response's, which GoPay
                // is removing.
                session.handle3dsVerification(activity, redirect, paymentReturnUrl)
            }

            when (response.state) {
                ChargeState.SUCCEEDED, ChargeState.FAILED -> return response
                else -> Unit
            }

            if (attempts >= MAX_POLL_ATTEMPTS) return response
            attempts++

            busyLabel = if (attempts == 1) "Confirming payment…" else "Still confirming… ($attempts)"
            delay(POLL_INTERVAL_MS)
            response = session.getChargeState()
        }
    }

    /**
     * Creates a *fresh* payment on the simulated merchant backend and opens a session for it.
     *
     * Called on every pay attempt, not once per checkout. A payment is single-use: once it has been
     * charged the gateway won't accept another charge on the same `payment_id`, so reusing the
     * session would leave the shopper stuck after a decline with no way to try another method. A
     * real shop behaves the same way — a retry means a new payment.
     */
    private suspend fun startNewSession(): PaymentSession {
        session?.close()
        session = null

        busyLabel = "Preparing your order…"
        val created = MerchantBackendSimulator.createPayment(
            amount = cart.total,
            currency = cart.currency
        )
        val started = GopaySDK.getInstance().startPaymentSession(
            paymentId = created.paymentId,
            paymentSecret = created.paymentSecret
        )
        session = started
        paymentId = created.paymentId
        paymentReturnUrl = created.returnUrl
        return started
    }

    /**
     * A payment window closed before it finished (`PAYMENT_011`). Closing the Google Pay sheet
     * sends no charge, so a soft banner is all it takes. Closing the 3DS challenge comes after the
     * charge went out, and the issuer may have authorised it already: offering a new payment then
     * could charge the shopper twice, so the charge decides first. A result is shown, an undecided
     * charge is polled, and only a charge the gateway does not have (404) or that did not go
     * through gets the banner. When the charge cannot be read, the pending screen offers Refresh
     * rather than a new payment. The iOS demo does the same.
     */
    private suspend fun afterClosedWindow(activity: Activity, session: PaymentSession?) {
        val cancelled = "Payment cancelled. Pick a method and try again."
        if (!chargeSent || session == null) {
            banner = cancelled
            return
        }
        busyLabel = "Checking the payment…"
        // The last charge read, which the pending screen falls back to if reading fails later.
        var lastRead: ChargePaymentResponse? = null
        try {
            val charge = session.getChargeState().also { lastRead = it }
            when (charge.state) {
                ChargeState.SUCCEEDED -> outcome = Outcome(charge.state, charge, null)
                ChargeState.REQUESTED, ChargeState.PROCESSING, ChargeState.UNKNOWN -> {
                    val settled = settle(activity, session, charge)
                    outcome = Outcome(settled.state, settled, settled.failReason)
                }
                else -> {
                    // Not through: back to the checkout, also from the pending screen's Refresh.
                    outcome = null
                    banner = cancelled
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val noCharge = e is GopaySDKException &&
                e.errorCode == GopayErrorCodes.NETWORK_CLIENT_ERROR && e.getHttpStatusCode() == 404
            if (noCharge) {
                // The gateway has no charge on this payment: nothing went out.
                outcome = null
                banner = cancelled
                return
            }
            // The charge may be through, so no new payment: the pending screen and its Refresh,
            // with the reason where the result screen shows it rather than behind it in a banner.
            val reason = if (e is GopaySDKException) "[${e.errorCode}] ${e.message}" else e.message
            outcome = Outcome(
                state = lastRead?.state ?: ChargeState.UNKNOWN,
                response = lastRead,
                message = "Couldn't check the payment: $reason"
            )
        }
    }

    private fun fail(message: String) {
        outcome = Outcome(ChargeState.FAILED, null, message)
    }

    // endregion

    private companion object {
        /** ~45 s of polling — long enough for a slow issuer, short enough to not strand the user. */
        const val POLL_INTERVAL_MS = 1_500L
        const val MAX_POLL_ATTEMPTS = 30
    }
}
