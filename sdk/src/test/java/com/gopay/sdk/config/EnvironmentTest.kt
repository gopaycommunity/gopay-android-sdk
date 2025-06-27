package com.gopay.sdk.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Environment enum.
 */
class EnvironmentTest {

    @Test
    fun testEnvironmentValues() {
        // Verify that all expected environments are defined
        val environments = Environment.values()
        assertEquals(4, environments.size)
        assertTrue(environments.any { it.name == "PRODUCTION" })
        assertTrue(environments.any { it.name == "SANDBOX" })
        assertTrue(environments.any { it.name == "DEVELOPMENT" })
        assertTrue(environments.any { it.name == "STAGING" })
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
    fun testDevelopmentEnvironment() {
        // Verify development environment properties
        assertEquals("https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/", Environment.DEVELOPMENT.apiBaseUrl)
    }

    @Test
    fun testStagingEnvironment() {
        // Verify staging environment properties
        assertEquals("https://api.staging.gopay.com/v1/", Environment.STAGING.apiBaseUrl)
    }

    @Test
    fun testEndpointComposition() {
        // Verify that we can compose full endpoints correctly
        val paymentEndpoint = Environment.PRODUCTION.apiBaseUrl + GopayConfig.PAYMENT_ENDPOINT
        assertEquals("https://api.gopay.com/v1/payments", paymentEndpoint)
    }
} 