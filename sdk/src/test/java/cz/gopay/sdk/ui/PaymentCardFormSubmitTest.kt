package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The submit of a form behind `GopaySDK.submitCardForm`. It waits on `GET /cards/public-key`
 * every time, so a host cancelling it mid-flight is routine, and the cancellation has to reach
 * the caller's scope with the card still in the form.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentCardFormSubmitTest {

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

    @Test
    fun `the submit returns the JWE, empties the form and reports nothing`() = runBlocking {
        CardFormStore.update(FORM, owner, valid)
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
    fun `the submit refuses an empty form and keeps an invalid one`() {
        CardFormStore.update(FORM, owner, CardFormData())
        val empty = assertThrows(GopaySDKException::class.java) {
            runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
        }
        assertEquals(GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, empty.code)
        assertEquals(NO_CARD_DATA_MESSAGE, empty.message)

        CardFormStore.update(FORM, owner, valid.copy(cvv = "12"))
        val invalid = assertThrows(GopaySDKException::class.java) {
            runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
        }
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, invalid.code)
        assertEquals(INVALID_CARD_FORM_DATA_MESSAGE, invalid.message)
        assertEquals(setOf(FORM), CardFormStore.storedFormIds())
        assertEquals(0, resets)
        assertEquals("ON_SUBMIT shows its errors whether or not the submit passes", 2, attempts)
        assertTrue("the core reports nothing; the public call does", reported.isEmpty())
    }

    @Test
    fun `a cancelled submit rethrows and keeps the card`() {
        CardFormStore.update(FORM, owner, valid)
        val cancel = CancellationException("host left the screen")

        val e = assertThrows(CancellationException::class.java) {
            runBlocking { submitCardForm(FORM) { throw cancel } }
        }

        assertSame(cancel, e)
        assertEquals("the card stays in the form", 0, resets)
        assertEquals(setOf(FORM), CardFormStore.storedFormIds())
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a failed encryption throws what the encryption threw and keeps the card`() {
        CardFormStore.update(FORM, owner, valid)
        val failure = IllegalStateException("key fetch failed")

        val e = assertThrows(IllegalStateException::class.java) {
            runBlocking { submitCardForm(FORM) { throw failure } }
        }

        assertSame(failure, e)
        assertEquals(0, resets)
        assertEquals(setOf(FORM), CardFormStore.storedFormIds())
    }

    @Test
    fun `a form that left the screen, or an id no form has, is VALIDATION_008`() {
        CardFormStore.update(FORM, owner, valid)
        CardFormStore.unregister(FORM, owner)

        for (id in listOf(FORM, "no-such-form")) {
            val e = assertThrows(id, GopaySDKException::class.java) {
                runBlocking { submitCardForm(id) { throw AssertionError("must not encrypt") } }
            }
            assertEquals(id, GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, e.code)
        }
    }

    @Test
    fun `a form with one field filled is invalid, not empty`() {
        CardFormStore.update(FORM, owner, CardFormData(cvv = "1"))

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { submitCardForm(FORM) { throw AssertionError("must not encrypt") } }
        }

        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.code)
    }

    private companion object {
        const val FORM = "submit-test"
    }
}
