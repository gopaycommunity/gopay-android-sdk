package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.exception.ErrorReporter
import com.gopay.sdk.exception.GopayErrorCodes
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.internal.GopayContextProvider
import com.gopay.sdk.model.AuthenticationResponse
import com.gopay.sdk.model.Jwk
import com.gopay.sdk.modules.network.NetworkManager
import com.gopay.sdk.modules.network.GopayApiService
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.Base64Utils
import com.gopay.sdk.util.JwtUtils
import com.gopay.sdk.util.JsonUtils
import okhttp3.CertificatePinner
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import com.gopay.sdk.service.EncryptionService
import com.gopay.sdk.service.PublicKeyService
import com.gopay.sdk.service.CardTokenizationService
import com.gopay.sdk.model.CardData
import com.gopay.sdk.model.CardTokenResponse

/**
 * Public interface for the Gopay SDK. Only methods in this interface are intended for public use.
 */
interface GopaySDKInterface {
    fun getTokenStorage(): TokenStorage
    fun getApiService(): GopayApiService
    fun setAuthenticationResponse(authResponse: AuthenticationResponse)
    suspend fun authenticate(
        clientId: String,
        clientSecret: String,
        scope: String?
    ): AuthenticationResponse
    suspend fun refreshToken(): AuthenticationResponse
    fun isAuthenticated(): Boolean
    fun logout()
}

/**
 * Main entry point for the Gopay SDK.
 */
class GopaySDK private constructor(
    /**
     * The configuration for this SDK instance.
     */
    val config: GopayConfig,
    val sslSocketFactory: SSLSocketFactory? = null,
    val trustManager: X509TrustManager? = null,
    val certificatePinner: CertificatePinner? = null
) : GopaySDKInterface {

    /**
     * Network manager handling HTTP client and API service.
     * Uses automatically obtained Application context.
     */
    private val networkManager =
        NetworkManager(config, GopayContextProvider.getApplicationContext(), sslSocketFactory, trustManager, certificatePinner)

    private val tokenStorage: TokenStorage = networkManager.tokenStorage
    private val apiService: GopayApiService = networkManager.apiService
    private val encryptionService = EncryptionService(tokenStorage)
    private val publicKeyService = PublicKeyService(apiService, tokenStorage)
    private val cardTokenizationService = CardTokenizationService(
        apiService,
        encryptionService,
        publicKeyService,
        tokenStorage
    )

    /**
     * Gets the token storage instance for managing authentication tokens
     *
     * @return TokenStorage instance
     */
    override fun getTokenStorage(): TokenStorage = networkManager.tokenStorage;

    /**
     * Gets the API service instance for advanced usage and testing
     *
     * @return GopayApiService instance
     */
    override fun getApiService(): GopayApiService = networkManager.apiService

    /**
     * Sets authentication tokens from server-side authentication response.
     * This method validates JWT expiration and saves the tokens to storage.
     *
     * @param authResponse The authentication response from server-side authentication
     * @throws GopaySDKException if the access token is expired or invalid
     */
    override fun setAuthenticationResponse(authResponse: AuthenticationResponse) {
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
     * Authenticates with the GoPay API using client credentials flow.
     * This method should be called from a coroutine context.
     *
     * @param clientId The client ID for authentication
     * @param clientSecret The client secret for authentication
     * @param scope Space-separated list of required scopes (optional)
     * @return AuthenticationResponse with tokens
     * @throws GopaySDKException if authentication fails
     */
    override suspend fun authenticate(
        clientId: String,
        clientSecret: String,
        scope: String?
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
    override suspend fun refreshToken(): AuthenticationResponse {
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

            return authResponse
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
    override fun isAuthenticated(): Boolean {
        val accessToken = getTokenStorage().getAccessToken()
        return accessToken != null && !JwtUtils.isTokenExpired(accessToken)
    }

    /**
     * Clears all stored authentication tokens.
     */
    override fun logout() {
        getTokenStorage().clear()
    }

    /**
     * [DEV/INTERNAL] Tokenizes a card by encrypting card data and calling GoPay API.
     *
     * WARNING: This method is intended for development and internal testing only.
     * It should NOT be called in production code. This API will be made private in production releases.
     *
     * @param cardData The card information to tokenize
     * @param permanent Whether to save the card for permanent usage (default: false)
     * @return CardTokenResponse containing token and card metadata
     * @throws IllegalArgumentException for invalid card data
     * @throws IllegalStateException if no access token is available
     * @throws Exception for encryption, network, or API errors
     */
    @Deprecated("This method is for development/testing only and will be made private in production.", level = DeprecationLevel.WARNING)
    suspend fun tokenizeCard(cardData: CardData, permanent: Boolean = false): CardTokenResponse {
        return cardTokenizationService.tokenizeCardWithValidation(cardData, permanent)
    }

    /**
     * [DEV/INTERNAL] Gets the public encryption key used for encrypting card data.
     * This method first checks for a cached key in storage and only fetches from the API if needed.
     * This method should be called from a coroutine context.
     *
     * WARNING: This method is intended for development and internal testing only.
     * It should NOT be called in production code. This API will be made private in production releases.
     *
     * @param forceRefresh If true, bypasses cache and fetches fresh key from API
     * @return JwkResponse containing the public encryption key
     * @throws GopaySDKException if the request fails or user is not authenticated
     */
    @Deprecated("This method is for development/testing only and will be made private in production.", level = DeprecationLevel.WARNING)
    suspend fun getPublicKey(forceRefresh: Boolean = false): Jwk {
        try {
            val tokenStorage = getTokenStorage()

            // Check for cached key first (unless force refresh is requested)
            if (!forceRefresh) {
                val cachedKey = tokenStorage.getPublicKey()
                if (cachedKey != null) {
                    // Try to parse the cached key to ensure it's valid JSON
                    try {
                        val parsedKey: Jwk? = JsonUtils.fromJson(cachedKey)
                        if (parsedKey != null) {
                            return parsedKey
                        }
                    } catch (e: Exception) {
                        // If cached key is invalid, continue to fetch fresh key
                        // Log or ignore the parse error and fetch from API
                    }
                }
            }

            // Call the API service - AuthenticationInterceptor handles token validation and refresh
            val response = networkManager.apiService.getPublicKey()

            if (!response.isSuccessful) {
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.NETWORK_SERVER_ERROR,
                    message = "Failed to fetch public key: ${response.code()} ${response.message()}"
                )
            }

            val jwk = response.body()
                ?: throw GopaySDKException(
                    errorCode = GopayErrorCodes.NETWORK_SERVER_ERROR,
                    message = "Empty response body when fetching public key"
                )

            // Convert Jwk to JwkResponse for backward compatibility
            val publicKeyResponse = Jwk(
                kty = jwk.kty,
                kid = jwk.kid,
                use = jwk.use,
                alg = jwk.alg,
                n = jwk.n,
                e = jwk.e
            )

            // Cache the public key for future use
            try {
                val publicKeyJson = JsonUtils.toJson(publicKeyResponse)
                if (publicKeyJson != null) {
                    tokenStorage.savePublicKey(publicKeyJson)
                }
                // If serialization fails, continue without caching
                // This won't affect the main functionality
            } catch (e: Exception) {
                // If serialization fails, continue without caching
                // This won't affect the main functionality
            }

            return publicKeyResponse

        } catch (e: Exception) {
            when (e) {
                is GopaySDKException -> throw e
                else -> throw GopaySDKException(
                    errorCode = GopayErrorCodes.NETWORK_SERVER_ERROR,
                    message = "Failed to retrieve public key: ${e.message}",
                    cause = e
                )
            }
        }
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