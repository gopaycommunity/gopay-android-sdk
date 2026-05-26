package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.CardTokenRequest
import cz.gopay.sdk.model.CardTokenResponse
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.ChargePaymentResponse
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCreateResponse
import cz.gopay.sdk.model.QrPaymentDetails
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    /**
     * Creates a payment for a specific e-shop (goid).
     * Authorization header is automatically added by AuthenticationInterceptor.
     *
     * Maps to POST /eshops/{goid}/payments in Payments.yaml.
     */
    @POST("eshops/{goid}/payments")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun createPayment(
        @Path("goid") goid: String,
        @Body request: PaymentCreateRequest
    ): Response<PaymentCreateResponse>

    /**
     * Retrieves the status of an existing payment.
     * Requires payment:read scope.
     * Maps to GET /payments/{payment_id} in Payments.yaml.
     */
    @GET("payments/{payment_id}")
    @Headers(
        "Accept: application/json"
    )
    suspend fun getPaymentStatus(
        @Path("payment_id") paymentId: String
    ): Response<PaymentCreateResponse>

    /**
     * Charges a payment using the specified payment instrument.
     * Requires payment:create scope.
     * Maps to POST /payments/{payment_id}/charge in Payments.yaml.
     */
    @POST("payments/{payment_id}/charge")
    @Headers(
        "Content-Type: application/json",
        "Accept: application/json"
    )
    suspend fun chargePayment(
        @Path("payment_id") paymentId: String,
        @Body request: ChargePaymentRequest
    ): Response<ChargePaymentResponse>

    /**
     * Gets the current state of a payment charge.
     * Requires payment:read scope.
     * Maps to GET /payments/{payment_id}/charge in Payments.yaml.
     */
    @GET("payments/{payment_id}/charge")
    @Headers(
        "Accept: application/json"
    )
    suspend fun getChargeState(
        @Path("payment_id") paymentId: String
    ): Response<ChargePaymentResponse>

    /**
     * Retrieves QR code payment info for a bank transfer payment.
     * Requires payment:read scope.
     * Maps to GET /payments/{payment_id}/qr-payment/info in Payments.yaml.
     *
     * @param paymentId Payment identifier
     * @param format Image format for QR codes — "png" or "svg" (default: "png")
     */
    @GET("payments/{payment_id}/qr-payment/info")
    @Headers("Accept: application/json")
    suspend fun getQrPaymentInfo(
        @Path("payment_id") paymentId: String,
        @Query("format") format: String? = null
    ): Response<QrPaymentDetails>

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
@Deprecated("Use Jwk from model package instead", ReplaceWith("Jwk", "cz.gopay.sdk.model.Jwk"))
data class JwkResponse(
    val kty: String,           // Key type, always "RSA"
    val kid: String,           // Key ID containing information about key age
    val use: String,           // Key usage, always "enc"
    val alg: String,           // Algorithm, e.g. "RSA-OAEP-256"
    val n: String,             // RSA public key modulus part
    val e: String              // RSA public key exponent part
)