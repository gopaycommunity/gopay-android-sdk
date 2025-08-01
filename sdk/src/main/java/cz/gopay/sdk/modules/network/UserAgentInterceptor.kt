package com.gopay.sdk.modules.network

import com.gopay.sdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp interceptor that adds User-Agent header to all requests
 */
internal class UserAgentInterceptor : Interceptor {

    companion object {
        private const val USER_AGENT_HEADER = "User-Agent"
        private const val USER_AGENT_FORMAT = "GoPay Android SDK %s"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        val userAgent = String.format(USER_AGENT_FORMAT, BuildConfig.VERSION_NAME)
        
        val requestWithUserAgent = originalRequest.newBuilder()
            .header(USER_AGENT_HEADER, userAgent)
            .build()

        return chain.proceed(requestWithUserAgent)
    }
}

