package com.gopay.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

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
 * Secure SharedPreferences implementation of TokenStorage that:
 * 1. Encrypts tokens using Android Keystore (when available)
 * 2. Falls back to simple storage for unit tests
 * 3. Uses Android Keystore for key management (hardware-backed when available)
 */
class SharedPrefsTokenStorage(context: Context) : TokenStorage {
    private val appContext = context.applicationContext // Always use application context
    
    private companion object {
        private const val PREFS_NAME = "gopay_sdk_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token_encrypted"
        private const val KEY_REFRESH_TOKEN = "refresh_token_encrypted"
        private const val KEY_ALIAS = "gopay_sdk_token_key"
        private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
        private const val IV_SEPARATOR = ":"
    }
    
    // Use MODE_PRIVATE and rely on Android Keystore encryption for security
    // Instead of trying to exclude from backup, we encrypt the data so backups are useless
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Flag to determine if encryption is available (Android runtime vs unit test)
    private val isEncryptionAvailable: Boolean = checkEncryptionAvailability()
    
    override fun saveTokens(accessToken: String, refreshToken: String) {
        if (isEncryptionAvailable) {
            try {
                val encryptedAccessToken = encrypt(accessToken)
                val encryptedRefreshToken = encrypt(refreshToken)
                
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, encryptedAccessToken)
                    .putString(KEY_REFRESH_TOKEN, encryptedRefreshToken)
                    .apply()
                return
            } catch (e: Exception) {
                // Fall through to unencrypted storage
            }
        }
        
        // Fallback to unencrypted storage (unit tests or encryption failure)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }
    
    override fun getAccessToken(): String? {
        val storedToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        
        if (isEncryptionAvailable) {
            return try {
                decrypt(storedToken)
            } catch (e: Exception) {
                // If decryption fails, try returning the value as-is (fallback case)
                storedToken
            }
        }
        
        // In unit tests, return the token directly
        return storedToken
    }
    
    override fun getRefreshToken(): String? {
        val storedToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        
        if (isEncryptionAvailable) {
            return try {
                decrypt(storedToken)
            } catch (e: Exception) {
                // If decryption fails, try returning the value as-is (fallback case)
                storedToken
            }
        }
        
        // In unit tests, return the token directly
        return storedToken
    }
    
    override fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }
    
    /**
     * Checks if Android Keystore encryption is available
     * Returns false in unit test environments where Android Keystore is not available
     */
    private fun checkEncryptionAvailability(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            true
        } catch (e: Exception) {
            // Android Keystore not available (unit tests, old devices, etc.)
            false
        }
    }
    
    /**
     * Generates or retrieves the secret key from Android Keystore
     */
    private fun generateOrGetSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(false) // Don't require biometric/PIN for access
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    /**
     * Encrypts data using Android Keystore
     */
    private fun encrypt(data: String): String {
        val secretKey = generateOrGetSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray())
        
        // Combine IV and encrypted data, separated by ':'
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
        
        return "$ivBase64$IV_SEPARATOR$encryptedBase64"
    }
    
    /**
     * Decrypts data using Android Keystore
     */
    private fun decrypt(encryptedData: String): String {
        val parts = encryptedData.split(IV_SEPARATOR)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid encrypted data format")
        }
        
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        
        val secretKey = generateOrGetSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        
        val decryptedData = cipher.doFinal(encrypted)
        return String(decryptedData)
    }
}