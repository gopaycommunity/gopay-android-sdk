package com.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class JwtUtilsTest {

    @Test
    fun testIsTokenExpired_withValidToken() {
        // Given a JWT token that expires in the future (1 hour from now)
        val futureExp = (System.currentTimeMillis() / 1000) + 3600 // 1 hour from now
        val token = createTestJwtToken(exp = futureExp)
        
        // When checking if token is expired
        val isExpired = JwtUtils.isTokenExpired(token)
        
        // Then it should not be expired
        assertFalse("Token should not be expired", isExpired)
    }
    
    @Test
    fun testIsTokenExpired_withExpiredToken() {
        // Given a JWT token that expired 1 hour ago
        val pastExp = (System.currentTimeMillis() / 1000) - 3600 // 1 hour ago
        val token = createTestJwtToken(exp = pastExp)
        
        // When checking if token is expired
        val isExpired = JwtUtils.isTokenExpired(token)
        
        // Then it should be expired
        assertTrue("Token should be expired", isExpired)
    }
    
    @Test
    fun testIsTokenExpired_withNoExpiration() {
        // Given a JWT token with no expiration time
        val token = createTestJwtToken(exp = null)
        
        // When checking if token is expired
        val isExpired = JwtUtils.isTokenExpired(token)
        
        // Then it should not be considered expired
        assertFalse("Token without expiration should not be expired", isExpired)
    }
    
    @Test
    fun testIsTokenExpired_withMalformedToken() {
        // Given a malformed JWT token
        val malformedToken = "invalid.token"
        
        // When checking if token is expired
        val isExpired = JwtUtils.isTokenExpired(malformedToken)
        
        // Then it should be considered expired (safe default)
        assertTrue("Malformed token should be considered expired", isExpired)
    }
    
    @Test
    fun testGetClientId_withValidToken() {
        // Given a JWT token with sub field
        val clientId = "test_client_123"
        val token = createTestJwtToken(sub = clientId)
        
        // When extracting client ID
        val extractedClientId = JwtUtils.getClientId(token)
        
        // Then it should return the correct client ID
        assertEquals("Should extract correct client ID", clientId, extractedClientId)
    }
    
    @Test
    fun testGetClientId_withNoClientId() {
        // Given a JWT token without sub field
        val token = createTestJwtToken(sub = null)
        
        // When extracting client ID
        val extractedClientId = JwtUtils.getClientId(token)
        
        // Then it should return null
        assertNull("Should return null when no client ID", extractedClientId)
    }
    
    @Test
    fun testGetClientId_withMalformedToken() {
        // Given a malformed JWT token
        val malformedToken = "invalid.token"
        
        // When extracting client ID
        val extractedClientId = JwtUtils.getClientId(malformedToken)
        
        // Then it should return null
        assertNull("Should return null for malformed token", extractedClientId)
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
        
        // Create payload - ensure it's valid JSON even when empty
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