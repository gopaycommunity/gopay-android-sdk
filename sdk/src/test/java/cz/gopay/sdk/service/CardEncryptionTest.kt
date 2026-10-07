package cz.gopay.sdk.service

import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserDataDetected
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.CardFormUrl
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.modules.network.PublicApi
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * GPMOB-155: the card JWE carries the gateway's clock. Every encryption fetches the key again
 * and takes `iat` from that response's `Date` header; the device clock is only the fallback.
 */
class CardEncryptionTest {

    private val keys = TestJwe()

    private val cardData =
        CardData(cardPan = "4532015112830366", expMonth = "12", expYear = "30", cvv = "123")

    /** The header production sent on 30 Sep 2026, and the same instant in Unix seconds. */
    private val gatewayDate = "Wed, 30 Sep 2026 17:34:17 GMT"
    private val gatewaySeconds = 1_790_789_657L

    /** A device clock an hour ahead of the gateway. */
    private val skewedDeviceSeconds = gatewaySeconds + 3600

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

    @Test
    fun `iat comes from the Date header even when the device clock is off`() = runBlocking {
        val api = FakePublicApi { Response.success(keys.jwk, headersOf("Date", gatewayDate)) }

        val payload = keys.decrypt(encryption(api).encrypt(cardData, CLIENT_ID))

        assertEquals(gatewaySeconds, payload.iat)
        assertEquals(gatewaySeconds + 600, payload.exp)
        assertEquals(CLIENT_ID, payload.clientId)
        assertTrue(payload.jti.startsWith("android-"))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `every encryption fetches the key again`() = runBlocking {
        val api = FakePublicApi { Response.success(keys.jwk, headersOf("Date", gatewayDate)) }
        val encryption = encryption(api)

        encryption.encrypt(cardData, CLIENT_ID)
        encryption.encrypt(cardData, CLIENT_ID)

        assertEquals(2, api.keyRequests)
    }

    @Test
    fun `concurrent encryptions each make their own request`() = runBlocking {
        val api = FakePublicApi { Response.success(keys.jwk, headersOf("Date", gatewayDate)) }
        val encryption = encryption(api)

        List(3) { async { encryption.encrypt(cardData, CLIENT_ID) } }.awaitAll()

        assertEquals(3, api.keyRequests)
    }

    @Test
    fun `without a Date header the device clock is used and a warning logged`() = runBlocking {
        val api = FakePublicApi { Response.success(keys.jwk) }

        val payload = keys.decrypt(encryption(api).encrypt(cardData, CLIENT_ID))

        assertEquals(skewedDeviceSeconds, payload.iat)
        assertEquals(skewedDeviceSeconds + 600, payload.exp)
        assertEquals(listOf(CardEncryption.MISSING_DATE_WARNING), warnings)
    }

    @Test
    fun `an unreadable Date header falls back the same way`() = runBlocking {
        val api = FakePublicApi {
            Response.success(keys.jwk, headersOf("Date", "Wed, 30 Sep 2026 19:34:17 CEST"))
        }

        val payload = keys.decrypt(encryption(api).encrypt(cardData, CLIENT_ID))

        assertEquals(skewedDeviceSeconds, payload.iat)
        assertEquals(skewedDeviceSeconds + 600, payload.exp)
        assertEquals(listOf(CardEncryption.MISSING_DATE_WARNING), warnings)
    }

    @Test
    fun `missing credentials surface AUTH_SHAREABLE_KEY_MISSING, not an IllegalArgumentException`() {
        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking {
                CardEncryption(
                    publicApi = {
                        throw GopaySDKException(
                            errorCode = GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING,
                            message = "clientId and shareableKey must be set on GopayConfig"
                        )
                    },
                    deviceClockSeconds = { skewedDeviceSeconds }
                ).encrypt(cardData, null)
            }
        }

        assertEquals(GopayErrorCodes.AUTH_SHAREABLE_KEY_MISSING, e.errorCode)
    }

    @Test
    fun `an HTTP error on the key fetch keeps the client error code`() {
        val api = FakePublicApi {
            val body = """{"error":"unauthorized"}"""
                .toResponseBody("application/json".toMediaType())
            Response.error(401, body)
        }

        val e = assertThrows(GopaySDKException::class.java) {
            runBlocking { encryption(api).encrypt(cardData, CLIENT_ID) }
        }

        assertEquals(GopayErrorCodes.NETWORK_CLIENT_ERROR, e.errorCode)
        assertEquals(401, e.httpContext?.statusCode)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `a transport failure on the key fetch reaches the caller as before`() {
        val io = IOException("unreachable")
        val api = FakePublicApi { throw io }

        val e = assertThrows(IOException::class.java) {
            runBlocking { encryption(api).encrypt(cardData, CLIENT_ID) }
        }

        assertSame(io, e)
    }

    private fun encryption(api: PublicApi) =
        CardEncryption(publicApi = { api }, deviceClockSeconds = { skewedDeviceSeconds })

    /** Answers `GET /cards/public-key` with [key] and counts how often it was asked. */
    private class FakePublicApi(private val key: () -> Response<Jwk>) : PublicApi {
        @Volatile
        var keyRequests = 0

        override suspend fun getPublicKey(): Response<Jwk> {
            synchronized(this) { keyRequests++ }
            return key()
        }

        override suspend fun getCardFormUrl(): Response<CardFormUrl> =
            throw UnsupportedOperationException()

        override suspend fun getBrowserData(userAgent: String?): Response<BrowserDataDetected> =
            throw UnsupportedOperationException()
    }

    private companion object {
        const val CLIENT_ID = "merchant-client-001"
    }
}
