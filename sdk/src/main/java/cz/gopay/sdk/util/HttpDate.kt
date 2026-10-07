package cz.gopay.sdk.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Reads the HTTP `Date` header (RFC 9110 §5.6.7) the gateway sends with its responses.
 *
 * Only the preferred IMF-fixdate form is accepted, e.g. `Wed, 30 Sep 2026 17:34:17 GMT`. The
 * obsolete RFC 850 and asctime forms, any zone other than `GMT`, an impossible date or time, and
 * a day of the week that does not match the date all read as unusable, so the caller falls back.
 * OkHttp's own `Headers.getDate` is not used because it accepts those obsolete forms too.
 * `java.time` is not available on minSdk 24 without desugaring. Mirrors the iOS
 * `parseHTTPDate(_:)`, which accepts and rejects the same inputs.
 */
internal object HttpDate {

    private val IMF_FIXDATE =
        Regex("""[A-Z][a-z]{2}, [0-9]{2} [A-Z][a-z]{2} [0-9]{4} [0-9]{2}:[0-9]{2}:[0-9]{2} GMT""")

    private val DAYS_OF_WEEK = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** Seconds since the Unix epoch, or null when [value] is absent or not an IMF-fixdate. */
    fun parseSeconds(value: String?): Long? {
        if (value == null || !IMF_FIXDATE.matches(value)) return null
        val utc = TimeZone.getTimeZone("UTC")
        // SimpleDateFormat is not thread-safe; one per call costs nothing next to the request.
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = utc
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = format.parse(value, position) ?: return null
        if (position.index != value.length) return null
        // The formatter does not hold the day name against the date, so check it here.
        val dayOfWeek = Calendar.getInstance(utc, Locale.US)
            .apply { time = date }
            .get(Calendar.DAY_OF_WEEK)
        if (value.substring(0, 3) != DAYS_OF_WEEK[dayOfWeek - 1]) return null
        return date.time / 1000L
    }
}
