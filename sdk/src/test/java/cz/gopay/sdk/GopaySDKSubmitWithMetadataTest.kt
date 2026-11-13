package cz.gopay.sdk

import cz.gopay.sdk.config.GopayConfig
import cz.gopay.sdk.config.GopayEnvironment
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.CardScheme
import cz.gopay.sdk.model.CardServiceType
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.NetworkModule
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.modules.network.ShareableKeyInterceptor
import cz.gopay.sdk.service.CardMetadataLookup
import cz.gopay.sdk.service.TestJwe
import cz.gopay.sdk.telemetry.ApiCallTelemetryInterceptor
import cz.gopay.sdk.telemetry.TelemetryCapture
import cz.gopay.sdk.testing.SharedSdkState
import cz.gopay.sdk.testing.awaitSettled
import cz.gopay.sdk.testing.drain
import cz.gopay.sdk.ui.CardFormData
import cz.gopay.sdk.ui.CardFormHandlers
import cz.gopay.sdk.ui.CardFormStore
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The public `GopaySDK.submitCardFormWithMetadata` from the call down to the transport: the real
 * SDK, its encryption, its lookup, its shareable-key client and its telemetry, with only the
 * gateway faked by the last interceptor. What leaves the device and what the host gets back. The
 * iOS SDK has the same test against its scripted gateway.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GopaySDKSubmitWithMetadataTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val keys = TestJwe()
    private val telemetry = TelemetryCapture()
    private val threads = Executors.newCachedThreadPool()
    private val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
    private val reported: MutableList<GopaySDKException> = Collections.synchronizedList(mutableListOf())
    private val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val defaultWarnSink = SdkLog.warnSink
    private val owner = Any()
    private var resets = 0

    /** How the gateway answers the key fetch and the lookup. */
    @Volatile
    private var publicKey: () -> Pair<Int, String> = {
        200 to NetworkModule.moshi.adapter(Jwk::class.java).toJson(keys.jwk)
    }

    @Volatile
    private var lookup: (Interceptor.Chain) -> Pair<Int, String> = { 200 to VISA_GOLD }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        SdkLog.warnSink = { warnings.add(it) }
        TelemetryCapture.reset()
        telemetry.install()
        GopaySDK.initialize(
            GopayConfig(
                environment = GopayEnvironment.SANDBOX,
                clientId = "client-1",
                shareableKey = "key-1",
                // On, so the lookup's warning is logged; the HTTP log belongs to the SDK's own
                // client, which this test replaces.
                debugLoggingEnabled = true,
                errorCallback = { reported.add(it) }
            )
        )
        val sdk = GopaySDK.getInstance()
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher(threads))
            .addInterceptor(ShareableKeyInterceptor("client-1", "key-1"))
            .addInterceptor(ApiCallTelemetryInterceptor(sdk.telemetry!!, basePath = "/api/"))
            .addInterceptor(Interceptor { chain -> respond(chain) })
            .build()
        sdk.setPublicApiForTest(
            NetworkModule.createRetrofit(client, "https://gw.example/api/").create(PublicApi::class.java)
        )
        CardFormStore.register(FORM, owner, CardFormHandlers(reset = { resets++ }, submitAttempted = {}))
        CardFormStore.update(FORM, owner, CardFormData("4444444444444448", "1228", "123"))
    }

    @After
    fun tearDown() {
        drain(threads)
        CardFormStore.unregister(FORM, owner)
        GopaySDK::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        TelemetryCapture.reset()
        ErrorReporter.clear()
        SdkLog.warnSink = defaultWarnSink
        Dispatchers.resetMain()
    }

    private fun respond(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests.add(request)
        val (code, body) = if (request.url.encodedPath.endsWith("/cards/bin-lookup")) lookup(chain) else publicKey()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fake")
            .header("Date", "Wed, 30 Sep 2026 17:34:17 GMT")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun lookups() = synchronized(requests) { requests.filter { it.url.encodedPath.endsWith("/cards/bin-lookup") } }

    private fun apiCalls(action: String) = telemetry.events.filter { it["event_type"] == "api_call" && it["action"] == action }

    @Test
    fun `the call sends exactly eight digits and returns the JWE of the card with its metadata`() {
        val result = runBlocking { GopaySDK.getInstance().submitCardFormWithMetadata(FORM) }

        assertEquals(
            CardMetadata(CardScheme.VISA, corporate = false, brand = "GOLD", serviceType = CardServiceType.CREDIT, issuerCountry = "POL"),
            result.cardMetadata
        )
        assertEquals("the JWE is the card of the form", "4444444444444448", keys.decrypt(result.encryptedPayload).cardPan)
        val sent = lookups().single()
        assertEquals("POST", sent.method)
        assertEquals("{\"bin\":\"44444444\"}", Buffer().also { sent.body!!.writeTo(it) }.readUtf8())
        assertTrue(sent.header("Authorization")!!.startsWith("Basic "))
        assertEquals("the form was emptied", 1, resets)
        assertTrue(warnings.isEmpty())
        assertTrue(reported.isEmpty())
        // The lookup is reported without its request; nothing sent carries the digits.
        val call = apiCalls("bin-lookup").single()
        assertFalse(call.containsKey("req_body"))
        assertFalse(telemetry.bodies.any { it.contains("44444444") })
    }

    @Test
    fun `a 404 from the lookup gives the JWE, no metadata and one warning`() {
        lookup = { 404 to """{"code":404,"error":"NOT_FOUND","message":"No card details found for the BIN"}""" }

        val result = runBlocking { GopaySDK.getInstance().submitCardFormWithMetadata(FORM) }

        assertNull(result.cardMetadata)
        assertEquals("4444444444444448", keys.decrypt(result.encryptedPayload).cardPan)
        assertEquals(listOf(CardMetadataLookup.warning("HTTP 404")), warnings.toList())
        assertTrue("nothing through errorCallback", reported.isEmpty())
        assertEquals(1, resets)
        assertFalse("an error answer is not reported", apiCalls("bin-lookup").single().containsKey("res_body"))
    }

    @Test
    fun `a failed key fetch cancels the lookup, is reported once and keeps the card`() {
        val lookupArrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        // The lookup hangs in the transport until the SDK cancels its call, as OkHttp would.
        lookup = { chain ->
            lookupArrived.countDown()
            awaitSettled("the SDK cancelled the lookup") { chain.call().isCanceled() }
            release.countDown()
            throw IOException("Canceled")
        }
        publicKey = {
            // Only once the lookup is on its way, so there is one to cancel.
            lookupArrived.await(30, TimeUnit.SECONDS)
            500 to """{"code":500,"error":"INTERNAL","message":"boom"}"""
        }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { GopaySDK.getInstance().submitCardFormWithMetadata(FORM) }
        }

        assertEquals(GopayErrorCodes.NETWORK_SERVER_ERROR, e.code)
        assertEquals("reported once", listOf(e), reported.toList())
        assertEquals("the card stays in the form", 0, resets)
        assertEquals(setOf(FORM), CardFormStore.storedFormIds())
        assertTrue("the lookup's call was cancelled", release.await(30, TimeUnit.SECONDS))
        drain(threads)
        assertTrue("a lookup the failed encryption took with it is not reported", apiCalls("bin-lookup").isEmpty())
        assertEquals(1, apiCalls("public-key").size)
        assertTrue("a cancelled lookup is no warning", warnings.isEmpty())
    }

    private companion object {
        const val FORM = "public-submit-with-metadata"
        const val VISA_GOLD =
            """{"scheme":"VISA","corporate":false,"brand":"GOLD","service_type":"CREDIT","issuer_country":"POL"}"""
    }
}
