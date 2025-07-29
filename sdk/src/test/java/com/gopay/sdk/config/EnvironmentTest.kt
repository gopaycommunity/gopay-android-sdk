package com.gopay.sdk.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Environment sealed class.
 */
class EnvironmentTest {

    @Test
    fun testEnvironmentTypes() {
        // Verify that all expected environments are defined and have correct types
        assertTrue(Environment.PRODUCTION is Environment)
        assertTrue(Environment.SANDBOX is Environment)
        
        // Verify specific object types
        assertTrue(Environment.PRODUCTION is Environment.PRODUCTION)
        assertTrue(Environment.SANDBOX is Environment.SANDBOX)
    }

    @Test
    fun testProductionEnvironment() {
        // Verify production environment properties
        assertEquals("https://api.gopay.com/v1/", Environment.PRODUCTION.apiBaseUrl)
    }

    @Test
    fun testSandboxEnvironment() {
        // Verify sandbox environment properties
        assertEquals("https://api.sandbox.gopay.com/v1/", Environment.SANDBOX.apiBaseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithCustomUrl() {
        // Verify development environment with custom URL
        val customUrl = "https://localhost:8080"
        val developmentEnv = Environment.DEVELOPMENT.create(customUrl)
        assertEquals("https://localhost:8080/", developmentEnv.apiBaseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithTrailingSlash() {
        // Verify development environment handles trailing slash correctly
        val customUrl = "https://localhost:8080/"
        val developmentEnv = Environment.DEVELOPMENT.create(customUrl)
        assertEquals("https://localhost:8080/", developmentEnv.apiBaseUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDevelopmentEnvironmentWithEmptyUrl() {
        // Verify development environment rejects empty URL
        Environment.DEVELOPMENT.create("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDevelopmentEnvironmentWithInvalidUrl() {
        // Verify development environment rejects invalid URL
        Environment.DEVELOPMENT.create("invalid-url")
    }

    @Test
    fun testEndpointComposition() {
        // Verify that we can compose full endpoints correctly
        val paymentEndpoint = Environment.PRODUCTION.apiBaseUrl + "payments"
        assertEquals("https://api.gopay.com/v1/payments", paymentEndpoint)
    }
} 