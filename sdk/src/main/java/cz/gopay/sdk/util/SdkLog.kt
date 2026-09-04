package cz.gopay.sdk.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.GopaySDK

/**
 * Logging of the SDK towards the host application.
 *
 * Warnings are things the integrator should know about but that never stop the SDK from working,
 * such as a theme key that was dropped because its value could not be read. They go to Logcat
 * under [TAG] at warn level, and only while the host has debug logging on, matching the rest of
 * the SDK and the iOS side.
 */
internal object SdkLog {
    const val TAG = "GopaySDK"

    /**
     * Where warnings go. Replaced in unit tests, which run without the Android runtime, to
     * collect the messages instead of writing them to Logcat.
     */
    @VisibleForTesting
    internal var warnSink: (String) -> Unit = { message ->
        // Plain JVM unit tests have no Android runtime; a log line is never worth failing over.
        runCatching { Log.w(TAG, message) }
    }

    fun w(message: String) {
        // Before initialize(), and in plain JVM unit tests, there is no config to consult; the
        // sink is replaced in tests, so a silent no-op here would cost the suite its assertions.
        val debug = runCatching { GopaySDK.getInstance().isDebugEnabled() }.getOrDefault(true)
        if (debug) warnSink(message)
    }
}
