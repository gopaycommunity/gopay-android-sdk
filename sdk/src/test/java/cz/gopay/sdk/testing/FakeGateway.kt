package cz.gopay.sdk.testing

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.SessionAuthInterceptor
import cz.gopay.sdk.modules.network.TokenResponse
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.telemetry.ApiCallTelemetryInterceptor
import cz.gopay.sdk.telemetry.TelemetryCapture
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.Collections
import java.util.concurrent.Executors

/**
 * The gateway behind a real SDK: `startPaymentSession` on an initialized [GopaySDK], a real
 * OkHttp client with the session's auth and telemetry interceptors, and only the transport faked
 * by the last interceptor, which hands each request to [answer]. The token exchange always
 * succeeds and `init-session` always answers 204, so a test sees only the requests it is about.
 *
 * Every error a public call reports is in [reported], every operational event the SDK sends in
 * [telemetry]. Call [close] in `@After`: it lets the requests still in the background finish
 * ([drain]) and drops the SDK instance, the telemetry overrides and the error callback.
 */
internal class FakeGateway {

    /** The requests the gateway got, `init-session` left out, oldest first. */
    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())

    /** What `errorCallback` heard. */
    val reported: MutableList<GopaySDKException> = Collections.synchronizedList(mutableListOf())

    /** How the gateway answers a request: an HTTP status and a JSON body. */
    @Volatile
    var answer: (Request) -> Pair<Int, String> = { 200 to "{}" }

    /** The operational data the SDK sends, captured instead of posted. */
    val telemetry = TelemetryCapture()

    private val threads = Executors.newCachedThreadPool()

    /** Starts a session for [paymentId] on a fresh SDK. */
    fun start(paymentId: String = PAYMENT): PaymentSession =
        runBlocking { sdk().startPaymentSession(paymentId, "secret") }

    fun close() {
        drain(threads)
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        TelemetryCapture.reset()
        ErrorReporter.clear()
    }

    private fun sdk(): GopaySDK {
        TelemetryCapture.reset()
        telemetry.install()
        GopaySDK.initialize(
            GopayConfig(
                environment = GopayEnvironment.SANDBOX,
                clientId = "client-1",
                shareableKey = "key-1",
                errorCallback = { reported.add(it) }
            )
        )
        val sdk = GopaySDK.getInstance()
        val sdkTelemetry = sdk.telemetry!!
        sdk.setPaymentSessionFactoryForTest(
            PaymentSession.Factory(
                authApi = object : AuthApi {
                    override suspend fun token(authorization: String, grantType: String, scope: String?) =
                        retrofit2.Response.success(TokenResponse(accessToken = "jwt", tokenType = "Bearer"))
                },
                paymentApiBuilder = { provider ->
                    val client = OkHttpClient.Builder()
                        .dispatcher(Dispatcher(threads))
                        .addInterceptor(SessionAuthInterceptor(provider))
                        .addInterceptor(ApiCallTelemetryInterceptor(sdkTelemetry, basePath = "/api/"))
                        .addInterceptor(Interceptor { chain -> respond(chain) })
                        .build()
                    NetworkModule.createRetrofit(client, "https://gw.example/api/").create(PaymentApi::class.java)
                },
                publicApi = {
                    object : PublicApi {
                        override suspend fun getBrowserData(userAgent: String?) = retrofit2.Response.success(
                            BrowserDataDetected(ip = "192.0.2.1", userAgent = userAgent ?: "", acceptHeader = "*/*")
                        )
                        override suspend fun getPublicKey(): retrofit2.Response<Jwk> = throw UnsupportedOperationException()
                    }
                },
                telemetry = sdkTelemetry
            )
        )
        return sdk
    }

    private fun respond(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val (code, body) = if (request.url.encodedPath.endsWith("/init-session")) {
            204 to ""
        } else {
            requests.add(request)
            answer(request)
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    companion object {
        const val PAYMENT = "3000000001"
    }
}
