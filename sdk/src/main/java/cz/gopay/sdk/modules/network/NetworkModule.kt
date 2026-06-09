package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.NetworkConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Module responsible for creating and configuring HTTP client and Retrofit instance
 */
internal object NetworkModule {

    /**
     * Creates a configured OkHttpClient based on the provided NetworkConfig
     * 
     * @param config the network configuration
     * @return configured OkHttpClient
     */
    internal fun createOkHttpClient(config: NetworkConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)

        // Add User-Agent interceptor first to ensure it's applied to all requests
        builder.addInterceptor(UserAgentInterceptor())

        // Add logging if enabled
        if (config.enableLogging) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        // Add custom SSL factory and trust manager if provided
        if (config.sslSocketFactory != null && config.trustManager != null) {
            builder.sslSocketFactory(config.sslSocketFactory, config.trustManager)
        }

        // Add certificate pinning if configured
        config.certificatePinner?.let { builder.certificatePinner(it) }

        // Add any custom interceptors
        config.interceptors.forEach { interceptor ->
            builder.addInterceptor(interceptor)
        }

        return builder.build()
    }

    /**
     * Shared, thread-safe Moshi instance. Reused across every Retrofit client built by the SDK so
     * the Kotlin reflection adapters get registered (and cached) only once per process.
     */
    internal val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Creates a Retrofit instance with the provided OkHttpClient and baseUrl, reusing the shared
     * [moshi] converter.
     */
    internal fun createRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
