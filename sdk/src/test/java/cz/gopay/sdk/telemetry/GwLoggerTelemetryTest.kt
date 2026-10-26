package cz.gopay.sdk.telemetry

import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import cz.gopay.sdk.modules.network.SessionAuthInterceptor
import cz.gopay.sdk.modules.network.SessionTokenProvider
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The emitter: what each event carries, where it goes, and that nothing it does can reach the
 * payment. Every event a test sends is also checked against the gw-logger schema.
 */
class GwLoggerTelemetryTest {

    private val capture = TelemetryCapture()
    private var clock = 0L

    private fun telemetry(clientId: String? = "client_test_123", client: Call.Factory = capture.client) =
        GwLoggerTelemetry(
            endpoint = "https://lx.sandbox.gopay.com/events",
            clientId = clientId,
            client = client,
            nanoTime = { clock }
        )

    private val events get() = capture.events.onEach { GwLoggerSchema.assertValid(it) }

    private fun number(value: Any?): Long? = (value as JsonText.JsonNumber?)?.literal?.toLong()

    @Before
    fun setUp() = TelemetryCapture.reset()

    @After
    fun tearDown() {
        TelemetryCapture.reset()
        ErrorReporter.clear()
    }

    @Test
    fun `goes to the environment's own host, and nowhere without one`() {
        assertEquals("https://lx.sandbox.gopay.com/events", Environment.SANDBOX.telemetryUrl)
        assertEquals("https://lx.gopay.com/events", Environment.PRODUCTION.telemetryUrl)
        assertNull(Environment.DEVELOPMENT("https://mock.test/").telemetryUrl)
        assertNull(Telemetry.create(GopayConfig(Environment.DEVELOPMENT("https://gw.sandbox.gopay.com/x/"))))
    }

    @Test
    fun `cannot be turned off by any configuration`() {
        for (debug in listOf(true, false)) {
            for (env in listOf(Environment.SANDBOX, Environment.PRODUCTION)) {
                assertTrue(Telemetry.create(GopayConfig(env, clientId = "c", debug = debug)) != null)
            }
        }
    }

    @Test
    fun `posts one event per request with only Content-Type, as JSON under event`() {
        telemetry().init()

        val request = capture.requests.single()
        assertEquals("https://lx.sandbox.gopay.com/events", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("application/json", request.body!!.contentType().toString())
        assertNull(request.header("Authorization"))
        assertTrue(capture.bodies.single().startsWith("{\"event\":{"))
    }

    @Test
    fun `init is a navigation with no target, no payment and a null duration`() {
        telemetry().init()

        val event = events.single()
        assertEquals("navigation", event["event_type"])
        assertEquals("init", event["navigation_type"])
        assertTrue(event.containsKey("target"))
        assertNull(event["target"])
        assertTrue(event.containsKey("duration"))
        assertNull(event["duration"])
        assertFalse(event.containsKey("payment_session_id"))
        assertEquals("client_test_123", event["client_id"])
        assertEquals("android-sdk", event["integration"])
        assertTrue(capture.bodies.single().contains("\"duration\":null"))
    }

    @Test
    fun `leaves out an empty client id instead of sending it empty`() {
        val telemetry = telemetry(clientId = "")
        telemetry.attach("3000000001")
        telemetry.init()

        val event = events.single()
        assertFalse(event.containsKey("client_id"))
        assertEquals("3000000001", event["payment_session_id"])
    }

    @Test
    fun `sends nothing that has neither attribution key, and spends no number on it`() {
        val telemetry = telemetry(clientId = null)
        telemetry.init()
        assertTrue(capture.requests.isEmpty())

        telemetry.attach("3000000001")
        telemetry.navigate("3000000001")
        assertEquals(1L, number(events.single()["seq"]))
    }

    @Test
    fun `carries the payment only from the start of its session on`() {
        val telemetry = telemetry()
        telemetry.init()
        telemetry.attach("3000000001")
        telemetry.navigate("3000000001")
        telemetry.ready(GwLoggerTelemetry.Method.CARD, GwLoggerTelemetry.CARD_FORM_FLOW)
        telemetry.leave("3000000001")
        telemetry.detach("3000000001")
        telemetry.ready(GwLoggerTelemetry.Method.CARD, GwLoggerTelemetry.CARD_FORM_FLOW)

        val ids = events.map { it["payment_session_id"] }
        assertEquals(listOf(null, "3000000001", "3000000001", "3000000001", null), ids)
        assertEquals("attach", events[1]["flow"])
        assertFalse(events[0].containsKey("payment_session_id"))
    }

    @Test
    fun `names its own payment over the latest one`() {
        val telemetry = telemetry()
        telemetry.attach("A")
        telemetry.attach("B")
        telemetry.leave("A")
        telemetry.init()

        assertEquals(listOf("A", "B"), events.map { it["payment_session_id"] })
    }

    @Test
    fun `keeps one transaction id and a new trace id per event`() {
        val telemetry = telemetry()
        telemetry.init()
        telemetry.init()
        telemetry(clientId = "other").init()

        val sent = events
        assertEquals(1, sent.map { it["transaction_id"] }.toSet().size)
        assertEquals(3, sent.map { it["trace_id"] }.toSet().size)
        assertNotEquals(sent[0]["sdk_instance_id"], sent[2]["sdk_instance_id"])
        assertEquals(sent[0]["sdk_instance_id"], sent[1]["sdk_instance_id"])
    }

    @Test
    fun `numbers the events it sent 1, 2, 3, per instance, with no gap for one it did not`() {
        val telemetry = telemetry()
        telemetry.init()
        // Refused by the schema's size cap rather than sent and lost.
        telemetry.apiCall("GET", "/x".repeat(10_000), 200, 1, null, null, null)
        telemetry.init()
        telemetry.init()
        telemetry().init()

        assertEquals(listOf(1L, 2L, 3L, 1L), events.map { number(it["seq"]) })
    }

    @Test
    fun `spends separate budgets, so polling never silences the funnel or the errors`() {
        val telemetry = telemetry()
        repeat(205) { telemetry.apiCall("GET", "/payments/{id}/charge", 200, 1, null, null, "1") }
        telemetry.leave("1")
        telemetry.error(GopaySDKException(GopayErrorCodes.VALIDATION_INVALID_INPUT, "x"), "1")
        repeat(160) { telemetry.walletStep(GwLoggerTelemetry.Method.GOOGLE_PAY, "begin", GwLoggerTelemetry.StepStatus.START, "1") }

        val types = events.groupingBy { it["event_type"] as String to (it["function_name"] ?: it["navigation_type"]) }.eachCount()
        assertEquals(200, events.count { it["event_type"] == "api_call" })
        assertEquals(1, types["navigation" to "leave"])
        assertEquals(1, types["js_event" to GopayErrorCodes.VALIDATION_INVALID_INPUT])
        assertEquals(150, types["js_event" to "GPAY.begin"])
        assertEquals((1L..352L).toList(), events.map { number(it["seq"]) })
    }

    @Test
    fun `gives every budget back when a payment session starts`() {
        val telemetry = telemetry()
        repeat(201) { telemetry.apiCall("GET", "/payments/{id}/charge", 200, 1, null, null, "1") }
        repeat(76) { telemetry.ready(GwLoggerTelemetry.Method.CARD, null) }
        telemetry.navigate("2")
        telemetry.apiCall("GET", "/payments/{id}/charge", 200, 1, null, null, "2")
        telemetry.leave("2")

        val tail = events.takeLast(3).map { it["navigation_type"] ?: it["event_type"] }
        assertEquals(listOf("navigate", "api_call", "leave"), tail)
        assertEquals(200 + 75 + 3, events.size)
    }

    @Test
    fun `never lets a failure out, whether building or sending`() {
        val throwingFactory = Call.Factory { throw IllegalStateException("no client") }
        telemetry(client = throwingFactory).init()

        var ids = 0
        val throwingIds = GwLoggerTelemetry(
            endpoint = "https://lx.sandbox.gopay.com/events",
            clientId = "c",
            client = capture.client,
            // The instance id, then a failure for the trace id of the event.
            newId = { if (ids++ == 0) "instance" else throw IllegalStateException("no randomness") }
        )
        throwingIds.init()

        val failingIngest = OkHttpClient.Builder()
            .dispatcher(capture.client.dispatcher)
            .addInterceptor(Interceptor { throw IOException("ingest down") })
            .build()
        telemetry(client = failingIngest).init()

        assertTrue(capture.requests.isEmpty())
    }

    @Test
    fun `reports an SDK error as a js_event with its scrubbed message, an HTTP error not at all`() {
        Telemetry.current = telemetry()

        ErrorReporter.report(
            GopaySDKException(GopayErrorCodes.VALIDATION_INVALID_INPUT, "card 4111111111111111 bad for jan@example.com"),
            paymentId = "3000000001"
        )
        ErrorReporter.report(
            GopaySDKException(
                GopayErrorCodes.NETWORK_CLIENT_ERROR,
                "Failed to charge payment: HTTP 409",
                httpContext = HttpErrorContext(statusCode = 409)
            )
        )

        val event = events.single()
        assertEquals("js_event", event["event_type"])
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, event["function_name"])
        assertEquals(GopayErrorCodes.VALIDATION_INVALID_INPUT, event["error_code"])
        assertEquals("card [redacted] bad for [redacted]", event["return_value"])
        assertEquals("3000000001", event["payment_session_id"])
        assertNull(event["duration"])
        for (field in listOf("action", "http_method", "target", "status_code", "res_body")) {
            assertFalse(event.containsKey(field))
        }
    }

    @Test
    fun `reports an error once, however many entry points it passes`() {
        Telemetry.current = telemetry()
        val error = GopaySDKException(GopayErrorCodes.VALIDATION_INVALID_INPUT, "x")

        ErrorReporter.report(error)
        ErrorReporter.report(error)

        assertEquals(1, events.size)
    }

    @Test
    fun `submit says that the form was submitted and nothing about it`() {
        val telemetry = telemetry()
        clock = 1_000_000_000
        telemetry.ready(GwLoggerTelemetry.Method.CARD, GwLoggerTelemetry.CARD_FORM_FLOW)
        clock = 3_500_000_000
        telemetry.submit("card-form", GwLoggerTelemetry.Method.CARD, "card", GwLoggerTelemetry.CARD_FORM_FLOW)

        val submit = events[1]
        assertEquals(
            setOf(
                "client_id", "sdk_instance_id", "sdk_version", "integration", "trace_id", "transaction_id",
                "origin", "duration", "event_type", "interaction_type", "element_id", "payment_method", "flow", "seq"
            ),
            submit.keys
        )
        assertEquals("submit", submit["interaction_type"])
        assertEquals("card-form", submit["element_id"])
        assertEquals("card", submit["payment_method"])
        assertEquals(2500L, number(submit["duration"]))
    }

    @Test
    fun `times ready from init and unmount from ready`() {
        clock = 0
        val telemetry = telemetry()
        clock = 1_200_000_000
        telemetry.ready(GwLoggerTelemetry.Method.CARD, GwLoggerTelemetry.CARD_FORM_FLOW)
        clock = 4_200_000_000
        telemetry.unmount(GwLoggerTelemetry.Method.CARD, chargeInFlight = true)
        telemetry.unmount(GwLoggerTelemetry.Method.CARD, chargeInFlight = false)

        val (ready, unmount, secondUnmount) = events
        assertEquals("PAYMENT_CARD.ready", ready["function_name"])
        assertEquals(1200L, number(ready["duration"]))
        assertEquals("return-payload", ready["flow"])
        assertEquals("PAYMENT_CARD.unmount", unmount["function_name"])
        assertEquals(3000L, number(unmount["duration"]))
        assertEquals("charge-aborted", unmount["return_value"])
        assertEquals("{\"charge_in_flight\":true}", unmount["params"])
        // The mount took its ready with it: nothing to time the next one from.
        assertNull(secondUnmount["duration"])
        assertEquals("idle", secondUnmount["return_value"])
    }

    @Test
    fun `reports a wallet step with its outcome in params and its detail as the return value`() {
        val telemetry = telemetry()
        clock = 1_000_000_000
        val begin = telemetry.now()
        telemetry.walletStep(GwLoggerTelemetry.Method.GOOGLE_PAY, "begin", GwLoggerTelemetry.StepStatus.START, "1")
        clock = 1_750_000_000
        telemetry.walletStep(
            GwLoggerTelemetry.Method.GOOGLE_PAY, "checkout", GwLoggerTelemetry.StepStatus.FAILURE, "1",
            detail = "PAYMENT_006", startedAt = begin
        )
        telemetry.walletAvailability(GwLoggerTelemetry.Method.GOOGLE_PAY, "isWalletAvailable", true, false, begin)
        telemetry.walletAvailability(GwLoggerTelemetry.Method.GOOGLE_PAY, "isWalletAvailable", true, true, begin)
        telemetry.walletAvailability(GwLoggerTelemetry.Method.GOOGLE_PAY, "isWalletAvailable", false, false, begin)

        val (start, checkout, unavailable, available, failed) = events
        assertEquals("GPAY.begin", start["function_name"])
        assertEquals("start", start["return_value"])
        assertEquals("{\"status\":\"start\"}", start["params"])
        assertNull(start["duration"])
        assertEquals("GPAY.checkout", checkout["function_name"])
        assertEquals("PAYMENT_006", checkout["return_value"])
        assertEquals("{\"status\":\"failure\"}", checkout["params"])
        assertEquals(750L, number(checkout["duration"]))
        assertEquals("GPAY.readyToPay", unavailable["function_name"])
        assertEquals("unsupported-device", unavailable["return_value"])
        assertEquals("{\"available\":false,\"caller\":\"isWalletAvailable\"}", unavailable["params"])
        assertEquals("available", available["return_value"])
        assertEquals("{\"available\":true,\"caller\":\"isWalletAvailable\"}", available["params"])
        assertEquals("readiness-check-failed", failed["return_value"])
    }

    @Test
    fun `reports a 3DS challenge as the leave, with where it went and nothing of its query`() {
        telemetry().redirect("3000000001", "https://acs.example-bank.com/challenge?creq=SECRET")

        val event = events.single()
        assertEquals("leave", event["navigation_type"])
        assertEquals("3ds-redirect", event["flow"])
        assertEquals("https://acs.example-bank.com/challenge", event["target"])
    }

    // the API call interceptor

    private fun apiClient(telemetry: GwLoggerTelemetry, answer: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ApiCallTelemetryInterceptor(telemetry, basePath = "/gp-gw/api/4.0/"))
            .addInterceptor(Interceptor { answer(it.request()) })
            .build()

    private fun answer(request: Request, code: Int, body: String) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("x")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun `reports a gateway call with its template, status, duration and redacted bodies`() {
        val telemetry = telemetry()
        val client = apiClient(telemetry) {
            clock += 42_000_000
            answer(it, 200, """{"state":"ACTION_REQUIRED","action":{"redirect_url":"https://acs.test/x?creq=1"}}""")
        }

        val response = client.newCall(
            Request.Builder()
                .url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/3000000001/charge?x=1")
                .post("""{"payment_instrument":{"input":{"encrypted_card":"eyJ.abc"}}}""".toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()

        // The caller still reads the whole body.
        assertTrue(response.body!!.string().contains("ACTION_REQUIRED"))
        val event = events.single()
        assertEquals("api_call", event["event_type"])
        assertEquals("charge", event["action"])
        assertEquals("POST", event["http_method"])
        assertEquals("/payments/{id}/charge", event["target"])
        assertEquals(200L, number(event["status_code"]))
        assertEquals(42L, number(event["duration"]))
        assertEquals("3000000001", event["payment_session_id"])
        assertEquals("{\"payment_instrument\":{\"input\":\"[redacted]\"}}", event["req_body"])
        assertEquals("{\"state\":\"ACTION_REQUIRED\",\"action\":{\"redirect_url\":\"[redacted]\"}}", event["res_body"])
    }

    @Test
    fun `names the poll by what it reads, not by the id at its end`() {
        val telemetry = telemetry()
        apiClient(telemetry) { answer(it, 404, "{}") }
            .newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/3000000001").build())
            .execute().close()
        apiClient(telemetry) { answer(it, 200, "{}") }
            .newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/0b8e9c1e-7a52-4c1b-9d3f-2f6a1e4d5c7b/qr-payment/info?format=png").build())
            .execute().close()

        val (status, qr) = events
        assertEquals("/payments/{id}", status["target"])
        assertEquals("payments", status["action"])
        assertEquals(404L, number(status["status_code"]))
        assertEquals("/payments/{id}/qr-payment/info", qr["target"])
        assertEquals("info", qr["action"])
        assertFalse(capture.bodies.any { it.contains("\"target\":\"/payments/3000000001") })
    }

    @Test
    fun `reports a request that got no response with a null status and passes the failure on`() {
        val telemetry = telemetry()
        val client = apiClient(telemetry) { throw IOException("timeout") }

        val thrown = runCatching {
            client.newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/cards/public-key").build()).execute()
        }.exceptionOrNull()

        assertTrue(thrown is IOException)
        val event = events.single()
        assertTrue(event.containsKey("status_code"))
        assertNull(event["status_code"])
        assertEquals("/cards/public-key", event["target"])
        assertEquals("public-key", event["action"])
    }

    @Test
    fun `sends a call too large for the ingest without its bodies, and nothing larger`() {
        val telemetry = telemetry()
        // Three bytes a character: two bodies cut at 2000 characters and a long path pass 16 KB.
        val wide = "{" + ('a'..'e').joinToString(",") { "\"$it\":\"" + "€".repeat(500) + "\"" } + "}"
        val path = "/" + List(7) { "€".repeat(200) }.joinToString("/")
        telemetry.apiCall("POST", path, 200, 1, wide, wide, "1")
        telemetry.apiCall("GET", "/x".repeat(9000), 200, 1, null, null, "1")

        val event = events.single()
        assertEquals(path, event["target"])
        assertFalse(event.containsKey("req_body"))
        assertFalse(event.containsKey("res_body"))
        assertTrue(capture.bodies.single().toByteArray().size <= 16 * 1024)
        assertEquals(1L, number(event["seq"]))
    }

    @Test
    fun `reports a form body as its fields and keeps the token and the public key to itself`() {
        val telemetry = telemetry()
        apiClient(telemetry) { answer(it, 200, "{\"access_token\":\"jwt\",\"token_type\":\"Bearer\"}") }
            .newCall(
                Request.Builder()
                    .url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/oauth2/token")
                    .post(FormBody.Builder().add("grant_type", "payment_credentials").add("scope", "payment:charge payment:read").build())
                    .build()
            ).execute().close()
        apiClient(telemetry) { answer(it, 200, "{\"kty\":\"RSA\",\"n\":\"abc\",\"e\":\"AQAB\"}") }
            .newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/cards/public-key").build())
            .execute().close()

        val (token, key) = events
        assertEquals("{\"grant_type\":\"payment_credentials\",\"scope\":\"payment:charge payment:read\"}", token["req_body"])
        assertFalse(token.containsKey("res_body"))
        assertFalse(key.containsKey("res_body"))
        assertEquals(200L, number(key["status_code"]))
    }

    @Test
    fun `reports both requests of a 401 retry, each as it went out`() {
        val telemetry = telemetry()
        val provider = object : SessionTokenProvider {
            override fun currentToken() = "stale"
            override fun invalidateToken() = Unit
            override suspend fun reauthenticate() = "fresh"
        }
        var calls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(SessionAuthInterceptor(provider))
            .addInterceptor(ApiCallTelemetryInterceptor(telemetry, basePath = "/gp-gw/api/4.0/"))
            .addInterceptor(Interceptor { answer(it.request(), if (calls++ == 0) 401 else 200, "{\"state\":\"PAID\"}") })
            .build()

        client.newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/payments/3000000001").build())
            .execute().close()

        assertEquals(listOf(401L, 200L), events.map { number(it["status_code"]) })
        assertTrue(events.all { it["payment_session_id"] == "3000000001" })
    }

    @Test
    fun `steps through a Google Pay payment the way gw-ui reads it`() {
        val telemetry = telemetry()
        clock = 1_000_000_000
        telemetry.googlePay("1").apply {
            begin()
            clock = 3_000_000_000
            authorized()
            checkoutStarted()
            clock = 4_000_000_000
            checkoutEnded(null)
        }
        telemetry.googlePay("1").apply {
            begin()
            clock = 5_000_000_000
            sheetFailed(GopaySDKException(GopayErrorCodes.PAYMENT_CANCELLED, "closed"))
        }
        telemetry.googlePay("1").apply {
            begin()
            sheetFailed(GopaySDKException(GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE, "no Play services"))
            launchFailed(GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "no launcher"))
            sheetFailed(GopaySDKException(GopayErrorCodes.PAYMENT_PROCESSING_FAILED, "bad token"))
            checkoutEnded(GopaySDKException(GopayErrorCodes.PAYMENT_DECLINED, "Charge failed"))
        }

        val steps = events.map { (it["function_name"] ?: it["element_id"]) to (it["return_value"] ?: "-") }
        assertEquals(
            listOf(
                "GPAY.begin" to "start",
                "googlepay-button" to "-",
                "GPAY.paymentAuthorized" to "success",
                "GPAY.checkout" to "start",
                "GPAY.checkout" to "success",
                "GPAY.begin" to "start",
                "GPAY.cancel" to "info",
                "GPAY.begin" to "start",
                "GPAY.begin" to "PAYMENT_004",
                "GPAY.begin" to "PAYMENT_003",
                "GPAY.paymentAuthorized" to "PAYMENT_003",
                "GPAY.checkout" to "PAYMENT_006"
            ),
            steps
        )
        // The sheet's steps from its opening, the charge's outcome from its sending.
        val durations = events.take(5).map { number(it["duration"]) }
        assertEquals(listOf(null, null, 2000L, null, 1000L), durations)
        assertEquals(1000L, number(events[6]["duration"]))
        assertEquals("googlepay", events[1]["payment_method"])
    }

    @Test
    fun `takes nothing from the caller when reporting fails`() {
        val telemetry = telemetry(client = Call.Factory { throw IllegalStateException("no client") })
        val response = apiClient(telemetry) { answer(it, 200, "{\"ok\":true}") }
            .newCall(Request.Builder().url("https://gw.sandbox.gopay.com/gp-gw/api/4.0/cards/public-key").build())
            .execute()

        assertEquals("{\"ok\":true}", response.body!!.string())
    }
}
