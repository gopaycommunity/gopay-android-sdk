package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.internal.GopayContextProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for the GopaySDK class.
 */
class GopaySDKTest {

    @After
    fun tearDown() {
        // Reset SDK instance and context between tests using reflection to access private field
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        
        // Clear the context provider for clean test state
        GopayContextProvider.clearContext()
    }

    @Test
    fun testInitialization() {
        // Given a configuration and mock context
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debug = true
        )
        
        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // When initializing the SDK
        GopaySDK.initialize(config)

        // Then SDK should be initialized
        assertTrue(GopaySDK.isInitialized())
    }

    @Test
    fun testGetInstanceBeforeInitialization() {
        // When attempting to get the instance before initialization
        // Then a GopaySDKException should be thrown
        val exception = assertThrows(GopaySDKException::class.java) {
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
            debug = true
        )

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // When initializing with sandbox config
        GopaySDK.initialize(sandboxConfig)
        val sdk = GopaySDK.getInstance()

        // Then the configuration should be correctly passed
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertTrue(sdk.isDebugEnabled())
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

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

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

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

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

    @Test
    fun testDebugEnabled() {
        // Given a configuration with debug enabled
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debug = true
        )

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // When initializing the SDK
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        // Then the debug should be enabled
        assertTrue(sdk.isDebugEnabled())
    }

    @Test
    fun testDebugDisabled() {
        // Given a configuration with debug disabled
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debug = false
        )

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // When initializing the SDK
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        // Then the debug should be disabled
        assertFalse(sdk.isDebugEnabled())
    }
}   