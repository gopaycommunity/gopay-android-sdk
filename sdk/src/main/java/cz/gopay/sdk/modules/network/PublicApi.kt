package cz.gopay.sdk.modules.network

import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.CardFormUrl
import cz.gopay.sdk.model.Jwk
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
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

    /**
     * Returns the address, User-Agent and Accept headers of this very request, as the gateway
     * saw them, for `browser_data` on a charge. Maps to `GET /cards/browser-data`. The deployed
     * gateway takes the shareable key here and refuses a payment or merchant token ("Token
     * domain not permitted"), which is why it lives on this API and not the session's.
     *
     * [userAgent] goes out as the request's `User-Agent` and comes back as `user_agent`. It has
     * to be the User-Agent the 3DS challenge WebView will send, because the issuer compares the
     * value in the AReq with the browser it then sees; the SDK's own HTTP User-Agent would fail
     * that comparison. Null leaves the header to [UserAgentInterceptor].
     */
    @GET("cards/browser-data")
    @Headers("Accept: application/json")
    suspend fun getBrowserData(
        @Header("User-Agent") userAgent: String?
    ): Response<BrowserDataDetected>
}
