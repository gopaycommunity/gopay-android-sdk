package cz.gopay.sdk.integration

import android.content.Context
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.internal.GopayContextProvider
import cz.gopay.sdk.modules.network.GopayApiService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        
        // Clear the context provider for clean test state
        GopayContextProvider.clearContext()
    }

    @Test
    fun testFullConfigurationFlow() {
        // 1. Create configuration for sandbox environment
        val config = GopayConfig(
            environment = Environment.SANDBOX,
            debug = true,
            requestTimeoutMs = 45000
        )

        // 1.5 SDK should not be initialized yet
        assertFalse(GopaySDK.isInitialized())

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        // 2. Initialize the SDK
        GopaySDK.initialize(config)
        assertTrue(GopaySDK.isInitialized())

        // 3. Get SDK instance
        val sdk = GopaySDK.getInstance()
        
        // 4. Verify configuration is properly accessible
        assertEquals(Environment.SANDBOX, sdk.config.environment)
        assertEquals("https://api.sandbox.gopay.com/v1/", sdk.config.apiBaseUrl)
        assertTrue(sdk.config.debug)
        assertEquals(45000L, sdk.config.requestTimeoutMs)

        // 5. Verify SDK is properly initialized and accessible
        assertNotNull(sdk.getTokenStorage())
        assertNotNull(sdk.getApiService())
    }

    @Test
    fun testEnvironmentSwitching() {
        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
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
        assertEquals("https://api.gopay.com/v1/", sdk.config.apiBaseUrl)
    }

    @Test
    fun testQrPaymentInfoEndpointIsWiredUp() {
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

        GopaySDK.initialize(GopayConfig(environment = Environment.SANDBOX))
        val apiService = GopaySDK.getInstance().getApiService()

        assertNotNull(apiService)

        // Verify the interface declares getQrPaymentInfo with the expected parameters
        val method = GopayApiService::class.java.methods.find { it.name == "getQrPaymentInfo" }
        assertNotNull("getQrPaymentInfo method must be declared in GopayApiService", method)
        assertEquals(
            "getQrPaymentInfo must accept paymentId and format parameters",
            2,
            method!!.parameterCount - 1 // subtract the coroutine Continuation parameter
        )
    }
}