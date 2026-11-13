package cz.gopay.sdk

import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.GopayCardData
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.ui.INVALID_CARD_FORM_DATA_MESSAGE
import cz.gopay.sdk.ui.NO_CARD_DATA_MESSAGE
import cz.gopay.sdk.ui.CardFormData
import cz.gopay.sdk.ui.CardFormHandlers
import android.app.Application
import android.content.Context
import cz.gopay.sdk.telemetry.Telemetry
import cz.gopay.sdk.ui.CardFormStore
import cz.gopay.sdk.ui.ForegroundScreen
import cz.gopay.sdk.util.SdkLog
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the GopaySDK singleton lifecycle and configuration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GopaySDKTest {

    @get:Rule
    val sharedState = SharedSdkState()

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
        ForegroundScreen.reset()
        Dispatchers.resetMain()
    }

    /** Every error the SDK reported to the callback a config here registered. */
    private val reported = mutableListOf<GopaySDKException>()

    // ---- initialize(context, config), the 3.0 way

    private val application = mock<Application>()
    private val context = mock<Context> {
        on { applicationContext }.thenReturn(application)
        on { packageName }.thenReturn("com.example.shop")
    }

    @Test
    fun `initialize with a Context keeps its application Context and starts following the screens`() {
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.SANDBOX))

        assertTrue(GopaySDK.isInitialized())
        assertSame("the application Context, not the one handed in", application, ForegroundScreen.appContext)
        verify(application, times(1)).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `initialize with a Context tells the operational data the app's origin at once`() {
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.SANDBOX))

        assertEquals("com.example.shop", Telemetry.origin)
    }

    @Test
    fun `initializing twice with a Context registers the callbacks once`() {
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.SANDBOX))
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.PRODUCTION))

        verify(application, times(1)).registerActivityLifecycleCallbacks(any())
        assertEquals(GopayEnvironment.PRODUCTION, GopaySDK.getInstance().config.environment)
    }

    @Test
    fun `a config the SDK cannot use leaves the Context of an earlier initialize in place`() {
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.SANDBOX))
        val other = mock<Context> { on { applicationContext }.thenReturn(mock<Application>()) }

        assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(other, GopayConfig(environment = GopayEnvironment.DEVELOPMENT("https://bad host/")))
        }

        assertSame(application, ForegroundScreen.appContext)
    }

    @Test
    @Suppress("DEPRECATION") // the deprecated initialize is what is tested
    fun `the deprecated initialize without a Context leaves the SDK without one`() {
        GopaySDK.initialize(context, GopayConfig(environment = GopayEnvironment.SANDBOX))

        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX))

        assertTrue(GopaySDK.isInitialized())
        assertNull(ForegroundScreen.appContext)
    }

    /** The warnings of a block, as the debug log would show them. */
    private fun warningsOf(block: () -> Unit): List<String> {
        val sink = SdkLog.warnSink
        val warnings = mutableListOf<String>()
        SdkLog.warnSink = { warnings.add(it) }
        try {
            block()
        } finally {
            SdkLog.warnSink = sink
        }
        return warnings
    }

    @Test
    fun `isWalletAvailable before any initialize answers false and says why, it does not throw`() {
        var available: Boolean? = null

        val warnings = warningsOf { available = runBlocking { GopaySDK.isWalletAvailable() } }

        assertEquals(false, available)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("not initialized"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun `isWalletAvailable after an initialize without a Context answers false and says why, it does not throw`() {
        GopaySDK.initialize(
            GopayConfig(environment = GopayEnvironment.SANDBOX, debugLoggingEnabled = true, errorCallback = { reported.add(it) })
        )
        var available: Boolean? = null

        val warnings = warningsOf { available = runBlocking { GopaySDK.isWalletAvailable() } }

        assertEquals(false, available)
        assertTrue(warnings.single(), warnings.single().contains("without a Context"))
        assertTrue("nothing failed, so nothing is reported", reported.isEmpty())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `the answer false without a Context is quiet when the debug log is off`() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX))

        val warnings = warningsOf { assertEquals(false, runBlocking { GopaySDK.isWalletAvailable() }) }

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun testInitialization() {
        val config = GopayConfig(environment = GopayEnvironment.SANDBOX, debugLoggingEnabled = true)

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

        assertEquals(GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED, exception.code)
        assertTrue(exception.message!!.contains("has not been initialized"))
        assertTrue("never reported", reported.isEmpty())
    }

    @Test
    fun testConfigurationPassing() {
        val sandboxConfig = GopayConfig(environment = GopayEnvironment.SANDBOX, debugLoggingEnabled = true)

        GopaySDK.initialize(sandboxConfig)
        val sdk = GopaySDK.getInstance()

        assertEquals(GopayEnvironment.SANDBOX, sdk.config.environment)
        assertTrue(sdk.isDebugEnabled())
        assertEquals(GopayEnvironment.SANDBOX.baseUrl, sdk.config.baseUrl)
    }

    @Test
    fun testConfigurationWithCustomTimeout() {
        val customTimeoutMs = 60_000L
        val config = GopayConfig(
            environment = GopayEnvironment.PRODUCTION,
            requestTimeoutMs = customTimeoutMs
        )

        GopaySDK.initialize(config)

        assertEquals(customTimeoutMs, GopaySDK.getInstance().config.requestTimeoutMs)
    }

    @Test
    fun testReinitializationOverridesConfig() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX))
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.PRODUCTION))

        assertEquals(GopayEnvironment.PRODUCTION, GopaySDK.getInstance().config.environment)
    }

    @Test
    fun testDebugEnabled() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, debugLoggingEnabled = true))

        assertTrue(GopaySDK.getInstance().isDebugEnabled())
    }

    @Test
    fun testDebugDisabled() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, debugLoggingEnabled = false))

        assertFalse(GopaySDK.getInstance().isDebugEnabled())
    }

    @Test
    fun `an empty paymentId or paymentSecret is VALIDATION_007 before anything is sent`() {
        // The iOS SDK validates the pair the same way; a bare IllegalArgumentException was the
        // one platform-only exception on this path. SANDBOX is never reached: the check comes
        // before the token request.
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { reported.add(it) }))
        val sdk = GopaySDK.getInstance()

        for ((id, secret) in listOf("" to "secret", " " to "secret", "pay-1" to "", "pay-1" to "\t")) {
            val e = assertThrows("'$id' / '$secret'", GopaySDKException::class.java) {
                runBlocking { sdk.startPaymentSession(id, secret) }
            }
            assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.code)
            assertTrue(e.message!!.endsWith("must not be empty"))
        }
        assertEquals("each one reached the callback once", 4, reported.size)
    }

    @Test
    fun `card data that fails validation is VALIDATION_007 before the key is fetched`() {
        // No clientId/shareableKey on purpose: a card that got past validation would fail with
        // AUTH_011 on the key fetch, so VALIDATION_007 proves the check runs first.
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX))
        val sdk = GopaySDK.getInstance()
        val good = GopayCardData(cardPan = "4444444444444448", expMonth = "12", expYear = "28", cvv = "123")
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
            assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, e.code)
            assertEquals(message, e.message)
        }
        // The rules are the same as on iOS: a 13-digit PAN and a 4-digit CVV pass validation and
        // reach the key fetch, which fails for the missing credentials, not the card.
        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { sdk.encryptCardData(good.copy(cardPan = "4444444444448", cvv = "1234", expYear = "2028")) }
        }
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.code)
    }

    @Test
    fun `a development URL the HTTP client refuses is CONFIG_006 from initialize`() {
        // GopayEnvironment.DEVELOPMENT checks the scheme only; Retrofit's own check used to throw a
        // bare IllegalArgumentException out of initialize.
        val e = assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(
                GopayConfig(
                    environment = GopayEnvironment.DEVELOPMENT.create("http://gw.exa mple.com/api/"),
                    errorCallback = { reported.add(it) }
                )
            )
        }

        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
        assertTrue(e.cause is IllegalArgumentException)
        assertFalse("a failed initialize leaves no instance behind", GopaySDK.isInitialized())
        assertEquals("the callback from the same config hears of it once", listOf(e), reported)
    }

    @Test
    fun `a failed initialize leaves the earlier instance and callback in place`() {
        val earlier = mutableListOf<GopaySDKException>()
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { earlier.add(it) }))

        val e = assertThrows(GopaySDKException::class.java) {
            GopaySDK.initialize(
                GopayConfig(
                    environment = GopayEnvironment.DEVELOPMENT.create("http://gw.exa mple.com/api/"),
                    errorCallback = { reported.add(it) }
                )
            )
        }

        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
        assertEquals("the failing config's own callback hears of it once", listOf(e), reported)
        assertEquals(GopayEnvironment.SANDBOX, GopaySDK.getInstance().config.environment)
        // The earlier callback is still the installed one.
        val later = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().startPaymentSession("", "secret") }
        }
        assertEquals(listOf(later), earlier)
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `submitCardForm without a form on screen is VALIDATION_008 with the no-data message, once`() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { reported.add(it) }))

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().submitCardForm() }
        }

        assertEquals(GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, e.code)
        assertEquals("VALIDATION_008", e.code)
        assertEquals("No card form data available. Please use PaymentCardForm to enter card data.", e.message)
        assertEquals(listOf(e), reported)
    }

    @Test
    fun `submitCardForm on an empty form is VALIDATION_008, on an invalid one VALIDATION_007, and both tell the form a submit ran`() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { reported.add(it) }))
        var attempts = 0
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = {}, submitAttempted = { attempts++ }))
        val sdk = GopaySDK.getInstance()

        CardFormStore.update("form-1", formOwner, CardFormData())
        val empty = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardForm("form-1") } }
        assertEquals(GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, empty.code)
        assertEquals(NO_CARD_DATA_MESSAGE, empty.message)

        CardFormStore.update("form-1", formOwner, CardFormData("4532015112830366", "1230", "12"))
        val invalid = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardForm("form-1") } }
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, invalid.code)
        assertEquals(INVALID_CARD_FORM_DATA_MESSAGE, invalid.message)

        assertEquals("ON_SUBMIT shows its errors whether or not the submit passes", 2, attempts)
        assertEquals(listOf(empty, invalid), reported)
        assertEquals("a failed submit keeps the data", setOf("form-1"), CardFormStore.storedFormIds())
    }

    @Test
    fun `submitCardForm hands a valid form to encryptCardData and keeps it when that fails`() {
        // No clientId/shareableKey: the key fetch fails with AUTH_011 before any request, which
        // proves the form passed validation and reached encryptCardData.
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { reported.add(it) }))
        var resets = 0
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update("form-1", formOwner, CardFormData("4532015112830366", "1230", "123"))

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().submitCardForm() }
        }

        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.code)
        assertEquals("reported once, by encryptCardData, not again by submitCardForm", listOf(e), reported)
        assertEquals("the card stays in the form after a failed encryption", 0, resets)
        assertEquals(setOf("form-1"), CardFormStore.storedFormIds())
    }

    @Test
    fun `submitCardFormWithMetadata fails like submitCardForm, with the same codes, each reported once`() {
        // No clientId/shareableKey: the lookup has nothing to ask with and stays quiet, and the
        // key fetch fails with AUTH_011, the error submitCardForm reports for the same form.
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX, errorCallback = { reported.add(it) }))
        var resets = 0
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        val sdk = GopaySDK.getInstance()

        val none = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardFormWithMetadata("no-such-form") } }
        assertEquals(GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, none.code)
        assertEquals(NO_CARD_DATA_MESSAGE, none.message)

        CardFormStore.update("form-1", formOwner, CardFormData("4532015112830366", "1230", "12"))
        val invalid = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardFormWithMetadata("form-1") } }
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, invalid.code)
        assertEquals(INVALID_CARD_FORM_DATA_MESSAGE, invalid.message)

        CardFormStore.update("form-1", formOwner, CardFormData("4532015112830366", "1230", "123"))
        val noKey = assertThrows(GopaySDKException::class.java) { runBlocking { sdk.submitCardFormWithMetadata() } }
        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, noKey.code)

        assertEquals("the failed lookup reports nothing, the rest once each", listOf(none, invalid, noKey), reported)
        assertEquals("the card stays in the form after a failed encryption", 0, resets)
        assertEquals(setOf("form-1"), CardFormStore.storedFormIds())
    }

    @Test
    fun `clearCardFormData empties one form or every form`() {
        GopaySDK.initialize(GopayConfig(environment = GopayEnvironment.SANDBOX))
        val resets = mutableListOf<String>()
        CardFormStore.register("form-1", formOwner, CardFormHandlers(reset = { resets += "form-1" }, submitAttempted = {}))
        CardFormStore.register("form-2", formOwner, CardFormHandlers(reset = { resets += "form-2" }, submitAttempted = {}))
        CardFormStore.update("form-1", formOwner, CardFormData("4532015112830366", "1230", "123"))
        CardFormStore.update("form-2", formOwner, CardFormData("4444444444444448", "1228", "321"))

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
            environment = GopayEnvironment.SANDBOX,
            clientId = "client-id",
            shareableKey = "shareable-key"
        )

        GopaySDK.initialize(config)

        val sdk = GopaySDK.getInstance()
        assertEquals("client-id", sdk.config.clientId)
        assertEquals("shareable-key", sdk.config.shareableKey)
    }
}
