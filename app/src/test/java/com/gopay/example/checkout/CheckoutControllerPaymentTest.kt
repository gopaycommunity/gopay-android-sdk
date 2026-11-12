package com.gopay.example.checkout

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.model.PaymentOutcome
import cz.gopay.sdk.model.PaymentState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The checkout's payment: created and its session started when the checkout opens, charged by
 * whichever method the shopper picks, and replaced by a new one only when it is over. The pay
 * button never creates a payment. The iOS demo's view model is tested for the same behaviour.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutControllerPaymentTest {

    private val services = FakeCheckoutServices()
    private val controller = CheckoutController(services)

    // ---- Opening

    @Test
    fun `opening the checkout creates its payment and starts the session, once`() = runTest {
        controller.open()
        controller.open()

        assertEquals(listOf("payment-1"), services.created)
        assertEquals(1, services.sessions.size)
        assertEquals("payment-1", controller.paymentId)
        assertTrue(controller.canPay)
        assertNull(controller.openError)
        assertEquals("nothing is charged by opening", emptyList<String>(), services.last.chargeCalls)
    }

    @Test
    fun `while the checkout opens the pay button waits, and a tap creates nothing`() = runTest {
        services.createGate = CompletableDeferred()
        val opening = launch { controller.open() }
        runCurrent()

        assertTrue(controller.isOpening)
        assertFalse(controller.canPay)
        assertEquals("Preparing your order…", controller.payButtonTitle)
        assertFalse("the form and the methods are not locked", controller.isBusy)
        controller.pay()
        controller.open()
        assertEquals("neither the tap nor a second open created a payment", emptyList<String>(), services.created)

        services.createGate!!.complete(Unit)
        opening.join()
        assertFalse(controller.isOpening)
        assertTrue(controller.canPay)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `a checkout that could not open says so, and only Try again opens it`() = runTest {
        services.createFailure = RuntimeException("Failed to create payment: HTTP 503")

        controller.open()

        assertEquals("Couldn't prepare your order: Failed to create payment: HTTP 503", controller.openError)
        assertFalse(controller.canPay)
        assertFalse(controller.isOpening)
        assertNull(controller.paymentId)
        assertNull("not a failed payment", controller.outcome)

        controller.pay()
        assertEquals("the pay button creates no payment", emptyList<String>(), services.created)

        // Try again.
        controller.open()
        assertNull(controller.openError)
        assertTrue(controller.canPay)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `a session that could not start reads with its code`() = runTest {
        services.onSession = { throw sdkError(GopayErrorCodes.NETWORK_NO_CONNECTION, "No internet connection") }

        controller.open()

        assertEquals("Couldn't prepare your order: [NETWORK_004] No internet connection", controller.openError)
        assertFalse(controller.canPay)
    }

    // ---- Paying

    @Test
    fun `every method charges the session the checkout opened`() = runTest {
        controller.open()
        val session = services.last

        controller.select(CheckoutController.Method.CARD)
        controller.pay()

        assertEquals(listOf("payment-1"), services.created)
        assertEquals(listOf("chargeWithEncryptedCard", "awaitChargeState"), session.calls)
        assertEquals(ChargeState.SUCCEEDED, controller.outcome?.state)
        assertEquals("payment-1", controller.outcome?.paymentId)
        assertEquals("the return URL is the payment's own", "https://shop.example/return", session.awaitOptions?.returnUrl)
        assertFalse(session.closed)
    }

    @Test
    fun `the saved card is tokenized on the tap and charged on the same session`() = runTest {
        controller.open()
        assertEquals("not before the shopper pays", 0, services.savedCardTokens)

        controller.select(CheckoutController.Method.SAVED_CARD)
        controller.pay()

        assertEquals(1, services.savedCardTokens)
        assertEquals(listOf("chargeWithCardToken"), services.last.chargeCalls)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `transfer details and then a card are the same payment`() = runTest {
        controller.open()
        val session = services.last

        controller.select(CheckoutController.Method.BANK_TRANSFER)
        controller.pay()
        assertNotNull(controller.qrDetails)
        controller.dismissQr()
        controller.select(CheckoutController.Method.CARD)
        controller.pay()

        assertEquals(listOf("payment-1"), services.created)
        assertSame(session, services.last)
        assertEquals(
            listOf("getQrPaymentInfo", "chargeWithEncryptedCard", "awaitChargeState"),
            session.calls
        )
    }

    @Test
    fun `an empty card form is said under the form and nothing is charged`() = runTest {
        controller.open()
        services.cardForm = { throw sdkError(GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, "The card form is empty") }

        controller.pay()

        assertEquals("Please enter your card details and try again.", controller.cardFormError)
        assertEquals(emptyList<String>(), services.last.chargeCalls)
        assertNull(controller.outcome)
        assertEquals(listOf("payment-1"), services.created)
    }

    // ---- A declined payment

    @Test
    fun `a declined payment shows its result and a new payment is opened behind it`() = runTest {
        controller.open()
        val declined = services.last
        declined.awaits = mutableListOf({
            throw sdkError(
                GopayErrorCodes.PAYMENT_DECLINED, "The charge failed",
                chargeIn(ChargeState.FAILED, failReason = "Insufficient funds")
            )
        })

        controller.pay()

        val result = controller.outcome!!
        assertTrue(result.isFailure)
        assertEquals("Insufficient funds", result.message)
        assertEquals("the result is of the declined payment", "payment-1", result.paymentId)
        // Without a tap on "Try another method": the next payment is already there.
        assertTrue(declined.closed)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
        assertEquals("payment-2", controller.paymentId)

        controller.resumeShopping()
        assertTrue(controller.canPay)
        controller.pay()
        assertEquals(listOf("chargeWithEncryptedCard"), services.last.chargeCalls)
        assertEquals("the retry is paid on the new payment", "payment-2", controller.outcome?.paymentId)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
    }

    @Test
    fun `a new payment that could not be opened after a decline leaves Try again, not a dead button`() = runTest {
        controller.open()
        services.last.awaits = mutableListOf({
            throw sdkError(GopayErrorCodes.PAYMENT_DECLINED, "The charge failed", chargeIn(ChargeState.FAILED))
        })
        services.createFailure = RuntimeException("Failed to create payment: HTTP 503")

        controller.pay()
        controller.resumeShopping()

        assertNotNull(controller.openError)
        assertFalse(controller.canPay)
        controller.open()
        assertTrue(controller.canPay)
        assertEquals("payment-2", controller.paymentId)
    }

    // ---- An error before a charge exists

    @Test
    fun `a wallet sheet that fails is a banner on the same payment, not a failed payment`() = runTest {
        services.walletOnDevice = true
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "Google Pay failed: merchant") }

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals("[PAYMENT_003] Google Pay failed: merchant", controller.banner)
        assertNull("no Payment failed screen", controller.outcome)
        assertEquals(listOf("payment-1"), services.created)
        assertEquals("payment-1", controller.paymentId)
        assertFalse(session.closed)
        assertFalse(session.calls.contains("getPaymentOutcome"))
        assertTrue(controller.canPay)
    }

    @Test
    fun `a closed wallet sheet is a banner on the same payment`() = runTest {
        services.walletOnDevice = true
        controller.open()
        services.last.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CANCELLED, "Google Pay sheet was closed") }

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals("Payment cancelled. Pick a method and try again.", controller.banner)
        assertNull(controller.outcome)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(services.last.closed)
    }

    @Test
    fun `a saved card that could not be tokenized is a banner on the same payment`() = runTest {
        val failing = object : CheckoutServices by services {
            override suspend fun savedCardToken(): String = throw RuntimeException("Failed to tokenize card: HTTP 500")
        }
        val controller = CheckoutController(failing)
        controller.open()

        controller.select(CheckoutController.Method.SAVED_CARD)
        controller.pay()

        assertEquals("Failed to tokenize card: HTTP 500", controller.banner)
        assertNull(controller.outcome)
        assertEquals(emptyList<String>(), services.last.chargeCalls)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(services.last.closed)
    }

    // ---- An interrupted charge

    @Test
    fun `a charge that was interrupted and waits for 3DS is finished, not replaced`() = runTest {
        controller.open()
        val session = services.last
        // The gateway already has a charge on the payment, a double tap for one.
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, "HTTP 409") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.ACTION_REQUIRED) }

        controller.pay()

        assertEquals(
            "read in one call, then waited for with the challenge",
            listOf("chargeWithEncryptedCard", "getPaymentOutcome", "awaitChargeState"),
            session.calls
        )
        assertEquals(ChargeState.SUCCEEDED, controller.outcome?.state)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(session.closed)
    }

    @Test
    fun `a 3DS challenge the shopper closed gives the payment up for a new one`() = runTest {
        controller.open()
        val session = services.last
        session.awaits = mutableListOf({ throw sdkError(GopayErrorCodes.PAYMENT_CANCELLED, "3DS verification was closed") })
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.ACTION_REQUIRED) }

        controller.pay()

        assertNull("back on the checkout, no result", controller.outcome)
        assertEquals("Payment cancelled. Pick a method and try again.", controller.banner)
        assertTrue(session.closed)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
        assertEquals("payment-2", controller.paymentId)
        assertTrue(controller.canPay)
    }

    @Test
    fun `a closed challenge on a payment that went through anyway shows the payment paid`() = runTest {
        controller.open()
        val session = services.last
        session.awaits = mutableListOf({ throw sdkError(GopayErrorCodes.PAYMENT_CANCELLED, "3DS verification was closed") })
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.PAID, PaymentState.PAID, ChargeState.SUCCEEDED) }

        controller.pay()

        assertTrue(controller.outcome!!.isSuccess)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `an interrupted charge that the gateway never got leaves the payment to be paid`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_TIMEOUT, "Timed out") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.NOT_CHARGED) }

        controller.pay()

        assertNull(controller.outcome)
        assertEquals("[NETWORK_001] Timed out. Pick a method and try again.", controller.banner)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(session.closed)
    }

    @Test
    fun `an interrupted charge found declined shows the result and opens a new payment`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_SERVER_ERROR, "HTTP 502") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.DECLINED, PaymentState.CANCELED, ChargeState.FAILED) }

        controller.pay()

        assertTrue(controller.outcome!!.isFailure)
        assertEquals("payment-1", controller.outcome?.paymentId)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
    }

    @Test
    fun `a payment that cannot be read after its charge went out is unread, and no new payment is offered`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_NO_CONNECTION, "No internet connection") }
        session.outcome = { throw sdkError(GopayErrorCodes.NETWORK_NO_CONNECTION, "No internet connection") }

        controller.pay()

        val result = controller.outcome!!
        assertTrue(result.isUnread)
        assertEquals("Couldn't check the payment", resultTitle(result))
        assertEquals("Couldn't check the payment: [NETWORK_004] No internet connection", result.message)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(session.closed)

        // Refresh, with the connection back: the same payment is waited for.
        session.awaits = mutableListOf({ chargeIn(ChargeState.SUCCEEDED) })
        controller.refreshChargeState()
        assertTrue(controller.outcome!!.isSuccess)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `a wait that ends in an error after the state was read is not finished, not unread`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, "HTTP 409") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.ACTION_REQUIRED) }
        session.awaits = mutableListOf({
            throw sdkError(GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE, "3DS verification could not be loaded")
        })

        controller.pay()

        val result = controller.outcome!!
        assertFalse(result.isUnread)
        assertTrue(result.isUnfinished)
        assertEquals(ChargeState.ACTION_REQUIRED, result.state)
        assertEquals("Payment not finished", resultTitle(result))
        assertEquals("[PAYMENT_010] 3DS verification could not be loaded", result.message)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `a wait that fails on the network after the state was read is a state that could not be read`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, "HTTP 409") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.PROCESSING) }
        session.awaits = mutableListOf({ throw sdkError(GopayErrorCodes.NETWORK_TIMEOUT, "Timed out") })

        controller.pay()

        val result = controller.outcome!!
        assertTrue(result.isUnread)
        assertEquals(ChargeState.PROCESSING, result.state)
        assertEquals("Couldn't check the payment: [NETWORK_001] Timed out", result.message)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `a challenge that shows up only in the second wait and is closed there gives the payment up too`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, "HTTP 409") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.ACTION_REQUIRED) }
        session.awaits = mutableListOf({ throw sdkError(GopayErrorCodes.PAYMENT_CANCELLED, "3DS verification was closed") })

        controller.pay()

        assertNull(controller.outcome)
        assertEquals("Payment cancelled. Pick a method and try again.", controller.banner)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
    }

    @Test
    fun `an undecided payment without a charge is pending, with nothing to wait for`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_SERVER_ERROR, "HTTP 500") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, PaymentState.UNKNOWN) }

        controller.pay()

        val result = controller.outcome!!
        assertEquals("Payment pending", resultTitle(result))
        assertFalse(session.calls.contains("awaitChargeState"))
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `an interrupted charge that turns out declined while it is waited for opens a new payment`() = runTest {
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.PAYMENT_CHARGE_ALREADY_EXISTS, "HTTP 409") }
        // Not decided when the payment is read; the wait that follows ends in the decline.
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.UNDECIDED, chargeState = ChargeState.PROCESSING) }
        session.awaits = mutableListOf({
            throw sdkError(GopayErrorCodes.PAYMENT_DECLINED, "The charge failed", chargeIn(ChargeState.FAILED))
        })

        controller.pay()

        assertEquals(listOf("chargeWithEncryptedCard", "getPaymentOutcome", "awaitChargeState", "close"), session.calls)
        assertTrue(controller.outcome!!.isFailure)
        assertEquals("payment-1", controller.outcome?.paymentId)
        assertEquals(listOf("payment-1", "payment-2"), services.created)
        assertEquals("payment-2", controller.paymentId)
        controller.resumeShopping()
        assertTrue(controller.canPay)
    }

    // ---- The wallet row

    @Test
    fun `opening the checkout asks the gateway nothing about the wallet`() = runTest {
        services.walletOnDevice = true

        controller.open()

        // getWalletInfo on the fake fails the test: the session made no call at all.
        assertEquals(emptyList<String>(), services.last.calls)
        assertNull(controller.banner)
        assertNull(controller.openError)
        assertTrue(controller.canPay)
    }

    @Test
    fun `Google Pay is offered when the device can pay with it, and the device is asked once`() = runTest {
        services.walletOnDevice = true
        controller.open()
        services.last.awaits = mutableListOf({
            throw sdkError(GopayErrorCodes.PAYMENT_DECLINED, "The charge failed", chargeIn(ChargeState.FAILED))
        })

        assertTrue(controller.methods.contains(CheckoutController.Method.GOOGLE_PAY))
        // A decline opens the next payment: the device's answer is not asked for again.
        controller.pay()
        assertEquals(listOf("payment-1", "payment-2"), services.created)
        assertEquals(1, services.walletChecks)
        assertTrue(controller.methods.contains(CheckoutController.Method.GOOGLE_PAY))
    }

    @Test
    fun `the wallet row is there while the checkout still opens`() = runTest {
        services.walletOnDevice = true
        services.createGate = CompletableDeferred()
        val opening = launch { controller.open() }
        runCurrent()

        assertTrue(controller.isOpening)
        assertTrue(controller.methods.contains(CheckoutController.Method.GOOGLE_PAY))
        opening.cancel()
    }

    @Test
    fun `a device without Google Pay has no Google Pay row`() = runTest {
        controller.open()

        assertEquals(
            listOf(
                CheckoutController.Method.CARD,
                CheckoutController.Method.SAVED_CARD,
                CheckoutController.Method.BANK_TRANSFER
            ),
            controller.methods
        )
    }

    @Test
    fun `paying with Google Pay leaves the gateway's info to chargeWithWallet`() = runTest {
        services.walletOnDevice = true
        controller.open()

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals(listOf("chargeWithWallet", "awaitChargeState"), services.last.calls)
    }

    @Test
    fun `info that cannot be read after the tap is a banner on the same payment`() = runTest {
        services.walletOnDevice = true
        controller.open()
        val session = services.last
        // What chargeWithWallet throws when its own read of the info fails: nothing was charged.
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_CLIENT_ERROR, "Failed to get Google Pay info: HTTP 403") }

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals("[NETWORK_002] Failed to get Google Pay info: HTTP 403", controller.banner)
        assertNull(controller.outcome)
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(session.closed)
        assertFalse(session.calls.contains("getPaymentOutcome"))
        assertTrue(controller.canPay)
    }

    @Test
    fun `a 5xx from the info read inside chargeWithWallet is read like a charge whose answer was lost`() = runTest {
        services.walletOnDevice = true
        controller.open()
        val session = services.last
        // The checkout cannot tell the info's 5xx from the charge's, so it reads the payment first.
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_SERVER_ERROR, "Failed to get Google Pay info: HTTP 502") }
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.NOT_CHARGED) }

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals(listOf("chargeWithWallet", "getPaymentOutcome"), session.calls)
        assertNull(controller.outcome)
        assertEquals(
            "[NETWORK_003] Failed to get Google Pay info: HTTP 502. Pick a method and try again.",
            controller.banner
        )
        assertEquals(listOf("payment-1"), services.created)
        assertFalse(session.closed)
        assertTrue(controller.canPay)
    }

    @Test
    fun `an info read inside chargeWithWallet that got no answer is read the same way`() = runTest {
        services.walletOnDevice = true
        controller.open()
        val session = services.last
        session.chargeAnswer = { throw sdkError(GopayErrorCodes.NETWORK_NO_CONNECTION, "No internet connection") }
        // Still offline: the payment cannot be read either, so the result screen and its Refresh.
        session.outcome = { throw sdkError(GopayErrorCodes.NETWORK_NO_CONNECTION, "No internet connection") }

        controller.select(CheckoutController.Method.GOOGLE_PAY)
        controller.pay()

        assertEquals(listOf("chargeWithWallet", "getPaymentOutcome"), session.calls)
        assertTrue(controller.outcome!!.isUnread)
        assertEquals("payment-1", controller.outcome?.paymentId)
        assertEquals("no second info, no charge and no new payment", listOf("payment-1"), services.created)
        assertFalse(session.closed)

        // Back online, Refresh finds nothing charged and the same payment is there to pay.
        session.awaits = mutableListOf({ throw sdkError(GopayErrorCodes.PAYMENT_NOT_FOUND, "HTTP 404") })
        session.outcome = { outcomeOf(PaymentOutcome.Outcome.NOT_CHARGED) }
        controller.refreshChargeState()
        assertNull(controller.outcome)
        assertEquals(listOf("payment-1"), services.created)
        assertTrue(controller.canPay)
    }

    // ---- Leaving

    @Test
    fun `leaving without paying closes the session and asks nothing of the backend`() = runTest {
        controller.open()
        val session = services.last

        controller.finish()

        assertTrue(session.closed)
        assertNull(controller.paymentId)
        assertFalse(controller.canPay)
        assertEquals(listOf("payment-1"), services.created)
    }

    @Test
    fun `leaving while the checkout still opens keeps no session behind`() = runTest {
        services.createGate = CompletableDeferred()
        val opening = launch { controller.open() }
        runCurrent()

        controller.finish()
        services.createGate!!.complete(Unit)
        opening.join()

        assertEquals("the session was not even started", 0, services.sessions.size)
        assertNull(controller.paymentId)
        assertFalse(controller.canPay)
    }

    @Test
    fun `leaving while the session starts closes the session that arrives late`() = runTest {
        services.startGate = CompletableDeferred()
        val opening = launch { controller.open() }
        runCurrent()
        assertEquals("the payment exists and its session is on its way", listOf("payment-1"), services.created)
        assertTrue(controller.isOpening)

        controller.finish()
        services.startGate!!.complete(Unit)
        opening.join()

        assertTrue("kept by nobody, so closed", services.last.closed)
        assertNull(controller.paymentId)
        assertFalse(controller.canPay)
        assertFalse(controller.isOpening)
        assertNull("leaving is not an error", controller.openError)
    }
}
