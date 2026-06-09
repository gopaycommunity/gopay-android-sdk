package cz.gopay.sdk.exception

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers [GopaySDKException]'s error-category predicates, HTTP accessors and [toString], plus the
 * [HttpErrorContext] value type. Reporting is disabled around each test so the singleton
 * [ErrorReporter] can't leak a callback between cases.
 */
class GopaySDKExceptionTest {

    @Before
    fun setUp() = ErrorReporter.clear()

    @After
    fun tearDown() = ErrorReporter.clear()

    @Test
    fun `error category predicates match the code prefix`() {
        assertTrue(GopaySDKException("AUTH_001", "m").isAuthenticationError())
        assertTrue(GopaySDKException("NETWORK_002", "m").isNetworkError())
        assertTrue(GopaySDKException("CONFIG_003", "m").isConfigurationError())
        assertTrue(GopaySDKException("PAYMENT_006", "m").isPaymentError())
        assertTrue(GopaySDKException("VALIDATION_001", "m").isValidationError())
    }

    @Test
    fun `predicates are mutually exclusive across domains`() {
        val authError = GopaySDKException("AUTH_001", "m")
        assertTrue(authError.isAuthenticationError())
        assertFalse(authError.isNetworkError())
        assertFalse(authError.isConfigurationError())
        assertFalse(authError.isPaymentError())
        assertFalse(authError.isValidationError())
    }

    @Test
    fun `getHttpStatusCode returns code when context present and null otherwise`() {
        val withCtx = GopaySDKException(
            errorCode = "NETWORK_002",
            message = "m",
            httpContext = HttpErrorContext(statusCode = 503)
        )
        assertEquals(503, withCtx.getHttpStatusCode())

        val withoutCtx = GopaySDKException("AUTH_001", "m")
        assertNull(withoutCtx.getHttpStatusCode())
    }

    @Test
    fun `cause and additionalData are retained`() {
        val cause = IllegalStateException("root")
        val data = mapOf<String, Any>("paymentId" to "p_123")
        val ex = GopaySDKException(
            errorCode = "PAYMENT_003",
            message = "failed",
            cause = cause,
            additionalData = data
        )

        assertSame(cause, ex.cause)
        assertEquals(data, ex.additionalData)
    }

    @Test
    fun `toString includes http context only when present`() {
        val plain = GopaySDKException("AUTH_001", "boom").toString()
        assertTrue(plain.contains("errorCode='AUTH_001'"))
        assertTrue(plain.contains("message='boom'"))
        assertFalse(plain.contains("httpContext="))

        val withCtx = GopaySDKException(
            errorCode = "NETWORK_002",
            message = "boom",
            httpContext = HttpErrorContext(statusCode = 418, requestMethod = "POST", requestUrl = "https://x/y")
        ).toString()
        assertTrue(withCtx.contains("httpContext="))
        assertTrue(withCtx.contains("418"))
    }

    @Test
    fun `HttpErrorContext toString omits the response body but keeps method and url`() {
        val ctx = HttpErrorContext(
            statusCode = 400,
            responseBody = "secret-ish details",
            requestUrl = "https://api.gopay.com/charge",
            requestMethod = "POST"
        )
        val s = ctx.toString()

        assertTrue(s.contains("statusCode=400"))
        assertTrue(s.contains("requestMethod=POST"))
        assertTrue(s.contains("requestUrl=https://api.gopay.com/charge"))
        assertFalse("Response body must not leak into toString", s.contains("secret-ish details"))
    }

    @Test
    fun `HttpErrorContext exposes its fields`() {
        val ctx = HttpErrorContext(401, "body", "https://u", "GET")
        assertEquals(401, ctx.statusCode)
        assertEquals("body", ctx.responseBody)
        assertEquals("https://u", ctx.requestUrl)
        assertEquals("GET", ctx.requestMethod)
    }
}
