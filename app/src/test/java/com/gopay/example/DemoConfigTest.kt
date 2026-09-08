package com.gopay.example

import cz.gopay.sdk.config.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoConfigTest {

    @Test
    fun `a blank base URL maps to the sandbox gateway`() {
        assertEquals(Environment.SANDBOX, demoBaseUrlEnvironment(""))
        assertEquals(Environment.SANDBOX, demoBaseUrlEnvironment("   "))
    }

    @Test
    fun `the sandbox gateway URL maps to the sandbox environment`() {
        assertEquals(Environment.SANDBOX, demoBaseUrlEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0/"))
        assertEquals(Environment.SANDBOX, demoBaseUrlEnvironment("https://gw.sandbox.gopay.com/gp-gw/api/4.0"))
        assertEquals(Environment.SANDBOX, demoBaseUrlEnvironment("  https://gw.sandbox.gopay.com/gp-gw/api/4.0/  "))
    }

    @Test
    fun `the production gateway URL maps to the production environment`() {
        assertEquals(Environment.PRODUCTION, demoBaseUrlEnvironment("https://gate.gopay.com/gp-gw/api/4.0/"))
        assertEquals(Environment.PRODUCTION, demoBaseUrlEnvironment("https://gate.gopay.com/gp-gw/api/4.0"))
    }

    @Test
    fun `a custom base URL maps to a development gateway on that host`() {
        val environment = demoBaseUrlEnvironment("https://gw.example.com/gp-gw/api/4.0")

        assertTrue(environment is Environment.DEVELOPMENT)
        assertEquals("https://gw.example.com/gp-gw/api/4.0/", environment.apiBaseUrl)
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
    fun `the badge offers Development exactly when a custom gateway is configured`() {
        assertTrue(DemoEnvironment.SANDBOX in DemoConfig.availableEnvironments)
        assertTrue(DemoEnvironment.PRODUCTION in DemoConfig.availableEnvironments)
        assertEquals(
            demoInitialEnvironment(DemoConfig.developmentBaseUrl) == DemoEnvironment.DEVELOPMENT,
            DemoEnvironment.DEVELOPMENT in DemoConfig.availableEnvironments
        )
        assertEquals(demoInitialEnvironment(DemoConfig.developmentBaseUrl), DemoConfig.environment)
    }
}
