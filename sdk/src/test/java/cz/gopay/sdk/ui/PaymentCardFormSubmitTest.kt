package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.ui.utils.CardValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The shared submit of a form, and the older submit function the form hands to `onFormReady`
 * on top of it. The submit waits on `GET /cards/public-key` every time, so a host cancelling it
 * mid-flight is routine, and cancellation has to reach the caller's scope, not the form's error
 * callback. A form that fails validation is the same VALIDATION_007 `submitCardForm` throws,
 * returned instead, and reported to `errorCallback` once like a thrown one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentCardFormSubmitTest {

    private val results = mutableListOf<CardEncryptionResult>()
    private val validations = mutableListOf<CardValidator.CardValidationResult>()
    private val reported = mutableListOf<GopaySDKException>()
    private var resets = 0
    private var attempts = 0
    private val owner = Any()

    private val valid = CardFormData(cardNumberDigits = "4532015112830366", expirationDigits = "1230", cvv = "123")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        ErrorReporter.setErrorCallback { reported.add(it) }
        CardFormStore.register(FORM, owner, CardFormHandlers(reset = { resets++ }, submitAttempted = { attempts++ }))
    }

    @After
    fun clear() {
        CardFormStore.unregister(FORM, owner)
        ErrorReporter.clear()
        Dispatchers.resetMain()
    }

    private suspend fun submit(data: CardFormData = valid, encrypt: suspend (CardData) -> String): CardEncryptionResult {
        CardFormStore.update(FORM, data)
        return submitCardDataImpl(
            formId = FORM,
            onEncryptionComplete = { results += it },
            onValidationError = { validations += it },
            encrypt = encrypt
        )
    }

    @Test
    fun `the shared submit returns the JWE, empties the form and reports nothing`() = runBlocking {
        CardFormStore.update(FORM, valid)
        var encrypted: CardData? = null

        val jwe = submitCardForm(FORM) { encrypted = it; "header.key.iv.ciphertext.tag" }

        assertEquals("header.key.iv.ciphertext.tag", jwe)
        assertEquals(CardData("4532015112830366", "12", "2030", "123"), encrypted)
        assertTrue("the SDK's copy is gone", CardFormStore.storedFormIds().isEmpty())
        assertEquals("the fields were emptied", 1, resets)
        assertEquals("the form was told a submit ran", 1, attempts)
        assertTrue("nothing to report", reported.isEmpty())
    }

    @Test
    fun `the shared submit refuses an empty form and keeps an invalid one`() {
        CardFormStore.update(FORM, CardFormData())
        val empty = assertThrows(GopaySDKException::class.java) {
            runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
        }
        assertEquals(CardEncryptionResult.NO_CARD_DATA_MESSAGE, empty.message)

        CardFormStore.update(FORM, valid.copy(cvv = "12"))
        val invalid = assertThrows(GopaySDKException::class.java) {
            runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
        }
        assertEquals(CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE, invalid.message)
        assertEquals(setOf(FORM), CardFormStore.storedFormIds())
        assertEquals(0, resets)
        assertEquals("ON_SUBMIT shows its errors whether or not the submit passes", 2, attempts)
        assertTrue("the core reports nothing; the public call does", reported.isEmpty())
    }

    @Test
    fun `a cancelled submit rethrows and reports nothing`() {
        val cancel = CancellationException("host left the screen")

        val e = assertThrows(CancellationException::class.java) {
            runBlocking { submit { throw cancel } }
        }

        assertSame(cancel, e)
        assertTrue("no callback for a cancellation", results.isEmpty())
        assertEquals("the card stays in the form", 0, resets)
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a failed encryption still reports an error`() = runBlocking {
        val failure = IllegalStateException("key fetch failed")

        val result = submit { throw failure }

        assertSame(failure, (result as CardEncryptionResult.Error).exception)
        assertEquals(listOf(result), results)
        assertEquals(0, resets)
    }

    @Test
    fun `a successful encryption reports the JWE and clears the form`() = runBlocking {
        val result = submit { "header.key.iv.ciphertext.tag" }

        assertEquals(CardEncryptionResult.Success("header.key.iv.ciphertext.tag"), result)
        assertEquals(listOf(result), results)
        assertEquals(1, resets)
        assertTrue(validations.isEmpty())
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `an empty form is VALIDATION_007 with the no-data message, reported once`() = runBlocking {
        val result = submit(CardFormData()) { throw AssertionError("must not encrypt") }

        val error = result as CardEncryptionResult.Error
        assertEquals(CardEncryptionResult.NO_CARD_DATA_MESSAGE, error.message)
        val e = error.exception as GopaySDKException
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
        assertEquals(1, validations.size)
        assertEquals("an error the SDK returns is reported like one it throws", listOf(e), reported)
        assertEquals(0, resets)
    }

    @Test
    fun `an invalid form is VALIDATION_007 with the iOS message`() = runBlocking {
        val result = submit(valid.copy(cvv = "12")) { throw AssertionError("must not encrypt") }

        val error = result as CardEncryptionResult.Error
        assertEquals(CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE, error.message)
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, (error.exception as GopaySDKException).errorCode)
        assertTrue(validations.single().cardNumber.isValid)
        assertTrue(!validations.single().cvv.isValid)
        assertEquals(1, reported.size)
        assertNull(results.filterIsInstance<CardEncryptionResult.Success>().firstOrNull())
    }

    private companion object {
        const val FORM = "submit-test"
    }
}
