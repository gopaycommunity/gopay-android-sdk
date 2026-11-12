package com.gopay.example.checkout

import android.app.Activity
import com.gopay.example.MerchantBackendSimulator
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentDetails
import cz.gopay.sdk.model.PaymentOutcome
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.session.AwaitChargeOptions
import cz.gopay.sdk.session.PaymentSessionApi
import kotlinx.coroutines.CompletableDeferred
import org.mockito.Mockito.mock

/*
 * The checkout's outside world for its unit tests: a merchant backend that numbers the payments
 * it creates and a session per payment that records every call and answers as the test tells it.
 * Nothing here reaches the network or the SDK.
 */

/** The merchant backend and the SDK's session-less calls, as [CheckoutController] sees them. */
internal class FakeCheckoutServices : CheckoutServices {

    /** The ids of the payments created, oldest first: `payment-1`, `payment-2`, … */
    val created = mutableListOf<String>()

    /** The session started for each payment, in the same order. */
    val sessions = mutableListOf<FakeSession>()

    /** Thrown by the next `createPayment` instead of creating one, then forgotten. */
    var createFailure: Exception? = null

    /** When set, `createPayment` waits for it: the checkout is then caught in the middle of opening. */
    var createGate: CompletableDeferred<Unit>? = null

    /** When set, `startPaymentSession` waits for it, with the payment created and the session made. */
    var startGate: CompletableDeferred<Unit>? = null

    /** What Google Pay on the device answers. */
    var walletOnDevice = false

    /** How many times the device was asked. */
    var walletChecks = 0

    /** What the card form hands over; throws what `submitCardForm` throws. */
    var cardForm: () -> String = { "jwe-of-the-card" }
    var savedCardTokens = 0

    /** Sets up each session as it is started. */
    var onSession: (FakeSession) -> Unit = {}

    val last: FakeSession get() = sessions.last()

    override suspend fun createPayment(amount: Int, currency: String): MerchantBackendSimulator.CreatedPayment {
        createGate?.await()
        createFailure?.let {
            createFailure = null
            throw it
        }
        val id = "payment-${created.size + 1}"
        created += id
        return MerchantBackendSimulator.CreatedPayment(id, "secret-of-$id", "https://shop.example/return")
    }

    override suspend fun startPaymentSession(paymentId: String, paymentSecret: String): PaymentSessionApi =
        FakeSession(paymentId).also {
            onSession(it)
            sessions += it
            startGate?.await()
        }

    override suspend fun isWalletAvailable(): Boolean {
        walletChecks++
        return walletOnDevice
    }

    override suspend fun submitCardForm(formId: String): String = cardForm()

    override suspend fun savedCardToken(): String {
        savedCardTokens++
        return "token-of-the-saved-card"
    }
}

/** A payment session of the test's own: every call is in [calls], the answers are the test's. */
internal class FakeSession(override val paymentId: String) : PaymentSessionApi {

    /** The calls made, by name, in order. */
    val calls = mutableListOf<String>()
    var closed = false

    /** The answer to any of the three charge calls. */
    var chargeAnswer: () -> ChargePaymentResponse = { chargeIn(ChargeState.REQUESTED) }

    /** What `awaitChargeState` ends with, call after call; the last one repeats. */
    var awaits: MutableList<() -> ChargePaymentResponse> = mutableListOf({ chargeIn(ChargeState.SUCCEEDED) })

    /** What `getPaymentOutcome` reads. */
    var outcome: () -> PaymentOutcome = { throw AssertionError("the test reads no outcome") }

    /** The options the last `awaitChargeState` was called with. */
    var awaitOptions: AwaitChargeOptions? = null

    val chargeCalls: List<String> get() = calls.filter { it.startsWith("chargeWith") }

    override suspend fun getPaymentOutcome(): PaymentOutcome = record("getPaymentOutcome").let { outcome() }

    override suspend fun awaitChargeState(options: AwaitChargeOptions): ChargePaymentResponse {
        record("awaitChargeState")
        awaitOptions = options
        val answer = if (awaits.size > 1) awaits.removeAt(0) else awaits.first()
        return answer()
    }

    override suspend fun getQrPaymentInfo(format: QrCodeFormat?): QrPaymentDetails =
        record("getQrPaymentInfo").let { mock(QrPaymentDetails::class.java) }

    override suspend fun chargeWithWallet(
        browserData: BrowserData?, challengePreference: ChallengePreference?, activity: Activity?
    ): ChargePaymentResponse = record("chargeWithWallet").let { chargeAnswer() }

    override suspend fun chargeWithCardToken(
        cardToken: String, browserData: BrowserData?, challengePreference: ChallengePreference?, activity: Activity?
    ): ChargePaymentResponse = record("chargeWithCardToken").let { chargeAnswer() }

    override suspend fun chargeWithEncryptedCard(
        payload: String, browserData: BrowserData?, challengePreference: ChallengePreference?, activity: Activity?
    ): ChargePaymentResponse = record("chargeWithEncryptedCard").let { chargeAnswer() }

    override fun close() {
        calls += "close"
        closed = true
    }

    // What the checkout does not call.
    override suspend fun getWalletInfo(): GooglePayInfoResponse = notCalled("getWalletInfo")
    override suspend fun getStatus(): PaymentDetails = notCalled("getStatus")
    override suspend fun getChargeState(): ChargePaymentResponse = notCalled("getChargeState")
    override suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse = notCalled("charge")
    override suspend fun completeBrowserData(browserData: BrowserData): BrowserData = notCalled("completeBrowserData")
    override suspend fun handle3dsVerification(redirectUrl: String, returnUrl: String?, activity: Activity?) =
        notCalled("handle3dsVerification")

    private fun record(call: String) {
        check(!closed) { "$call on the closed session of $paymentId" }
        calls += call
    }

    private fun notCalled(call: String): Nothing = throw AssertionError("the checkout does not call $call")
}

internal fun chargeIn(state: ChargeState, failReason: String? = null) =
    ChargePaymentResponse(id = "charge-1", state = state, failReason = failReason)

internal fun sdkError(code: String, message: String, chargeState: ChargePaymentResponse? = null) =
    GopaySDKException(code = code, message = message, chargeState = chargeState)

/** What `getPaymentOutcome` returns for a payment in [state] with a charge in [chargeState], if any. */
internal fun outcomeOf(
    outcome: PaymentOutcome.Outcome,
    state: PaymentState = PaymentState.CREATED,
    chargeState: ChargeState? = null
) = PaymentOutcome(
    outcome,
    PaymentDetails(
        id = "payment-1",
        orderNumber = "order-1",
        state = state,
        amount = 100,
        currency = Currency.CZK,
        customer = PaymentCustomer(email = "customer@example.com"),
        gatewayUrl = "https://gw.sandbox.gopay.com/gw/v3/payment-1",
        charge = chargeState?.let { chargeIn(it) }
    )
)
