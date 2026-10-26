package cz.gopay.sdk.modules.network

import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.config.NetworkConfig
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.telemetry.ApiCallTelemetryInterceptor
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import okhttp3.CertificatePinner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Builds and holds the auth-separated API clients for the Gopay SDK.
 *
 * All clients share the same base [OkHttpClient] (connection pool, timeouts, SSL config,
 * UserAgent, logging) and only differ in their auth interceptor:
 * - [authApi] — no auth (used to obtain payment_credentials tokens)
 * - [buildPaymentApi] — per-session Bearer via [SessionAuthInterceptor]
 * - [publicApi] — Basic `client_id:shareable_key` via [ShareableKeyInterceptor]
 *
 * Each client reports its requests to [telemetry] through an [ApiCallTelemetryInterceptor] added
 * after its auth interceptor, so what is reported is the request that went out.
 */
internal class NetworkManager(
    private val gopayConfig: GopayConfig,
    private val sslSocketFactory: SSLSocketFactory? = null,
    private val trustManager: X509TrustManager? = null,
    private val certificatePinner: CertificatePinner? = null,
    private val telemetry: GwLoggerTelemetry? = null
) {

    private val baseUrl: String = gopayConfig.apiBaseUrl.let { if (it.endsWith("/")) it else "$it/" }

    /** OkHttp client with no auth interceptors — every API client clones from this. */
    private val baseClient: OkHttpClient = NetworkModule.createOkHttpClient(
        NetworkConfig(
            baseUrl = baseUrl,
            readTimeoutSeconds = gopayConfig.requestTimeoutMs / 1000,
            connectTimeoutSeconds = gopayConfig.requestTimeoutMs / 2000,
            enableLogging = gopayConfig.debug,
            sslSocketFactory = sslSocketFactory,
            trustManager = trustManager,
            certificatePinner = certificatePinner
        )
    )

    private val telemetryInterceptor: ApiCallTelemetryInterceptor? = telemetry?.let {
        ApiCallTelemetryInterceptor(it, basePath = baseUrl.toHttpUrlOrNull()?.encodedPath ?: "/")
    }

    /** [baseClient] with [auth], if any, then the telemetry interceptor last. */
    private fun apiClient(auth: Interceptor? = null): OkHttpClient {
        if (auth == null && telemetryInterceptor == null) return baseClient
        return baseClient.newBuilder()
            .apply { auth?.let { addInterceptor(it) } }
            .apply { telemetryInterceptor?.let { addInterceptor(it) } }
            .build()
    }

    /**
     * Unauthenticated [AuthApi] used by PaymentSession to exchange `payment_id`/`payment_secret`
     * for a payment-scoped JWT.
     */
    val authApi: AuthApi = NetworkModule.createRetrofit(apiClient(), baseUrl)
        .create(AuthApi::class.java)

    /**
     * Builds a [PaymentApi] backed by an OkHttp client that attaches the JWT held by [provider].
     * One instance per PaymentSession.
     */
    fun buildPaymentApi(provider: SessionTokenProvider): PaymentApi {
        val client = apiClient(SessionAuthInterceptor(provider))
        return NetworkModule.createRetrofit(client, baseUrl)
            .create(PaymentApi::class.java)
    }

    /**
     * Returns a [PublicApi] for shareable-key endpoints. Requires both `clientId` and
     * `shareableKey` to be set on [GopayConfig]; otherwise throws
     * [GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING].
     */
    val publicApi: PublicApi by lazy {
        val clientId = gopayConfig.clientId
        val shareableKey = gopayConfig.shareableKey
        if (clientId.isNullOrBlank() || shareableKey.isNullOrBlank()) {
            throw GopaySDKException(
                errorCode = GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING,
                message = "clientId and shareableKey must be set on GopayConfig to use public endpoints"
            )
        }
        val client = apiClient(ShareableKeyInterceptor(clientId, shareableKey))
        NetworkModule.createRetrofit(client, baseUrl)
            .create(PublicApi::class.java)
    }
}
