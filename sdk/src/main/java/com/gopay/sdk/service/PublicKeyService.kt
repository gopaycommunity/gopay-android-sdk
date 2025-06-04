package com.gopay.sdk.service

import com.gopay.sdk.model.Jwk
import com.gopay.sdk.modules.network.GopayApiService
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.JsonUtils

/**
 * Service for fetching and managing the public encryption key from GoPay API
 * Uses consolidated GopayApiService with automatic authorization via AuthenticationInterceptor
 */
class PublicKeyService(
    private val apiService: GopayApiService,
    private val tokenStorage: TokenStorage
) {

    /**
     * Fetches the public encryption key from GoPay API and stores it
     * Authorization is automatically handled by AuthenticationInterceptor
     * 
     * @return The JWK public key
     * @throws IllegalStateException if no access token is available
     * @throws Exception for network or API errors
     */
    suspend fun fetchAndStorePublicKey(): Jwk {
        val response = apiService.getPublicKey()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch public key: ${response.code()} ${response.message()}")
        }
        
        val jwk = response.body()
            ?: throw Exception("Empty response body when fetching public key")
        
        // Store the JWK as JSON string
        val jwkJson = JsonUtils.toJson(jwk)
            ?: throw Exception("Failed to serialize JWK")
        tokenStorage.savePublicKey(jwkJson)
        
        return jwk
    }

    /**
     * Gets the cached public key from storage, or fetches it if not available
     * 
     * @return The JWK public key
     */
    suspend fun getPublicKey(): Jwk {
        val cachedKey = tokenStorage.getPublicKey()
        
        return if (cachedKey != null) {
            // Parse cached key
            JsonUtils.fromJson<Jwk>(cachedKey)
                ?: throw Exception("Invalid cached public key format")
        } else {
            // Fetch from API
            fetchAndStorePublicKey()
        }
    }

    /**
     * Checks if a valid public key is available in cache
     */
    fun isPublicKeyAvailable(): Boolean {
        return tokenStorage.getPublicKey() != null
    }

    /**
     * Clears the cached public key (useful for forcing a refresh)
     */
    fun clearCachedPublicKey() {
        // We can only clear all storage or implement a more granular clear method
        // For now, this would need to be implemented in TokenStorage interface
        // tokenStorage.clearPublicKey()
    }
} 