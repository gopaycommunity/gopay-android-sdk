package com.gopay.sdk.modules.network

import android.content.Context
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.config.NetworkConfig
import com.gopay.sdk.exception.GopayErrorCodes
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.storage.SharedPrefsTokenStorage
import com.gopay.sdk.storage.TokenStorage
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Manages HTTP client and API service instances for the Gopay SDK
 */
internal class NetworkManager(
    gopayConfig: GopayConfig,
    context: Context,
    private val sslSocketFactory: SSLSocketFactory? = null,
    private val trustManager: X509TrustManager? = null,
    private val certificatePinner: CertificatePinner? = null
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
    // Initialize token storage with provided context
    private val _tokenStorage: TokenStorage = SharedPrefsTokenStorage(context)

    /**
     * The API service interface implementation
     */
    val apiService: GopayApiService
    
    init {

        // Create authentication interceptor with token storage
        // Create a temporary API service for the interceptor to use for token refresh
        val tempNetworkConfig = NetworkConfig(
            baseUrl = gopayConfig.apiBaseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000,
            enableLogging = gopayConfig.debugLoggingEnabled,
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            certificatePinner = certificatePinner
        )
        val tempClient = NetworkModule.createOkHttpClient(tempNetworkConfig)
        val tempRetrofit = NetworkModule.createRetrofit(tempClient, tempNetworkConfig.baseUrl)
        val tempApiService = tempRetrofit.create(GopayApiService::class.java)
        
        val authInterceptor = AuthenticationInterceptor(_tokenStorage, tempApiService)
        
        // Convert GopayConfig to NetworkConfig with authentication interceptor
        val networkConfig = NetworkConfig(
            baseUrl = gopayConfig.apiBaseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000, // Half the read timeout
            enableLogging = gopayConfig.debugLoggingEnabled,
            interceptors = listOf(authInterceptor),
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            certificatePinner = certificatePinner
        )
        
        // Create the HTTP client and Retrofit instance
        okHttpClient = NetworkModule.createOkHttpClient(networkConfig)
        retrofit = NetworkModule.createRetrofit(okHttpClient, networkConfig.baseUrl)
        
        // Create the API service
        apiService = retrofit.create(GopayApiService::class.java)
    }
    
    /**
     * Gets the token storage instance
     */
    val tokenStorage: TokenStorage
        get() = _tokenStorage
} 