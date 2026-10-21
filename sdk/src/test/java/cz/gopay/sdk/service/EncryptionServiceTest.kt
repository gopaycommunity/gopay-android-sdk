package cz.gopay.sdk.service

import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.JweHeader
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JsonUtils
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies [EncryptionService] produces a spec-compliant RFC 7516 JWE (RSA-OAEP-256 + A256GCM).
 *
 * The service only encrypts, so these tests generate an RSA keypair, hand the service a JWK built
 * from the public half, and then decrypt the resulting JWE with the private half to assert the
 * full round-trip recovers the original [CardData]. Runs on the plain JVM provider — no Android.
 */
class EncryptionServiceTest {

    private lateinit var service: EncryptionService
    private lateinit var keys: TestJwe
    private lateinit var jwk: Jwk

    private val cardData = CardData(
        cardPan = "4532015112830366",
        expMonth = "12",
        expYear = "30",
        cvv = "123"
    )
    private val testClientId = "merchant-client-001"
    private val issuedAt = 1_790_789_657L

    @Before
    fun setUp() {
        service = EncryptionService()
        keys = TestJwe()
        jwk = keys.jwk
    }

    @Test
    fun `createJweEncryptedPayload produces five compact segments`() {
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId, issuedAt)

        val parts = jwe.split(".")
        assertEquals("JWE compact serialization must have 5 segments", 5, parts.size)
        parts.forEachIndexed { i, p ->
            assertTrue("Segment $i must not be empty", p.isNotEmpty())
        }
    }

    @Test
    fun `JWE header declares the expected algorithms and kid`() {
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId, issuedAt)
        val headerJson = String(Base64Utils.decodeUrlSafe(jwe.split(".")[0]), Charsets.UTF_8)

        val header = JsonUtils.fromJson<JweHeader>(headerJson)
        assertNotNull("Header must deserialize", header)
        assertEquals("RSA-OAEP-256", header!!.alg)
        assertEquals("A256GCM", header.enc)
        assertEquals("JWE", header.typ)
        assertEquals("test-key-id-1", header.kid)
    }

    @Test
    fun `JWE round-trips back to the original card data`() {
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId, issuedAt)

        val cek = keys.decryptCek(Base64Utils.decodeUrlSafe(jwe.split(".")[1]))
        assertEquals("CEK must be 256 bits", 32, cek.size)
        val decrypted = keys.decrypt(jwe)
        assertEquals("card_pan", cardData.cardPan, decrypted.cardPan)
        assertEquals("exp_month", cardData.expMonth, decrypted.expMonth)
        assertEquals("exp_year", cardData.expYear, decrypted.expYear)
        assertEquals("cvv", cardData.cvv, decrypted.cvv)
        assertEquals("client_id", testClientId, decrypted.clientId)
        assertEquals("iat must be the time it was given", issuedAt, decrypted.iat)
        assertEquals("exp must be iat + 600s", issuedAt + 600L, decrypted.exp)
        assertTrue("jti must have android- prefix", decrypted.jti.startsWith("android-"))
    }

    @Test
    fun `each call uses fresh randomness so output differs`() {
        val first = service.createJweEncryptedPayload(cardData, jwk, testClientId, issuedAt)
        val second = service.createJweEncryptedPayload(cardData, jwk, testClientId, issuedAt)

        val firstParts = first.split(".")
        val secondParts = second.split(".")

        // Header is deterministic; encrypted key, IV and ciphertext must differ (random CEK + IV).
        assertEquals(firstParts[0], secondParts[0])
        assertNotEquals("Encrypted CEK should differ", firstParts[1], secondParts[1])
        assertNotEquals("IV should differ", firstParts[2], secondParts[2])
        assertNotEquals("Ciphertext should differ", firstParts[3], secondParts[3])
    }

    @Test
    fun `a malformed JWK modulus throws INTERNAL_001 with the cause, as on iOS`() {
        val badJwk = jwk.copy(n = "not-base64url-!!!")

        val e = assertThrows(GopaySDKException::class.java) {
            service.createJweEncryptedPayload(cardData, badJwk, testClientId, issuedAt)
        }

        assertEquals(GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR, e.errorCode)
        assertTrue(e.message!!.startsWith("Card encryption failed: the public key could not be built"))
        assertNotNull("the original failure is the cause", e.cause)
    }

    @Test
    fun `a key the cipher refuses throws INTERNAL_001 with the cause`() {
        // A 512-bit key is too short for RSA-OAEP-256 to wrap a 256-bit CEK, so the cipher
        // itself fails rather than the JWK parsing. That failure must leave as the shared code.
        val weakJwk = TestJwe(keySize = 512).jwk

        val e = assertThrows(GopaySDKException::class.java) {
            service.createJweEncryptedPayload(cardData, weakJwk, testClientId, issuedAt)
        }

        assertEquals(GopayErrorCodes.INTERNAL_UNEXPECTED_ERROR, e.errorCode)
        assertTrue(e.message!!.startsWith("Card encryption failed: the JWE could not be built"))
        assertNotNull(e.cause)
    }
}
