package cz.gopay.sdk.ui

import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.CardScheme
import cz.gopay.sdk.model.CardServiceType
import cz.gopay.sdk.model.EncryptedCardPayloadWithMetadata
import cz.gopay.sdk.model.GopayCardData
import cz.gopay.sdk.service.CardMetadataLookup
import cz.gopay.sdk.testing.HangingPublicApi
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The submit behind `GopaySDK.submitCardFormWithMetadata`: the JWE of `submitCardForm` with the
 * card's metadata looked up beside the encryption. The lookup can only add to the result; it
 * never fails the submit, delays it past its own limit or changes what happens to the form.
 * Time is the test scheduler's and every wait is a gate. The iOS SDK tests the same cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentCardFormSubmitWithMetadataTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val reported = mutableListOf<GopaySDKException>()
    private var resets = 0
    private var attempts = 0
    private val owner = Any()
    private val defaultWarnSink = SdkLog.warnSink
    private val warnings = mutableListOf<String>()

    private val valid = CardFormData(cardNumberDigits = "4444444444444448", expirationDigits = "1230", cvv = "123")
    private val visa = CardMetadata(
        scheme = CardScheme.VISA, corporate = false, brand = "GOLD",
        serviceType = CardServiceType.DEBIT, issuer = "Example Bank", issuerCountry = "CZE"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        SdkLog.warnSink = { warnings.add(it) }
        ErrorReporter.setErrorCallback { reported.add(it) }
        CardFormStore.register(FORM, owner, CardFormHandlers(reset = { resets++ }, submitAttempted = { attempts++ }))
    }

    @After
    fun clear() {
        CardFormStore.unregister(FORM, owner)
        ErrorReporter.clear()
        SdkLog.warnSink = defaultWarnSink
        Dispatchers.resetMain()
    }

    private fun assertFormEmptied() {
        assertTrue("the SDK's copy is gone", CardFormStore.storedFormIds().isEmpty())
        assertEquals("the fields were emptied", 1, resets)
    }

    private fun assertCardKept() {
        assertEquals("the card stays in the form", setOf(FORM), CardFormStore.storedFormIds())
        assertEquals(0, resets)
    }

    @Test
    fun `the submit returns the JWE with the metadata and empties the form`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        var encrypted: GopayCardData? = null
        var lookedUp: String? = null

        val result = submitCardFormWithMetadata(
            FORM,
            encrypt = { encrypted = it; JWE },
            lookUp = { lookedUp = it; visa }
        )

        assertEquals(EncryptedCardPayloadWithMetadata(encryptedPayload = JWE, cardMetadata = visa), result)
        assertEquals(GopayCardData("4444444444444448", "12", "2030", "123"), encrypted)
        assertEquals("the lookup is asked about the card of the form", "4444444444444448", lookedUp)
        assertFormEmptied()
        assertEquals("the form was told a submit ran", 1, attempts)
        assertTrue("nothing to report", reported.isEmpty())
    }

    @Test
    fun `a lookup that finds nothing returns the JWE without metadata and empties the form`() = runTest {
        CardFormStore.update(FORM, owner, valid)

        val result = submitCardFormWithMetadata(FORM, encrypt = { JWE }, lookUp = { null })

        assertEquals(JWE, result.encryptedPayload)
        assertNull(result.cardMetadata)
        assertFormEmptied()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a lookup that throws is no metadata, never a failed submit`() = runTest {
        CardFormStore.update(FORM, owner, valid)

        val result = submitCardFormWithMetadata(FORM, encrypt = { JWE }, lookUp = { error("lookup broke") })

        assertEquals(EncryptedCardPayloadWithMetadata(JWE, null), result)
        assertFormEmptied()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a lookup cancelled by anyone but the caller is no metadata, never a cancelled submit`() = runTest {
        CardFormStore.update(FORM, owner, valid)

        val result = submitCardFormWithMetadata(
            FORM,
            encrypt = { JWE },
            lookUp = { throw CancellationException("the HTTP client gave up") }
        )

        assertEquals(EncryptedCardPayloadWithMetadata(JWE, null), result)
        assertFormEmptied()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `the lookup runs beside the encryption and the result waits for both`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        val key = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<CardMetadata?>()
        var encrypting = false
        var lookingUp = false

        val submit = async {
            submitCardFormWithMetadata(
                FORM,
                encrypt = { encrypting = true; key.await(); JWE },
                lookUp = { lookingUp = true; answer.await() }
            )
        }
        runCurrent()

        assertTrue("the encryption started", encrypting)
        assertTrue("the lookup started while the key was still on its way", lookingUp)

        key.complete(Unit)
        runCurrent()
        assertFalse("the JWE alone is not the result", submit.isCompleted)
        assertCardKept()

        answer.complete(visa)
        assertEquals(EncryptedCardPayloadWithMetadata(JWE, visa), submit.await())
        assertFormEmptied()
        assertEquals("nothing waited on a clock", 0L, currentTime)
    }

    @Test
    fun `a lookup that answers first waits for the encryption`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        val key = CompletableDeferred<Unit>()

        val submit = async {
            submitCardFormWithMetadata(FORM, encrypt = { key.await(); JWE }, lookUp = { visa })
        }
        runCurrent()
        assertFalse(submit.isCompleted)
        assertCardKept()

        key.complete(Unit)

        assertEquals(EncryptedCardPayloadWithMetadata(JWE, visa), submit.await())
    }

    @Test
    fun `a lookup slower than three seconds returns the JWE without metadata`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        val lookup = CardMetadataLookup(publicApi = { HangingPublicApi() })

        val result = submitCardFormWithMetadata(FORM, encrypt = { JWE }, lookUp = { lookup.lookUp(it) })

        assertEquals(EncryptedCardPayloadWithMetadata(JWE, null), result)
        assertEquals("the lookup's own limit, counted from the submit", 3_000L, currentTime)
        assertEquals(listOf(CardMetadataLookup.warning("no answer in 3000 ms")), warnings)
        assertFormEmptied()
        assertTrue("a slow lookup is nobody's error", reported.isEmpty())
    }

    @Test
    fun `a failed encryption cancels the lookup, throws what it threw and keeps the card`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        val key = CompletableDeferred<Unit>()
        val failure = GopaySDKException(code = GopayErrorCodes.NETWORK_IO_ERROR, message = "key fetch failed")
        var lookupCancelled = false

        val submit = async {
            runCatching {
                submitCardFormWithMetadata(
                    FORM,
                    encrypt = { key.await(); throw failure },
                    lookUp = {
                        try {
                            awaitCancellation()
                        } catch (e: CancellationException) {
                            lookupCancelled = true
                            throw e
                        }
                    }
                )
            }.exceptionOrNull()
        }
        runCurrent()
        key.complete(Unit)

        assertSame("the encryption's own error, as submitCardForm throws it", failure, submit.await())
        assertTrue(lookupCancelled)
        assertEquals("nothing waited for the lookup's limit", 0L, currentTime)
        assertCardKept()
    }

    @Test
    fun `a submit cancelled while only the lookup is left keeps the card`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        var encrypted = false
        var returned = false

        val submit = launch {
            submitCardFormWithMetadata(
                FORM,
                encrypt = { encrypted = true; JWE },
                lookUp = { awaitCancellation() }
            )
            returned = true
        }
        runCurrent()
        assertTrue("the JWE exists, only the lookup is still running", encrypted)

        submit.cancel(CancellationException("host left the screen"))
        submit.join()

        assertTrue(submit.isCancelled)
        assertFalse(returned)
        assertCardKept()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `a cancelled submit ends both, rethrows and keeps the card`() = runTest {
        CardFormStore.update(FORM, owner, valid)
        var encryptionCancelled = false
        var lookupCancelled = false
        var returned = false

        val submit = launch {
            submitCardFormWithMetadata(
                FORM,
                encrypt = {
                    try {
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        encryptionCancelled = true
                        throw e
                    }
                },
                lookUp = {
                    try {
                        awaitCancellation()
                    } catch (e: CancellationException) {
                        lookupCancelled = true
                        throw e
                    }
                }
            )
            returned = true
        }
        runCurrent()

        submit.cancel(CancellationException("host left the screen"))
        submit.join()

        assertTrue(submit.isCancelled)
        assertFalse(returned)
        assertTrue(encryptionCancelled)
        assertTrue(lookupCancelled)
        assertCardKept()
        assertTrue(reported.isEmpty())
    }

    @Test
    fun `an empty, invalid or unsupported card is refused before anything is encrypted or looked up`() = runTest {
        val cases = listOf(
            CardFormData() to GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY,
            valid.copy(cvv = "12") to GopayErrorCodes.VALIDATION_INVALID_INPUT,
            // Discover: sixteen digits that pass the Luhn check, of a brand the form does not accept.
            valid.copy(cardNumberDigits = "6011000000000004") to GopayErrorCodes.VALIDATION_INVALID_INPUT
        )
        for ((data, code) in cases) {
            CardFormStore.update(FORM, owner, data)

            val e = runCatching {
                submitCardFormWithMetadata(
                    FORM,
                    encrypt = { throw AssertionError("must not encrypt") },
                    lookUp = { throw AssertionError("must not look up") }
                )
            }.exceptionOrNull()

            assertEquals(code, (e as GopaySDKException).code)
        }
        assertEquals(0, resets)
        assertEquals("ON_SUBMIT shows its errors whether or not the submit passes", 3, attempts)
        assertTrue("the core reports nothing; the public call does", reported.isEmpty())
    }

    @Test
    fun `the result prints its metadata but never the JWE`() {
        val printed = EncryptedCardPayloadWithMetadata(JWE, visa).toString()

        assertFalse(printed, printed.contains(JWE))
        assertTrue(printed, printed.contains("GOLD"))
    }

    private companion object {
        const val FORM = "submit-with-metadata-test"
        const val JWE = "header.key.iv.ciphertext.tag"
    }
}
