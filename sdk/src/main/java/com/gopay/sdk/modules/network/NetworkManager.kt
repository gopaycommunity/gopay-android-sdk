package com.gopay.sdk.modules.network

import android.content.Context
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import com.gopay.sdk.storage.SharedPrefsTokenStorage
import com.gopay.sdk.storage.TokenStorage
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Manages HTTP client and API service instances for the Gopay SDK
 */
internal class NetworkManager(
    gopayConfig: GopayConfig,
    context: Context
) {
    
    /**
     * OkHttpClient instance configured according to the provided settings
     */
    private val okHttpClient: OkHttpClient
    
    /**
     * Retrofit instance for making API calls
     */
    private val retrofit: Retrofit
    
    /**
     * Token storage for authentication
     */
    private val tokenStorage: TokenStorage
    
    /**
     * The API service interface implementation
     */
    val apiService: GopayApiService
    
    init {
        // Initialize token storage with provided context
        tokenStorage = SharedPrefsTokenStorage(context)
        
        // Create authentication interceptor with token storage
        // Create a temporary API service for the interceptor to use for token refresh
        val tempNetworkConfig = NetworkConfig(
            baseUrl = gopayConfig.apiBaseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000,
            enableLogging = gopayConfig.debugLoggingEnabled
        )
        val tempClient = NetworkModule.createOkHttpClient(tempNetworkConfig)
        val tempRetrofit = NetworkModule.createRetrofit(tempClient, tempNetworkConfig.baseUrl)
        val tempApiService = tempRetrofit.create(GopayApiService::class.java)
        
        val authInterceptor = AuthenticationInterceptor(tokenStorage, tempApiService)
        
        // Convert GopayConfig to NetworkConfig with authentication interceptor
        val networkConfig = NetworkConfig(
            baseUrl = gopayConfig.apiBaseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000, // Half the read timeout
            enableLogging = gopayConfig.debugLoggingEnabled,
            interceptors = listOf(authInterceptor)
        )
        
        // Create the HTTP client and Retrofit instance
        okHttpClient = NetworkModule.createOkHttpClient(networkConfig)
        retrofit = NetworkModule.createRetrofit(okHttpClient, networkConfig.baseUrl)
        
        // Create the API service
        apiService = retrofit.create(GopayApiService::class.java)
    }
    
    /**
     * Gets the token storage instance
     * 
     * @return TokenStorage instance
     */
    fun getTokenStorage(): TokenStorage = tokenStorage
    
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