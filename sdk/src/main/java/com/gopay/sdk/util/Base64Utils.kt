package com.gopay.sdk.util

/**
 * Base64 utility that works in both Android runtime and unit test environments
 */
object Base64Utils {
    
    /**
     * Decodes a Base64 URL-safe encoded string
     * 
     * @param input The Base64 URL-safe encoded string
     * @return The decoded byte array
     */
    fun decodeUrlSafe(input: String): ByteArray {
        return try {
            // Try Android's Base64 first (available in Android runtime)
            android.util.Base64.decode(input, android.util.Base64.URL_SAFE)
        } catch (e: Exception) {
            // Fallback to Java's Base64 (available in unit tests)
            java.util.Base64.getUrlDecoder().decode(input)
        }
    }
} 