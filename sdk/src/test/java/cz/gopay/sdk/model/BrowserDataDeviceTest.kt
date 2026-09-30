package cz.gopay.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Pins the two parts of `BrowserData.deviceDefault` that carry a convention rather than a plain
 * device reading: the timezone sign and the shape of the fallback User-Agent.
 *
 * `deviceDefault` itself needs an `Activity` and a `WebSettings`, neither of which a plain JVM
 * unit test can construct against the stubbed `android.jar`, so the parts that decide what the
 * issuer sees were pulled out to where a test can reach them.
 */
class BrowserDataDeviceTest {

    @Test
    fun `the timezone is reported in minutes west of UTC`() {
        // The JavaScript Date.getTimezoneOffset() convention the issuer reads the field as:
        // east of UTC is negative.
        assertEquals(-60, jsTimezoneOffsetMinutes(3_600_000))
        assertEquals(-120, jsTimezoneOffsetMinutes(7_200_000))
        assertEquals(0, jsTimezoneOffsetMinutes(0))
        assertEquals(300, jsTimezoneOffsetMinutes(-18_000_000))
    }

    @Test
    fun `daylight saving moves the offset, as the issuer expects`() {
        val prague = TimeZone.getTimeZone("Europe/Prague")
        val winter = GregorianCalendar(prague).apply { set(2026, 0, 15, 12, 0, 0) }.timeInMillis
        val summer = GregorianCalendar(prague).apply { set(2026, 6, 15, 12, 0, 0) }.timeInMillis

        assertEquals("CET", -60, jsTimezoneOffsetMinutes(prague.getOffset(winter)))
        assertEquals("CEST", -120, jsTimezoneOffsetMinutes(prague.getOffset(summer)))
    }

    @Test
    fun `the synthetic user agent looks like a WebView`() {
        val ua = syntheticUserAgent(release = "14", sdkInt = 34, model = "Pixel 8")

        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            ua
        )
    }

    @Test
    fun `an unnamed release falls back to the API level`() {
        // Build.VERSION.RELEASE is empty on some builds and null off-device; an issuer reading
        // "Android null" or "Android ;" would score the charge on a broken string.
        assertTrue(
            syntheticUserAgent(release = "", sdkInt = 34, model = "Pixel 8")
                .contains("Android 34; Pixel 8")
        )
        assertTrue(
            syntheticUserAgent(release = null, sdkInt = 34, model = "Pixel 8")
                .contains("Android 34; Pixel 8")
        )
    }

    @Test
    fun `an unnamed model falls back to the platform name`() {
        assertTrue(
            syntheticUserAgent(release = "14", sdkInt = 34, model = "")
                .contains("Android 14; Android)")
        )
        assertTrue(
            syntheticUserAgent(release = "14", sdkInt = 34, model = null)
                .contains("Android 14; Android)")
        )
    }
}
