package cz.gopay.sdk.modules.network

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopayGatewayError
import cz.gopay.sdk.exception.GopaySDKException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

/**
 * The gateway explains a failed request in its `Error-Response-Body`; the SDK keeps that
 * explanation instead of a bare "HTTP 403", and never fails over a body that is not one.
 */
class GatewayErrorBodyTest {

    private val json = "application/json".toMediaType()

    @Test
    fun `reads error, message and detail from the gateway's error body`() {
        val parsed = GatewayErrorBody.parse(
            """{"code":403,"error":"FORBIDDEN","message":"Google Pay is not enabled",""" +
                """"detail":"payment method not allowed","path":"/payments/1","timestamp":"2026-10-15T10:00:00Z"}"""
        )

        assertEquals(
            GopayGatewayError(
                error = "FORBIDDEN",
                message = "Google Pay is not enabled",
                detail = "payment method not allowed"
            ),
            parsed
        )
    }

    @Test
    fun `a partial body keeps what is there`() {
        assertEquals(
            GopayGatewayError(error = "BAD_REQUEST"),
            GatewayErrorBody.parse("""{"error":"BAD_REQUEST","detail":null,"message":42}""")
        )
    }

    @Test
    fun `anything that is not the gateway's error body gives null`() {
        listOf(null, "", "<html>502 Bad Gateway</html>", "[]", "{}", """{"code":500}""", "{\"error\":")
            .forEach { assertNull("$it", GatewayErrorBody.parse(it)) }
    }

    @Test
    fun `unwrap keeps the gateway's error and appends its message`() {
        val response: Response<String> = Response.error(
            403,
            """{"error":"FORBIDDEN","message":"Google Pay is not enabled","detail":"x"}""".toResponseBody(json)
        )

        val e = assertThrows(GopaySDKException::class.java) { response.unwrap("get Google Pay info") }

        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        assertEquals("Failed to get Google Pay info: HTTP 403: Google Pay is not enabled", e.message)
        assertEquals("FORBIDDEN", e.httpContext?.gatewayError?.error)
        assertEquals("x", e.httpContext?.gatewayError?.detail)
    }

    @Test
    fun `a blank gateway message adds nothing and a padded one is trimmed`() {
        assertEquals("", GatewayErrorBody.messageSuffix(GopayGatewayError(message = "  ")))
        assertEquals("", GatewayErrorBody.messageSuffix(GopayGatewayError(error = "FORBIDDEN")))
        assertEquals(": Not enabled", GatewayErrorBody.messageSuffix(GopayGatewayError(message = " Not enabled\n")))
    }

    @Test
    fun `unwrap without a gateway body says only the status`() {
        val response: Response<String> = Response.error(502, "<html>bad gateway</html>".toResponseBody(null))

        val e = assertThrows(GopaySDKException::class.java) { response.unwrap("charge payment") }

        assertEquals("Failed to charge payment: HTTP 502", e.message)
        assertNull(e.httpContext?.gatewayError)
        assertEquals("<html>bad gateway</html>", e.httpContext?.responseBody)
    }
}
