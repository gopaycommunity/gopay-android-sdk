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
    fun `a base URL maps to a development gateway on that host`() {
        val environment = demoBaseUrlEnvironment("https://gw.example.com/gp-gw/api/4.0")

        assertTrue(environment is Environment.DEVELOPMENT)
        assertEquals("https://gw.example.com/gp-gw/api/4.0/", environment.apiBaseUrl)
    }

    /** Holds whether or not local.properties names a gateway, so it is safe to run either way. */
    @Test
    fun `the badge offers Development exactly when a gateway is configured`() {
        assertTrue(DemoEnvironment.SANDBOX in DemoConfig.availableEnvironments)
        assertTrue(DemoEnvironment.PRODUCTION in DemoConfig.availableEnvironments)
        assertEquals(
            DemoConfig.developmentBaseUrl.isNotBlank(),
            DemoEnvironment.DEVELOPMENT in DemoConfig.availableEnvironments
        )
    }
}
