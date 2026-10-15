package cz.gopay.sdk.modules.network

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException

/**
 * Tests [unwrap], the single choke point every SDK API call funnels through so all HTTP failures
 * surface as a uniformly-shaped [GopaySDKException].
 */
class ApiUtilsTest {

    @Test
    fun `unwrap returns body on a successful response`() {
        val response = Response.success("payload")

        val result = response.unwrap("fetch thing")

        assertEquals("payload", result)
    }

    @Test
    fun `unwrap throws INTERNAL_003 when a 2xx has no body`() {
        val response: Response<String> = Response.success<String?>(null) as Response<String>

        try {
            response.unwrap("fetch thing")
            fail("Expected GopaySDKException for empty body")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR, e.errorCode)
            assertTrue(e.message!!.contains("fetch thing"))
        }
    }

    @Test
    fun `unwrap throws NETWORK_CLIENT_ERROR with http context on error response`() {
        val errorBody = """{"error":"bad request"}""".toResponseBody(null)
        val response: Response<String> = Response.error(400, errorBody)

        try {
            response.unwrap("charge payment")
            fail("Expected GopaySDKException for HTTP error")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
            assertTrue(e.message!!.contains("charge payment"))
            assertTrue(e.message!!.contains("400"))

            val ctx = e.httpContext
            assertNotNull("HTTP context must be populated", ctx)
            assertEquals(400, ctx!!.statusCode)
            assertEquals("""{"error":"bad request"}""", ctx.responseBody)
            assertNotNull(ctx.requestUrl)
            assertNotNull(ctx.requestMethod)
        }
    }

    @Test
    fun `unwrap surfaces the status code through getHttpStatusCode`() {
        val response: Response<String> = Response.error(500, "boom".toResponseBody(null))

        try {
            response.unwrap("do thing")
            fail("Expected GopaySDKException")
        } catch (e: GopaySDKException) {
            assertEquals(500, e.getHttpStatusCode())
            assertTrue(e.isNetworkError())
        }
    }

    @Test
    fun `apiCall returns the unwrapped body`() = runTest {
        val result = apiCall("fetch thing") { Response.success("payload") }

        assertEquals("payload", result)
    }

    @Test
    fun `apiCall wraps a Moshi decoding failure as INTERNAL_SERIALIZATION_ERROR`() = runTest {
        // Retrofit decodes the body before unwrap runs, so without apiCall this escapes the SDK
        // error contract as a raw JsonDataException the host never catches.
        try {
            apiCall<String>("get charge state") {
                throw JsonDataException("Required value 'returnUrl' missing at $")
            }
            fail("Expected GopaySDKException for a malformed response")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR, e.errorCode)
            assertTrue(e.message!!.contains("get charge state"))
            assertTrue(e.cause is JsonDataException)
        }
    }

    @Test
    fun `apiCall wraps a syntactically broken body as INTERNAL_SERIALIZATION_ERROR`() = runTest {
        // JsonEncodingException extends IOException, not JsonDataException, so a captive portal
        // answering 200 with HTML used to reach the host as a bare exception.
        try {
            apiCall<String>("get charge state") {
                throw JsonEncodingException("Use JsonReader.setLenient(true) at path $")
            }
            fail("Expected GopaySDKException for an unreadable body")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR, e.errorCode)
            assertTrue(e.message!!.contains("get charge state"))
            assertTrue(e.cause is JsonEncodingException)
        }
    }

    @Test
    fun `apiCall lets a GopaySDKException through unchanged`() = runTest {
        val errorBody = """{"error":"bad request"}""".toResponseBody(null)

        try {
            apiCall<String>("charge payment") { Response.error(400, errorBody) }
            fail("Expected GopaySDKException for an HTTP error")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
            assertNotNull(e.httpContext)
        }
    }

    @Test
    fun `unwrap throws NETWORK_SERVER_ERROR for a 5xx`() {
        val response: Response<String> = Response.error(503, "".toResponseBody(null))

        val e = assertThrows(GopaySDKException::class.java) { response.unwrap("charge payment") }

        assertEquals(GopayErrorCodes.NETWORK_SERVER_ERROR, e.errorCode)
        assertEquals(503, e.getHttpStatusCode())
    }

    @Test
    fun `apiCall maps each transport failure to its network code with the cause kept`() = runTest {
        val cases = listOf(
            SocketTimeoutException("read timed out") to GopayErrorCodes.NETWORK_TIMEOUT,
            InterruptedIOException("timeout") to GopayErrorCodes.NETWORK_TIMEOUT,
            UnknownHostException("gw.sandbox.gopay.com") to GopayErrorCodes.NETWORK_NO_CONNECTION,
            ConnectException("Connection refused") to GopayErrorCodes.NETWORK_NO_CONNECTION,
            NoRouteToHostException("unreachable") to GopayErrorCodes.NETWORK_NO_CONNECTION,
            SSLHandshakeException("bad certificate").apply {
                initCause(CertificateException("untrusted"))
            } to GopayErrorCodes.NETWORK_SSL_ERROR,
            SSLHandshakeException("chain").apply {
                initCause(CertPathValidatorException("no trust anchor"))
            } to GopayErrorCodes.NETWORK_SSL_ERROR,
            SSLPeerUnverifiedException("Hostname gw.example not verified") to GopayErrorCodes.NETWORK_SSL_ERROR,
            // The connection, not the certificate: a handshake cut off or reset midway.
            SSLHandshakeException("Remote host terminated the handshake") to GopayErrorCodes.NETWORK_IO_ERROR,
            SSLException("Connection reset") to GopayErrorCodes.NETWORK_IO_ERROR,
            IOException("unexpected end of stream") to GopayErrorCodes.NETWORK_IO_ERROR
        )
        for ((failure, code) in cases) {
            try {
                apiCall<String>("charge payment") { throw failure }
                fail("Expected GopaySDKException for $failure")
            } catch (e: GopaySDKException) {
                assertEquals("$failure", code, e.errorCode)
                assertSame(failure, e.cause)
            }
        }
    }

    @Test
    fun `apiCall lets a coroutine cancellation through unmapped`() = runTest {
        val cancellation = CancellationException("host left")
        try {
            apiCall<String>("charge payment") { throw cancellation }
            fail("Expected the cancellation")
        } catch (e: CancellationException) {
            assertSame(cancellation, e)
        }
    }
}
