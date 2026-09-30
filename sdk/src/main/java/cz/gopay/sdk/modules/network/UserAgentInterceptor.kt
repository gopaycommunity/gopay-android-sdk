package cz.gopay.sdk.modules.network

import cz.gopay.sdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that identifies the SDK in the `User-Agent` header of every request that
 * does not set one itself.
 *
 * A request that names its own User-Agent keeps it: `GET /cards/browser-data` sends the 3DS
 * challenge WebView's, because the gateway echoes the header back as `browser_data.user_agent`
 * and the issuer expects that value to match the browser it later sees. Overwriting it here
 * would put the SDK's HTTP client name into the AReq instead.
 */
internal class UserAgentInterceptor : Interceptor {

    companion object {
        private const val USER_AGENT_HEADER = "User-Agent"
        private const val USER_AGENT_FORMAT = "GoPay Android SDK %s"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.header(USER_AGENT_HEADER) != null) {
            return chain.proceed(originalRequest)
        }

        val userAgent = String.format(USER_AGENT_FORMAT, BuildConfig.VERSION_NAME)

        val requestWithUserAgent = originalRequest.newBuilder()
            .header(USER_AGENT_HEADER, userAgent)
            .build()

        return chain.proceed(requestWithUserAgent)
    }
}

