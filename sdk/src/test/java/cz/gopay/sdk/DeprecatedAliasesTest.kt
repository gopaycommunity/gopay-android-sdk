package cz.gopay.sdk

import android.app.Activity
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import cz.gopay.sdk.model.BankAccountInternationalDetails
import cz.gopay.sdk.model.BankAccountLocalDetails
import cz.gopay.sdk.model.BankTransferRecipient
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.GooglePayInfoResponse
import cz.gopay.sdk.model.GopayCardData
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentDetails
import cz.gopay.sdk.model.PaymentState
import cz.gopay.sdk.model.QrCodeList
import cz.gopay.sdk.model.QrInternationalBankAccount
import cz.gopay.sdk.model.QrLocalBankAccount
import cz.gopay.sdk.model.QrPaymentDetails
import cz.gopay.sdk.model.QrRecipient
import cz.gopay.sdk.model.QrRecipientAddress
import cz.gopay.sdk.model.QrRecipientBankAccount
import cz.gopay.sdk.model.RecipientAddress
import cz.gopay.sdk.model.RecipientBankAccount
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.ui.CardFormValidationDisplay
import cz.gopay.sdk.ui.PaymentCardFormValidationDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code written against 2.7 by the names 3.0 renames. That this file compiles is the first thing
 * it tests: every name here is a deprecated alias. The assertions pin that an alias is the new
 * name and nothing else. The wallet aliases on the session are tested where the session is,
 * `PaymentSessionSharedApiTest` and `PaymentSessionTransportTest`.
 *
 * What an alias cannot cover is not here, because it does not compile: `Environment.SANDBOX`
 * and `Environment.DEVELOPMENT(…)` (Kotlin does not reach nested classes through a type alias),
 * `GopayConfig.copy(debug = …)`, `GopayConfig(environment, clientId, shareableKey, debug = …)`
 * with more than the environment given by position, `DEVELOPMENT(customUrl = …)`, and
 * `gwUrl = …` as a named argument of the `PaymentDetails` constructor or of its `copy`.
 */
@Suppress("DEPRECATION")
class DeprecatedAliasesTest {

    @Test
    fun `GopayConfig with debug is the config with debugLoggingEnabled`() {
        val named = GopayConfig(environment = GopayEnvironment.SANDBOX, clientId = "c", debug = true)
        val mixed = GopayConfig(GopayEnvironment.SANDBOX, debug = true)

        assertEquals(GopayConfig(GopayEnvironment.SANDBOX, clientId = "c", debugLoggingEnabled = true), named)
        assertEquals(GopayConfig(GopayEnvironment.SANDBOX, debugLoggingEnabled = true), mixed)
        assertTrue(named.debug)
        assertEquals(false, GopayConfig(GopayEnvironment.SANDBOX).debug)
    }

    @Test
    fun `the deprecated config constructor keeps every other argument`() {
        val callback: (GopaySDKException) -> Unit = {}
        val old = GopayConfig(
            environment = GopayEnvironment.PRODUCTION,
            clientId = "c",
            shareableKey = "k",
            requestTimeoutMs = 5000,
            debug = true,
            errorCallback = callback,
            locale = "de",
            customLocales = emptyMap()
        )

        val new = GopayConfig(
            environment = GopayEnvironment.PRODUCTION,
            clientId = "c",
            shareableKey = "k",
            requestTimeoutMs = 5000,
            debugLoggingEnabled = true,
            errorCallback = callback,
            locale = "de",
            customLocales = emptyMap()
        )
        assertEquals(new, old)
    }

    @Test
    fun `apiBaseUrl is baseUrl on the config and on the environment`() {
        val environment: Environment = GopayEnvironment.SANDBOX
        val config = GopayConfig(environment)

        assertEquals(GopayEnvironment.SANDBOX.baseUrl, environment.apiBaseUrl)
        assertEquals(config.baseUrl, config.apiBaseUrl)
    }

    @Test
    fun `Environment as a type is GopayEnvironment`() {
        fun describe(environment: Environment): String = environment.baseUrl

        assertEquals(GopayEnvironment.PRODUCTION.baseUrl, describe(GopayEnvironment.PRODUCTION))
    }

    @Test
    fun `errorCode is code and getHttpStatusCode is httpStatus`() {
        val e = GopaySDKException(
            code = GopayErrorCodes.NETWORK_CLIENT_ERROR,
            message = "HTTP 404",
            httpContext = HttpErrorContext(statusCode = 404)
        )

        assertEquals(e.code, e.errorCode)
        assertEquals(404, e.getHttpStatusCode())
        assertEquals(e.httpStatus, e.getHttpStatusCode())
        assertEquals(null, GopaySDKException(code = "X", message = "m").getHttpStatusCode())
    }

    @Test
    fun `GopaySDKException with errorCode is the exception with code`() {
        val cause = IllegalStateException("boom")
        val context = HttpErrorContext(statusCode = 500)

        val e = GopaySDKException(
            errorCode = GopayErrorCodes.NETWORK_SERVER_ERROR,
            message = "HTTP 500",
            cause = cause,
            httpContext = context
        )

        assertEquals(GopayErrorCodes.NETWORK_SERVER_ERROR, e.code)
        assertEquals("HTTP 500", e.message)
        assertSame(cause, e.cause)
        assertSame(context, e.httpContext)
        assertEquals("X", GopaySDKException(errorCode = "X", message = "m").code)
    }

    @Test
    fun `CardFormValidationDisplay is PaymentCardFormValidationDisplay, entries included`() {
        val mode: CardFormValidationDisplay = CardFormValidationDisplay.ON_SUBMIT

        assertSame(PaymentCardFormValidationDisplay.ON_SUBMIT, mode)
        assertSame(PaymentCardFormValidationDisplay.HIDDEN, CardFormValidationDisplay.HIDDEN)
        assertSame(PaymentCardFormValidationDisplay.LIVE, CardFormValidationDisplay.LIVE)
    }

    @Test
    fun `PaymentCreateResponse is PaymentDetails`() {
        val old: PaymentCreateResponse = PaymentCreateResponse(
            id = "1",
            orderNumber = "o-1",
            state = PaymentState.CREATED,
            amount = 100,
            currency = Currency.CZK,
            customer = PaymentCustomer(email = "a@b.cz"),
            gatewayUrl = "https://gw.example/1"
        )

        val new: PaymentDetails = old
        assertEquals(new, old)
    }

    @Test
    fun `gwUrl is gatewayUrl`() {
        val payment = PaymentDetails(
            id = "1",
            orderNumber = "o-1",
            state = PaymentState.CREATED,
            amount = 100,
            currency = Currency.CZK,
            customer = PaymentCustomer(email = "a@b.cz"),
            gatewayUrl = "https://gw.example/1"
        )

        assertEquals("https://gw.example/1", payment.gwUrl)
        assertEquals(payment.gatewayUrl, payment.gwUrl)
        // The alias is not a second field: the JSON still has the one `gw_url`.
        val json = NetworkModule.moshi.adapter(PaymentDetails::class.java).toJson(payment)
        assertTrue(json, "\"gw_url\":\"https://gw.example/1\"" in json)
        assertTrue(json, "gwUrl" !in json && "gatewayUrl" !in json)
    }

    @Test
    fun `CardData is GopayCardData`() {
        val old: CardData = CardData(cardPan = "4444444444444448", expMonth = "12", expYear = "28", cvv = "123")

        val new: GopayCardData = old
        assertEquals(GopayCardData("4444444444444448", "12", "28", "123"), new)
        assertEquals("still masked under either name", "GopayCardData(cardPan=****4448, expMonth=12, expYear=28, cvv=***)", old.toString())
    }

    @Test
    fun `the QR models of 2_7 are the Qr models`() {
        val local: BankAccountLocalDetails =
            BankAccountLocalDetails(prefix = "000000", accountNumber = "2600000001", bankCode = "2010", variableSymbol = "1")
        val international: BankAccountInternationalDetails = BankAccountInternationalDetails(iban = "CZ00")
        val account: RecipientBankAccount = RecipientBankAccount(local = local, international = international)
        val address: RecipientAddress = RecipientAddress(city = "Praha")
        val recipient: BankTransferRecipient = BankTransferRecipient(name = "GoPay", bankAccount = account, address = address)

        val new = QrRecipient(
            name = "GoPay",
            bankAccount = QrRecipientBankAccount(
                local = QrLocalBankAccount("000000", "2600000001", "2010", "1"),
                international = QrInternationalBankAccount(iban = "CZ00")
            ),
            address = QrRecipientAddress(city = "Praha")
        )
        assertEquals(new, recipient)

        // What 2.7 code read from the details still reads, by the old type names.
        val details = QrPaymentDetails(amount = 100, currency = Currency.CZK, recipient = recipient, qrCode = QrCodeList())
        val readLocal: BankAccountLocalDetails? = details.recipient.bankAccount?.local
        assertEquals("2600000001", readLocal?.accountNumber)
    }

    @Test
    fun `isGooglePayAvailable still takes an activity and the wallet info`() {
        // Compiled, not run: the check needs Google Play services.
        val check: suspend GopaySDK.(Activity, GooglePayInfoResponse) -> Boolean = GopaySDK::isGooglePayAvailable

        assertTrue(check.toString().isNotEmpty())
    }
}
