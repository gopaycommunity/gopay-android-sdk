package cz.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gateway's `Date` header becomes the card JWE's `iat`, so only a clean IMF-fixdate counts.
 * The table is shared with the iOS `parseHTTPDate(_:)` tests; both SDKs must give the same
 * answer for every row.
 */
class HttpDateTest {

    private val accepted = listOf(
        // The header production sent on 30 Sep 2026.
        "Wed, 30 Sep 2026 17:34:17 GMT" to 1_790_789_657L,
        "Thu, 01 Jan 1970 00:00:00 GMT" to 0L,
        "Thu, 29 Feb 2024 12:00:00 GMT" to 1_709_208_000L,
        "Sun, 29 Feb 2032 12:00:00 GMT" to 1_961_668_800L
    )

    private val rejected = listOf(
        null,
        "",
        "yesterday",
        "1790789657",
        // Obsolete forms RFC 9110 lets a recipient read, refused on both platforms.
        "Wednesday, 30-Sep-26 17:34:17 GMT",
        "Wed Sep 30 17:34:17 2026",
        // Zones.
        "Wed, 30 Sep 2026 17:34:17 +0000",
        "Wed, 30 Sep 2026 17:34:17 UTC",
        "Wed, 30 Sep 2026 17:34:17 CET",
        "Wed, 30 Sep 2026 17:34:17 gmt",
        "Wed, 30 Sep 2026 17:34:17 GMTX",
        "Wed, 30 Sep 2026 17:34:17",
        // Dates and times that do not exist.
        "Wed, 31 Sep 2026 17:34:17 GMT",
        "Sun, 29 Feb 2026 12:00:00 GMT",
        "Wed, 30 Sep 2026 24:00:00 GMT",
        "Wed, 30 Sep 2026 17:34:60 GMT",
        "Wed, 30 Sep 2026 25:34:17 GMT",
        // Names.
        "Wed, 30 Sept 2026 17:34:17 GMT",
        "wed, 30 sep 2026 17:34:17 GMT",
        "WED, 30 SEP 2026 17:34:17 GMT",
        "Wed, 30 Foo 2026 17:34:17 GMT",
        // Single digits.
        "Thu, 1 Oct 2026 17:34:17 GMT",
        "Wed, 1 Oct 2026 17:34:17 GMT",
        "Wed, 30 Sep 2026 7:34:17 GMT",
        // Digits other than ASCII.
        "Wed, ٣٠ Sep 2026 17:34:17 GMT",
        // Spacing and punctuation.
        " Wed, 30 Sep 2026 17:34:17 GMT",
        "Wed, 30 Sep 2026 17:34:17 GMT ",
        "Wed,  30 Sep 2026 17:34:17 GMT",
        "Wed 30 Sep 2026 17:34:17 GMT",
        "Wed, 30 Sep 2026 17:34:17 GMT trailing",
        // Years.
        "Wed, 30 Sep 26 17:34:17 GMT",
        "Wed, 30 Sep 02026 17:34:17 GMT",
        // A day of the week that does not match the date.
        "Thu, 30 Sep 2026 17:34:17 GMT"
    )

    @Test
    fun `an IMF-fixdate reads as Unix seconds`() {
        accepted.forEach { (value, seconds) ->
            assertEquals(value, seconds, HttpDate.parseSeconds(value))
        }
    }

    @Test
    fun `everything else reads as null`() {
        rejected.forEach { value -> assertEquals("'$value'", null, HttpDate.parseSeconds(value)) }
    }
}
