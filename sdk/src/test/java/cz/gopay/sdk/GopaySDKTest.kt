package cz.gopay.sdk

import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.ui.CardEncryptionResult
import cz.gopay.sdk.ui.CardFormData
import cz.gopay.sdk.ui.CardFormHandlers
import cz.gopay.sdk.ui.CardFormStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the GopaySDK singleton lifecycle and configuration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GopaySDKTest {

    private val formOwner = Any()

    @Before
    fun mainThread() {
        // The card form's handlers run on the main thread; on the JVM that is this one.
        Dispatchers.setMain(Dispatchers.Unconfined)
        // Operational data stays on this machine.
        TelemetryCapture().install()
    }

    @After
    fun tearDown() {
        // Reset SDK singleton between tests.
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)

        // GopaySDK.initialize() also mutates GopayLocales (a separate JVM-wide singleton); undo
        // that too, or a non-default locale/customLocales config here would leak into other tests.
        GopayLocales.clearCustom()
        GopayLocales.setDefaultLocale(null)
        ErrorReporter.clear()
        TelemetryCapture.reset()
        CardFormStore.unregister("form-1", formOwner)
        CardFormStore.unregister("form-2", formOwner)
        Dispatchers.resetMain()
    }

    /** Every error the SDK reported to the callback a config here registered. */
    private val reported = mutableListOf<GopaySDKException>()

    @Test
    fun testInitialization() {
        val config = GopayConfig(environment = Environment.SANDBOX, debug = true)

        GopaySDK.initialize(config)

        assertTrue(GopaySDK.isInitialized())
    }

    @Test
    fun testGetInstanceBeforeInitialization() {
        // CONFIG_001 is thrown before initialize, when no callback is installed yet; a callback
        // an earlier initialize left behind does not hear of it either.
        ErrorReporter.setErrorCallback { reported.add(it) }

        val exception = assertThrows(GopaySDKException::class.java) {
            GopaySDK.getInstance()
        }

        assertEquals(GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED, exception.errorCode)
        assertTrue(exception.message!!.contains("has not been initialized"))
        assertTrue("never reported", reported.isEmpty())
    }

    @Test
    fun testConfigurationPassing() {
        val sandboxConfig = GopayConfig(environment = Environment.SANDBOX, debug = true)

        GopaySDK.initialize(sandboxConfig)
        val sdk = GopaySDK.getInstance()

        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertTrue(sdk.isDebugEnabled())
        assertEquals(Environment.SANDBOX.apiBaseUrl, sdk.config.apiBaseUrl)
    }

    @Test
    fun testConfigurationWithCustomTimeout() {
        val customTimeoutMs = 60_000L
        val config = GopayConfig(
            environment = Environment.PRODUCTION,
            requestTimeoutMs = customTimeoutMs
        )

        GopaySDK.initialize(config)

        assertEquals(customTimeoutMs, GopaySDK.getInstance().config.requestTimeoutMs)
    }

    @Test
    fun testReinitializationOverridesConfig() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        GopaySDK.initialize(GopayConfig(environment = Environment.PRODUCTION))

        assertEquals(Environment.PRODUCTION, GopaySDK.getInstance().config.environment)
    }

    @Test
    fun testDebugEnabled() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, debug = true))

        assertTrue(GopaySDK.getInstance().isDebugEnabled())
    }

    @Test
    fun testDebugDisabled() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, debug = false))

        assertFalse(GopaySDK.getInstance().isDebugEnabled())
    }

    @Test
    fun `an empty paymentId or paymentSecret is VALIDATION_007 before anything is sent`() {
        // The iOS SDK validates the pair the same way; a bare IllegalArgumentException was the
        // one platform-only exception on this path. SANDBOX is never reached: the check comes
        // before the token request.
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, errorCallback = { reported.add(it) }))
        val sdk = GopaySDK.getInstance()

        for ((id, secret) in listOf("" to "secret", " " to "secret", "pay-1" to "", "pay-1" to "\t")) {
            val e = assertThrows("'$id' / '$secret'", GopaySDKException::class.java) {
                runBlocking { sdk.startPaymentSession(id, secret) }
            }
            assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
            assertTrue(e.message!!.endsWith("must not be empty"))
        }
        assertEquals("each one reached the callback once", 4, reported.size)
    }

    @Test
    fun `card data that fails validation is VALIDATION_007 before the key is fetched`() {
        // No clientId/shareableKey on purpose: a card that got past validation would fail with
        // AUTH_011 on the key fetch, so VALIDATION_007 proves the check runs first.
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        val sdk = GopaySDK.getInstance()
        val good = CardData(cardPan = "4444444444444448", expMonth = "12", expYear = "28", cvv = "123")
        val bad = mapOf(
            "Card PAN cannot be empty" to good.copy(cardPan = ""),
            "Card PAN must be 13-19 digits" to good.copy(cardPan = "444444444444"),
            "Card PAN must contain only digits" to good.copy(cardPan = "4444 4444 4444 4448"),
            "Expiration month must be 01-12" to good.copy(expMonth = "13"),
            "Expiration year must be 2-4 digits" to good.copy(expYear = "2"),
            "CVV must be 3-4 digits" to good.copy(cvv = "12")
        )

        for ((message, card) in bad) {
            val e = assertThrows(message, GopaySDKException::class.java) {
                runBlocking { sdk.encryptCardData(card) }
            }
            assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
            assertEquals(message, e.message)
        }
        // The rules are the same as on iOS: a 13-digit PAN and a 4-digit CVV pass validation and
        // reach the key fetch, which fails for the missing credentials, not the card.
        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { sdk.encryptCardData(good.copy(cardPan = "4444444444448", cvv = "1234", expYear = "2028")) }
        }
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.errorCode)
    }

    @Test
    fun `a development URL the HTTP client refuses is CONFIG_006 from initialize`() {
        // Environment.DEVELOPMENT checks the scheme only; Retrofit's own check used to throw a
        // bare IllegalArgumentException out of initialize.
        val e = assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(
                GopayConfig(
                    environment = Environment.DEVELOPMENT.create("http://gw.exa mple.com/api/"),
                    errorCallback = { reported.add(it) }
                )
            )
        }

        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.errorCode)
        assertTrue(e.cause is IllegalArgumentException)
        assertFalse("a failed initialize leaves no instance behind", GopaySDK.isInitialized())
        assertEquals("the callback from the same config hears of it once", listOf(e), reported)
    }

    @Test
    fun `a failed initialize leaves the earlier instance and callback in place`() {
        val earlier = mutableListOf<GopaySDKException>()
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, errorCallback = { earlier.add(it) }))

        val e = assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(
                GopayConfig(
                    environment = Environment.DEVELOPMENT.create("http://gw.exa mple.com/api/"),
                    errorCallback = { reported.add(it) }
                )
            )
        }

        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.errorCode)
        assertEquals("the failing config's own callback hears of it once", listOf(e), reported)
        assertEquals(Environment.SANDBOX, GopaySDK.getInstance().config.environment)
        // The earlier callback is still the installed one.
        val later = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().startPaymentSession("", "secret") }
        }
        assertEquals(listOf(later), earlier)
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `submitCardForm without a form on screen is VALIDATION_007 with the no-data message, once`() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, errorCallback = { reported.add(it) }))

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().submitCardForm() }
        }

        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.errorCode)
        assertEquals(CardEncryptionResult.NO_CARD_DATA_MESSAGE, e.message)
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `submitCardForm on an empty or invalid form is VALIDATION_007 and tells the form a submit ran`() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, errorCallback = { reported.add(it) }))
        var attempts = 0
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = {}, submitAttempted = { attempts++ }))
        val sdk = GopaySDK.getInstance()

        CardFormStore.update("form-1", CardFormData())
        val empty = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardForm("form-1") } }
        assertEquals(CardEncryptionResult.NO_CARD_DATA_MESSAGE, empty.message)

        CardFormStore.update("form-1", CardFormData("4532015112830366", "1230", "12"))
        val invalid = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardForm("form-1") } }
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, invalid.errorCode)
        assertEquals(CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE, invalid.message)

        assertEquals("ON_SUBMIT shows its errors whether or not the submit passes", 2, attempts)
        assertEquals(listOf(empty, invalid), reported)
        assertEquals("a failed submit keeps the data", setOf("form-1"), CardFormStore.storedFormIds())
    }

    @Test
    fun `submitCardForm hands a valid form to encryptCardData and keeps it when that fails`() {
        // No clientId/shareableKey: the key fetch fails with AUTH_011 before any request, which
        // proves the form passed validation and reached encryptCardData.
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX, errorCallback = { reported.add(it) }))
        var resets = 0
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update("form-1", CardFormData("4532015112830366", "1230", "123"))

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().submitCardForm() }
        }

        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.errorCode)
        assertEquals("reported once, by encryptCardData, not again by submitCardForm", listOf(e), reported)
        assertEquals("the card stays in the form after a failed encryption", 0, resets)
        assertEquals(setOf("form-1"), CardFormStore.storedFormIds())
    }

    @Test
    fun `clearCardFormData empties one form or every form`() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        val resets = mutableListOf<String>()
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = { resets += "form-1" }, submitAttempted = {}))
        CardFormStore.register("form-2", formOwner, CardFormHandlers(reset = { resets += "form-2" }, submitAttempted = {}))
        CardFormStore.update("form-1", CardFormData("4532015112830366", "1230", "123"))
        CardFormStore.update("form-2", CardFormData("4444444444444448", "1228", "321"))

        GopaySDK.getInstance().clearCardFormData("form-2")
        assertEquals(listOf("form-2"), resets)
        assertEquals(setOf("form-1"), CardFormStore.storedFormIds())

        GopaySDK.getInstance().clearCardFormData()
        assertEquals(listOf("form-2", "form-1", "form-2"), resets)
        assertTrue(CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun testShareableKeyConfig() {
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            clientId = "client-id",
            shareableKey = "shareable-key"
        )

        GopaySDK.initialize(config)

        val sdk = GopaySDK.getInstance()
        assertEquals("client-id", sdk.config.clientId)
        assertEquals("shareable-key", sdk.config.shareableKey)
    }
}
