package com.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {
    
    @Test
    fun testComposeUrl_withBaseUrlEndingWithSlash() {
        // Given a base URL ending with slash and various endpoints
        val baseUrl = "https://api.gopay.com/v1/"
        val endpoints = listOf(
            "payments",
            "/payments",
            "customers",
            "/customers"
        )
        
        // When composing URLs
        val results = endpoints.map { UrlUtils.composeUrl(baseUrl, it) }
        
        // Then all results should have exactly one slash between base and endpoint
        assertEquals("https://api.gopay.com/v1/payments", results[0])
        assertEquals("https://api.gopay.com/v1/payments", results[1])
        assertEquals("https://api.gopay.com/v1/customers", results[2])
        assertEquals("https://api.gopay.com/v1/customers", results[3])
    }
    
    @Test
    fun testComposeUrl_withBaseUrlNotEndingWithSlash() {
        // Given a base URL not ending with slash and various endpoints
        val baseUrl = "https://api.gopay.com/v1"
        val endpoints = listOf(
            "payments",
            "/payments",
            "customers",
            "/customers"
        )
        
        // When composing URLs
        val results = endpoints.map { UrlUtils.composeUrl(baseUrl, it) }
        
        // Then all results should have exactly one slash between base and endpoint
        assertEquals("https://api.gopay.com/v1/payments", results[0])
        assertEquals("https://api.gopay.com/v1/payments", results[1])
        assertEquals("https://api.gopay.com/v1/customers", results[2])
        assertEquals("https://api.gopay.com/v1/customers", results[3])
    }
    
    @Test
    fun testComposeUrl_withEmptyEndpoint() {
        // Given a base URL and empty endpoint
        val baseUrl = "https://api.gopay.com/v1/"
        val endpoint = ""
        
        // When composing URL
        val result = UrlUtils.composeUrl(baseUrl, endpoint)
        
        // Then result should be the base URL
        assertEquals("https://api.gopay.com/v1/", result)
    }
    
    @Test
    fun testComposeUrl_withComplexEndpoints() {
        // Given a base URL and complex endpoints with query parameters
        val baseUrl = "https://api.gopay.com/v1"
        val endpoints = listOf(
            "payments?id=123",
            "customers/profile?name=test",
            "/transactions/list?from=2023-01-01&to=2023-12-31"
        )
        
        // When composing URLs
        val results = endpoints.map { UrlUtils.composeUrl(baseUrl, it) }
        
        // Then all results should be correctly formatted
        assertEquals("https://api.gopay.com/v1/payments?id=123", results[0])
        assertEquals("https://api.gopay.com/v1/customers/profile?name=test", results[1])
        assertEquals("https://api.gopay.com/v1/transactions/list?from=2023-01-01&to=2023-12-31", results[2])
    }
} 