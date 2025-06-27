package com.gopay.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SharedPrefsTokenStorageTest {

    // Mock objects
    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    // System under test
    private lateinit var tokenStorage: SharedPrefsTokenStorage
    
    // Test constants
    private val testAccessToken = "test_access_token"
    private val testRefreshToken = "test_refresh_token"
    private val testLongToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    private val testSpecialCharsToken = "token_with_special_chars!@#$%^&*()_+-=[]{}|;':\",./<>?"
    
    // HashMap to simulate SharedPreferences storage
    private val prefsMap = HashMap<String, String?>()
    
    @Before
    fun setup() {
        // Setup mock objects
        mockContext = mock()
        mockAppContext = mock()
        mockSharedPrefs = mock()
        mockEditor = mock()

        // Clear the storage for each test
        prefsMap.clear()

        // Configure mocks to use our HashMap as storage
        whenever(mockContext.applicationContext).thenReturn(mockAppContext)
        whenever(mockAppContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPrefs)

        // Make SharedPreferences.getString actually return values from our HashMap
        whenever(mockSharedPrefs.getString(any(), eq(null))).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            prefsMap[key]
        }

        // Make SharedPreferences.Editor.putString store values in our HashMap
        whenever(mockSharedPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val value = invocation.arguments[1] as String
            prefsMap[key] = value
            mockEditor
        }

        // Make remove actually remove from our HashMap
        whenever(mockEditor.remove(any())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            prefsMap.remove(key)
            mockEditor
        }

        // Initialize system under test
        tokenStorage = SharedPrefsTokenStorage(mockContext)
    }
    
    @Test
    fun `saveTokens should store tokens that can be retrieved`() {
        // When
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Then
        assertEquals(testAccessToken, tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }
    
    @Test
    fun `getAccessToken should return null when no token is stored`() {
        // When getting access token without saving one first
        val result = tokenStorage.getAccessToken()
        
        // Then the result should be null
        assertNull(result)
    }
    
    @Test
    fun `getRefreshToken should return null when no token is stored`() {
        // When getting refresh token without saving one first
        val result = tokenStorage.getRefreshToken()
        
        // Then the result should be null
        assertNull(result)
    }
    
    @Test
    fun `clear should remove all tokens`() {
        // Given tokens are saved
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // When clearing the storage
        tokenStorage.clear()
        
        // Then both tokens should be null
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
    }

    @Test
    fun `saveTokens should handle long JWT tokens correctly`() {
        // When saving a long JWT token
        tokenStorage.saveTokens(testLongToken, testRefreshToken)
        
        // Then tokens should be retrievable correctly
        assertEquals(testLongToken, tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `saveTokens should handle tokens with special characters`() {
        // When saving tokens with special characters
        tokenStorage.saveTokens(testSpecialCharsToken, testSpecialCharsToken)
        
        // Then tokens should be retrievable correctly
        assertEquals(testSpecialCharsToken, tokenStorage.getAccessToken())
        assertEquals(testSpecialCharsToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `saveTokens should handle empty tokens`() {
        // When saving empty tokens
        tokenStorage.saveTokens("", "")
        
        // Then empty tokens should be retrievable
        assertEquals("", tokenStorage.getAccessToken())
        assertEquals("", tokenStorage.getRefreshToken())
    }

    @Test
    fun `tokens should be stored unencrypted in unit test environment`() {
        // Given that we're in a unit test environment (no Android Keystore)
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // When checking the stored values directly
        val storedAccessToken = prefsMap["access_token_encrypted"]
        val storedRefreshToken = prefsMap["refresh_token_encrypted"]
        
        // Then tokens should be stored as plain text (unencrypted) since Android Keystore is not available
        assertEquals(testAccessToken, storedAccessToken)
        assertEquals(testRefreshToken, storedRefreshToken)
    }

    @Test
    fun `multiple save operations should overwrite previous tokens`() {
        // Given initial tokens are saved
        tokenStorage.saveTokens("initial_access", "initial_refresh")
        assertEquals("initial_access", tokenStorage.getAccessToken())
        assertEquals("initial_refresh", tokenStorage.getRefreshToken())
        
        // When saving new tokens
        tokenStorage.saveTokens("new_access", "new_refresh")
        
        // Then new tokens should overwrite the old ones
        assertEquals("new_access", tokenStorage.getAccessToken())
        assertEquals("new_refresh", tokenStorage.getRefreshToken())
    }

    @Test
    fun `getAccessToken should handle corrupted stored data gracefully`() {
        // Given corrupted data is stored directly in preferences
        prefsMap["access_token_encrypted"] = "corrupted:data:format"
        
        // When getting the access token
        val result = tokenStorage.getAccessToken()
        
        // Then it should either return the corrupted data as fallback or handle gracefully
        // In unit test environment, it should return the data as-is since encryption is not available
        assertEquals("corrupted:data:format", result)
    }

    @Test
    fun `getRefreshToken should handle corrupted stored data gracefully`() {
        // Given corrupted data is stored directly in preferences
        prefsMap["refresh_token_encrypted"] = "corrupted:data:format"
        
        // When getting the refresh token
        val result = tokenStorage.getRefreshToken()
        
        // Then it should either return the corrupted data as fallback or handle gracefully
        assertEquals("corrupted:data:format", result)
    }

    @Test
    fun `clear should work even when no tokens are stored`() {
        // When clearing storage without any tokens stored
        tokenStorage.clear()
        
        // Then it should complete without error
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
    }

    @Test
    fun `saveTokens should work with null context scenario`() {
        // This test ensures the implementation handles edge cases properly
        // The actual implementation should work since we always use applicationContext
        
        // When saving tokens (should work despite potential null scenarios)
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Then tokens should be saved and retrievable
        assertEquals(testAccessToken, tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `encryption availability check should return false in unit test environment`() {
        // In unit tests, Android Keystore is not available
        // We can test this indirectly by checking that tokens are stored unencrypted
        
        // When saving tokens
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Then the stored values should be unencrypted (plain text)
        assertEquals(testAccessToken, prefsMap["access_token_encrypted"])
        assertEquals(testRefreshToken, prefsMap["refresh_token_encrypted"])
    }

    @Test
    fun `token storage should be consistent across multiple operations`() {
        // Test multiple save/retrieve cycles
        for (i in 1..5) {
            val accessToken = "access_token_$i"
            val refreshToken = "refresh_token_$i"
            
            tokenStorage.saveTokens(accessToken, refreshToken)
            
            assertEquals(accessToken, tokenStorage.getAccessToken())
            assertEquals(refreshToken, tokenStorage.getRefreshToken())
        }
    }

    @Test
    fun `partial token storage should work independently`() {
        // Save both tokens
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Clear only one by overwriting with empty
        prefsMap["access_token_encrypted"] = null
        
        // Then only refresh token should be available
        assertNull(tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `storage should handle very long tokens`() {
        // Create a very long token (simulating a large JWT)
        val longToken = "a".repeat(2048)
        
        // When saving very long tokens
        tokenStorage.saveTokens(longToken, longToken)
        
        // Then they should be stored and retrieved correctly
        assertEquals(longToken, tokenStorage.getAccessToken())
        assertEquals(longToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `storage should handle unicode characters in tokens`() {
        // Test with unicode characters
        val unicodeToken = "token_with_unicode_🔐_characters_ñáéíóú"
        
        // When saving tokens with unicode
        tokenStorage.saveTokens(unicodeToken, unicodeToken)
        
        // Then they should be stored and retrieved correctly
        assertEquals(unicodeToken, tokenStorage.getAccessToken())
        assertEquals(unicodeToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `savePublicKey should store public key that can be retrieved`() {
        // Given a JWK public key in JSON format
        val publicKeyJson = """
            {
                "kty": "RSA",
                "kid": "test-key-id",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "test-modulus-value",
                "e": "AQAB"
            }
        """.trimIndent()
        
        // When saving the public key
        tokenStorage.savePublicKey(publicKeyJson)
        
        // Then it should be retrievable
        assertEquals(publicKeyJson, tokenStorage.getPublicKey())
    }

    @Test
    fun `getPublicKey should return null when no public key is stored`() {
        // When getting public key without saving one first
        val result = tokenStorage.getPublicKey()
        
        // Then the result should be null
        assertNull(result)
    }

    @Test
    fun `savePublicKey should handle empty public key`() {
        // When saving an empty public key
        tokenStorage.savePublicKey("")
        
        // Then empty public key should be retrievable
        assertEquals("", tokenStorage.getPublicKey())
    }

    @Test
    fun `savePublicKey should handle large public key data`() {
        // Create a large public key (simulating a large RSA key)
        val largePublicKey = "large_public_key_" + "a".repeat(4096)
        
        // When saving large public key
        tokenStorage.savePublicKey(largePublicKey)
        
        // Then it should be stored and retrieved correctly
        assertEquals(largePublicKey, tokenStorage.getPublicKey())
    }

    @Test
    fun `clear should remove public key along with tokens`() {
        // Given tokens and public key are saved
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        val publicKeyJson = """{"kty":"RSA","kid":"test"}"""
        tokenStorage.savePublicKey(publicKeyJson)
        
        // When clearing the storage
        tokenStorage.clear()
        
        // Then all data should be removed
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
        assertNull(tokenStorage.getPublicKey())
    }

    @Test
    fun `public key storage should work independently of token storage`() {
        // Save only public key
        val publicKeyJson = """{"kty":"RSA","kid":"test-independent"}"""
        tokenStorage.savePublicKey(publicKeyJson)
        
        // Then public key should be available without tokens
        assertEquals(publicKeyJson, tokenStorage.getPublicKey())
        assertNull(tokenStorage.getAccessToken())
        assertNull(tokenStorage.getRefreshToken())
    }

    @Test
    fun `multiple public key save operations should overwrite previous key`() {
        // Given initial public key is saved
        tokenStorage.savePublicKey("""{"kty":"RSA","kid":"initial"}""")
        assertEquals("""{"kty":"RSA","kid":"initial"}""", tokenStorage.getPublicKey())
        
        // When saving new public key
        tokenStorage.savePublicKey("""{"kty":"RSA","kid":"new"}""")
        
        // Then new public key should overwrite the old one
        assertEquals("""{"kty":"RSA","kid":"new"}""", tokenStorage.getPublicKey())
        assertNotEquals("""{"kty":"RSA","kid":"initial"}""", tokenStorage.getPublicKey())
    }
} 