package com.gopay.sdk.modules.network

import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Manages HTTP client and API service instances for the Gopay SDK
 */
internal class NetworkManager(gopayConfig: GopayConfig) {
    
    /**
     * OkHttpClient instance configured according to the provided settings
     */
    private val okHttpClient: OkHttpClient
    
    /**
     * Retrofit instance for making API calls
     */
    private val retrofit: Retrofit
    
    /**
     * The API service interface implementation
     */
    val apiService: GopayApiService
    
    init {
        // Convert GopayConfig to NetworkConfig
        val networkConfig = NetworkConfig(
            baseUrl = gopayConfig.apiBaseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000, // Half the read timeout
            enableLogging = gopayConfig.debugLoggingEnabled
        )
        
        // Create the HTTP client and Retrofit instance
        okHttpClient = NetworkModule.createOkHttpClient(networkConfig)
        retrofit = NetworkModule.createRetrofit(okHttpClient, networkConfig.baseUrl)
        
        // Create the API service
        apiService = retrofit.create(GopayApiService::class.java)
    }
    
    /**
     * Updates the NetworkManager with custom SSL settings
     * 
     * @param networkConfig the network configuration with SSL settings
     * @return a new NetworkManager with updated settings
     */
    fun withSecuritySettings(networkConfig: NetworkConfig): NetworkManager {
        // This could be expanded to allow updating security settings after initialization
        throw UnsupportedOperationException("Not implemented yet")
    }
} 