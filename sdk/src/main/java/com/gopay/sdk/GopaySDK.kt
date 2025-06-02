package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.exception.ErrorReporter
import com.gopay.sdk.exception.GopayErrorCodes
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.internal.GopayContextProvider
import com.gopay.sdk.model.AuthenticationResponse
import com.gopay.sdk.model.PaymentMethod
import com.gopay.sdk.modules.network.NetworkManager
import com.gopay.sdk.service.PaymentService
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.Base64Utils
import com.gopay.sdk.util.JwtUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Main entry point for the Gopay SDK.
 */
class GopaySDK private constructor(
    /**
     * The configuration for this SDK instance.
     */
    val config: GopayConfig
) {
    
    private val paymentService = PaymentService()
    
    /**
     * Network manager handling HTTP client and API service.
     * Uses automatically obtained Application context.
     */
    private val networkManager = NetworkManager(config, GopayContextProvider.getApplicationContext())
    
    /**
     * Get available payment methods.
     * 
     * @return List of available payment methods
     */
    fun getPaymentMethods(): List<PaymentMethod> {
        return paymentService.getAvailablePaymentMethods()
    }
    
    /**
     * Process a payment.
     * 
     * @param paymentMethodId ID of the selected payment method
     * @param amount Amount to charge
     * @return True if payment was successful, false otherwise
     */
    fun processPayment(paymentMethodId: String, amount: Double): Boolean {
        return paymentService.processPayment(paymentMethodId, amount)
    }
    
    /**
     * Gets the token storage instance for managing authentication tokens
     * 
     * @return TokenStorage instance
     */
    fun getTokenStorage(): TokenStorage = networkManager.tokenStorage;
    
    /**
     * Sets authentication tokens from server-side authentication response.
     * This method validates JWT expiration and saves the tokens to storage.
     * 
     * @param authResponse The authentication response from server-side authentication
     * @throws GopaySDKException if the access token is expired or invalid
     */
    fun setAuthenticationResponse(authResponse: AuthenticationResponse) {
        val tokenStorage = getTokenStorage()
        
        // Validate that the access token is not expired
        if (JwtUtils.isTokenExpired(authResponse.accessToken)) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_ACCESS_TOKEN_EXPIRED,
                message = "Access token is expired"
            )
        }
        
        // Note: Refresh tokens are opaque strings (not JWTs) according to GoPay API documentation
        // Their validity is determined by the authorization server, not by client-side validation
        
        // Save the tokens to storage
        tokenStorage.saveTokens(authResponse.accessToken, authResponse.refreshToken)
    }
    
    /**
     * Configure advanced security settings for the SDK's network client.
     * Use this to set up certificate pinning or custom certificates.
     *
     * @param sslSocketFactory Custom SSL socket factory (optional)
     * @param trustManager Custom X509TrustManager (required if sslSocketFactory is provided)
     * @param certificatePinner Certificate pinner for public key pinning (optional)
     * @return The same SDK instance for chaining
     */
    fun configureSecuritySettings(
        sslSocketFactory: SSLSocketFactory? = null,
        trustManager: X509TrustManager? = null,
        certificatePinner: CertificatePinner? = null
    ): GopaySDK {
        // Create a NetworkConfig with the security settings
        // example:
        // val securityConfig = NetworkConfig(
        //     baseUrl = config.apiBaseUrl,
        //     readTimeoutSeconds = config.requestTimeoutMs / 1000,
        //     connectTimeoutSeconds = config.requestTimeoutMs / 2000,
        //     enableLogging = config.debugLoggingEnabled,
        //     sslSocketFactory = sslSocketFactory,
        //     trustManager = trustManager,
        //     certificatePinner = certificatePinner
        // )
        
        // Use the withSecuritySettings method when implemented
        // networkManager.withSecuritySettings(securityConfig)
        
        return this
    }
    
    /**
     * Authenticates with the GoPay API using client credentials flow.
     * This method should be called from a coroutine context.
     * 
     * @param clientId The client ID for authentication
     * @param clientSecret The client secret for authentication
     * @param scope Space-separated list of required scopes (optional)
     * @return AuthenticationResponse with tokens
     * @throws GopaySDKException if authentication fails
     */
    suspend fun authenticate(
        clientId: String,
        clientSecret: String,
        scope: String? = null
    ): AuthenticationResponse {
        try {
            // Create Basic authentication header
            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = Base64Utils.encodeUrlSafe(credentials)
            val authHeader = "Basic $encodedCredentials"
            
            // Call the API service
            val response = networkManager.apiService.authenticate(
                authorization = authHeader,
                grantType = "client_credentials",
                scope = scope
            )
            
            // Convert to public model
            val authResponse = AuthenticationResponse.fromAuthResponse(response)
            
            // Automatically save the tokens to storage
            setAuthenticationResponse(authResponse)

            return authResponse
        } catch (e: Exception) {
            when (e) {
                is GopaySDKException -> throw e
                else -> throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_INVALID_CREDENTIALS,
                    message = "Authentication failed: ${e.message}",
                    cause = e,
                )
            }
        }
    }
    
    /**
     * Refreshes the current access token using the stored refresh token.
     * This method should be called from a coroutine context.
     * 
     * @return AuthenticationResponse with new tokens
     * @throws GopaySDKException if refresh fails
     */
    suspend fun refreshToken(): AuthenticationResponse = withContext(Dispatchers.IO) {
        try {
            val tokenStorage = getTokenStorage()
            val refreshToken = tokenStorage.getRefreshToken()
                ?: throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_NO_TOKENS_AVAILABLE,
                    message = "No refresh token available"
                )
            
            // Extract client ID from current access token
            val accessToken = tokenStorage.getAccessToken()
            val clientId = if (accessToken != null) {
                JwtUtils.getClientId(accessToken)
            } else {
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_INVALID_CLIENT_ID,
                    message = "Cannot extract client ID from access token"
                )
            }
            
            // Call the API service for token refresh
            val response = networkManager.apiService.authenticate(
                authorization = null,
                grantType = "refresh_token",
                refreshToken = refreshToken,
                clientId = clientId
            )
            
            // Convert to public model
            val authResponse = AuthenticationResponse.fromAuthResponse(response)
            
            // Automatically save the new tokens to storage
            setAuthenticationResponse(authResponse)
            
            authResponse
        } catch (e: Exception) {
            when (e) {
                is GopaySDKException -> throw e
                else -> throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_TOKEN_REFRESH_FAILED,
                    message = "Token refresh failed: ${e.message}",
                    cause = e
                )
            }
        }
    }
    
    /**
     * Checks if the user is currently authenticated (has valid access token).
     * 
     * @return True if authenticated with valid token, false otherwise
     */
    fun isAuthenticated(): Boolean {
        val accessToken = getTokenStorage().getAccessToken()
        return accessToken != null && !JwtUtils.isTokenExpired(accessToken)
    }
    
    /**
     * Clears all stored authentication tokens.
     */
    fun logout() {
        getTokenStorage().clear()
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var instance: GopaySDK? = null
        
        /**
         * Initialize the SDK with the given configuration.
         * Application context is obtained automatically.
         * Must be called before using any SDK features.
         *
         * @param config The SDK configuration
         */
        @JvmStatic
        fun initialize(config: GopayConfig) {
            // Set up error reporting if callback is provided
            ErrorReporter.setErrorCallback(config.errorCallback)
            instance = GopaySDK(config)
        }
        
        /**
         * Initialize the SDK with manual context (for special cases).
         * This is provided for backward compatibility and special use cases
         * where automatic context detection might not work.
         *
         * @param config The SDK configuration
         * @param context Android context (will use applicationContext)
         */
        @JvmStatic
        fun initialize(config: GopayConfig, context: Context) {
            // Set up error reporting if callback is provided
            ErrorReporter.setErrorCallback(config.errorCallback)
            // Set the context manually before creating the SDK instance
            GopayContextProvider.setApplicationContext(context.applicationContext)
            instance = GopaySDK(config)
        }
        
        /**
         * Get the singleton instance of the SDK.
         * 
         * @throws GopaySDKException if SDK hasn't been initialized
         * @return The SDK instance
         */
        @JvmStatic
        fun getInstance(): GopaySDK {
            return instance ?: throw GopaySDKException(
                errorCode = GopayErrorCodes.CONFIG_SDK_NOT_INITIALIZED,
                message = "GopaySDK has not been initialized. Call GopaySDK.initialize(config) first."
            )
        }
        
        /**
         * Check if the SDK has been initialized.
         *
         * @return True if initialized, false otherwise
         */
        @JvmStatic
        fun isInitialized(): Boolean {
            return instance != null
        }
    }
} 