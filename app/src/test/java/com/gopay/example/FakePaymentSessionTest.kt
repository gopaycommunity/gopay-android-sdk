package com.gopay.example

import android.app.Activity
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The README's "Replacing the session in tests", compiled and run from outside the SDK module:
 * an app's own class takes a [PaymentSessionApi], and its test hands it a session of its own
 * that answers with values built through the public constructors of the response models.
 */
class FakePaymentSessionTest {

    /** What an app does with a session: reads the outcome and says what to show. */
    class Checkout(private val session: PaymentSessionApi) {
        suspend fun resultMessage(): String = try {
            when (session.getPaymentOutcome().outcome) {
                PaymentOutcome.Outcome.PAID -> "Paid"
                PaymentOutcome.Outcome.DECLINED -> "Declined"
                PaymentOutcome.Outcome.NOT_CHARGED -> "Not charged"
                PaymentOutcome.Outcome.UNDECIDED -> "Still processing"
            }
        } catch (e: GopaySDKException) {
            if (e.code == GopayErrorCodes.PAYMENT_NOT_FOUND) "No such payment" else "Try again"
        }
    }

    /** A session of the test's own. Only what the test reads answers; the rest is not called. */
    class FakeSession(
        private val outcome: () -> PaymentOutcome
    ) : PaymentSessionApi {
        override val paymentId = "3310782449"
        override suspend fun getPaymentOutcome(): PaymentOutcome = outcome()
        override suspend fun getStatus(): PaymentDetails = outcome().payment
        override suspend fun getChargeState(): ChargePaymentResponse = outcome().charge ?: notCalled()
        override suspend fun awaitChargeState(options: AwaitChargeOptions): ChargePaymentResponse = notCalled()
        override suspend fun charge(request: ChargePaymentRequest): ChargePaymentResponse = notCalled()
        override suspend fun completeBrowserData(browserData: BrowserData): BrowserData = browserData
        override suspend fun getQrPaymentInfo(format: QrCodeFormat?): QrPaymentDetails = notCalled()
        override suspend fun getWalletInfo(): GooglePayInfoResponse = notCalled()
        override suspend fun chargeWithWallet(
            activity: Activity, browserData: BrowserData?, challengePreference: ChallengePreference?
        ): ChargePaymentResponse = notCalled()
        override suspend fun chargeWithCardToken(
            activity: Activity, cardToken: String, browserData: BrowserData?, challengePreference: ChallengePreference?
        ): ChargePaymentResponse = notCalled()
        override suspend fun chargeWithCardToken(
            cardToken: String, browserData: BrowserData, challengePreference: ChallengePreference?
        ): ChargePaymentResponse = notCalled()
        override suspend fun chargeWithEncryptedCard(
            activity: Activity, payload: String, browserData: BrowserData?, challengePreference: ChallengePreference?
        ): ChargePaymentResponse = notCalled()
        override suspend fun chargeWithEncryptedCard(
            payload: String, browserData: BrowserData, challengePreference: ChallengePreference?
        ): ChargePaymentResponse = notCalled()
        override suspend fun handle3dsVerification(activity: Activity, redirectUrl: String, returnUrl: String?) = Unit
        override fun close() = Unit

        private fun notCalled(): Nothing = throw AssertionError("the test does not make this call")
    }

    private fun payment(state: PaymentState, charge: ChargeState? = null) = PaymentDetails(
        id = "3310782449",
        orderNumber = "order-1",
        state = state,
        amount = 100,
        currency = Currency.CZK,
        customer = PaymentCustomer(email = "customer@example.com"),
        gwUrl = "https://gw.sandbox.gopay.com/gw/v3/3310782449",
        charge = charge?.let { ChargePaymentResponse(id = "charge-1", state = it) }
    )

    @Test
    fun `a paid payment shows as paid`() = runBlocking {
        val paid = PaymentOutcome(PaymentOutcome.Outcome.PAID, payment(PaymentState.PAID, ChargeState.SUCCEEDED))

        assertEquals("Paid", Checkout(FakeSession { paid }).resultMessage())
        assertEquals("the charge is the payment's own", ChargeState.SUCCEEDED, paid.charge?.state)
    }

    @Test
    fun `a declined payment shows as declined`() = runBlocking {
        val declined = PaymentOutcome(PaymentOutcome.Outcome.DECLINED, payment(PaymentState.CANCELED, ChargeState.FAILED))

        assertEquals("Declined", Checkout(FakeSession { declined }).resultMessage())
    }

    @Test
    fun `the fake throws what the session throws`() = runBlocking {
        val session = FakeSession {
            throw GopaySDKException(code = GopayErrorCodes.PAYMENT_NOT_FOUND, message = "HTTP 404")
        }

        assertEquals("No such payment", Checkout(session).resultMessage())
    }
}
