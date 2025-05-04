package com.gopay.sdk.util

/**
 * Utility class for URL handling operations
 */
object UrlUtils {
    
    /**
     * Safely composes a URL by combining a base URL and an endpoint.
     * Ensures that there is exactly one "/" between the base URL and endpoint.
     * 
     * @param baseUrl The base URL (e.g., "https://api.gopay.com/v1/")
     * @param endpoint The endpoint to append (e.g., "payments")
     * @return The properly formatted complete URL
     */
    fun composeUrl(baseUrl: String, endpoint: String): String {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val normalizedEndpoint = if (endpoint.startsWith("/")) endpoint.substring(1) else endpoint
        
        return normalizedBase + normalizedEndpoint
    }
} 