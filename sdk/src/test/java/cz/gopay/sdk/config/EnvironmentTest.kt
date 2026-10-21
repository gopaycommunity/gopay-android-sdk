package cz.gopay.sdk.config

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        assertEquals("https://gate.gopay.com/gp-gw/api/4.0/", Environment.PRODUCTION.apiBaseUrl)
    }

    @Test
    fun testSandboxEnvironment() {
        // Verify sandbox environment properties
        assertEquals("https://gw.sandbox.gopay.com/gp-gw/api/4.0/", Environment.SANDBOX.apiBaseUrl)
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

    @Test
    fun testDevelopmentEnvironmentAcceptsUppercaseScheme() {
        // URL schemes are case-insensitive, and iOS accepts this form — the same demo base URL
        // must not work on one platform and fail on the other.
        val developmentEnv = Environment.DEVELOPMENT.create("HTTPS://localhost:8080/")
        assertEquals("HTTPS://localhost:8080/", developmentEnv.apiBaseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithEmptyUrl() {
        // An empty URL is the shared CONFIG_006, the code iOS reports for a base URL it cannot
        // use, not a bare IllegalArgumentException.
        val e = assertThrows(GopaySDKException::class.java) { Environment.DEVELOPMENT.create("") }
        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.errorCode)
    }

    @Test
    fun testDevelopmentEnvironmentWithInvalidUrl() {
        val e = assertThrows(GopaySDKException::class.java) {
            Environment.DEVELOPMENT.create("invalid-url")
        }
        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.errorCode)
        assertTrue(e.message!!.contains("http://"))
    }

    @Test
    fun testEndpointComposition() {
        // Verify that we can compose full endpoints correctly
        val paymentEndpoint = Environment.PRODUCTION.apiBaseUrl + "payments"
        assertEquals("https://gate.gopay.com/gp-gw/api/4.0/payments", paymentEndpoint)
    }
} 