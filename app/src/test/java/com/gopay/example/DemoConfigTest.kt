package com.gopay.example

import cz.gopay.sdk.config.GopayEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoConfigTest {

    @Test
    fun `a blank base URL maps to the sandbox gateway`() {
        assertEquals(GopayEnvironment.SANDBOX, demoBaseUrlEnvironment(""))
        assertEquals(GopayEnvironment.SANDBOX, demoBaseUrlEnvironment("   "))
    }

    @Test
    fun `the sandbox gateway URL maps to the sandbox environment`() {
        assertEquals(GopayEnvironment.SANDBOX, demoBaseUrlEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0/"))
        assertEquals(GopayEnvironment.SANDBOX, demoBaseUrlEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0"))
        assertEquals(GopayEnvironment.SANDBOX, demoBaseUrlEnvironment("  https://gw.sandbox.gopay.com/gp-gw/api/4.0/  "))
    }

    @Test
    fun `the production gateway URL maps to the production environment`() {
        assertEquals(GopayEnvironment.PRODUCTION, demoBaseUrlEnvironment("https://gate.gopay.com/gp-gw/api/4.0/"))
        assertEquals(GopayEnvironment.PRODUCTION, demoBaseUrlEnvironment("https://gate.gopay.com/gp-gw/api/4.0"))
    }

    @Test
    fun `a custom base URL maps to a development gateway on that host`() {
        val environment = demoBaseUrlEnvironment("https://gw.example.com/gp-gw/api/4.0")

        assertTrue(environment is GopayEnvironment.DEVELOPMENT)
        assertEquals("https://gw.example.com/gp-gw/api/4.0/", environment.baseUrl)
    }

    @Test
    fun `the badge starts on the environment the base URL names`() {
        assertEquals(DemoEnvironment.SANDBOX, demoInitialEnvironment(""))
        assertEquals(DemoEnvironment.SANDBOX, demoInitialEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0/"))
        assertEquals(DemoEnvironment.PRODUCTION, demoInitialEnvironment("https://gate.gopay.com/gp-gw/api/4.0/"))
        assertEquals(DemoEnvironment.DEVELOPMENT, demoInitialEnvironment("https://gw.example.com/gp-gw/api/4.0"))
    }

    /** Holds whatever local.properties says, so it is safe to run either way. */
    @Test
    fun `the badge reads the environment the configured gateway names`() {
        assertEquals(demoInitialEnvironment(DemoConfig.developmentBaseUrl), DemoConfig.environment)
    }
}
