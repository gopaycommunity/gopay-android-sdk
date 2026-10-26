package cz.gopay.sdk.telemetry

import cz.gopay.sdk.BuildConfig
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.util.SdkLog
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Operational data of one SDK instance, sent to GoPay's event logging service (gw-logger) the
 * way the GoPay browser SDK sends it, so one query reads both. Mirrors the iOS SDK's `Telemetry`.
 *
 * Fire and forget: one event per POST, nothing awaits it, nothing retries it (the ingest answers
 * 204 whether or not it kept the event, so a retry could not tell a transient failure from a
 * refused payload), the response is not read, and every failure, building the event included,
 * is swallowed. Nothing here can delay or fail a payment.
 *
 * Every event carries the same envelope ([base]); `seq` numbers the events this instance actually
 * handed to the HTTP client, 1, 2, 3, so a gap in it is an event that was sent and never stored.
 * An event refused by its budget, by a missing attribution key or by a failure to build takes
 * no number. The event is built and redacted on the caller's thread outside the lock; the lock
 * covers only the budget, the number and the hand-over to the HTTP client.
 *
 * With `GopayConfig.debug` on, each sent event is named in one Logcat line, without its content.
 */
internal class GwLoggerTelemetry(
    private val endpoint: String,
    private val clientId: String?,
    private val client: Call.Factory,
    private val nanoTime: () -> Long = System::nanoTime,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    /** The payment method an event is about, and the prefix of its `function_name`. */
    enum class Method(val prefix: String) {
        CARD("PAYMENT_CARD"),
        GOOGLE_PAY("GPAY")
    }

    /** gw-ui's outcome of a wallet step, in `params.status`. */
    enum class StepStatus(val value: String) {
        START("start"),
        SUCCESS("success"),
        FAILURE("failure"),
        INFO("info")
    }

    /**
     * Split, as in the browser SDK: charge-state polling must not spend the budget the leave and
     * the errors need, and a shopper opening and closing the wallet sheet must not silence the
     * funnel markers.
     *
     * Spent per payment session rather than per instance: a browser SDK lives as long as its page,
     * an app's lives as long as its process, which is days and many payments. Every session start
     * gives all four back (see [navigate]).
     */
    private enum class Budget(val limit: Int) {
        API_CALL(200),
        LIFECYCLE(75),
        STEP(150),
        ERROR(50)
    }

    /** One per `initialize`: what ties an init to the payments made through that instance. */
    val instanceId: String = newId()

    /** When the instance was created, which `ready` is timed from. */
    private val createdAt: Long = nanoTime()

    /** Guards everything below. */
    private val lock = Any()

    /** The last `ready` per method, which `submit` and `unmount` are timed from. */
    private val readyAt = HashMap<Method, Long>()

    /**
     * Payment ids of the sessions being started or open, latest last. An event that does not
     * name its payment is attributed to the latest, as the browser SDK attributes everything to
     * the payment attached last; a payment-scoped request names its own.
     */
    private val attached = ArrayList<String>()

    private var lastSeq = 0L
    private val spent = IntArray(Budget.values().size)

    /** The monotonic clock the durations are measured on, in nanoseconds. */
    fun now(): Long = nanoTime()

    /** Whole milliseconds since [start], or null when there is nothing to time from. */
    fun msSince(start: Long?): Long? =
        start?.let { ((nanoTime() - it) / 1_000_000.0).roundToLong() }

    /** A session for [paymentId] is being started: events from here on belong to it. */
    fun attach(paymentId: String) {
        synchronized(lock) { attached.add(paymentId) }
    }

    /** The session for [paymentId] failed to start or was closed. */
    fun detach(paymentId: String) {
        synchronized(lock) { attached.remove(paymentId) }
    }

    private fun latestPaymentId(): String? = synchronized(lock) { attached.lastOrNull() }

    /** `navigation` / `init`: the SDK was initialized. */
    fun init() = navigation("init", paymentId = null, flow = null, target = null)

    /**
     * `navigation` / `navigate`, flow `attach`: a payment session started. Every budget starts
     * over with it, so the payments before cannot have spent this one's.
     */
    fun navigate(paymentId: String) {
        synchronized(lock) { spent.fill(0) }
        navigation("navigate", paymentId, flow = "attach", target = null)
    }

    /** `navigation` / `leave`: a payment session was closed. */
    fun leave(paymentId: String) = navigation("leave", paymentId, flow = null, target = null)

    /**
     * `navigation` / `leave`, flow `3ds-redirect`: the 3DS challenge is being opened, with its
     * origin and path as the target and nothing of its query.
     */
    fun redirect(paymentId: String, url: String) =
        navigation("leave", paymentId, flow = "3ds-redirect", target = Redaction.safeTarget(url))

    private fun navigation(type: String, paymentId: String?, flow: String?, target: String?) {
        post(Budget.LIFECYCLE, paymentId) {
            put("event_type", "navigation")
            put("navigation_type", type)
            put("target", target)
            putIfPresent("flow", flow)
        }
    }

    /** `<METHOD>.ready`: what the integrator shows is usable, timed from `initialize`. */
    fun ready(method: Method, flow: String?) {
        synchronized(lock) { readyAt[method] = nanoTime() }
        post(Budget.LIFECYCLE, paymentId = null) {
            put("event_type", "js_event")
            put("function_name", "${method.prefix}.ready")
            put("duration", msSince(createdAt))
            putIfPresent("flow", flow)
        }
    }

    /**
     * `interaction` / `submit`: the user submitted. Nothing about what they entered. Timed from
     * the method's ready, which only the card form has; a wallet's submit has no duration.
     * [paymentId] when the submit belongs to a known payment, otherwise the latest attached one.
     */
    fun submit(elementId: String, method: Method, paymentMethod: String, flow: String?, paymentId: String? = null) {
        val since = synchronized(lock) { readyAt[method] }
        post(Budget.LIFECYCLE, paymentId) {
            put("event_type", "interaction")
            put("interaction_type", "submit")
            put("element_id", elementId)
            put("payment_method", paymentMethod)
            putIfPresent("flow", flow)
            put("duration", msSince(since))
        }
    }

    /**
     * `<METHOD>.unmount`: what was ready left the screen, timed from that ready. [chargeInFlight]
     * is whether a charge was running anywhere in the app when it left, on any session; iOS reads
     * it the same way.
     */
    fun unmount(method: Method, chargeInFlight: Boolean) {
        // The mount that ended takes its ready with it.
        val since = synchronized(lock) { readyAt.remove(method) }
        post(Budget.LIFECYCLE, paymentId = null) {
            put("event_type", "js_event")
            put("function_name", "${method.prefix}.unmount")
            put("duration", msSince(since))
            put("return_value", if (chargeInFlight) "charge-aborted" else "idle")
            putIfPresent("params", Redaction.describeParams(mapOf("charge_in_flight" to chargeInFlight)))
        }
    }

    /**
     * `<METHOD>.<step>` of a wallet payment, its outcome in `params.status` and the detail, or
     * the status itself, in `return_value`.
     */
    fun walletStep(
        method: Method,
        step: String,
        status: StepStatus,
        paymentId: String?,
        detail: String? = null,
        startedAt: Long? = null
    ) {
        post(Budget.STEP, paymentId) {
            put("event_type", "js_event")
            put("function_name", "${method.prefix}.$step")
            put("duration", msSince(startedAt))
            put("return_value", Redaction.safeErrorMessage(detail ?: status.value))
            putIfPresent("params", Redaction.describeParams(mapOf("status" to status.value)))
        }
    }

    /**
     * `<METHOD>.readyToPay`: the answer of an availability check, whichever way it went, so the
     * data holds a rate rather than only the refusals. Every check is reported, as in the browser
     * SDK; iOS skips a repeated answer only for its synchronous `canUseApplePay`, which SwiftUI
     * calls from view bodies. [checkSucceeded] false is a check that failed rather than a device
     * that cannot pay.
     */
    fun walletAvailability(method: Method, caller: String, checkSucceeded: Boolean, ready: Boolean, startedAt: Long?) {
        val available = checkSucceeded && ready
        post(Budget.STEP, paymentId = null) {
            put("event_type", "js_event")
            put("function_name", "${method.prefix}.readyToPay")
            put("duration", msSince(startedAt))
            put(
                "return_value",
                when {
                    available -> "available"
                    checkSucceeded -> "unsupported-device"
                    else -> "readiness-check-failed"
                }
            )
            putIfPresent(
                "params",
                Redaction.describeParams(mapOf("available" to available, "caller" to caller))
            )
        }
    }

    /** The steps of one Google Pay payment of [paymentId]; see [GooglePayFunnel]. */
    fun googlePay(paymentId: String) = GooglePayFunnel(paymentId)

    /**
     * `GPAY.<step>` for one sheet: `begin` when the sheet is asked for (failure when it cannot
     * come up), `cancel` when the user closes it, the `googlepay-button` submit and then
     * `paymentAuthorized` when the token arrives (failure when it does not, or does not read),
     * all timed from the sheet's opening, and `checkout` when the charge is sent and when it ends,
     * timed from its sending, as on iOS. Google Pay has no `completePayment`: its sheet is gone
     * before the charge.
     */
    inner class GooglePayFunnel internal constructor(private val paymentId: String) {
        private var beginAt: Long? = null
        private var checkoutAt: Long? = null

        private fun step(
            step: String,
            status: StepStatus,
            failure: Throwable? = null,
            since: Long? = beginAt
        ) = walletStep(
            Method.GOOGLE_PAY, step, status, paymentId,
            detail = failure?.let { Redaction.describeFailure(it) },
            startedAt = since.takeIf { status != StepStatus.START }
        )

        fun begin() {
            beginAt = now()
            step("begin", StepStatus.START)
        }

        /** The sheet could not be started, or ended without a token. */
        fun sheetFailed(failure: GopaySDKException) = when (failure.errorCode) {
            GopayErrorCodes.PAYMENT_CANCELLED -> step("cancel", StepStatus.INFO)
            // The sheet never came up, rather than failing once it was there.
            GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE -> step("begin", StepStatus.FAILURE, failure)
            else -> step("paymentAuthorized", StepStatus.FAILURE, failure)
        }

        /** The sheet could not even be opened. */
        fun launchFailed(failure: GopaySDKException) = step("begin", StepStatus.FAILURE, failure)

        /** The token arrived and reads: the user authorised. */
        fun authorized() {
            submit("googlepay-button", Method.GOOGLE_PAY, "googlepay", flow = null, paymentId = paymentId)
            step("paymentAuthorized", StepStatus.SUCCESS)
        }

        fun checkoutStarted() {
            checkoutAt = now()
            step("checkout", StepStatus.START)
        }

        /** The charge ended, with [failure] when it did not go through. */
        fun checkoutEnded(failure: Throwable?) = if (failure == null) {
            step("checkout", StepStatus.SUCCESS, since = checkoutAt)
        } else {
            step("checkout", StepStatus.FAILURE, failure, since = checkoutAt)
        }
    }

    /**
     * `api_call`: one HTTP request to the gateway, success or failure. [template] is the path
     * with its ids as `{id}`; [statusCode] is null when no response arrived. The bodies are
     * raw here, text or a form's fields, and redacted on the way in.
     */
    fun apiCall(
        httpMethod: String,
        template: String,
        statusCode: Int?,
        durationMs: Long,
        requestBody: Any?,
        responseBody: Any?,
        paymentId: String?
    ) {
        post(Budget.API_CALL, paymentId) {
            put("event_type", "api_call")
            put("action", lastSegment(template))
            put("http_method", httpMethod)
            put("target", template)
            put("status_code", statusCode)
            put("duration", durationMs)
            putIfPresent("req_body", Redaction.serializeBody(requestBody))
            putIfPresent("res_body", Redaction.serializeBody(responseBody))
        }
    }

    /**
     * `js_event` / `<errorCode>`: an SDK error. An HTTP error is not one: it already went out as
     * the `api_call` that carries its status, and sending it twice would double every failed
     * request in the data. The message is the SDK's own, scrubbed. No `params`: the status,
     * method and endpoint they would hold belong to an HTTP error, which never gets here.
     */
    fun error(error: GopaySDKException, paymentId: String?) {
        if (error.httpContext != null) return
        val code = error.errorCode.takeIf { it.isNotEmpty() } ?: "UNKNOWN"
        post(Budget.ERROR, paymentId) {
            put("event_type", "js_event")
            put("function_name", code)
            put("error_code", code)
            put("return_value", Redaction.safeErrorMessage(Redaction.sdkMessage(error)))
        }
    }

    /** `/payments/{id}/charge` → `charge`: the last segment that is not an id. */
    private fun lastSegment(template: String): String =
        template.split('/').lastOrNull { it.isNotEmpty() && !it.startsWith("{") } ?: ""

    /** The fields every event carries; an absent attribution key is left out, never sent empty. */
    private fun base(paymentId: String?): LinkedHashMap<String, Any?> = LinkedHashMap<String, Any?>().apply {
        putIfPresent("client_id", clientId?.takeIf { it.isNotEmpty() })
        putIfPresent("payment_session_id", (paymentId ?: latestPaymentId())?.takeIf { it.isNotEmpty() })
        put("sdk_instance_id", instanceId)
        put("sdk_version", BuildConfig.VERSION_NAME)
        put("integration", INTEGRATION)
        put("trace_id", newId())
        put("transaction_id", Telemetry.transactionId)
        put("origin", Telemetry.origin.take(MAX_ORIGIN_LENGTH))
        put("duration", null)
    }

    /**
     * Builds and sends one event. The event, its redaction and its JSON are made outside the
     * lock; under it only the budget, the `seq` the JSON gets closed with and the hand-over. An
     * event that cannot be built, has no attribution key, is refused by its budget or does not
     * fit the ingest's 16 KB takes no `seq` and leaves no gap. An `api_call` too large with its
     * bodies goes without them, as on iOS.
     */
    private fun post(budget: Budget, paymentId: String?, fields: LinkedHashMap<String, Any?>.() -> Unit) {
        try {
            if (exhausted(budget)) return
            val event = base(paymentId).apply(fields)
            // gw-logger wants one of the two and drops an event with neither.
            if (!event.containsKey("client_id") && !event.containsKey("payment_session_id")) return
            var json = JsonText.stringify(event)
            if (bytes(json) > MAX_EVENT_BYTES) {
                event.remove("req_body")
                event.remove("res_body")
                json = JsonText.stringify(event)
            }
            if (bytes(json) > MAX_EVENT_BYTES) return
            synchronized(lock) {
                if (spent[budget.ordinal] >= budget.limit) return
                val seq = lastSeq + 1
                // The event's own closing brace makes way for the number, then the envelope's.
                val body = "{\"event\":" + json.dropLast(1) + ",\"seq\":" + seq + "}}"
                val request = Request.Builder()
                    .url(endpoint)
                    .post(body.toByteArray(Charsets.UTF_8).toRequestBody(JSON))
                    .build()
                client.newCall(request).enqueue(Telemetry.discardResponse)
                lastSeq = seq
                spent[budget.ordinal]++
            }
            SdkLog.d("telemetry: ${event["event_type"]} ${nameOf(event)}")
        } catch (e: Throwable) {
            // Logging is never worth a thrown error in a payment flow.
        }
    }

    private fun exhausted(budget: Budget): Boolean =
        synchronized(lock) { spent[budget.ordinal] >= budget.limit }

    private fun bytes(json: String): Int = json.toByteArray(Charsets.UTF_8).size

    private fun nameOf(event: Map<String, Any?>): Any? = when (event["event_type"]) {
        "api_call" -> event["action"]
        "navigation" -> event["navigation_type"]
        "interaction" -> event["element_id"]
        else -> event["function_name"]
    }

    private fun LinkedHashMap<String, Any?>.putIfPresent(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    companion object {
        /** Which distribution reports, beside the browser SDK's `browser-sdk-esm` and the like. */
        const val INTEGRATION = "android-sdk"

        /**
         * The card form's flow: it hands the JWE back to the app rather than charging, the browser
         * SDK's encrypt-only flow.
         */
        const val CARD_FORM_FLOW = "return-payload"

        /** gw-logger's cap on a request body, less what the envelope and the `seq` add. */
        const val MAX_EVENT_BYTES = 16 * 1024 - 64

        private const val MAX_ORIGIN_LENGTH = 2048

        private val JSON = "application/json".toMediaType()
    }
}
