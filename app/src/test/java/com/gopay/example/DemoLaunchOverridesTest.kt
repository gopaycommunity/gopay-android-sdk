package com.gopay.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the parsing half of the launch-override path. Applying them ([DemoConfig.applyLaunchOverrides])
 * re-initializes the SDK and so belongs in an instrumented test; what's worth pinning down here is
 * that a plain launch stays a no-op and that shell-mangled extras can't reach the SDK as garbage.
 */
class DemoLaunchOverridesTest {

    private fun overridesOf(vararg extras: Pair<String, String?>): DemoLaunchOverrides {
        val map = extras.toMap()
        return DemoLaunchOverrides.from { key -> map[key] }
    }

    /**
     * The literal key strings are a cross-platform contract: the iOS example reads the same names
     * as launch arguments (`DemoOverrides.swift`), so one documented set of keys drives both demo
     * apps. Renaming a constant here must not silently drift from iOS.
     */
    @Test
    fun `extra names match the iOS GOPAY_DEMO launch arguments`() {
        assertEquals("GOPAY_DEMO_BASE_URL", DemoLaunchOverrides.EXTRA_BASE_URL)
        assertEquals("GOPAY_DEMO_CLIENT_ID", DemoLaunchOverrides.EXTRA_CLIENT_ID)
        assertEquals("GOPAY_DEMO_SHAREABLE_KEY", DemoLaunchOverrides.EXTRA_SHAREABLE_KEY)
        assertEquals("GOPAY_DEMO_CLIENT_SECRET", DemoLaunchOverrides.EXTRA_CLIENT_SECRET)
        assertEquals("GOPAY_DEMO_GOID", DemoLaunchOverrides.EXTRA_GOID)
    }

    @Test
    fun `a launch with no extras produces no overrides`() {
        val overrides = overridesOf()

        assertTrue(overrides.isEmpty)
        assertNull(overrides.baseUrl)
    }

    @Test
    fun `every extra is read`() {
        val overrides = overridesOf(
            DemoLaunchOverrides.EXTRA_BASE_URL to "https://gw.example.com/gp-gw/api/4.0/",
            DemoLaunchOverrides.EXTRA_CLIENT_ID to "SDK",
            DemoLaunchOverrides.EXTRA_SHAREABLE_KEY to "sk_test",
            DemoLaunchOverrides.EXTRA_CLIENT_SECRET to "cs_test",
            DemoLaunchOverrides.EXTRA_GOID to "8761908826"
        )

        assertEquals("https://gw.example.com/gp-gw/api/4.0/", overrides.baseUrl)
        assertEquals("SDK", overrides.clientId)
        assertEquals("sk_test", overrides.shareableKey)
        assertEquals("cs_test", overrides.clientSecret)
        assertEquals("8761908826", overrides.goid)
        assertTrue(!overrides.isEmpty)
    }

    @Test
    fun `extras are independent - naming one leaves the rest absent`() {
        val overrides = overridesOf(DemoLaunchOverrides.EXTRA_BASE_URL to "https://gw.example.com/")

        assertEquals("https://gw.example.com/", overrides.baseUrl)
        assertNull(overrides.clientId)
        assertNull(overrides.shareableKey)
        assertNull(overrides.clientSecret)
        assertNull(overrides.goid)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        val overrides = overridesOf(DemoLaunchOverrides.EXTRA_BASE_URL to "  https://gw.example.com/  ")

        assertEquals("https://gw.example.com/", overrides.baseUrl)
    }

    @Test
    fun `a blank extra counts as absent rather than as an empty value`() {
        val overrides = overridesOf(
            DemoLaunchOverrides.EXTRA_BASE_URL to "",
            DemoLaunchOverrides.EXTRA_CLIENT_ID to "   "
        )

        assertTrue(overrides.isEmpty)
        assertNull(overrides.baseUrl)
        assertNull(overrides.clientId)
    }

    // --- normalizeDemoBaseUrl: the gate both override channels go through -------------------

    @Test
    fun `a well-formed url is accepted unchanged`() {
        val result = normalizeDemoBaseUrl("https://gw.example.com/gp-gw/api/4.0/")

        assertEquals("https://gw.example.com/gp-gw/api/4.0/", result.getOrNull())
    }

    @Test
    fun `a missing trailing slash is added`() {
        val result = normalizeDemoBaseUrl("https://gw.example.com/gp-gw/api/4.0")

        assertEquals("https://gw.example.com/gp-gw/api/4.0/", result.getOrNull())
    }

    @Test
    fun `a non-http scheme is refused`() {
        assertTrue(normalizeDemoBaseUrl("ftp://gw.example.com/").isFailure)
        assertTrue(normalizeDemoBaseUrl("gw.example.com").isFailure)
        assertTrue(normalizeDemoBaseUrl("").isFailure)
    }

    /**
     * These clear the SDK's own scheme check but have no usable host. Without the extra host
     * check they reach OkHttp inside `GopaySDK.initialize` and bring the app down on launch.
     */
    @Test
    fun `a url with no usable host is refused rather than left to crash OkHttp`() {
        assertTrue("bare scheme", normalizeDemoBaseUrl("https://").isFailure)
        assertTrue("port only", normalizeDemoBaseUrl("https://:8080/").isFailure)
        assertTrue("space in host", normalizeDemoBaseUrl("https://gw example.com/").isFailure)
    }

    @Test
    fun `a refusal carries a reason worth logging`() {
        val reason = normalizeDemoBaseUrl("https://").exceptionOrNull()?.message

        assertTrue("got: $reason", !reason.isNullOrBlank())
    }

    /**
     * Ports, queries and fragments clear `java.net.URI` but not OkHttp — the parser Retrofit
     * actually uses. Before the gate moved to `HttpUrl` these reached `GopaySDK.initialize` and
     * crashed the app on launch.
     */
    @Test
    fun `urls that only OkHttp rejects are refused`() {
        assertTrue("port out of range", normalizeDemoBaseUrl("https://gw.example.com:99999/").isFailure)
        assertTrue("port zero", normalizeDemoBaseUrl("https://gw.example.com:0/").isFailure)
        assertTrue("query", normalizeDemoBaseUrl("https://gw.example.com/api?x=1").isFailure)
        assertTrue("fragment", normalizeDemoBaseUrl("https://gw.example.com/api#frag").isFailure)
    }

    /**
     * The mirror image: `java.net.URI` reports no host for these, but they are ordinary URLs
     * OkHttp handles — and underscored hosts are common for internal test gateways, so refusing
     * them would have been a real obstruction.
     */
    @Test
    fun `hosts that only java-net-URI rejects are accepted`() {
        assertEquals(
            "https://gw_stage.internal/api/",
            normalizeDemoBaseUrl("https://gw_stage.internal/api").getOrNull()
        )
        assertTrue(normalizeDemoBaseUrl("https://gw.ex\u00e4mple.com/api/").isSuccess)
    }

    @Test
    fun `an explicit port is preserved`() {
        assertEquals(
            "https://gw.example.com:8443/gp-gw/api/4.0/",
            normalizeDemoBaseUrl("https://gw.example.com:8443/gp-gw/api/4.0").getOrNull()
        )
    }
}
