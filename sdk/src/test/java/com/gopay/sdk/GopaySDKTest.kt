package com.gopay.sdk

import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.lang.IllegalStateException

/**
 * Unit tests for the GopaySDK class.
 */
class GopaySDKTest {

    @After
    fun tearDown() {
        // Reset SDK instance between tests using reflection to access private field
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testInitialization() {
        // Given a configuration
        val config = GopayConfig(
            environment = Environment.SANDBOX, 
            debugLoggingEnabled = true
        )

        // When initializing the SDK
        GopaySDK.initialize(config)

        // Then SDK should be initialized
        assertTrue(GopaySDK.isInitialized())
    }

    @Test
    fun testGetInstanceBeforeInitialization() {
        // When attempting to get the instance before initialization
        // Then an IllegalStateException should be thrown
        val exception = assertThrows(IllegalStateException::class.java) {
            GopaySDK.getInstance()
        }
        
        // Verify the error message
        assertTrue(exception.message!!.contains("has not been initialized"))
    }

    @Test
    fun testConfigurationPassing() {
        // Given configurations with different environments
        val sandboxConfig = GopayConfig(
            environment = Environment.SANDBOX,
            debugLoggingEnabled = true
        )

        // When initializing with sandbox config
        GopaySDK.initialize(sandboxConfig)
        val sdk = GopaySDK.getInstance()

        // Then the configuration should be correctly passed
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertTrue(sdk.config.debugLoggingEnabled)
        assertEquals(Environment.SANDBOX.apiBaseUrl, sdk.config.apiBaseUrl)
    }

    @Test
    fun testConfigurationWithCustomTimeout() {
        // Given a configuration with custom timeout
        val customTimeoutMs = 60000L
        val config = GopayConfig(
            environment = Environment.PRODUCTION,
            requestTimeoutMs = customTimeoutMs
        )

        // When initializing the SDK
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()

        // Then the timeout should be correctly passed
        assertEquals(customTimeoutMs, sdk.config.requestTimeoutMs)
    }

    @Test
    fun testReinitializationOverridesConfig() {
        // Given initial sandbox configuration
        val sandboxConfig = GopayConfig(
            environment = Environment.SANDBOX
        )

        // When initializing with sandbox config
        GopaySDK.initialize(sandboxConfig)
        
        // And then reinitializing with production config
        val productionConfig = GopayConfig(
            environment = Environment.PRODUCTION
        )
        GopaySDK.initialize(productionConfig)
        
        // Then the most recent configuration should be used
        val sdk = GopaySDK.getInstance()
        assertEquals(Environment.PRODUCTION, sdk.config.environment)
    }
} 