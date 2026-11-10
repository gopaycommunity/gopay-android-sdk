package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.NetworkConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
            readTimeoutMs = 60_000,
            connectTimeoutMs = 30_000
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
    fun testCreateOkHttpClient_hasUserAgentInterceptor() {
        // Given a network config
        val config = NetworkConfig(baseUrl = "https://api.example.com")
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then client should have a UserAgentInterceptor
        val hasUserAgentInterceptor = client.interceptors.any { it is UserAgentInterceptor }
        assertTrue("Client should have a User-Agent interceptor", hasUserAgentInterceptor)
    }
    
    @Test
    fun testCreateOkHttpClient_userAgentInterceptorIsFirst() {
        // Given a network config with logging enabled
        val config = NetworkConfig(
            baseUrl = "https://api.example.com",
            enableLogging = true
        )
        
        // When creating an OkHttpClient
        val client = NetworkModule.createOkHttpClient(config)
        
        // Then UserAgentInterceptor should be the first interceptor
        val firstInterceptor = client.interceptors.firstOrNull()
        assertTrue("User-Agent interceptor should be first", 
            firstInterceptor is UserAgentInterceptor)
    }
    
    @Test
    fun testSharedMoshiInstance() {
        // The shared Moshi instance should be available and the same on every read.
        val a = NetworkModule.moshi
        val b = NetworkModule.moshi
        assertNotNull("Moshi instance should not be null", a)
        assertSame("Moshi should be a singleton across calls", a, b)
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