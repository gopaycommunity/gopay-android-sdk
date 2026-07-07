package cz.gopay.sdk.integration

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.locales.GopayLocales
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integration tests covering GopaySDK initialization and config propagation.
 */
class ConfigurationIntegrationTest {

    @After
    fun tearDown() {
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)

        // GopaySDK.initialize() also mutates GopayLocales (a separate JVM-wide singleton); undo
        // that too, or a non-default locale/customLocales config here would leak into other tests.
        GopayLocales.clearCustom()
        GopayLocales.setDefaultLocale(null)
    }

    @Test
    fun testFullConfigurationFlow() {
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            clientId = "test-client",
            shareableKey = "test-shareable",
            debug = true,
            requestTimeoutMs = 45_000
        )

        assertFalse(GopaySDK.isInitialized())

        GopaySDK.initialize(config)
        assertTrue(GopaySDK.isInitialized())

        val sdk = GopaySDK.getInstance()
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertEquals("https://api.sandbox.gopay.com/v1/", sdk.config.apiBaseUrl)
        assertEquals("test-client", sdk.config.clientId)
        assertEquals("test-shareable", sdk.config.shareableKey)
        assertTrue(sdk.config.debug)
        assertEquals(45_000L, sdk.config.requestTimeoutMs)
    }

    @Test
    fun testEnvironmentSwitching() {
        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        assertEquals(Environment.SANDBOX, GopaySDK.getInstance().config.environment)

        GopaySDK.initialize(GopayConfig(environment = Environment.PRODUCTION))
        val sdk = GopaySDK.getInstance()
        assertEquals(Environment.PRODUCTION, sdk.config.environment)
        assertEquals("https://api.gopay.com/v1/", sdk.config.apiBaseUrl)
    }
}
