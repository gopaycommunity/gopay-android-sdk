package cz.gopay.sdk.telemetry

import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * An HTTP client for the operational data that never touches the network: every POST is kept and
 * answered with 204 on the calling thread, so an emitter's event is in [requests] by the time it
 * returns. [install] makes every SDK instance initialized afterwards send here.
 */
internal class TelemetryCapture {

    val requests = mutableListOf<Request>()

    /** The `event` objects posted, parsed, oldest first. */
    val events: List<Map<String, Any?>>
        get() = bodies.map { body ->
            @Suppress("UNCHECKED_CAST")
            (JsonText.parse(body) as Map<String, Any?>)["event"] as Map<String, Any?>
        }

    /** The raw request bodies, oldest first. */
    val bodies: List<String>
        get() = synchronized(requests) {
            requests.map { request -> Buffer().also { request.body!!.writeTo(it) }.readUtf8() }
        }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher(DirectExecutor))
        .addInterceptor { chain ->
            synchronized(requests) { requests.add(chain.request()) }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        .build()

    fun install() {
        Telemetry.httpClientOverride = client
    }

    /** Runs each call where it is enqueued. */
    private object DirectExecutor : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    companion object {
        /** Drops every override and the current instance, so no test leaks into the next. */
        fun reset() {
            Telemetry.current = null
            Telemetry.httpClientOverride = null
        }
    }
}
