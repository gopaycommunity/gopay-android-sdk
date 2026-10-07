package cz.gopay.sdk.ui

import cz.gopay.sdk.model.CardData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The submit waits on `GET /cards/public-key` every time, so a host cancelling it mid-flight is
 * routine. Cancellation has to reach the caller's scope, not the form's error callback.
 */
class PaymentCardFormSubmitTest {

    private val results = mutableListOf<CardEncryptionResult>()
    private var resets = 0

    private suspend fun submit(encrypt: suspend (CardData) -> String) =
        submitCardDataImpl(
            cardNumberDigits = "4532015112830366",
            expirationDateDigits = "1230",
            cvv = "123",
            onEncryptionComplete = { results += it },
            onValidationError = null,
            resetForm = { resets++ },
            encrypt = encrypt
        )

    @Test
    fun `a cancelled submit rethrows and reports nothing`() {
        val cancel = CancellationException("host left the screen")

        val e = assertThrows(CancellationException::class.java) {
            runBlocking { submit { throw cancel } }
        }

        assertSame(cancel, e)
        assertTrue("no callback for a cancellation", results.isEmpty())
        assertEquals("the card stays in the form", 0, resets)
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
    }
}
