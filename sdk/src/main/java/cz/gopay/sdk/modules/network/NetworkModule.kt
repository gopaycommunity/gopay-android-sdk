package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.NetworkConfig
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import cz.gopay.sdk.model.ChargePaymentResponseAdapter
import cz.gopay.sdk.model.UnknownEnumAdapter
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
            builder.addInterceptor(loggingInterceptor())
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
     * The client the SDK's operational data goes out through, built on its own rather than from
     * the API clients: no auth, no debug log of bodies, no certificate configuration meant for
     * the gateway, and a 10-second limit on each event. Only the SDK's `User-Agent`.
     */
    internal fun createTelemetryClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TELEMETRY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TELEMETRY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TELEMETRY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(TELEMETRY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor(UserAgentInterceptor())
        .build()

    private const val TELEMETRY_TIMEOUT_SECONDS = 10L

    /**
     * The debug log of every request and response, bodies included, with the credentials taken
     * out: the `Authorization` header, which carries `payment_id:payment_secret` on the token
     * request, and the body values [RedactingHttpLogger] masks.
     */
    internal fun loggingInterceptor(
        logger: HttpLoggingInterceptor.Logger = RedactingHttpLogger()
    ): HttpLoggingInterceptor = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    /**
     * Shared, thread-safe Moshi instance. Reused across every Retrofit client built by the SDK so
     * the Kotlin reflection adapters get registered (and cached) only once per process. The
     * charge adapter goes in front of the reflection factory so it can delegate to it, and the
     * unknown-enum adapter wraps the standard enum adapter for every enum the gateway sends.
     */
    internal val moshi: Moshi = Moshi.Builder()
        .add(ChargePaymentResponseAdapter.Factory)
        .add(UnknownEnumAdapter.Factory)
        .add(KotlinJsonAdapterFactory())
        .build()

    /**
     * Creates a Retrofit instance with the provided OkHttpClient and baseUrl, reusing the shared
     * [moshi] converter.
     *
     * @throws GopaySDKException [GopayErrorCodes.CONFIG_INVALID_BASE_URL] for a base URL the
     *         HTTP client cannot use, which [cz.gopay.sdk.config.Environment.DEVELOPMENT] lets
     *         through: it checks the scheme only. Retrofit would otherwise throw a bare
     *         `IllegalArgumentException` out of `GopaySDK.initialize`.
     */
    internal fun createRetrofit(client: OkHttpClient, baseUrl: String): Retrofit {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val builder = try {
            Retrofit.Builder().baseUrl(normalizedBaseUrl)
        } catch (e: IllegalArgumentException) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.CONFIG_INVALID_BASE_URL,
                message = "Invalid API base URL '$normalizedBaseUrl': ${e.message}",
                cause = e
            )
        }
        return builder
            .client(client)
            .addConverterFactory(ReadingConverterFactory(MoshiConverterFactory.create(moshi)))
            .build()
    }
}
