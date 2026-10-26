package cz.gopay.sdk.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.GopaySDK

/**
 * Logging of the SDK towards the host application.
 *
 * Warnings are things the integrator should know about but that never stop the SDK from working:
 * a value the gateway left out and the SDK had to read as absent, or a device fact the SDK had to
 * synthesize because the platform would not give it. They go to Logcat under [TAG] at warn level,
 * and only while the host has debug logging on, the same condition the error path uses, so a
 * release build stays quiet. The iOS SDK reports the same things the same way.
 *
 * The card form calls it when the host's theme and the system disagree about dark mode. The other
 * callers live on the 3DS work that builds on top of this branch, the browser data and the charge
 * response adapter, and their tests use the seam below.
 */
internal object SdkLog {
    const val TAG = "GopaySDK"

    /**
     * Where warnings go. Replaced in unit tests, which run without the Android runtime, to
     * collect the messages instead of writing them to Logcat. The tests that do so live with the
     * callers, on the branch that stands on this one.
     */
    @VisibleForTesting
    internal var warnSink: (String) -> Unit = { message ->
        // Plain JVM unit tests have no Android runtime; a log line is never worth failing over.
        runCatching { Log.w(TAG, message) }
    }

    /** Where debug lines go; replaced in unit tests like [warnSink]. */
    @VisibleForTesting
    internal var debugSink: (String) -> Unit = { message ->
        runCatching { Log.d(TAG, message) }
    }

    fun w(message: String) {
        if (debugEnabled()) warnSink(message)
    }

    /** A debug line, under the same condition as [w]: the operational data names each event here. */
    fun d(message: String) {
        if (debugEnabled()) debugSink(message)
    }

    // Before initialize(), and in plain JVM unit tests, there is no config to consult; the sinks
    // are replaced in tests, so a silent no-op there would cost the suite its assertions.
    private fun debugEnabled(): Boolean =
        runCatching { GopaySDK.getInstance().isDebugEnabled() }.getOrDefault(true)
}
