package cz.gopay.sdk.service

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.CardScheme
import cz.gopay.sdk.model.CardServiceType
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.ShareableKeyInterceptor
import cz.gopay.sdk.telemetry.ApiCallTelemetryInterceptor
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.testing.HangingPublicApi
import cz.gopay.sdk.testing.awaitSettled
import cz.gopay.sdk.testing.drain
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `POST /cards/bin-lookup` behind `submitCardFormWithMetadata`: what goes out, how strictly the
 * answer is read, and that nothing that goes wrong is more than "no metadata". The eight digits
 * are card data, so no warning carries them. The iOS SDK tests the same cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardMetadataLookupTest {

    private val defaultWarnSink = SdkLog.warnSink
    private val warnings = mutableListOf<String>()

    @Before
    fun collectWarnings() {
        SdkLog.warnSink = { warnings.add(it) }
    }

    @After
    fun restoreLogSink() {
        SdkLog.warnSink = defaultWarnSink
    }

    private val json = "application/json".toMediaType()

    private val hanging = HangingPublicApi()

    /** The lookup over the SDK's own Retrofit and shareable-key client, the transport faked. */
    private fun lookupOver(gateway: (Request) -> Pair<Int, String>): CardMetadataLookup {
        val client = OkHttpClient.Builder()
            .addInterceptor(ShareableKeyInterceptor("client-1", "key-1"))
            .addInterceptor(Interceptor { chain ->
                val (code, body) = gateway(chain.request())
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fake")
                    .body(body.toResponseBody(json))
                    .build()
            })
            .build()
        val api = NetworkModule.createRetrofit(client, "https://gw.example/api/").create(PublicApi::class.java)
        return CardMetadataLookup(publicApi = { api })
    }

    private fun answering(code: Int, body: String) = lookupOver { code to body }

    private fun lookUp(code: Int, body: String): CardMetadata? = runBlocking { answering(code, body).lookUp(PAN) }

    private fun assertNoMetadata(reason: String, found: CardMetadata?) {
        assertNull(found)
        assertEquals(listOf(CardMetadataLookup.warning(reason)), warnings)
        assertFalse("the warning carries no card digits", warnings.single().contains(BIN))
        warnings.clear()
    }

    @Test
    fun `the lookup posts the first eight digits with the shareable key`() = runBlocking {
        val requests = mutableListOf<Request>()
        val lookup = lookupOver { requests.add(it); 200 to FULL }

        lookup.lookUp(PAN)

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/cards/bin-lookup", request.url.encodedPath)
        assertNull(request.url.query)
        val basic = Base64.getEncoder().encodeToString("client-1:key-1".toByteArray())
        assertEquals("Basic $basic", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
        assertEquals("{\"bin\":\"44444444\"}", Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a number shorter than eight digits or with anything but digits is not looked up`() = runBlocking {
        val lookup = lookupOver { throw AssertionError("must not be sent") }

        assertNull(lookup.lookUp("4444444"))
        assertNull(lookup.lookUp("4444 4444 4444 4448"))
        assertNull(lookup.lookUp(""))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a full answer is read field by field`() {
        assertEquals(
            CardMetadata(
                scheme = CardScheme.VISA,
                corporate = false,
                brand = "GOLD",
                serviceType = CardServiceType.DEBIT,
                issuer = "Example Bank, a.s.",
                issuerCountry = "CZE"
            ),
            lookUp(200, FULL)
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a minimal answer has the two required fields and nothing else`() {
        assertEquals(
            CardMetadata(scheme = CardScheme.MASTERCARD, corporate = true),
            lookUp(200, """{"scheme":"MASTERCARD","corporate":true}""")
        )
        assertEquals(
            CardMetadata(CardScheme.MASTERCARD, true, null, CardServiceType.CREDIT, null, null),
            lookUp(200, """{"scheme":"MASTERCARD","corporate":true,"service_type":"CREDIT","brand":null}""")
        )
    }

    @Test
    fun `an HTTP error is no metadata`() {
        assertNoMetadata("HTTP 404", lookUp(404, """{"code":404,"error":"NOT_FOUND","message":"Resource not found"}"""))
        assertNoMetadata("HTTP 500", lookUp(500, ""))
        assertNoMetadata("HTTP 401", lookUp(401, """{"error":"unauthorized"}"""))
    }

    @Test
    fun `a request that got no response is no metadata`() = runBlocking {
        // The reason is the SDK's code for the failure, as on iOS, never the failure's own text.
        assertNoMetadata(
            GopayErrorCodes.NETWORK_IO_ERROR,
            lookupOver { throw IOException("unreachable $BIN") }.lookUp(PAN)
        )
        assertNoMetadata(
            GopayErrorCodes.NETWORK_NO_CONNECTION,
            lookupOver { throw UnknownHostException("gw.example") }.lookUp(PAN)
        )
        assertNoMetadata(
            GopayErrorCodes.NETWORK_TIMEOUT,
            lookupOver { throw SocketTimeoutException("timeout") }.lookUp(PAN)
        )
    }

    @Test
    fun `a failure that is not the network's is no metadata, under the SDK's code for the unexpected`() = runBlocking {
        val lookup = CardMetadataLookup(publicApi = { throw IllegalStateException("broken $BIN") })

        assertNoMetadata(GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR, lookup.lookUp(PAN))
    }

    @Test
    fun `a cancellation that is not the caller's is no metadata, at once`() = runTest {
        // The HTTP client giving the request up on its own, while nobody cancelled the lookup.
        val lookup = CardMetadataLookup(publicApi = { throw CancellationException("the client gave up") })

        val found = lookup.lookUp(PAN)

        assertNoMetadata("CancellationException", found)
        assertEquals("nothing waited for the limit", 0L, currentTime)
    }

    @Test
    fun `missing credentials are no metadata, the encryption reports them`() = runBlocking {
        val lookup = CardMetadataLookup(publicApi = {
            throw GopaySDKException(code = GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, message = "no key")
        })

        assertNoMetadata(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, lookup.lookUp(PAN))
    }

    @Test
    fun `an answer that cannot be read is no metadata`() {
        val valid = """{"scheme":"VISA","corporate":false}"""
        val bodies = listOf(
            "", "<html>captive portal</html>", "{\"scheme\":", "[]", "null", "\"VISA\"", "12",
            // Anything after the one JSON value: not an answer of the gateway.
            "$valid x", "$valid$valid", "$valid,"
        )
        for (body in bodies) {
            assertNoMetadata("the answer could not be read", lookUp(200, body))
        }
    }

    @Test
    fun `an answer without a known scheme or a boolean corporate is no metadata`() {
        val bodies = listOf(
            """{"scheme":"AMEX","corporate":false}""",
            """{"scheme":"visa","corporate":false}""",
            """{"scheme":1,"corporate":false}""",
            """{"scheme":null,"corporate":false}""",
            """{"corporate":false,"brand":"GOLD"}""",
            """{"scheme":"VISA"}""",
            """{"scheme":"VISA","corporate":"false"}""",
            """{"scheme":"VISA","corporate":0}""",
            """{"scheme":"VISA","corporate":1}""",
            """{"scheme":"VISA","corporate":null}"""
        )
        for (body in bodies) assertNoMetadata("the answer could not be read", lookUp(200, body))
    }

    @Test
    fun `an optional field outside its documented shape is left out, the rest stays`() {
        val base = CardMetadata(scheme = CardScheme.VISA, corporate = false)
        fun read(fields: String) = lookUp(200, """{"scheme":"VISA","corporate":false,$fields}""")

        assertEquals("a number", base, read(""""brand":123"""))
        assertEquals("empty", base, read(""""brand":"""""))
        assertEquals("a value the SDK does not know", base, read(""""service_type":"PREPAID""""))
        assertEquals("lower case", base, read(""""service_type":"debit""""))
        assertEquals("201 characters", base, read(""""issuer":"${"x".repeat(201)}""""))
        assertEquals("200 characters", base.copy(issuer = "x".repeat(200)), read(""""issuer":"${"x".repeat(200)}""""))
        assertEquals("an object", base, read(""""issuer":{"name":"Bank"}"""))
        assertEquals("lower case", base, read(""""issuer_country":"cze""""))
        assertEquals("alpha-2", base, read(""""issuer_country":"CZ""""))
        assertEquals("four letters", base, read(""""issuer_country":"CZEX""""))
        // One bad field takes only itself.
        assertEquals(
            base.copy(brand = "GOLD", issuerCountry = "CZE"),
            read(""""brand":"GOLD","service_type":"PREPAID","issuer":7,"issuer_country":"CZE"""")
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `no other field of the answer is passed on`() {
        val found = lookUp(200, """{"scheme":"VISA","corporate":false,"bin":"$BIN","card_token":"tok_1","extra":{"a":1}}""")

        assertEquals(CardMetadata(scheme = CardScheme.VISA, corporate = false), found)
        assertFalse(found.toString().contains(BIN))
    }

    @Test
    fun `an answer that takes longer than three seconds is no metadata`() = runTest {
        val lookup = CardMetadataLookup(publicApi = { hanging })

        val found = lookup.lookUp(PAN)

        assertEquals("the limit is on the whole lookup", 3_000L, currentTime)
        assertNoMetadata("no answer in 3000 ms", found)
        assertTrue("the request is marked as ended by the limit, for the telemetry", hanging.limitSignal!!.limitReached)
    }

    @Test
    fun `a lookup its limit ended is one api_call without a status, from the lookup down to the transport`() {
        // The whole way: the lookup's tag has to reach the interceptor through Retrofit, set
        // before the cancel, or the cancelled request would go unreported.
        val capture = TelemetryCapture()
        val telemetry = GwLoggerTelemetry(
            endpoint = "https://lx.sandbox.gopay.com/events", clientId = "client-1", client = capture.client
        )
        val threads = Executors.newCachedThreadPool()
        val arrived = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher(threads))
            .addInterceptor(ShareableKeyInterceptor("client-1", "key-1"))
            .addInterceptor(ApiCallTelemetryInterceptor(telemetry, basePath = "/api/"))
            // A gateway that never answers: the request hangs until the lookup cancels its call.
            .addInterceptor(Interceptor { chain ->
                arrived.countDown()
                awaitSettled("the lookup cancelled its request") { chain.call().isCanceled() }
                throw IOException("Canceled")
            })
            .build()
        val api = NetworkModule.createRetrofit(client, "https://gw.example/api/").create(PublicApi::class.java)
        // A limit of its own, short and on the real clock: the request is real, so is its end.
        val lookup = CardMetadataLookup(publicApi = { api }, timeoutMs = 50)

        val found = runBlocking { lookup.lookUp(PAN) }
        drain(threads)

        assertTrue("the request went out", arrived.await(0, TimeUnit.SECONDS))
        assertNoMetadata("no answer in 50 ms", found)
        val event = capture.events.single()
        assertEquals("api_call", event["event_type"])
        assertEquals("/cards/bin-lookup", event["target"])
        assertEquals("POST", event["http_method"])
        assertTrue(event.containsKey("status_code"))
        assertNull(event["status_code"])
        assertFalse(event.containsKey("req_body"))
        assertFalse(event.containsKey("res_body"))
        assertFalse(capture.bodies.any { it.contains(BIN) })
    }

    @Test
    fun `a cancelled lookup ends with the cancellation and says nothing`() = runTest {
        var returned = false
        val job = launch {
            CardMetadataLookup(publicApi = { hanging }).lookUp(PAN)
            returned = true
        }
        runCurrent()

        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(returned)
        assertEquals("nothing waited for the limit", 0L, currentTime)
        assertTrue(warnings.isEmpty())
        assertFalse("not the limit: the telemetry stays silent about it", hanging.limitSignal!!.limitReached)
    }

    private companion object {
        /** The sandbox test card and its first eight digits. */
        const val PAN = "4444444444444448"
        const val BIN = "44444444"

        const val FULL = """{"scheme":"VISA","corporate":false,"brand":"GOLD","service_type":"DEBIT",""" +
            """"issuer":"Example Bank, a.s.","issuer_country":"CZE"}"""
    }
}
