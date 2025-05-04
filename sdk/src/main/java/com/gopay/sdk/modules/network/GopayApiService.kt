package com.gopay.sdk.modules.network

import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit interface for Gopay API calls
 * This will be expanded later with additional methods
 */
interface GopayApiService {
    /**
     * Placeholder for authentication endpoint
     * Will be implemented properly later
     * 
     * Note: Endpoint paths should not start with a slash as they are combined with the base URL
     * that already ends with a slash. Use the UrlUtils.composeUrl utility for manual URL building.
     */
    @POST("oauth2/token")
    suspend fun authenticate(
        @Header("Authorization") authorization: String
    ): Any // This will be replaced with proper response model
    
    // Additional API methods will be added here later
}