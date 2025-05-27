package com.gopay.sdk.modules.network

import com.gopay.sdk.exception.TokenRefreshException
import com.gopay.sdk.exception.UnauthenticatedException
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.JwtUtils
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException

/**
 * OkHttp interceptor that handles JWT token validation and automatic refresh
 */
internal class AuthenticationInterceptor(
    private val tokenStorage: TokenStorage,
    private val apiService: GopayApiService
) : Interceptor {

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip authentication for the token endpoint itself to avoid infinite loops
        if (originalRequest.url.encodedPath.contains("oauth2/token")) {
            return chain.proceed(originalRequest)
        }

        // Get the current access token
        val accessToken = tokenStorage.getAccessToken()
        
        if (accessToken == null) {
            // No access token available, try to refresh or throw exception
            handleMissingToken()
            // If we get here, a new token was obtained, retry with the new token
            return proceedWithToken(chain, originalRequest)
        }

        // Check if the access token is expired
        if (JwtUtils.isTokenExpired(accessToken)) {
            // Token is expired, try to refresh it
            refreshTokenIfPossible()
            // If we get here, a new token was obtained, retry with the new token
            return proceedWithToken(chain, originalRequest)
        }

        // Token is valid, proceed with the request
        return proceedWithToken(chain, originalRequest)
    }

    private fun proceedWithToken(chain: Interceptor.Chain, originalRequest: Request): Response {
        val accessToken = tokenStorage.getAccessToken()
            ?: throw UnauthenticatedException("No access token available after refresh attempt")

        val authenticatedRequest = originalRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private fun handleMissingToken() {
        val refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken == null) {
            throw UnauthenticatedException("No access token or refresh token available")
        }

        // Check if refresh token is expired
        if (JwtUtils.isTokenExpired(refreshToken)) {
            tokenStorage.clear()
            throw UnauthenticatedException("Both access and refresh tokens are expired")
        }

        // Try to refresh the token
        refreshToken(refreshToken)
    }

    private fun refreshTokenIfPossible() {
        val refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken == null) {
            tokenStorage.clear()
            throw UnauthenticatedException("No refresh token available")
        }

        // Check if refresh token is expired
        if (JwtUtils.isTokenExpired(refreshToken)) {
            tokenStorage.clear()
            throw UnauthenticatedException("Refresh token is expired")
        }

        // Try to refresh the token
        refreshToken(refreshToken)
    }

    private fun refreshToken(refreshToken: String) {
        try {
            // Extract client ID from the current access token
            val accessToken = tokenStorage.getAccessToken()
            val clientId = if (accessToken != null) {
                JwtUtils.getClientId(accessToken)
            } else {
                null
            }
            
            if (clientId == null) {
                tokenStorage.clear()
                throw UnauthenticatedException("Cannot extract client ID from access token")
            }

            // Make the refresh token request synchronously
            val response = runBlocking {
                apiService.authenticate(
                    authorization = null, // No authorization header needed for refresh token flow
                    grantType = GRANT_TYPE_REFRESH_TOKEN,
                    scope = null,
                    refreshToken = refreshToken,
                    clientId = clientId
                )
            }

            // Save the new tokens
            tokenStorage.saveTokens(response.access_token, response.refresh_token)

        } catch (e: HttpException) {
            // HTTP error during refresh - likely the refresh token is invalid
            tokenStorage.clear()
            throw UnauthenticatedException("Failed to refresh token: HTTP ${e.code()}", e)
        } catch (e: Exception) {
            // Other errors during refresh
            tokenStorage.clear()
            throw TokenRefreshException("Failed to refresh token", e)
        }
    }
} 