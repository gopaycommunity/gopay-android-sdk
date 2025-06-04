package com.gopay.sdk.modules.network

import com.gopay.sdk.model.CardTokenRequest
import com.gopay.sdk.model.CardTokenResponse
import com.gopay.sdk.model.Jwk
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit interface for Gopay API calls
 * Consolidated interface for all GoPay API endpoints
 * Authorization headers are automatically added by AuthenticationInterceptor
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
    
    /**
     * Gets the public encryption key used for encrypting card data.
     * Authorization header is automatically added by AuthenticationInterceptor.
     * 
     * @return JWK (JSON Web Key) containing the public encryption key
     */
    @GET("encryption/public-key")
    @Headers(
        "Accept: application/json"
    )
    suspend fun getPublicKey(): Response<Jwk>
    
    /**
     * Creates a card token using JWE encrypted payload
     * Authorization header is automatically added by AuthenticationInterceptor.
     * 
     * @param request Card tokenization request with JWE payload
     * @return Card tokenization response with token and metadata
     */
    @POST("cards/tokens")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun createCardToken(
        @Body request: CardTokenRequest
    ): Response<CardTokenResponse>
    
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

/**
 * @deprecated Use Jwk from model package instead
 * JWK (JSON Web Key) response for public encryption key
 * Used for encrypting card data according to RFC 7517
 */
@Deprecated("Use Jwk from model package instead", ReplaceWith("Jwk", "com.gopay.sdk.model.Jwk"))
data class JwkResponse(
    val kty: String,           // Key type, always "RSA"
    val kid: String,           // Key ID containing information about key age
    val use: String,           // Key usage, always "enc"
    val alg: String,           // Algorithm, e.g. "RSA-OAEP-256"
    val n: String,             // RSA public key modulus part
    val e: String              // RSA public key exponent part
)