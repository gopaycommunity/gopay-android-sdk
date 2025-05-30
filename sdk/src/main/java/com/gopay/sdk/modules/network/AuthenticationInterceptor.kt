package com.gopay.sdk.modules.network

import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.exception.GopayErrorCodes
import com.gopay.sdk.exception.HttpErrorContext
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
            ?: throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_NO_TOKENS_AVAILABLE,
                message = "No access token available after refresh attempt"
            )

        val authenticatedRequest = originalRequest.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            .build()

        return chain.proceed(authenticatedRequest)
    }

    private fun handleMissingToken() {
        val refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken == null) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_NO_TOKENS_AVAILABLE,
                message = "No access token or refresh token available"
            )
        }

        // Note: Refresh tokens are opaque strings (not JWTs) according to GoPay API documentation
        // Their validity is determined by the authorization server when attempting to use them

        // Try to refresh the token
        refreshToken(refreshToken)
    }

    private fun refreshTokenIfPossible() {
        val refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken == null) {
            tokenStorage.clear()
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_NO_TOKENS_AVAILABLE,
                message = "No refresh token available"
            )
        }

        // Note: Refresh tokens are opaque strings (not JWTs) according to GoPay API documentation
        // Their validity is determined by the authorization server when attempting to use them

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
                throw GopaySDKException(
                    errorCode = GopayErrorCodes.AUTH_INVALID_CLIENT_ID,
                    message = "Cannot extract client ID from access token"
                )
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
            val httpContext = HttpErrorContext(
                statusCode = e.code(),
                responseBody = e.response()?.errorBody()?.string(),
                requestUrl = "oauth2/token",
                requestMethod = "POST"
            )
            throw GopaySDKException(
                errorCode = if (e.code() in 400..499) GopayErrorCodes.NETWORK_CLIENT_ERROR else GopayErrorCodes.NETWORK_SERVER_ERROR,
                message = "Failed to refresh token: HTTP ${e.code()}",
                cause = e,
                httpContext = httpContext
            )
        } catch (e: Exception) {
            // Other errors during refresh
            tokenStorage.clear()
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_TOKEN_REFRESH_FAILED,
                message = "Failed to refresh token",
                cause = e
            )
        }
    }
} 