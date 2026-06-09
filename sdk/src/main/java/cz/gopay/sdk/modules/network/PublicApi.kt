package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.CardFormUrl
import cz.gopay.sdk.model.Jwk
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

/**
 * Retrofit interface for shareable-key endpoints — public resources that can be fetched from a
 * mobile client using `Basic base64(client_id:shareable_key)`.
 *
 * Authorization is attached by [ShareableKeyInterceptor].
 */
internal interface PublicApi {

    /**
     * Returns the JWK used to JWE-encrypt card data before sending the payload to the merchant
     * backend for tokenization. Maps to `GET /cards/public-key`.
     */
    @GET("cards/public-key")
    @Headers("Accept: application/json")
    suspend fun getPublicKey(): Response<Jwk>

    /**
     * Returns the URL of the hosted card input form. Maps to `GET /cards/card-form-url`.
     */
    @GET("cards/card-form-url")
    @Headers("Accept: application/json")
    suspend fun getCardFormUrl(): Response<CardFormUrl>
}
