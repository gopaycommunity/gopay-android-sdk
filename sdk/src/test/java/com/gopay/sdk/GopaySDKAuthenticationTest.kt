package com.gopay.sdk

import android.content.Context
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.internal.GopayContextProvider
import com.gopay.sdk.model.AuthenticationResponse
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.JwtUtils
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

/**
 * Unit tests for authentication functionality in GopaySDK
 * These tests focus on JWT validation logic without requiring Android context
 */
class GopaySDKAuthenticationTest {

    private lateinit var config: GopayConfig

    @Before
    fun setup() {
        config = GopayConfig(
            environment = Environment.SANDBOX,
            debug = true
        )
    }

    @After
    fun tearDown() {
        // Reset SDK instance and context between tests using reflection
        val field = GopaySDK::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
        
        // Clear the context provider for clean test state
        GopayContextProvider.clearContext()
    }

    @Test
    fun `JWT validation should work correctly for valid tokens`() {
        // Given a valid authentication response with non-expired access token
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val validAccessToken = createTestJwtToken(exp = futureExp, sub = "test_client")
        val validRefreshToken = createTestJwtToken(exp = futureExp + 7200) // 2 hours from access token (used as opaque string)
        
        val authResponse = AuthenticationResponse(
            accessToken = validAccessToken,
            tokenType = "bearer",
            refreshToken = validRefreshToken,
            scope = "payment:create payment:read"
        )

        // Then no exception should be thrown for valid tokens
        assertNotNull("Authentication response should be valid", authResponse)
        assertFalse("Access token should not be expired", JwtUtils.isTokenExpired(validAccessToken))
        // Note: Refresh tokens are not validated as JWTs in production
    }

    @Test
    fun `JWT validation should detect expired access token`() {
        // Given an authentication response with expired access token
        val pastExp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val expiredAccessToken = createTestJwtToken(exp = pastExp)
        
        // Then the JWT validation should detect the expired access token
        assertTrue("Access token should be expired", JwtUtils.isTokenExpired(expiredAccessToken))
        // Note: Refresh tokens are not validated as JWTs in production
    }

    @Test
    fun `setAuthenticationResponse should work with auto-context initialization`() {
        // Given SDK initialized with auto-context (mock context provider)
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()

        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val validAccessToken = createTestJwtToken(exp = futureExp)
        val validRefreshToken = createTestJwtToken(exp = futureExp + 7200)
        
        val authResponse = AuthenticationResponse(
            accessToken = validAccessToken,
            tokenType = "bearer",
            refreshToken = validRefreshToken
        )

        // Mock the token storage
        val mockTokenStorage = mock<TokenStorage>()
        injectMockTokenStorage(sdk, mockTokenStorage)

        // When setting authentication response with auto-context
        sdk.setAuthenticationResponse(authResponse)
        
        // Then the tokens should be saved successfully
        verify(mockTokenStorage).saveTokens(validAccessToken, validRefreshToken)
    }

    @Test
    fun `setAuthenticationResponse should throw exception for expired access token`() {
        // Given an authentication response with expired access token
        val pastExp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val expiredAccessToken = createTestJwtToken(exp = pastExp)
        val validRefreshToken = createTestJwtToken(exp = futureExp)
        
        val authResponse = AuthenticationResponse(
            accessToken = expiredAccessToken,
            tokenType = "bearer",
            refreshToken = validRefreshToken
        )

        // Initialize SDK with auto-context
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        
        // Mock a token storage and inject it using reflection
        val mockTokenStorage = mock<TokenStorage>()
        injectMockTokenStorage(sdk, mockTokenStorage)

        // When setting the authentication response with expired access token
        // Then a GopaySDKException should be thrown
        val exception = assertThrows(GopaySDKException::class.java) {
            sdk.setAuthenticationResponse(authResponse)
        }
        
        assertTrue("Exception message should mention expired access token", 
            exception.message!!.contains("Access token is expired"))
    }

    @Test
    fun `setAuthenticationResponse should save valid tokens successfully`() {
        // Given a valid authentication response with non-expired tokens
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val validAccessToken = createTestJwtToken(exp = futureExp, sub = "test_client")
        val validRefreshToken = createTestJwtToken(exp = futureExp + 7200) // 2 hours from access token
        
        val authResponse = AuthenticationResponse(
            accessToken = validAccessToken,
            tokenType = "bearer",
            refreshToken = validRefreshToken,
            scope = "payment:create payment:read"
        )

        // Initialize SDK with auto-context
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        
        // Mock a token storage and inject it using reflection
        val mockTokenStorage = mock<TokenStorage>()
        injectMockTokenStorage(sdk, mockTokenStorage)

        // When setting the authentication response with valid tokens
        sdk.setAuthenticationResponse(authResponse)

        // Then the tokens should be saved to storage
        verify(mockTokenStorage).saveTokens(validAccessToken, validRefreshToken)
    }

    @Test
    fun `setAuthenticationResponse should save tokens successfully even with expired refresh token`() {
        // Given an authentication response with valid access token and any refresh token
        // (refresh tokens are opaque strings, not JWTs, so expiration is not validated client-side)
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val pastExp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val validAccessToken = createTestJwtToken(exp = futureExp)
        val anyRefreshToken = createTestJwtToken(exp = pastExp) // This simulates an opaque string
        
        val authResponse = AuthenticationResponse(
            accessToken = validAccessToken,
            tokenType = "bearer",
            refreshToken = anyRefreshToken
        )

        // Initialize SDK with auto-context
        val mockContext = mock<Context>()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        GopayContextProvider.setApplicationContext(mockContext)
        
        GopaySDK.initialize(config)
        val sdk = GopaySDK.getInstance()
        
        // Mock a token storage and inject it using reflection
        val mockTokenStorage = mock<TokenStorage>()
        injectMockTokenStorage(sdk, mockTokenStorage)

        // When setting the authentication response
        // Then no exception should be thrown and tokens should be saved
        sdk.setAuthenticationResponse(authResponse)
        
        // Verify the tokens were saved to storage
        verify(mockTokenStorage).saveTokens(validAccessToken, anyRefreshToken)
    }

    /**
     * Helper method to inject a mock TokenStorage into the SDK using reflection
     * This allows us to test the setAuthenticationResponse method without Android context
     */
    private fun injectMockTokenStorage(sdk: GopaySDK, mockTokenStorage: TokenStorage) {
        try {
            // Get the NetworkManager field
            val networkManagerField = GopaySDK::class.java.getDeclaredField("networkManager")
            networkManagerField.isAccessible = true
            val networkManager = networkManagerField.get(sdk)
            
            // Get the _tokenStorage field in NetworkManager (private backing field)
            val tokenStorageField = networkManager::class.java.getDeclaredField("_tokenStorage")
            tokenStorageField.isAccessible = true
            tokenStorageField.set(networkManager, mockTokenStorage)
        } catch (e: Exception) {
            throw RuntimeException("Failed to inject mock token storage", e)
        }
    }

    /**
     * Helper method to create a test JWT token with specified claims
     */
    private fun createTestJwtToken(
        exp: Long? = null,
        sub: String? = null
    ): String {
        // Create header
        val header = """{"alg":"HS256","typ":"JWT"}"""
        val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
        
        // Create payload
        val payloadParts = mutableListOf<String>()
        exp?.let { payloadParts.add("\"exp\":$it") }
        sub?.let { payloadParts.add("\"sub\":\"$it\"") }
        
        val payload = if (payloadParts.isEmpty()) "{}" else "{${payloadParts.joinToString(",")}}"
        val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        
        // Create signature (dummy for testing)
        val signature = Base64.getUrlEncoder().withoutPadding().encodeToString("dummy_signature".toByteArray())
        
        return "$encodedHeader.$encodedPayload.$signature"
    }
} 