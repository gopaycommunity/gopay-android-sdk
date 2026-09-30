package cz.gopay.sdk.modules.network

import cz.gopay.sdk.BuildConfig
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for UserAgentInterceptor
 * 
 * These tests verify that:
 * - User-Agent header is added to every request that has none
 * - User-Agent format is correct: "GoPay Android SDK {VERSION}"
 * - Version is read from BuildConfig.VERSION_NAME
 * - A User-Agent the request set itself is left alone
 */
class UserAgentInterceptorTest {

    private lateinit var mockChain: Interceptor.Chain
    private lateinit var userAgentInterceptor: UserAgentInterceptor

    @Before
    fun setup() {
        mockChain = mock()
        userAgentInterceptor = UserAgentInterceptor()
    }

    @Test
    fun `intercept should add User-Agent header to request`() {
        // Given a request without User-Agent header
        val originalRequest = createMockRequest("https://api.gopay.com/payments")
        val expectedResponse = createMockResponse(200)

        whenever(mockChain.request()).thenReturn(originalRequest)
        whenever(mockChain.proceed(any())).thenReturn(expectedResponse)

        // When intercepting the request
        val response = userAgentInterceptor.intercept(mockChain)

        // Then User-Agent header should be added
        assertEquals(expectedResponse, response)

        val capturedRequest = argumentCaptor<Request>()
        verify(mockChain).proceed(capturedRequest.capture())

        val userAgentHeader = capturedRequest.firstValue.header("User-Agent")
        assertNotNull("User-Agent header should be present", userAgentHeader)
    }

    @Test
    fun `intercept should format User-Agent header correctly`() {
        // Given a request
        val originalRequest = createMockRequest("https://api.gopay.com/payments")
        val expectedResponse = createMockResponse(200)

        whenever(mockChain.request()).thenReturn(originalRequest)
        whenever(mockChain.proceed(any())).thenReturn(expectedResponse)

        // When intercepting the request
        userAgentInterceptor.intercept(mockChain)

        // Then User-Agent should have correct format
        val capturedRequest = argumentCaptor<Request>()
        verify(mockChain).proceed(capturedRequest.capture())

        val userAgentHeader = capturedRequest.firstValue.header("User-Agent")
        val expectedUserAgent = "GoPay Android SDK ${BuildConfig.VERSION_NAME}"
        assertEquals("User-Agent should match expected format", expectedUserAgent, userAgentHeader)
    }

    @Test
    fun `intercept should add User-Agent header to different endpoints`() {
        // Test different endpoints
        val endpoints = listOf(
            "https://api.gopay.com/payments",
            "https://api.gopay.com/oauth2/token",
            "https://api.gopay.com/v1/cards",
            "https://api.sandbox.gopay.com/payments"
        )

        endpoints.forEach { url ->
            // Create a new mock chain for each iteration to avoid verification conflicts
            val chain = mock<Interceptor.Chain>()
            val originalRequest = createMockRequest(url)
            val expectedResponse = createMockResponse(200)

            whenever(chain.request()).thenReturn(originalRequest)
            whenever(chain.proceed(any())).thenReturn(expectedResponse)

            // When intercepting the request
            userAgentInterceptor.intercept(chain)

            // Then User-Agent header should be added
            val capturedRequest = argumentCaptor<Request>()
            verify(chain).proceed(capturedRequest.capture())

            val userAgentHeader = capturedRequest.firstValue.header("User-Agent")
            assertNotNull("User-Agent header should be present for $url", userAgentHeader)
            assertEquals("User-Agent should match expected format for $url",
                "GoPay Android SDK ${BuildConfig.VERSION_NAME}", userAgentHeader)
        }
    }

    @Test
    fun `intercept should keep an existing User-Agent header`() {
        // GET /cards/browser-data names the 3DS WebView's User-Agent so the gateway echoes that
        // one back as browser_data.user_agent; the SDK's HTTP client name must not replace it.
        val originalRequest = Request.Builder()
            .url("https://api.gopay.com/cards/browser-data")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        val expectedResponse = createMockResponse(200)

        whenever(mockChain.request()).thenReturn(originalRequest)
        whenever(mockChain.proceed(any())).thenReturn(expectedResponse)

        userAgentInterceptor.intercept(mockChain)

        val capturedRequest = argumentCaptor<Request>()
        verify(mockChain).proceed(capturedRequest.capture())

        assertEquals("User-Agent set by the request must survive",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36",
            capturedRequest.firstValue.header("User-Agent"))
    }

    @Test
    fun `intercept should preserve other headers`() {
        // Given a request with other headers
        val originalRequest = Request.Builder()
            .url("https://api.gopay.com/payments")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .build()
        val expectedResponse = createMockResponse(200)

        whenever(mockChain.request()).thenReturn(originalRequest)
        whenever(mockChain.proceed(any())).thenReturn(expectedResponse)

        // When intercepting the request
        userAgentInterceptor.intercept(mockChain)

        // Then other headers should be preserved
        val capturedRequest = argumentCaptor<Request>()
        verify(mockChain).proceed(capturedRequest.capture())

        val modifiedRequest = capturedRequest.firstValue
        assertEquals("Content-Type should be preserved", 
            "application/json", modifiedRequest.header("Content-Type"))
        assertEquals("Accept should be preserved", 
            "application/json", modifiedRequest.header("Accept"))
        assertNotNull("User-Agent should be added", modifiedRequest.header("User-Agent"))
    }

    private fun createMockRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .build()
    }

    private fun createMockResponse(code: Int): okhttp3.Response {
        return okhttp3.Response.Builder()
            .request(createMockRequest("https://api.gopay.com/test"))
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Test Response")
            .build()
    }
}

