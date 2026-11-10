package cz.gopay.sdk.config


import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the GopayEnvironment sealed class.
 */
class GopayEnvironmentTest {

    @Test
    fun testEnvironmentTypes() {
        // Verify that all expected environments are defined and have correct types
        assertTrue(GopayEnvironment.PRODUCTION is GopayEnvironment)
        assertTrue(GopayEnvironment.SANDBOX is GopayEnvironment)
        
        // Verify specific object types
        assertTrue(GopayEnvironment.PRODUCTION is GopayEnvironment.PRODUCTION)
        assertTrue(GopayEnvironment.SANDBOX is GopayEnvironment.SANDBOX)
    }

    @Test
    fun testProductionEnvironment() {
        // Verify production environment properties
        assertEquals("https://gate.gopay.com/gp-gw/api/4.0/", GopayEnvironment.PRODUCTION.baseUrl)
    }

    @Test
    fun testSandboxEnvironment() {
        // Verify sandbox environment properties
        assertEquals("https://gw.sandbox.gopay.com/gp-gw/api/4.0/", GopayEnvironment.SANDBOX.baseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithCustomUrl() {
        // Verify development environment with custom URL
        val customUrl = "https://localhost:8080"
        val developmentEnv = GopayEnvironment.DEVELOPMENT.create(customUrl)
        assertEquals("https://localhost:8080/", developmentEnv.baseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithTrailingSlash() {
        // Verify development environment handles trailing slash correctly
        val customUrl = "https://localhost:8080/"
        val developmentEnv = GopayEnvironment.DEVELOPMENT.create(customUrl)
        assertEquals("https://localhost:8080/", developmentEnv.baseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentAcceptsUppercaseScheme() {
        // URL schemes are case-insensitive, and iOS accepts this form — the same demo base URL
        // must not work on one platform and fail on the other.
        val developmentEnv = GopayEnvironment.DEVELOPMENT.create("HTTPS://localhost:8080/")
        assertEquals("HTTPS://localhost:8080/", developmentEnv.baseUrl)
    }

    @Test
    fun testDevelopmentEnvironmentWithEmptyUrl() {
        // An empty URL is the shared CONFIG_006, the code iOS reports for a base URL it cannot
        // use, not a bare IllegalArgumentException.
        val e = assertThrows(GopaySDKException::class.java) { GopayEnvironment.DEVELOPMENT.create("") }
        assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
    }

    @Test
    fun testDevelopmentEnvironmentWithInvalidUrl() {
        val reported = mutableListOf<GopaySDKException>()
        ErrorReporter.setErrorCallback { reported.add(it) }
        try {
            val e = assertThrows(GopaySDKException::class.java) {
                GopayEnvironment.DEVELOPMENT.create("invalid-url")
            }
            assertEquals(GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
            assertTrue(e.message!!.contains("http://"))
            assertTrue("a constructor is not a call of the SDK: not reported", reported.isEmpty())
        } finally {
            ErrorReporter.clear()
        }
    }

    @Test
    fun testEndpointComposition() {
        // Verify that we can compose full endpoints correctly
        val paymentEndpoint = GopayEnvironment.PRODUCTION.baseUrl + "payments"
        assertEquals("https://gate.gopay.com/gp-gw/api/4.0/payments", paymentEndpoint)
    }

    @Test
    fun `a development URL that carries credentials is CONFIG_006, as on iOS`() {
        for (url in listOf(
            "https://user:secret@gw.example.com/api/",
            "https://gate.gopay.com@evil.example.com/",
            "https://:secret@gw.example.com"
        )) {
            val e = assertThrows(url, GopaySDKException::class.java) { GopayEnvironment.DEVELOPMENT(url) }
            assertEquals(url, GopayErrorCodes.CONFIG_INVALID_BASE_URL, e.code)
        }
    }

    @Test
    fun `an at sign outside the authority is not credentials`() {
        val env = GopayEnvironment.DEVELOPMENT("https://gw.example.com/api/v@1")

        assertEquals("https://gw.example.com/api/v@1/", env.baseUrl)
    }

    @Test
    fun `an environment prints its name, and a development one its address`() {
        assertEquals("SANDBOX", GopayEnvironment.SANDBOX.toString())
        assertEquals("PRODUCTION", GopayEnvironment.PRODUCTION.toString())
        assertEquals(
            "DEVELOPMENT(https://localhost:8443/api/)",
            GopayEnvironment.DEVELOPMENT("https://localhost:8443/api").toString()
        )
        // What a host logs: the config names its environment rather than an object's hash.
        assertTrue(GopayConfig(GopayEnvironment.SANDBOX).toString().contains("environment=SANDBOX,"))
    }
}
