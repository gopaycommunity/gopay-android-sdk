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