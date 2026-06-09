package cz.gopay.sdk.util

/**
 * Utility class for JWT token operations
 */
object JwtUtils {
    
    /**
     * Checks if a JWT token is expired
     *
     * @param token The JWT token to check
     * @return true if the token is expired, false otherwise
     */
    fun isTokenExpired(token: String): Boolean {
        val exp = try {
            extractLongFromJson(decodePayload(token), "exp")
        } catch (e: Exception) {
            // Token can't be decoded — treat as expired (safe default).
            return true
        }
        // No `exp` claim means the token doesn't declare an expiry.
        if (exp == null) return false
        return (System.currentTimeMillis() / 1000) >= exp
    }

    /**
     * Extracts the `exp` claim (Unix seconds) from a JWT payload. Returns `0L` when the claim is
     * absent or the token can't be decoded — callers should treat 0 as "no known expiry".
     *
     * Use this to capture the expiry once at token-issue time and avoid re-parsing the JWT on
     * every authenticated request.
     */
    fun expirationSecondsOrZero(token: String): Long {
        return try {
            extractLongFromJson(decodePayload(token), "exp") ?: 0L
        } catch (e: Exception) {
            0L
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