package cz.gopay.sdk.modules.network

import cz.gopay.sdk.util.Base64Utils
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Basic base64(clientId:shareableKey)` to every request.
 * Used by the [PublicApi] client.
 *
 * The shareable key is intended to be embedded in the mobile app — it grants access only to the
 * public-resource endpoints (public key, card form URL).
 */
internal class ShareableKeyInterceptor(
    clientId: String,
    shareableKey: String
) : Interceptor {

    private val header: String = Base64Utils.basicAuthHeader(clientId, shareableKey)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", header)
            .build()
        return chain.proceed(request)
    }
}
