package com.gopay.sdk.modules.network

import com.gopay.sdk.config.NetworkConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {

    @Test
    fun testCreateOkHttpClient_defaultConfig() {
        // Given a default network config
        val config = NetworkConfig(baseUrl = "https://api.example.com")
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then client should have expected default configuration
        assertEquals(30, client.readTimeoutMillis / 1000)
        assertEquals(15, client.connectTimeoutMillis / 1000)
        assertEquals(30, client.writeTimeoutMillis / 1000)
    }
    
    @Test
    fun testCreateOkHttpClient_withCustomTimeouts() {
        // Given a network config with custom timeouts
        val config = NetworkConfig(
            baseUrl = "https://api.example.com",
            readTimeoutSeconds = 60,
            connectTimeoutSeconds = 30
        )
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then client should have the custom timeout configuration
        assertEquals(60, client.readTimeoutMillis / 1000)
        assertEquals(30, client.connectTimeoutMillis / 1000)
        assertEquals(60, client.writeTimeoutMillis / 1000)
    }
    
    @Test
    fun testCreateOkHttpClient_withLoggingEnabled() {
        // Given a network config with logging enabled
        val config = NetworkConfig(
            baseUrl = "https://api.example.com",
            enableLogging = true
        )
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then client should have a logging interceptor
        val hasLoggingInterceptor = client.interceptors.any { it is HttpLoggingInterceptor }
        assertTrue("Client should have a logging interceptor", hasLoggingInterceptor)
    }
    
    @Test
    fun testCreateOkHttpClient_withCustomInterceptors() {
        // Given a custom interceptor
        val testInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                return chain.proceed(chain.request())
            }
        }
        
        // And a network config with the custom interceptor
        val config = NetworkConfig(
            baseUrl = "https://api.example.com",
            interceptors = listOf(testInterceptor)
        )
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then client should include the custom interceptor
        assertTrue("Client should have the custom interceptor", 
            client.interceptors.contains(testInterceptor))
    }
    
    @Test
    fun testCreateMoshi() {
        // When creating a Moshi instance
        val moshi = NetworkModule.createMoshi()
        
        // Then it should not be null
        assertNotNull("Moshi instance should not be null", moshi)
    }
    
    @Test
    fun testCreateRetrofit() {
        // Given a client and base URL
        val client = OkHttpClient()
        val baseUrl = "https://api.example.com/"
        
        // When creating a Retrofit instance
        val retrofit = NetworkModule.createRetrofit(client, baseUrl)
        
        // Then it should have the expected configuration
        assertNotNull("Retrofit instance should not be null", retrofit)
        assertEquals(baseUrl, retrofit.baseUrl().toString())
    }
    
    @Test
    fun testCreateRetrofit_withNormalization() {
        // Given a client and a base URL without trailing slash
        val client = OkHttpClient()
        val baseUrl = "https://api.example.com"
        
        // When creating a Retrofit instance
        val retrofit = NetworkModule.createRetrofit(client, baseUrl)
        
        // Then the baseUrl should be normalized with a trailing slash
        assertNotNull("Retrofit instance should not be null", retrofit)
        assertEquals("https://api.example.com/", retrofit.baseUrl().toString())
    }
} 