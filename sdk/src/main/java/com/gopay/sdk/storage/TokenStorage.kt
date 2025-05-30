package com.gopay.sdk.storage

import android.content.Context

/**
 * Interface for managing authentication token storage
 */
interface TokenStorage {
    /**
     * Saves authentication tokens
     * 
     * @param accessToken JWT access token from authentication
     * @param refreshToken Opaque string token used to obtain new access tokens
     */
    fun saveTokens(accessToken: String, refreshToken: String)

    /**
     * Retrieves the current access token
     * 
     * @return The JWT access token or null if not available
     */
    fun getAccessToken(): String?

    /**
     * Retrieves the current refresh token
     * 
     * @return The opaque refresh token string or null if not available
     */
    fun getRefreshToken(): String?

    /**
     * Clears all stored tokens
     */
    fun clear()
}

/**
 * SharedPreferences implementation of TokenStorage
 */
class SharedPrefsTokenStorage(context: Context) : TokenStorage {
    private val appContext = context.applicationContext // Always use application context
    
    private companion object {
        private const val PREFS_NAME = "gopay_sdk_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
    
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }
    
    override fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    override fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }
    
    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }
}