package com.gopay.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the all-or-nothing rule in [DemoConfig.applyLaunchOverrides]: a launch whose base URL
 * does not survive validation must not leave any part of itself behind.
 *
 * Only the rejection path is exercised here. It returns before touching the SDK, so it needs no
 * Android runtime; the accepting path calls `GopaySDK.initialize` and belongs in an instrumented
 * test. The parsing half lives in [DemoLaunchOverridesTest].
 */
class DemoConfigLaunchOverrideTest {

    @Test
    fun `a bad base URL drops the credentials that arrived with it`() {
        val baseUrlBefore = DemoConfig.developmentBaseUrl
        val credentialsBefore = DemoConfig.developmentCredentials

        val outcome = DemoConfig.applyLaunchOverrides(
            DemoLaunchOverrides(
                baseUrl = "https://",
                clientId = "SDK",
                shareableKey = "sk_live_secret",
                clientSecret = "cs_live_secret",
                goid = "8761908826"
            )
        )

        assertTrue("expected Rejected, got $outcome", outcome is OverrideOutcome.Rejected)
        // The point of the finding: the secrets must not reach the compiled-in gateway.
        assertEquals(credentialsBefore, DemoConfig.developmentCredentials)
        assertEquals(baseUrlBefore, DemoConfig.developmentBaseUrl)
    }

    @Test
    fun `the rejection names the offending key and value`() {
        val outcome = DemoConfig.applyLaunchOverrides(DemoLaunchOverrides(baseUrl = "not-a-url"))

        val reason = (outcome as OverrideOutcome.Rejected).reason
        assertTrue(reason, reason.contains("GOPAY_DEMO_BASE_URL"))
        assertTrue(reason, reason.contains("not-a-url"))
    }

    @Test
    fun `an http gateway is refused because the manifest forbids cleartext`() {
        val result = normalizeDemoBaseUrl("http://gw.example.com/gp-gw/api/4.0/")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("https://"))
        assertTrue(message, message.contains("usesCleartextTraffic"))
    }

    @Test
    fun `an https gateway still normalizes`() {
        val result = normalizeDemoBaseUrl("https://gw.example.com/gp-gw/api/4.0")

        assertFalse(result.isFailure)
        assertEquals("https://gw.example.com/gp-gw/api/4.0/", result.getOrNull())
    }
}
