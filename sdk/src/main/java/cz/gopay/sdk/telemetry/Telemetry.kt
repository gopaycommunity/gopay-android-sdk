package cz.gopay.sdk.telemetry

import android.content.Context
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.util.SdkLog
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide state of the operational data. Mirrors the iOS SDK's `Telemetry` setup.
 *
 * Always on, like the browser SDK's: there is no switch in [GopayConfig], and `debug` changes
 * only whether each sent event is named in Logcat. Off only where there is nowhere to send, a
 * development environment with a custom gateway URL.
 */
internal object Telemetry {

    /**
     * The instance of the SDK initialized last; null before `initialize` or without an endpoint.
     *
     * Only for what belongs to no SDK instance: the card form, which is composed without one, and
     * [cz.gopay.sdk.exception.ErrorReporter], which every entry point reports through. A payment
     * session reports through the instance that started it, so one payment never shows under two
     * `sdk_instance_id`s when the app initializes again in the middle of it.
     */
    @Volatile
    var current: GwLoggerTelemetry? = null

    /** One per process: what ties the events of every SDK instance in this app run together. */
    val transactionId: String by lazy { UUID.randomUUID().toString() }

    /**
     * The host app's package name, learned from the first `Context` the SDK is handed: the card
     * form, an activity of the SDK's own, or one the integrator passes in. Empty until then,
     * since `initialize` takes none.
     */
    @Volatile
    var origin: String = ""
        private set

    fun noteOrigin(context: Context?) {
        if (origin.isNotEmpty() || context == null) return
        runCatching { context.packageName }.getOrNull()?.let { origin = it }
    }

    /**
     * Charges sent and not yet answered, on any session of any SDK instance in the process: what
     * a card form leaving the screen reports as `charge_in_flight`. The form charges nothing
     * itself, so a charge anywhere in the app is the one it can have cut short.
     */
    val runningCharges = AtomicInteger(0)

    /** The HTTP client events go out through instead of the SDK's own. Tests only. */
    @VisibleForTesting
    @Volatile
    var httpClientOverride: Call.Factory? = null

    /** What every event's request ends in: the response is closed unread. */
    val discardResponse: Callback = object : Callback {
        override fun onFailure(call: Call, e: IOException) = Unit

        override fun onResponse(call: Call, response: Response) = response.close()
    }

    /** The telemetry of an SDK instance initialized with [config], or null when it has nowhere to send. */
    fun create(config: GopayConfig): GwLoggerTelemetry? = try {
        config.environment.telemetryUrl?.let { endpoint ->
            GwLoggerTelemetry(
                endpoint = endpoint,
                clientId = config.clientId,
                client = httpClientOverride ?: NetworkModule.createTelemetryClient()
            )
        }
    } catch (e: Throwable) {
        SdkLog.w("operational data is off for this instance: ${e.javaClass.simpleName}")
        null
    }
}
