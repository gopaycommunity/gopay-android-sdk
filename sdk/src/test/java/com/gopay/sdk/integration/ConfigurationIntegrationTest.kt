package com.gopay.sdk.integration

import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for the GopaySDK configuration flow.
 */
class ConfigurationIntegrationTest {

    @After
    fun tearDown() {
        // Reset SDK instance between tests
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    @Test
    fun testFullConfigurationFlow() {
        // 1. Create configuration for sandbox environment
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debugLoggingEnabled = true,
            requestTimeoutMs = 45000
        )

        // 1.5 SDK should not be initialized yet
        assertFalse(GopaySDK.isInitialized())

        // 2. Initialize the SDK
        GopaySDK.initialize(config)
        assertTrue(GopaySDK.isInitialized())

        // 3. Get SDK instance
        val sdk = GopaySDK.getInstance()
        
        // 4. Verify configuration is properly accessible
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertEquals("https://api.sandbox.gopay.com/v1", sdk.config.apiBaseUrl)
        assertTrue(sdk.config.debugLoggingEnabled)
        assertEquals(45000L, sdk.config.requestTimeoutMs)

        // 5. Verify payment methods are accessible
        val paymentMethods = sdk.getPaymentMethods()
        assertNotNull(paymentMethods)
        assertTrue(paymentMethods.isNotEmpty())

        // 6. Test payment processing
        assertTrue(sdk.processPayment("card", 100.0))
        assertTrue(sdk.processPayment("bank", 100.0))
        assertTrue(sdk.processPayment("wallet", 100.0))
    }

    @Test
    fun testEnvironmentSwitching() {
        // 1. Initially use sandbox environment
        GopaySDK.initialize(
            GopayConfig(
                environment = Environment.SANDBOX
            )
        )
        
        // 2. Verify we're in sandbox
        var sdk = GopaySDK.getInstance()
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        
        // 3. Switch to production
        GopaySDK.initialize(
            GopayConfig(
                environment = Environment.PRODUCTION
            )
        )
        
        // 4. Verify we're now in production
        sdk = GopaySDK.getInstance()
        assertEquals(Environment.PRODUCTION, sdk.config.environment)
        assertEquals("https://api.gopay.com/v1", sdk.config.apiBaseUrl)
    }
} 