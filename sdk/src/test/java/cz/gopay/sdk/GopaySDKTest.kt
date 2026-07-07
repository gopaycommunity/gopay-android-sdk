package cz.gopay.sdk

import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the GopaySDK singleton lifecycle and configuration.
 */
class GopaySDKTest {

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
    }

    @Test
    fun testInitialization() {
        val config = GopayConfig(environment = Environment.SANDBOX, debug = true)

        GopaySDK.initialize(config)

        assertTrue(GopaySDK.isInitialized())
    }

    @Test
    fun testGetInstanceBeforeInitialization() {
        val exception = assertThrows(GopaySDKException::class.java) {
            GopaySDK.getInstance()
        }
        assertTrue(exception.message!!.contains("has not been initialized"))
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
