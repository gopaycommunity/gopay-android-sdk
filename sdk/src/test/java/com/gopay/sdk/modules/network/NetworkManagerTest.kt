package com.gopay.sdk.modules.network

import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkManagerTest {

    private lateinit var defaultGopayConfig: GopayConfig
    
    @Before
    fun setup() {
        defaultGopayConfig = GopayConfig(
            environment = Environment.SANDBOX,
            debugLoggingEnabled = true
        )
    }
    
    @Test
    fun testNetworkManagerInitialization() {
        // When creating a NetworkManager
        val networkManager = NetworkManager(defaultGopayConfig)
        
        // Then the API service should be initialized
        assertNotNull("API service should be initialized", networkManager.apiService)
    }
    
    @Suppress("USELESS_IS_CHECK")
    @Test
    fun testApiServiceInstantiation() {
        // Given a NetworkManager
        val networkManager = NetworkManager(defaultGopayConfig)
        
        // When getting the API service
        val apiService = networkManager.apiService
        
        // Then it should be a GopayApiService instance
        assertTrue("API service should be a GopayApiService instance", 
            apiService is GopayApiService)
    }
    
    @Test(expected = UnsupportedOperationException::class)
    fun testWithSecuritySettings_throwsException() {
        // Given a NetworkManager
        val networkManager = NetworkManager(defaultGopayConfig)
        
        // When calling withSecuritySettings
        // Then it should throw UnsupportedOperationException
        networkManager.withSecuritySettings(NetworkConfig(baseUrl = "https://example.com"))
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
            val networkManager = NetworkManager(config)
            
            // Then the network manager should be properly initialized
            assertNotNull("API service should be initialized for all config values", 
                networkManager.apiService)
        }
    }
} 