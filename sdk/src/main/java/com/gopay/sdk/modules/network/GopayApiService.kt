package com.gopay.sdk.modules.network

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit interface for Gopay API calls
 * This will be expanded later with additional methods
 */
interface GopayApiService {
    /**
     * Authenticates with the GoPay API using client credentials or refresh token
     * 
     * For client_credentials flow:
     * - Requires Basic auth in the Authorization header
     * - Needs grant_type=client_credentials and scope in the form body
     *
     * For refresh_token flow:
     * - Requires grant_type=refresh_token, refresh_token value, and client_id
     * 
     * @param contentType Must be "application/x-www-form-urlencoded"
     * @param accept Must be "application/json"
     * @param authorization Basic auth header for client_credentials (optional for refresh_token flow)
     * @param grantType Either "client_credentials" or "refresh_token"
     * @param scope Space-separated list of required scopes (for client_credentials)
     * @param refreshToken Refresh token value (for refresh_token flow)
     * @param clientId Client ID for which the token was issued (for refresh_token flow)
     * @return Authentication response with tokens
     */
    @FormUrlEncoded
    @POST("oauth2/token")
    @Headers(
        "Accept: application/json"
    )
    suspend fun authenticate(
        @Header("Authorization") authorization: String? = null,
        @Field("grant_type") grantType: String,
        @Field("scope") scope: String? = null,
        @Field("refresh_token") refreshToken: String? = null,
        @Field("client_id") clientId: String? = null
    ): AuthResponse
    
    // Additional API methods will be added here later
}

/**
 * Response from OAuth authentication endpoint
 */
data class AuthResponse(
    val access_token: String,
    val token_type: String,
    val refresh_token: String,
    val scope: String? = null
)