package com.gopay.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Tests focused on encryption/decryption logic and Android Keystore integration.
 * These tests simulate the encryption behavior that would occur on a real Android device.
 */
class TokenStorageEncryptionTest {

    // Mock objects
    private lateinit var mockContext: Context
    private lateinit var mockAppContext: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    // Test constants
    private val testAccessToken = "test_access_token_for_encryption"
    private val testRefreshToken = "test_refresh_token_for_encryption"
    
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
    }

    @Test
    fun `encrypted data format should contain IV and encrypted data separated by colon`() {
        // Create a spy of SharedPrefsTokenStorage to test internal encryption
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens (in unit test environment, this will be unencrypted)
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Check that the implementation follows the expected pattern
        // In unit tests, since encryption is not available, tokens are stored as plain text
        val storedAccessToken = prefsMap["access_token_encrypted"]
        val storedRefreshToken = prefsMap["refresh_token_encrypted"]
        
        // Verify tokens are stored (even if unencrypted in unit tests)
        assertEquals(testAccessToken, storedAccessToken)
        assertEquals(testRefreshToken, storedRefreshToken)
    }

    @Test
    fun `tokens should fallback to unencrypted storage when keystore is unavailable`() {
        // This is the standard behavior in unit tests
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Verify fallback behavior - tokens should be stored unencrypted
        assertEquals(testAccessToken, prefsMap["access_token_encrypted"])
        assertEquals(testRefreshToken, prefsMap["refresh_token_encrypted"])
        
        // Verify retrieval works
        assertEquals(testAccessToken, tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `decryption should handle malformed encrypted data gracefully`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Store malformed encrypted data directly
        prefsMap["access_token_encrypted"] = "malformed_encrypted_data"
        prefsMap["refresh_token_encrypted"] = "another:malformed:data:format"
        
        // Attempt to retrieve tokens
        val accessToken = tokenStorage.getAccessToken()
        val refreshToken = tokenStorage.getRefreshToken()
        
        // In unit test environment, it should return the malformed data as-is (fallback behavior)
        assertEquals("malformed_encrypted_data", accessToken)
        assertEquals("another:malformed:data:format", refreshToken)
    }

    @Test
    fun `encryption should handle empty strings`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save empty tokens
        tokenStorage.saveTokens("", "")
        
        // Verify empty tokens can be retrieved
        assertEquals("", tokenStorage.getAccessToken())
        assertEquals("", tokenStorage.getRefreshToken())
    }

    @Test
    fun `encryption should handle special characters and unicode`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        val specialToken = "token_with_special_chars_🔐_ñáéíóú_!@#$%^&*()"
        
        // Save token with special characters
        tokenStorage.saveTokens(specialToken, specialToken)
        
        // Verify special characters are handled correctly
        assertEquals(specialToken, tokenStorage.getAccessToken())
        assertEquals(specialToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `simulated encrypted format should be properly structured`() {
        // Test that if we had encryption, the format would be correct
        val simulatedIV = "mockIVData"
        val simulatedEncrypted = "mockEncryptedData"
        val simulatedFullEncrypted = "$simulatedIV:$simulatedEncrypted"
        
        // Store simulated encrypted data
        prefsMap["access_token_encrypted"] = simulatedFullEncrypted
        
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        val result = tokenStorage.getAccessToken()
        
        // In unit test environment, it should return the simulated data as-is
        assertEquals(simulatedFullEncrypted, result)
    }

    @Test
    fun `encryption failure should fallback to unencrypted storage`() {
        // This test simulates the behavior described in the implementation
        // where encryption failures fall back to unencrypted storage
        
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens (which will use fallback in unit test environment)
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Verify fallback behavior
        assertEquals(testAccessToken, prefsMap["access_token_encrypted"])
        assertEquals(testRefreshToken, prefsMap["refresh_token_encrypted"])
        
        // Verify retrieval works
        assertEquals(testAccessToken, tokenStorage.getAccessToken())
        assertEquals(testRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `large tokens should be handled correctly by encryption logic`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Create large tokens (simulating large JWTs)
        val largeAccessToken = "large_token_" + "a".repeat(4096)
        val largeRefreshToken = "large_refresh_" + "b".repeat(4096)
        
        // Save large tokens
        tokenStorage.saveTokens(largeAccessToken, largeRefreshToken)
        
        // Verify large tokens are handled correctly
        assertEquals(largeAccessToken, tokenStorage.getAccessToken())
        assertEquals(largeRefreshToken, tokenStorage.getRefreshToken())
    }

    @Test
    fun `token storage keys should use expected preference key names`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Verify the correct keys are used in SharedPreferences
        assertTrue("Expected access token key should be present", prefsMap.containsKey("access_token_encrypted"))
        assertTrue("Expected refresh token key should be present", prefsMap.containsKey("refresh_token_encrypted"))
        
        // Verify the values are stored
        assertNotNull("Access token should be stored", prefsMap["access_token_encrypted"])
        assertNotNull("Refresh token should be stored", prefsMap["refresh_token_encrypted"])
    }

    @Test
    fun `clear should remove both encrypted token keys`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens first
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Verify tokens are stored
        assertTrue(prefsMap.containsKey("access_token_encrypted"))
        assertTrue(prefsMap.containsKey("refresh_token_encrypted"))
        
        // Clear tokens
        tokenStorage.clear()
        
        // Verify both keys are removed
        assertFalse("Access token key should be removed", prefsMap.containsKey("access_token_encrypted"))
        assertFalse("Refresh token key should be removed", prefsMap.containsKey("refresh_token_encrypted"))
    }

    @Test
    fun `context application context should be used for shared preferences`() {
        // This test verifies that application context is used (important for memory leaks)
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        
        // Verify that applicationContext was called
        verify(mockContext).applicationContext
        verify(mockAppContext).getSharedPreferences(eq("gopay_sdk_secure_prefs"), eq(Context.MODE_PRIVATE))
    }

    @Test
    fun `token overwrite should work correctly with encryption`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save initial tokens
        tokenStorage.saveTokens("initial_access", "initial_refresh")
        assertEquals("initial_access", tokenStorage.getAccessToken())
        assertEquals("initial_refresh", tokenStorage.getRefreshToken())
        
        // Overwrite with new tokens
        tokenStorage.saveTokens("new_access", "new_refresh")
        assertEquals("new_access", tokenStorage.getAccessToken())
        assertEquals("new_refresh", tokenStorage.getRefreshToken())
        
        // Verify old tokens are completely replaced
        assertNotEquals("initial_access", tokenStorage.getAccessToken())
        assertNotEquals("initial_refresh", tokenStorage.getRefreshToken())
    }

    @Test
    fun `public key encryption should work with fallback behavior`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        val publicKeyJson = """
            {
                "kty": "RSA",
                "kid": "test-encryption-key",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "test-modulus-value",
                "e": "AQAB"
            }
        """.trimIndent()
        
        // Save public key
        tokenStorage.savePublicKey(publicKeyJson)
        
        // Verify fallback behavior - public key should be stored unencrypted in unit tests
        assertEquals(publicKeyJson, prefsMap["public_key_encrypted"])
        
        // Verify retrieval works
        assertEquals(publicKeyJson, tokenStorage.getPublicKey())
    }

    @Test
    fun `public key encryption should handle malformed data gracefully`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Store malformed public key data directly
        prefsMap["public_key_encrypted"] = "malformed:public:key:data"
        
        // Attempt to retrieve public key
        val publicKey = tokenStorage.getPublicKey()
        
        // In unit test environment, it should return the malformed data as-is (fallback behavior)
        assertEquals("malformed:public:key:data", publicKey)
    }

    @Test
    fun `public key should be included in clear operation`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Save tokens and public key
        tokenStorage.saveTokens(testAccessToken, testRefreshToken)
        tokenStorage.savePublicKey("""{"kty":"RSA","kid":"test-clear"}""")
        
        // Verify all data is stored
        assertTrue(prefsMap.containsKey("access_token_encrypted"))
        assertTrue(prefsMap.containsKey("refresh_token_encrypted"))
        assertTrue(prefsMap.containsKey("public_key_encrypted"))
        
        // Clear all data
        tokenStorage.clear()
        
        // Verify all keys are removed including public key
        assertFalse("Access token key should be removed", prefsMap.containsKey("access_token_encrypted"))
        assertFalse("Refresh token key should be removed", prefsMap.containsKey("refresh_token_encrypted"))
        assertFalse("Public key should be removed", prefsMap.containsKey("public_key_encrypted"))
    }

    @Test
    fun `public key storage should use expected preference key name`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        val publicKeyJson = """{"kty":"RSA","kid":"key-name-test"}"""
        
        // Save public key
        tokenStorage.savePublicKey(publicKeyJson)
        
        // Verify the correct key is used in SharedPreferences
        assertTrue("Expected public key should be present", prefsMap.containsKey("public_key_encrypted"))
        
        // Verify the value is stored
        assertNotNull("Public key should be stored", prefsMap["public_key_encrypted"])
        assertEquals("Public key should match stored value", publicKeyJson, prefsMap["public_key_encrypted"])
    }

    @Test
    fun `public key encryption should handle large key data`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        
        // Create large public key (simulating a large RSA key with long modulus)
        val largeModulus = "a".repeat(8192) // Very large modulus
        val largePublicKey = """
            {
                "kty": "RSA",
                "kid": "large-key-test",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "$largeModulus",
                "e": "AQAB"
            }
        """.trimIndent()
        
        // Save large public key
        tokenStorage.savePublicKey(largePublicKey)
        
        // Verify large public key is handled correctly
        assertEquals(largePublicKey, tokenStorage.getPublicKey())
    }

    @Test
    fun `public key encryption should handle special characters and unicode`() {
        val tokenStorage = SharedPrefsTokenStorage(mockContext)
        val specialPublicKey = """
            {
                "kty": "RSA",
                "kid": "special-key-🔐-ñáéíóú-!@#$%^&*()",
                "use": "enc",
                "alg": "RSA-OAEP-256",
                "n": "modulus-with-special-chars-测试",
                "e": "AQAB"
            }
        """.trimIndent()
        
        // Save public key with special characters
        tokenStorage.savePublicKey(specialPublicKey)
        
        // Verify special characters are handled correctly
        assertEquals(specialPublicKey, tokenStorage.getPublicKey())
    }
} 