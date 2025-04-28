package com.gopay.sdk.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the GopayConfig class.
 */
class GopayConfigTest {

    @Test
    fun testDefaultConfigValues() {
        // When creating a config with only required values
        val config = GopayConfig(
            environment = Environment.PRODUCTION
        )

        // Then default values should be set
        assertEquals(Environment.PRODUCTION, config.environment)
        assertEquals(GopayConfig.DEFAULT_REQUEST_TIMEOUT, config.requestTimeoutMs)
        assertFalse(config.debugLoggingEnabled)
    }

    @Test
    fun testCustomConfigValues() {
        // Given custom configuration values
        val environment = Environment.DEVELOPMENT
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
        // For each environment
        for (environment in Environment.values()) {
            // When creating a config with that environment
            val config = GopayConfig(
                environment = environment
            )

            // Then apiBaseUrl should match the environment's URL
            assertEquals(environment.apiBaseUrl, config.apiBaseUrl)
        }
    }

    @Test
    fun testEndpointConstants() {
        // Verify the endpoint constants are properly defined
        assertEquals("/payments", GopayConfig.PAYMENT_ENDPOINT)
        assertEquals("/customers", GopayConfig.CUSTOMER_ENDPOINT)
    }
    
    @Test
    fun testDataClassCopy() {
        // Original config
        val originalConfig = GopayConfig(
            environment = Environment.SANDBOX,
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