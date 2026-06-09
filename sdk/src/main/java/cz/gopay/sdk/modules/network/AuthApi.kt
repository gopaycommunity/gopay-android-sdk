package cz.gopay.sdk.modules.network

import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * Retrofit interface for the OAuth2 token endpoint.
 *
 * Used on mobile with payment-scoped credentials: basic auth `payment_id:payment_secret`,
 * `grant_type=payment_credentials`, returns a JWT scoped to that single payment.
 *
 * No interceptor attaches a bearer token here — credentials are supplied per call.
 */
internal interface AuthApi {

    /**
     * Exchanges credentials for an access token.
     *
     * @param authorization `Basic base64(payment_id:payment_secret)`
     * @param grantType Custom GoPay value `payment_credentials` (Payments.yaml,
     *                  components.schemas.Payment-Credentials-Request). The legacy merchant
     *                  flow uses `client_credentials`; mobile never sends that.
     * @param scope Optional space-separated scope list.
     */
    @FormUrlEncoded
    @POST("oauth2/token")
    @Headers("Accept: application/json")
    suspend fun token(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String,
        @Field("scope") scope: String? = null
    ): Response<TokenResponse>
}

/**
 * Response from `POST /oauth2/token` shaped to the new `Access-Token` schema in Payments.yaml.
 * Distinct from the legacy [AuthResponse]: the new schema has no `refresh_token`, and includes
 * an `expires_in` field.
 */
internal data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    val scope: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null
)

