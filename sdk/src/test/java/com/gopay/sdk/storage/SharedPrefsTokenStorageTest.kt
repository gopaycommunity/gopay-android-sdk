package com.gopay.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
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
} 