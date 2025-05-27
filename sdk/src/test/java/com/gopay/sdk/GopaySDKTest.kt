package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.internal.GopayContextProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.lang.IllegalStateException
import okhttp3.CertificatePinner
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

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
            debugLoggingEnabled = true
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

        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)

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
    fun testConfigureSecuritySettings() {
        // Given an initialized SDK
        val config = GopayConfig(environment = Environment.SANDBOX)
        
        // Mock context for auto-initialization
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        
        // Create test SSL components
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
        val socketFactory = sslContext.socketFactory
        
        val certificatePinner = CertificatePinner.Builder()
            .add("api.sandbox.gopay.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
        
        // When configuring security settings
        val result = sdk.configureSecuritySettings(
            sslSocketFactory = socketFactory,
            trustManager = trustManager,
            certificatePinner = certificatePinner
        )
        
        // Then it should return the same SDK instance for method chaining
        assertSame(sdk, result)
        
        // Test with partial parameters
        val partialResult = sdk.configureSecuritySettings(
            certificatePinner = certificatePinner
        )
        assertSame(sdk, partialResult)
        
        // Test with no parameters
        val defaultResult = sdk.configureSecuritySettings()
        assertSame(sdk, defaultResult)
    }
} 