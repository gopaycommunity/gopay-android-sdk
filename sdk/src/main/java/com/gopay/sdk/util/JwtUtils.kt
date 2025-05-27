package com.gopay.sdk.util

/**
 * Utility class for JWT token operations
 */
object JwtUtils {
    
    /**
     * Checks if a JWT token is expired
     * 
     * @param token The JWT token to check
     * @return true if the token is expired, false otherwise
     * @throws IllegalArgumentException if the token is malformed
     */
    fun isTokenExpired(token: String): Boolean {
        try {
            val payloadJson = decodePayload(token)
            val exp = extractLongFromJson(payloadJson, "exp") ?: 0L
            
            // If no expiration time is set, consider it as not expired
            if (exp == 0L) return false
            
            // Compare with current time (exp is in seconds, System.currentTimeMillis() is in milliseconds)
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            return currentTimeSeconds >= exp
        } catch (e: Exception) {
            // If we can't decode the token, consider it expired
            return true
        }
    }
    
    /**
     * Decodes the payload of a JWT token
     * 
     * @param token The JWT token
     * @return String containing the JSON payload
     * @throws IllegalArgumentException if the token is malformed
     */
    private fun decodePayload(token: String): String {
        val parts = token.split(".")
        require(parts.size == 3) { "Invalid JWT token format" }

        val payload = parts[1]
        // Add padding if necessary for Base64 decoding
        val paddedPayload = payload + "=".repeat((4 - payload.length % 4) % 4)
        
        val decodedBytes = Base64Utils.decodeUrlSafe(paddedPayload)
        return String(decodedBytes)
    }
    
    /**
     * Extracts the client ID from a JWT token's sub field
     * 
     * @param token The JWT token
     * @return The client ID (sub field) if present, null otherwise
     */
    fun getClientId(token: String): String? {
        return try {
            val payloadJson = decodePayload(token)
            extractStringFromJson(payloadJson, "sub")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extracts a string value from a JSON string
     */
    private fun extractStringFromJson(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        val matchResult = regex.find(json)
        return matchResult?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }
    
    /**
     * Extracts a long value from a JSON string
     */
    private fun extractLongFromJson(json: String, key: String): Long? {
        val pattern = "\"$key\"\\s*:\\s*(\\d+)"
        val regex = Regex(pattern)
        val matchResult = regex.find(json)
        return matchResult?.groupValues?.get(1)?.toLongOrNull()
    }
} 