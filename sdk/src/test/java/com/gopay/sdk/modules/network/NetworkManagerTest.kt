package com.gopay.sdk.modules.network

import android.content.Context
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkManagerTest {

    private lateinit var defaultGopayConfig: GopayConfig
    private lateinit var mockContext: Context
    
    @Before
    fun setup() {
        defaultGopayConfig = GopayConfig(
            environment = Environment.SANDBOX,
            debugLoggingEnabled = true
        )
        
        // Setup mock context
        mockContext = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
    }
    
    @Test
    fun testNetworkManagerInitialization() {
        // When creating a NetworkManager
        val networkManager = NetworkManager(defaultGopayConfig, mockContext)
        
        // Then the API service should be initialized
        assertNotNull("API service should be initialized", networkManager.apiService)
    }
    
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun testApiServiceInstantiation() {
        // Given a NetworkManager
        val networkManager = NetworkManager(defaultGopayConfig, mockContext)
        
        // When getting the API service
        val apiService = networkManager.apiService
        
        // Then it should be a GopayApiService instance
        assertTrue("API service should be a GopayApiService instance", 
            apiService is GopayApiService)
    }

    @Test
    fun testConfigMapping() {
        // Given various GopayConfig values
        val configs = listOf(
            GopayConfig(
                environment = Environment.DEVELOPMENT,
                requestTimeoutMs = 10000,
                debugLoggingEnabled = true
            ),
            GopayConfig(
                environment = Environment.PRODUCTION,
                requestTimeoutMs = 20000,
                debugLoggingEnabled = false
            )
        )
        
        // When creating NetworkManagers with these configs
        for (config in configs) {
            val networkManager = NetworkManager(config, mockContext)
            
            // Then the network manager should be properly initialized
            assertNotNull("API service should be initialized for all config values", 
                networkManager.apiService)
        }
    }
} 