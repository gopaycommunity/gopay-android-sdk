package cz.gopay.sdk.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the GopayConfig class.
 */
class GopayConfigTest {

    @Test
    fun testDefaultConfigValues() {
        // When creating a config with only required values
        val config = GopayConfig(
            environment = GopayEnvironment.PRODUCTION
        )

        // Then default values should be set
        assertEquals(GopayEnvironment.PRODUCTION, config.environment)
        assertFalse(config.debugLoggingEnabled)
    }

    @Test
    fun `a requestTimeoutMs the SDK cannot use reads back as the default, as on iOS`() {
        for (unusable in listOf(0L, -1L, Long.MIN_VALUE, Int.MAX_VALUE + 1L, Long.MAX_VALUE)) {
            val config = GopayConfig(GopayEnvironment.SANDBOX, requestTimeoutMs = unusable)

            assertEquals("$unusable", GopayConfig.DEFAULT_REQUEST_TIMEOUT_MS, config.requestTimeoutMs)
            assertEquals("$unusable, for the debug log", unusable, config.replacedRequestTimeoutMs)
        }
        for (usable in listOf(1L, 1_500L, 45_000L, Int.MAX_VALUE.toLong())) {
            val config = GopayConfig(GopayEnvironment.SANDBOX, requestTimeoutMs = usable)

            assertEquals(usable, config.requestTimeoutMs)
            assertEquals(null, config.replacedRequestTimeoutMs)
        }
        // Through copy and the deprecated constructor too.
        assertEquals(30_000L, GopayConfig(GopayEnvironment.SANDBOX).copy(requestTimeoutMs = 0).requestTimeoutMs)
        @Suppress("DEPRECATION")
        assertEquals(30_000L, GopayConfig(GopayEnvironment.SANDBOX, debug = true, requestTimeoutMs = -5).requestTimeoutMs)
    }

    @Test
    fun `the config still copies, compares, destructures and prints as the data class of 2_x did`() {
        val callback = cz.gopay.sdk.exception.ErrorCallback { }
        val config = GopayConfig(
            environment = GopayEnvironment.SANDBOX,
            clientId = "client",
            shareableKey = "key",
            requestTimeoutMs = 12_000,
            debugLoggingEnabled = true,
            errorCallback = callback,
            locale = "de",
            customLocales = emptyMap()
        )

        assertEquals(config, config.copy())
        assertEquals(config.hashCode(), config.copy().hashCode())
        assertFalse(config == config.copy(locale = "cs"))
        assertEquals("cs", config.copy(locale = "cs").locale)
        assertEquals("client", config.copy(locale = "cs").clientId)

        val (environment, clientId, shareableKey, timeout, debug, errorCallback, locale, customLocales) = config
        assertEquals(GopayEnvironment.SANDBOX, environment)
        assertEquals("client", clientId)
        assertEquals("key", shareableKey)
        assertEquals(12_000L, timeout)
        assertTrue(debug)
        assertEquals(callback, errorCallback)
        assertEquals("de", locale)
        assertTrue(customLocales.isEmpty())

        assertTrue(config.toString(), config.toString().startsWith("GopayConfig(environment="))
        assertTrue(config.toString().contains("requestTimeoutMs=12000"))
    }

    @Test
    fun testCustomConfigValues() {
        // Given custom configuration values
        val environment = GopayEnvironment.DEVELOPMENT.create("https://localhost:8080")
        val timeoutMs = 45000L
        val enableDebug = true

        // When creating a config with custom values
        val config = GopayConfig(
            environment = environment,
            requestTimeoutMs = timeoutMs,
            debugLoggingEnabled = enableDebug
        )

        // Then custom values should be set
        assertEquals(environment, config.environment)
        assertEquals(timeoutMs, config.requestTimeoutMs)
        assertTrue(config.debugLoggingEnabled)
    }

    @Test
    fun testApiBaseUrlDerivedFromEnvironment() {
        // Test with predefined environments
        val environments = listOf(
            GopayEnvironment.PRODUCTION,
            GopayEnvironment.SANDBOX
        )
        
        for (environment in environments) {
            // When creating a config with that environment
            val config = GopayConfig(
                environment = environment
            )

            // Then baseUrl should match the environment's URL
            assertEquals(environment.baseUrl, config.baseUrl)
        }
        
        // Test with custom development environment
        val customDevEnv = GopayEnvironment.DEVELOPMENT.create("https://localhost:8080")
        val devConfig = GopayConfig(environment = customDevEnv)
        assertEquals("https://localhost:8080/", devConfig.baseUrl)
    }
    
    @Test
    fun testDataClassCopy() {
        // Original config
        val originalConfig = GopayConfig(
            environment = GopayEnvironment.SANDBOX,
            requestTimeoutMs = 30000L,
            debugLoggingEnabled = false
        )
        
        // When copying with a change
        val modifiedConfig = originalConfig.copy(
            debugLoggingEnabled = true
        )
        
        // Then only the specified parameter should be changed
        assertEquals(originalConfig.environment, modifiedConfig.environment)
        assertEquals(originalConfig.requestTimeoutMs, modifiedConfig.requestTimeoutMs)
        assertTrue(modifiedConfig.debugLoggingEnabled)
    }

} 