package cz.gopay.sdk.session

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.ChargeState
import cz.gopay.sdk.modules.network.AuthApi
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.modules.network.PaymentApi
import cz.gopay.sdk.modules.network.SessionAuthInterceptor
import cz.gopay.sdk.modules.network.TokenResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session calls run the way the SDK runs them: Retrofit's `suspend` endpoints over a real
 * OkHttp client, which executes them asynchronously on its dispatcher threads. Only the
 * transport is faked, by the last interceptor in the chain.
 *
 * An exception other than an `IOException` thrown by an interceptor there is rethrown on the
 * dispatcher thread and kills the host process, so every test also checks that no thread died.
 */
class PaymentSessionTransportTest {

    /** Exceptions that reached a thread's uncaught handler, i.e. would have crashed the app. */
    private val crashes = Collections.synchronizedList(mutableListOf<Throwable>())

    /** Runs the session's calls, so [assertNoCrash] can wait for every one of them to finish. */
    private val dispatcherThreads = Executors.newCachedThreadPool()
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before
    fun catchCrashes() {
        Thread.setDefaultUncaughtExceptionHandler { _, e -> crashes.add(e) }
    }

    @After
    fun restoreHandler() {
        // A test that failed before assertNoCrash must not leave a crash for the next one.
        dispatcherThreads.shutdown()
        dispatcherThreads.awaitTermination(5, TimeUnit.SECONDS)
        Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
    }

    @Test
    fun `reading the charge state of a closed session throws AUTH_013`() {
        val gateway = Gateway(chargeState())
        val session = session(gateway)
        session.close()

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.getChargeState() }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.errorCode)
        assertEquals("nothing may go out on a closed session", 0, gateway.requests.get())
        assertNoCrash()
    }

    @Test
    fun `a session closed while its token is being refreshed throws AUTH_013 without a crash`() {
        lateinit var session: PaymentSession
        val gateway = Gateway { chain ->
            // The token is rejected and, before the session can refresh it, the host closes it.
            session.close()
            response(chain, 401, "{}")
        }
        session = session(gateway)

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.getChargeState() }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_SESSION_CLOSED, e.errorCode)
        assertNoCrash()
    }

    @Test
    fun `a token refresh that fails offline throws AUTH_010 without a crash`() {
        val gateway = Gateway { chain -> response(chain, 401, "{}") }
        val session = session(gateway, refresh = { throw java.net.UnknownHostException("offline") })

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.getChargeState() }
        }

        // The code is what 2.0 reports for any failed token request; only the crash is fixed here.
        assertEquals(GopayErrorCodes.AUTH_PAYMENT_CREDENTIALS_INVALID, e.errorCode)
        assertNoCrash()
    }

    @Test
    fun `two 401s in a row throw AUTH_009 without a crash`() {
        val gateway = Gateway { chain -> response(chain, 401, "{}") }
        val session = session(gateway)

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.getChargeState() }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED, e.errorCode)
        assertEquals(2, gateway.requests.get())
        assertNoCrash()
    }

    @Test
    fun `a token the request cannot carry throws AUTH_009 without a crash`() {
        // A line break in the token makes OkHttp refuse the Authorization header outright.
        val gateway = Gateway(chargeState())
        val session = session(gateway, firstToken = "jwt\nx")

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { session.getChargeState() }
        }

        assertEquals(GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED, e.errorCode)
        assertEquals("nothing may go out without the header", 0, gateway.requests.get())
        assertNoCrash()
    }

    @Test
    fun `a call that goes through still returns the decoded body`() = runBlocking {
        val state = session(Gateway(chargeState())).getChargeState()

        assertEquals(ChargeState.SUCCEEDED, state.state)
        assertNoCrash()
    }

    private fun assertNoCrash() {
        // The dispatcher rethrows after it has failed the call, on its own thread; once the pool
        // has terminated, every such rethrow has reached the uncaught handler.
        dispatcherThreads.shutdown()
        assertTrue(dispatcherThreads.awaitTermination(5, TimeUnit.SECONDS))
        assertTrue("a thread died with $crashes", crashes.isEmpty())
    }

    /** Fake transport: answers every request with [answer] instead of going to the network. */
    private class Gateway(val answer: (Interceptor.Chain) -> Response) : Interceptor {
        val requests = AtomicInteger()
        override fun intercept(chain: Interceptor.Chain): Response {
            requests.incrementAndGet()
            return answer(chain)
        }
    }

    private fun chargeState(): (Interceptor.Chain) -> Response = { chain ->
        response(chain, 200, """{"id":"c1","state":"SUCCEEDED","return_url":"https://shop.example/r"}""")
    }

    /**
     * A session whose first token comes from the gateway as usual; every later one is up to
     * [refresh].
     */
    private fun session(
        gateway: Gateway,
        firstToken: String = "jwt",
        refresh: () -> retrofit2.Response<TokenResponse> = { token() }
    ): PaymentSession = runBlocking {
        val issued = AtomicInteger()
        PaymentSession.Factory(
            authApi = object : AuthApi {
                override suspend fun token(
                    authorization: String,
                    grantType: String,
                    scope: String?
                ): retrofit2.Response<TokenResponse> =
                    if (issued.getAndIncrement() == 0) token(firstToken) else refresh()
            },
            paymentApiBuilder = { provider ->
                val client = OkHttpClient.Builder()
                    .dispatcher(Dispatcher(dispatcherThreads))
                    .addInterceptor(SessionAuthInterceptor(provider))
                    .addInterceptor(gateway)
                    .build()
                NetworkModule.createRetrofit(client, "https://gw.example/api/")
                    .create(PaymentApi::class.java)
            },
            publicApi = { throw IOException("not used") }
        ).create(paymentId = "pay-1", paymentSecret = "secret", scope = PaymentSession.DEFAULT_SCOPE) {}
    }

    private companion object {
        fun token(accessToken: String = "jwt") =
            retrofit2.Response.success(TokenResponse(accessToken = accessToken, tokenType = "Bearer"))

        fun response(chain: Interceptor.Chain, code: Int, body: String): Response =
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
    }
}
