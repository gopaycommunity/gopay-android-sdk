package com.gopay.example.checkout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gopay.example.MerchantBackendSimulator
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.GopayCardData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.PaymentOutcome
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.session.AwaitChargeOptions
import cz.gopay.sdk.session.PaymentSessionApi
import kotlinx.coroutines.CancellationException

/**
 * Owns the whole checkout flow. This is the file to read if you want to see, in one place, how an
 * integration wires the SDK together:
 *
 *     the checkout opens:  create payment (your server) -> startPaymentSession
 *     the shopper pays:    charge(...) -> awaitChargeState()
 *
 * The payment and its session are there from the moment the checkout opens ([open]), as on the
 * web gateway, so the SDK has the payment ready before the shopper pays. Every payment method
 * then charges that one session and funnels into the same [runCharge] tail, because 3DS and
 * result handling are identical regardless of how the card data arrived: `awaitChargeState`
 * polls the charge and runs the 3DS challenge itself.
 *
 * A payment is single-use, so a retry after a decline needs a new one. The checkout creates it
 * as soon as the decline is known, behind the result screen, so it is ready again by the time
 * the shopper picks another method. The pay button never creates a payment itself.
 *
 * A checkout left open for long is not handled here: the SDK renews the session's token as
 * needed, and a payment nobody charged expires at the gateway after an hour, after which a
 * charge fails and the shopper starts over.
 *
 * A plain state holder. [CheckoutHolder], a `ViewModel` of the checkout Activity, keeps it, so the
 * state and a payment under way survive a rotation; they do not survive the death of the process.
 */
@Stable
class CheckoutController internal constructor(private val services: CheckoutServices) {

    constructor() : this(LiveCheckoutServices)

    enum class Method(
        val title: String,
        val subtitle: String,
        /** One line explaining which part of the SDK this row exercises. */
        val sdkNote: String
    ) {
        CARD(
            title = "Credit or debit card",
            subtitle = "Visa, Mastercard",
            sdkNote = "PaymentCardForm collects the card under a formId; GopaySDK.submitCardForm(formId) " +
                "encrypts it into a JWE on device, then chargeWithEncryptedCard() sends it. The PAN " +
                "never touches this app's code."
        ),
        GOOGLE_PAY(
            title = "Google Pay",
            subtitle = "Pay with your saved Google account",
            sdkNote = "GopaySDK.isWalletAvailable() asks the device whether this row is shown. " +
                "chargeWithWallet() then reads the gateway's /google-pay/info, presents the sheet " +
                "configured from it and charges the resulting token."
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
        /** What went wrong, when something did: the decline's reason, or the error that ended the wait. */
        val message: String?,
        /** The payment this is the result of. A declined one is replaced, so the checkout's own moves on. */
        val paymentId: String,
        /** The state itself could not be read; [state] is then the last one known, if any. */
        val unread: Boolean = false
    ) {
        val isSuccess: Boolean get() = state == ChargeState.SUCCEEDED
        val isFailure: Boolean get() = state == ChargeState.FAILED
        val isPending: Boolean get() = !isSuccess && !isFailure

        /**
         * The charge went out and its state could not be read, a network error above all: not
         * the same as a charge the bank has not confirmed yet, and the result screen says so.
         */
        val isUnread: Boolean get() = isPending && unread

        /**
         * The state was read and the charge is not decided, but waiting for it ended in an
         * error, a challenge that could not be shown for one: neither unread nor simply pending.
         */
        val isUnfinished: Boolean get() = isPending && !unread && message != null
    }

    val cart = DemoCart.sample

    var selectedMethod by mutableStateOf(Method.CARD)
    /** `null` follows the SDK/device default locale. */
    var locale by mutableStateOf<String?>(null)
    /** Non-null while a network call or a presented SDK sheet is in flight; carries the status. */
    var busyLabel by mutableStateOf<String?>(null)
    /**
     * Transient, non-fatal message: a payment window closed, or anything that went wrong before
     * a charge existed, a card that could not be tokenized or a wallet sheet that failed
     * (`PAYMENT_003`, `PAYMENT_004`). The payment is still there to be paid, so there is no
     * result to show.
     */
    var banner by mutableStateOf<String?>(null)
    /** Inline validation / encryption error shown under the card form. */
    var cardFormError by mutableStateOf<String?>(null)
    var outcome by mutableStateOf<Outcome?>(null)
    var qrDetails by mutableStateOf<QrPaymentDetails?>(null)
        private set
    var paymentId by mutableStateOf<String?>(null)
        private set

    /** True while [open] creates the payment and starts its session. */
    var isOpening by mutableStateOf(false)
        private set
    /** Why [open] failed. The checkout shows it with "Try again", which calls [open] once more. */
    var openError by mutableStateOf<String?>(null)
        private set
    /** Whether the Google Pay row is offered; the device decides, when the checkout opens. */
    var isWalletAvailable by mutableStateOf(false)
        private set

    /** The session of the checkout's payment; every method charges this one. */
    private var session by mutableStateOf<PaymentSessionApi?>(null)

    /** The `callback.return_url` the current payment was created with, from the backend. */
    private var paymentReturnUrl: String? = null

    /** Whether the current attempt's charge has gone out, which decides what a closed window means. */
    private var chargeSent = false

    /** The payment was declined or given up: [replaceSpentPayment] opens a new one. */
    private var paymentIsOver = false

    /** [finish] was called: an [open] still on its way keeps no session, and a payment no result. */
    private var hasLeft = false

    /** `awaitChargeState` said the charge needs a 3DS challenge and has not read a state since. */
    private var isWaitingForChallenge by mutableStateOf(false)

    /** [lookForWallet] has asked the device. */
    private var walletLookedFor = false

    val isBusy: Boolean get() = busyLabel != null

    /** There is a payment to pay and nothing in flight. Until then the pay button stays disabled. */
    val canPay: Boolean get() = session != null && !isBusy

    /**
     * Whether the checkout may be left. Leaving closes the session, so not while a payment runs:
     * its result would have nowhere to go. Leaving while the checkout still opens is fine.
     *
     * The one running state that can be left is the wait for the bank's 3DS challenge. The
     * challenge is a screen of its own on top of the checkout, so while it is there the
     * checkout's Back cannot be reached. It can be reached only when the system destroyed that
     * screen in the background and the app lived on: the wait then never ends by itself, and
     * leaving is the only way out short of killing the app. [finish] says what leaving does.
     */
    val canLeave: Boolean get() = !isBusy || isWaitingForChallenge

    /** The methods on offer: Google Pay only where the device can pay with it. */
    val methods: List<Method>
        get() = Method.entries.filter { it != Method.GOOGLE_PAY || isWalletAvailable }

    val payButtonTitle: String
        get() = when {
            isOpening -> "Preparing your order…"
            selectedMethod == Method.BANK_TRANSFER -> "Show transfer details"
            selectedMethod == Method.GOOGLE_PAY -> "Pay with Google Pay"
            else -> "Pay ${cart.formatted(cart.total)}"
        }

    /**
     * Picks a payment method. A banner belongs to the attempt with the method before: "Payment
     * cancelled" after a closed Google Pay sheet says nothing about the card the shopper turns
     * to next, so it goes when the method changes, and so does the message under the card form.
     */
    fun select(method: Method) {
        if (method != selectedMethod) {
            banner = null
            cardFormError = null
        }
        selectedMethod = method
    }

    fun dismissQr() {
        qrDetails = null
    }

    // region Actions

    /**
     * Creates the payment on the merchant backend and starts its session. Called once when the
     * checkout opens, by [CheckoutHolder], and again by "Try again" when that failed. It opens
     * nothing while a session is there or being opened, so calling it twice costs no second payment.
     *
     * The card form can be filled in the meantime; only the pay button waits.
     */
    suspend fun open() {
        if (session != null || isOpening || hasLeft) return
        isOpening = true
        openError = null
        try {
            lookForWallet()
            val created = services.createPayment(amount = cart.total, currency = cart.currency)
            // The shopper left while the backend answered: there is nobody to open it for.
            if (hasLeft) return
            val started = services.startPaymentSession(
                paymentId = created.paymentId,
                paymentSecret = created.paymentSecret
            )
            // Or left while the session started: it is closed rather than kept by nobody.
            if (hasLeft) {
                started.close()
                return
            }
            session = started
            paymentId = created.paymentId
            paymentReturnUrl = created.returnUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            openError = "Couldn't prepare your order: ${describe(e)}"
        } finally {
            isOpening = false
        }
    }

    suspend fun pay() {
        // The label is the lock. A second tap can land before the pay button has redrawn
        // disabled, so it is taken here, before the first suspension, and a second pay() that
        // finds it held sends nothing.
        if (isBusy) return
        // No payment, no pay: the button is disabled until open() has one, and never makes one.
        val current = session ?: return
        busyLabel = "Processing…"
        banner = null
        cardFormError = null
        chargeSent = false
        try {
            when (selectedMethod) {
                Method.CARD -> payWithNewCard(current)
                Method.GOOGLE_PAY -> payWithGooglePay(current)
                Method.SAVED_CARD -> payWithSavedCard(current)
                Method.BANK_TRANSFER -> showBankTransfer(current)
            }
        } catch (e: CancellationException) {
            // The checkout's own scope going away, not the user: nothing left to show it on.
            throw e
        } catch (e: GopaySDKException) {
            when {
                // The shopper left during the wait for 3DS and the closed session ended it:
                // not an error to show on a checkout nobody is in, and nothing to read.
                hasLeft -> Unit
                // Once the charge went out, no error may lead to a new payment: the charge decides.
                chargeSent || e.code == GopayErrorCodes.PAYMENT_CANCELLED -> afterInterruptedCharge(current, e)
                // Nothing was charged, so nothing failed: the same payment waits for another try.
                else -> banner = describe(e)
            }
        } catch (e: Exception) {
            if (!hasLeft) banner = describe(e)
        } finally {
            busyLabel = null
        }
        replaceSpentPayment()
    }

    /** Called from the result screen's "Try another method". */
    fun resumeShopping() {
        outcome = null
        cardFormError = null
    }

    /**
     * Called when the checkout is dismissed for good. A payment the shopper did not pay stays
     * `CREATED` and the gateway cancels it after an hour; the merchant backend does nothing.
     *
     * Left during the wait for 3DS ([canLeave]), a payment is still running. Closing the session
     * ends its polling, and [CheckoutHolder] then cancels the coroutine, which ends a wait for a
     * challenge screen that is gone. Whatever that payment still reports is dropped: no error,
     * no result and no new payment on a checkout nobody is in. The charge itself is the
     * gateway's and the merchant backend's to settle.
     */
    fun finish() {
        hasLeft = true
        closeSession()
    }

    private fun closeSession() {
        session?.close()
        session = null
        paymentId = null
        paymentReturnUrl = null
    }

    /** Waits for the charge again — used by the pending result screen. */
    suspend fun refreshChargeState() {
        val current = session ?: return
        // Same lock as pay(): a second tap before Refresh redraws disabled waits for nothing new.
        if (isBusy) return
        busyLabel = "Refreshing…"
        try {
            outcome = settle(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GopaySDKException) {
            // The charge is out: read it, and show what went wrong on the result screen, which is
            // the one on screen, rather than in the checkout's banner behind it.
            if (!hasLeft) afterInterruptedCharge(current, e)
        } catch (e: Exception) {
            if (!hasLeft) outcome = unread(current, outcome?.response, e)
        } finally {
            busyLabel = null
        }
        replaceSpentPayment()
    }

    // endregion

    // region Flows

    private suspend fun payWithNewCard(current: PaymentSessionApi) {
        // The SDK's card form has no pay button of its own: it keeps the card under CARD_FORM_ID
        // and the checkout's pay button asks the SDK to encrypt it, the same call as on iOS.
        // Submit validates locally before it encrypts, so an invalid card is never sent.
        busyLabel = "Checking your card…"
        val jwe = try {
            services.submitCardForm(CARD_FORM_ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GopaySDKException) {
            // An empty form has a code of its own. It is what a second tap finds after a
            // successful encryption emptied the fields: the gateway accepts each JWE only once, so
            // a retry needs the card typed again. A card that fails validation (the form shows
            // which field, validation = LIVE) or a key fetch that failed is shown under the form.
            cardFormError = if (e.code == GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY) {
                "Please enter your card details and try again."
            } else {
                e.message
            }
            return
        }

        busyLabel = "Authorizing…"
        runCharge(current) {
            current.chargeWithEncryptedCard(
                payload = jwe,
                challengePreference = ChallengePreference.AUTO
            )
        }
    }

    private suspend fun payWithGooglePay(current: PaymentSessionApi) {
        // The device said it can pay with Google Pay, which is why this row is on offer. The
        // gateway's info for this payment is asked only now that the shopper chose the wallet,
        // by chargeWithWallet itself, which configures the sheet from it. When the gateway
        // refuses that (a 4xx), nothing was charged: pay() shows it in the banner and the
        // payment stays. A 5xx or an answer that never came looks the same from here as a
        // charge call that ended so, and the checkout cannot tell which of the two calls it
        // was. So it goes the careful way of runCharge: the payment is read first, and only
        // when that says nothing was charged does the banner follow.
        busyLabel = "Waiting for Google Pay…"
        runCharge(current) { current.chargeWithWallet() }
    }

    private suspend fun payWithSavedCard(current: PaymentSessionApi) {
        // Stands in for a card the shopper saved on a previous order. In a real integration your
        // server holds the token; here we mint one on the fly from a known test card.
        busyLabel = "Loading saved card…"
        val token = services.savedCardToken()

        busyLabel = "Authorizing…"
        runCharge(current) {
            current.chargeWithCardToken(
                cardToken = token,
                challengePreference = ChallengePreference.AUTO
            )
        }
    }

    private suspend fun showBankTransfer(current: PaymentSessionApi) {
        busyLabel = "Fetching transfer details…"
        qrDetails = current.getQrPaymentInfo(QrCodeFormat.PNG)
    }

    /**
     * Asks Google Pay on the device whether it can pay, which decides whether the wallet row is
     * shown. The gateway is not asked: its info belongs to the moment the shopper picks the
     * wallet, and [payWithGooglePay] leaves it to `chargeWithWallet`. Asked once per checkout;
     * the answer does not depend on the payment.
     */
    private suspend fun lookForWallet() {
        if (walletLookedFor) return
        walletLookedFor = true
        isWalletAvailable = services.isWalletAvailable()
    }

    /**
     * Charge, then settle. Shared by every card-based method. A charge call that fails in a way
     * that leaves a charge possible counts as sent, so the charge decides what comes next: the
     * gateway already has one (`PAYMENT_013`), or the answer never arrived or was a 5xx and the
     * request may have reached it.
     */
    private suspend fun runCharge(
        current: PaymentSessionApi,
        charge: suspend () -> ChargePaymentResponse
    ) {
        try {
            charge()
        } catch (e: GopaySDKException) {
            if (e.chargeMayExist()) chargeSent = true
            throw e
        }
        chargeSent = true
        outcome = settle(current)
    }

    /**
     * Drives the charge to its result with the SDK's own polling: `awaitChargeState` reads
     * `GET /payments/{id}/charge` once a second until the gateway reports SUCCEEDED or FAILED.
     *
     * `ACTION_REQUIRED` is a normal step of that loop, not a failure: in the default automatic
     * 3DS mode the SDK presents its verification WebView for each new `redirectUrl` itself, with
     * the `callback.return_url` the payment was created with, and polls on afterwards. A charge
     * that ends `FAILED` arrives as `PAYMENT_006` with the state attached; one still in flight
     * after the initial timeout (30 s) as `PAYMENT_007`. Both become an outcome here: the result
     * screen shows "failed", or "pending" with a Refresh button that re-enters this method. A
     * declined payment is over, so it is marked for [replaceSpentPayment]. A closed challenge
     * (`PAYMENT_011`) and every other error propagate to the caller.
     */
    private suspend fun settle(current: PaymentSessionApi): Outcome {
        busyLabel = "Confirming payment…"
        var lastRead: ChargePaymentResponse? = null
        return try {
            val charge = current.awaitChargeState(
                AwaitChargeOptions(
                    // The address the payment was created with, not the charge response's, which
                    // GoPay is removing.
                    returnUrl = paymentReturnUrl,
                    onStateChange = { state ->
                        lastRead = state
                        // Back from "Verifying with your bank…" once the challenge is answered.
                        if (state.state != ChargeState.ACTION_REQUIRED) {
                            isWaitingForChallenge = false
                            busyLabel = "Confirming payment…"
                        }
                    },
                    onActionRequired = {
                        isWaitingForChallenge = true
                        busyLabel = "Verifying with your bank…"
                    }
                )
            )
            Outcome(charge.state, charge, null, current.paymentId)
        } catch (e: GopaySDKException) {
            when (e.code) {
                GopayErrorCodes.PAYMENT_DECLINED -> {
                    paymentIsOver = true
                    Outcome(ChargeState.FAILED, e.chargeState, e.chargeState?.failReason ?: e.message, current.paymentId)
                }
                // Not decided yet: the pending screen, whose Refresh waits again.
                GopayErrorCodes.PAYMENT_TIMEOUT ->
                    Outcome(lastRead?.state ?: ChargeState.PROCESSING, lastRead, null, current.paymentId)
                else -> throw e
            }
        } finally {
            isWaitingForChallenge = false
        }
    }

    /**
     * Opens a new payment in place of one that is over, declined or given up at the bank's
     * challenge: the old session is closed and [open] runs again. Called at the end of [pay] and
     * [refreshChargeState], after the result is on screen, so the shopper waits for nothing and
     * does not leave the checkout; "Try another method" finds the new payment ready or on its way.
     */
    private suspend fun replaceSpentPayment() {
        if (!paymentIsOver || hasLeft) return
        paymentIsOver = false
        closeSession()
        open()
    }

    /**
     * The charge was interrupted: a payment window closed before it finished (`PAYMENT_011`), or
     * the charge call or the wait for it failed with anything but a result (`PAYMENT_013`,
     * `NETWORK_*`, `PAYMENT_008`, `PAYMENT_010`, `AUTH_*`, …). Closing the Google Pay sheet sends
     * no charge, so a soft banner is all it takes and the payment stays. Once the charge went out
     * the issuer may have authorised it already, so offering a new payment could charge the
     * shopper twice, and the payment decides first, read in one call with `getPaymentOutcome()`:
     *
     * - paid: the result;
     * - declined: the result, and a new payment behind it;
     * - not decided: waited for again with `awaitChargeState`, which also shows a 3DS challenge
     *   the interrupted charge never got to (`ACTION_REQUIRED`). Only when the shopper closed
     *   that challenge is the payment given up for a new one;
     * - not charged: the banner. The payment stays unless the gateway has ended it.
     *
     * When the payment cannot be read, the result screen says so and offers Refresh rather than
     * a new payment. The iOS demo does the same.
     */
    private suspend fun afterInterruptedCharge(current: PaymentSessionApi, error: GopaySDKException) {
        val cancelledByShopper = error.code == GopayErrorCodes.PAYMENT_CANCELLED
        val notice = if (cancelledByShopper) {
            "Payment cancelled. Pick a method and try again."
        } else {
            "${describe(error)}. Pick a method and try again."
        }
        if (!chargeSent) {
            banner = notice
            return
        }
        busyLabel = "Checking the payment…"
        val read = try {
            current.getPaymentOutcome()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The charge may be through, so no new payment: the result screen and its Refresh,
            // with the reason where that screen shows it rather than behind it in a banner.
            outcome = unread(current, outcome?.response, e)
            return
        }
        val charge = read.charge
        when (read.outcome) {
            PaymentOutcome.Outcome.PAID ->
                outcome = Outcome(ChargeState.SUCCEEDED, charge, null, current.paymentId)

            PaymentOutcome.Outcome.DECLINED -> {
                outcome = Outcome(ChargeState.FAILED, charge, null, current.paymentId)
                paymentIsOver = true
            }

            PaymentOutcome.Outcome.NOT_CHARGED -> {
                // Nothing went out: back to the checkout, also from the result screen's Refresh.
                outcome = null
                banner = notice
                val ended = read.payment.state == PaymentState.CANCELED ||
                    read.payment.state == PaymentState.TIMEOUTED
                if (ended) paymentIsOver = true
            }

            PaymentOutcome.Outcome.UNDECIDED -> when {
                // The shopper closed the bank's challenge: this payment will not be paid.
                cancelledByShopper && charge?.state == ChargeState.ACTION_REQUIRED -> {
                    outcome = null
                    banner = notice
                    paymentIsOver = true
                }
                // No charge, and a payment in a state this SDK does not know: nothing to wait
                // for here, so the pending screen and its Refresh.
                charge == null -> outcome = Outcome(ChargeState.UNKNOWN, null, null, current.paymentId)
                else -> waitAgain(current, charge)
            }
        }
    }

    /**
     * Waits for a charge that is not decided after an interruption. A challenge the shopper
     * closes on the way is read like any other closed challenge. Any other error leaves the
     * result screen with the last state read and its Refresh: as a state that could not be
     * read when the reading itself failed on the network, as a payment not finished otherwise.
     */
    private suspend fun waitAgain(current: PaymentSessionApi, lastRead: ChargePaymentResponse?) {
        try {
            outcome = settle(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is GopaySDKException && e.code == GopayErrorCodes.PAYMENT_CANCELLED) {
                afterInterruptedCharge(current, e)
                return
            }
            if (e is GopaySDKException && e.code.startsWith("NETWORK_")) {
                outcome = unread(current, lastRead, e)
                return
            }
            outcome = Outcome(
                state = lastRead?.state ?: ChargeState.UNKNOWN,
                response = lastRead,
                message = describe(e),
                paymentId = current.paymentId
            )
        }
    }

    /** The result of a payment whose state could not be read; [lastRead] is what was known before. */
    private fun unread(current: PaymentSessionApi, lastRead: ChargePaymentResponse?, error: Exception) = Outcome(
        state = lastRead?.state ?: ChargeState.UNKNOWN,
        response = lastRead,
        message = "Couldn't check the payment: ${describe(error)}",
        paymentId = current.paymentId,
        unread = true
    )

    private fun describe(error: Exception): String =
        if (error is GopaySDKException) "[${error.code}] ${error.message}" else error.message ?: "Something went wrong"

    // endregion

    companion object {
        /** The id the checkout's `PaymentCardForm` is composed with and `submitCardForm` reads. */
        const val CARD_FORM_ID = "checkout-card"
    }
}

/**
 * Whether a charge call that threw this may still have left a charge on the payment: the gateway
 * has one already, or it answered with a 5xx, or no answer arrived at all.
 */
internal fun GopaySDKException.chargeMayExist(): Boolean = when {
    code == GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS -> true
    code == GopayErrorCodes.NETWORK_SERVER_ERROR -> true
    else -> httpContext == null && code in TRANSPORT_CODES
}

/** The transport failures, where the request may have reached the gateway and the answer was lost. */
internal val TRANSPORT_CODES = setOf(
    GopayErrorCodes.NETWORK_TIMEOUT,
    GopayErrorCodes.NETWORK_NO_CONNECTION,
    GopayErrorCodes.NETWORK_SSL_ERROR,
    GopayErrorCodes.NETWORK_IO_ERROR
)

/**
 * What the checkout asks of the world outside it: the merchant backend, and the calls of the SDK
 * that belong to no payment session. [LiveCheckoutServices] is the real thing; a unit test hands
 * [CheckoutController] its own, with a session of its own behind [startPaymentSession].
 */
internal interface CheckoutServices {
    /** Your server's part: creates the payment and returns what the app needs of it. */
    suspend fun createPayment(amount: Int, currency: String): MerchantBackendSimulator.CreatedPayment

    suspend fun startPaymentSession(paymentId: String, paymentSecret: String): PaymentSessionApi

    /** Whether Google Pay can pay on this device; the gateway is not asked. */
    suspend fun isWalletAvailable(): Boolean

    /** Validates and encrypts what the card form under [formId] holds, into a JWE. */
    suspend fun submitCardForm(formId: String): String

    /** The token of the shopper's saved card, which a real backend keeps from an earlier order. */
    suspend fun savedCardToken(): String
}

/** The SDK and the simulated merchant backend. */
internal object LiveCheckoutServices : CheckoutServices {

    override suspend fun createPayment(amount: Int, currency: String) =
        MerchantBackendSimulator.createPayment(amount = amount, currency = currency)

    override suspend fun startPaymentSession(paymentId: String, paymentSecret: String): PaymentSessionApi =
        GopaySDK.getInstance().startPaymentSession(paymentId = paymentId, paymentSecret = paymentSecret)

    override suspend fun isWalletAvailable(): Boolean = GopaySDK.isWalletAvailable()

    override suspend fun submitCardForm(formId: String): String =
        GopaySDK.getInstance().submitCardForm(formId)

    override suspend fun savedCardToken(): String {
        val jwe = GopaySDK.getInstance().encryptCardData(
            GopayCardData(cardPan = "4444444444444448", expMonth = "12", expYear = "28", cvv = "123")
        )
        return MerchantBackendSimulator.tokenizeCard(jwe)
    }
}
