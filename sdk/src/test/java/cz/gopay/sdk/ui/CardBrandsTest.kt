package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.telemetry.CardFormProgress
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.ui.utils.CardBrand
import cz.gopay.sdk.ui.utils.CardNumberInputValidator
import cz.gopay.sdk.ui.utils.CardValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The brands the card form accepts, [CardBrand], and what the form does with a number of any
 * other: the same as with a number that fails the Luhn check. The one test of the patterns;
 * when GoPay refines them, this table changes with them. The iOS SDK tests the same numbers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardBrandsTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val owner = Any()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        CardFormStore.register(FORM, owner, CardFormHandlers(reset = {}, submitAttempted = {}))
    }

    @After
    fun clear() {
        CardFormStore.unregister(FORM, owner)
        Dispatchers.resetMain()
    }

    /** Sixteen digits that start with [prefix] and pass the Luhn check; a whole number as it is. */
    private fun number(prefix: String): String {
        if (prefix.length == 16) return prefix
        val body = prefix.padEnd(15, '0')
        return (0..9).map { body + it }.first { CardNumberInputValidator.isValidLuhn(it) }
    }

    private val accepted = listOf(
        "4444444444444448" to CardBrand.VISA,
        "4532015112830366" to CardBrand.VISA,
        "4" to CardBrand.VISA,
        "4026" to CardBrand.VISA_ELECTRON,
        "417500" to CardBrand.VISA_ELECTRON,
        "4405" to CardBrand.VISA_ELECTRON,
        "4508" to CardBrand.VISA_ELECTRON,
        "4844" to CardBrand.VISA_ELECTRON,
        "4913" to CardBrand.VISA_ELECTRON,
        "4917" to CardBrand.VISA_ELECTRON,
        "5555555555554444" to CardBrand.MASTERCARD,
        "51" to CardBrand.MASTERCARD,
        "55" to CardBrand.MASTERCARD,
        "222100" to CardBrand.MASTERCARD,
        "23" to CardBrand.MASTERCARD,
        "26" to CardBrand.MASTERCARD,
        "271999" to CardBrand.MASTERCARD,
        "272099" to CardBrand.MASTERCARD,
        "50" to CardBrand.MAESTRO,
        "56" to CardBrand.MAESTRO,
        "59" to CardBrand.MAESTRO,
        "600" to CardBrand.MAESTRO,
        "60115" to CardBrand.MAESTRO,
        "61" to CardBrand.MAESTRO,
        "622125" to CardBrand.MAESTRO,
        "63" to CardBrand.MAESTRO,
        "643" to CardBrand.MAESTRO,
        "6759" to CardBrand.MAESTRO
    )

    private val refused = listOf(
        "60110" to "Discover",
        "644" to "Discover",
        "65" to "Discover",
        "622126" to "UnionPay",
        "3528" to "JCB",
        "2200" to "Mir",
        "222099" to "below the Mastercard range",
        "2721" to "above the Mastercard range",
        "1" to "UATP",
        "9792" to "Troy"
    )

    @Test
    fun `a number of an accepted brand is that brand and passes the form`() {
        for ((prefix, brand) in accepted) {
            val digits = number(prefix)
            assertEquals(prefix, brand, CardBrand.of(digits))
            assertTrue(prefix, CardValidator.validateCardNumber(digits).isValid)
            assertTrue(prefix, CardValidator.completesCardNumber(digits))
        }
    }

    @Test
    fun `Visa Electron is tried before Visa`() {
        assertEquals(listOf(CardBrand.VISA_ELECTRON, CardBrand.VISA), CardBrand.values().take(2))
        assertTrue(CardBrand.VISA.matches(number("4026")))
    }

    @Test
    fun `a number of any other brand has no brand and fails like a number that fails the Luhn check`() {
        val luhnFailure = CardValidator.validateCardNumber("4532015112830365")
        assertFalse(luhnFailure.isValid)

        for ((prefix, name) in refused) {
            val digits = number(prefix)
            assertNull(name, CardBrand.of(digits))
            assertEquals(name, luhnFailure, CardValidator.validateCardNumber(digits))
        }
    }

    @Test
    fun `the focus stays in the card number when its brand is not accepted`() {
        // The form moves the focus to the expiration on completesCardNumber alone.
        for ((prefix, name) in refused) assertFalse(name, CardValidator.completesCardNumber(number(prefix)))
        assertFalse("still being typed", CardValidator.completesCardNumber("4444"))
        assertFalse("fails the Luhn check", CardValidator.completesCardNumber("4532015112830365"))
    }

    @Test
    fun `the form's steps see a number of a brand it does not accept as partial, never complete`() {
        for ((prefix, name) in refused) {
            val progress = CardFormData(number(prefix), "1230", "123").progress()
            assertEquals(name, CardFormProgress.State.PARTIAL, progress.pan)
            assertEquals(name, CardFormProgress.State.COMPLETE, progress.cvv)
        }
        assertEquals(
            CardFormProgress.State.COMPLETE,
            CardFormData("4444444444444448", "1230", "123").progress().pan
        )
    }

    @Test
    fun `digits of another script are not card number digits`() {
        // "4" and fifteen Arabic-Indic digits: sixteen digits to Char.isDigit(), one to the form.
        val arabicIndic = "4" + "\u0664".repeat(14) + "\u0668"
        val fullwidth = "\uFF14".repeat(15) + "\uFF18"

        for (number in listOf(arabicIndic, fullwidth)) {
            assertFalse(CardValidator.validateCardNumber(number).isValid)
            assertFalse(CardValidator.completesCardNumber(number))
            assertFalse(CardNumberInputValidator.isComplete(number))
            assertFalse(CardNumberInputValidator.isValidLength(number))
            assertFalse(CardNumberInputValidator.isValidLuhn(number))
        }
        // The field keeps only the ASCII digits of what is typed or pasted into it.
        assertEquals("4", CardNumberInputValidator.validateInput(arabicIndic))
        assertEquals("", CardNumberInputValidator.validateInput(fullwidth))
        assertEquals("4444", CardNumberInputValidator.validateInput("44\u0664\u0664 44"))
        assertEquals("4", CardNumberInputValidator.getDigitsOnly(arabicIndic))
    }

    @Test
    fun `the form shows the message of an invalid number for a brand it does not accept`() {
        val strings = GopayLocales.CS
        fun error(digits: String, mode: PaymentCardFormValidationDisplay, attempted: Boolean) = CardFormErrors.of(
            data = CardFormData(cardNumberDigits = digits, expirationDigits = "1230", cvv = "123"),
            strings = strings, mode = mode, submitAttempted = attempted,
            cardNumberLeft = false, expirationLeft = false, cvvLeft = false
        ).cardNumber

        for ((prefix, name) in refused) {
            val digits = number(prefix)
            // At the same moment as a Luhn failure: once the number is whole in LIVE, on submit otherwise.
            assertEquals(name, strings.panErrorPattern, error(digits, PaymentCardFormValidationDisplay.LIVE, attempted = false))
            assertEquals(name, strings.panErrorPattern, error(digits, PaymentCardFormValidationDisplay.ON_SUBMIT, attempted = true))
            assertNull(name, error(digits, PaymentCardFormValidationDisplay.ON_SUBMIT, attempted = false))
        }
        assertEquals(
            strings.panErrorPattern,
            error("4532015112830365", PaymentCardFormValidationDisplay.LIVE, attempted = false)
        )
    }

    @Test
    fun `the submit of a brand the form does not accept is VALIDATION_007 and keeps the card`() {
        for ((prefix, name) in refused) {
            CardFormStore.update(FORM, owner, CardFormData(number(prefix), "1230", "123"))

            val e = assertThrows(name, GopaySDKException::class.java) {
                runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
            }

            assertEquals(name, GopayErrorCodes.VALIDATION_INVALID_INPUT, e.code)
            assertEquals(name, "VALIDATION_007", e.code)
            assertEquals(name, INVALID_CARD_FORM_DATA_MESSAGE, e.message)
            assertEquals(name, setOf(FORM), CardFormStore.storedFormIds())
        }
    }

    private companion object {
        const val FORM = "brands-test"
    }
}
