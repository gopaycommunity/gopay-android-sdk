package cz.gopay.sdk.modules.network

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64

/**
 * Verifies [ShareableKeyInterceptor] stamps every outbound request with the
 * `Authorization: Basic base64(clientId:shareableKey)` header expected by the public endpoints.
 */
class ShareableKeyInterceptorTest {

    @Test
    fun `intercept adds a Basic Authorization header derived from the credentials`() {
        val interceptor = ShareableKeyInterceptor(clientId = "client-1", shareableKey = "secret-key")
        val originalRequest = Request.Builder().url("https://api.gopay.com/cards/public-key").build()

        val captor = argumentCaptor<Request>()
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn originalRequest
        }
        whenever(chain.proceed(captor.capture())).doReturn(dummyResponse(originalRequest))

        interceptor.intercept(chain)

        val sent = captor.firstValue
        val authHeader = sent.header("Authorization")
        assertTrue("Header must be Basic", authHeader!!.startsWith("Basic "))

        val decoded = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        assertEquals("client-1:secret-key", decoded)
    }

    @Test
    fun `intercept preserves the original url`() {
        val interceptor = ShareableKeyInterceptor("c", "k")
        val originalRequest = Request.Builder().url("https://api.gopay.com/cards/form-url").build()

        val captor = argumentCaptor<Request>()
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn originalRequest
        }
        whenever(chain.proceed(captor.capture())).doReturn(dummyResponse(originalRequest))

        interceptor.intercept(chain)

        assertEquals(originalRequest.url, captor.firstValue.url)
    }

    private fun dummyResponse(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
}
