package cz.gopay.sdk.modules.network

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

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
    fun `unwrap throws AUTH_INVALID_RESPONSE when body is null`() {
        val response: Response<String> = Response.success<String?>(null) as Response<String>

        try {
            response.unwrap("fetch thing")
            fail("Expected GopaySDKException for empty body")
        } catch (e: GopaySDKException) {
            assertEquals(GopayErrorCodes.AUTH_INVALID_RESPONSE, e.errorCode)
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
}
