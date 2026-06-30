package cz.gopay.sdk.service

import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.CardJwePayload
import cz.gopay.sdk.model.JweHeader
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies [EncryptionService] produces a spec-compliant RFC 7516 JWE (RSA-OAEP-256 + A256GCM).
 *
 * The service only encrypts, so these tests generate an RSA keypair, hand the service a JWK built
 * from the public half, and then decrypt the resulting JWE with the private half to assert the
 * full round-trip recovers the original [CardData]. Runs on the plain JVM provider — no Android.
 */
class EncryptionServiceTest {

    private lateinit var service: EncryptionService
    private lateinit var keyPair: KeyPair
    private lateinit var jwk: Jwk

    private val cardData = CardData(
        cardPan = "4532015112830366",
        expMonth = "12",
        expYear = "30",
        cvv = "123"
    )
    private val testClientId = "merchant-client-001"

    @Before
    fun setUp() {
        service = EncryptionService()
        keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = keyPair.public as RSAPublicKey
        jwk = Jwk(
            kty = "RSA",
            kid = "test-key-id-1",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = Base64Utils.encodeUrlSafe(pub.modulus.toByteArray()),
            e = Base64Utils.encodeUrlSafe(pub.publicExponent.toByteArray())
        )
    }

    @Test
    fun `createJweEncryptedPayload produces five compact segments`() {
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId)

        val parts = jwe.split(".")
        assertEquals("JWE compact serialization must have 5 segments", 5, parts.size)
        parts.forEachIndexed { i, p ->
            assertTrue("Segment $i must not be empty", p.isNotEmpty())
        }
    }

    @Test
    fun `JWE header declares the expected algorithms and kid`() {
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId)
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
        val jwe = service.createJweEncryptedPayload(cardData, jwk, testClientId)
        val parts = jwe.split(".")

        val encodedHeader = parts[0]
        val encryptedKey = Base64Utils.decodeUrlSafe(parts[1])
        val iv = Base64Utils.decodeUrlSafe(parts[2])
        val ciphertext = Base64Utils.decodeUrlSafe(parts[3])
        val authTag = Base64Utils.decodeUrlSafe(parts[4])

        // 1) Recover the content encryption key via RSA-OAEP-256.
        val cek = decryptCek(encryptedKey)
        assertEquals("CEK must be 256 bits", 32, cek.size)

        // 2) Decrypt the content with AES-256-GCM, using the encoded header as AAD (RFC 7516 §5.1).
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, iv))
        aes.updateAAD(encodedHeader.toByteArray(Charsets.UTF_8))
        val plaintext = String(aes.doFinal(ciphertext + authTag), Charsets.UTF_8)

        val decrypted = JsonUtils.fromJson<CardJwePayload>(plaintext)
        assertNotNull("Payload must deserialize", decrypted)
        assertEquals("card_pan", cardData.cardPan, decrypted!!.cardPan)
        assertEquals("exp_month", cardData.expMonth, decrypted.expMonth)
        assertEquals("exp_year", cardData.expYear, decrypted.expYear)
        assertEquals("cvv", cardData.cvv, decrypted.cvv)
        assertEquals("client_id", testClientId, decrypted.clientId)
        assertTrue("iat must be a recent Unix timestamp", decrypted.iat > 0)
        assertEquals("exp must be iat + 600s", decrypted.iat + 600L, decrypted.exp)
        assertTrue("jti must have android- prefix", decrypted.jti.startsWith("android-"))
    }

    @Test
    fun `each call uses fresh randomness so output differs`() {
        val first = service.createJweEncryptedPayload(cardData, jwk, testClientId)
        val second = service.createJweEncryptedPayload(cardData, jwk, testClientId)

        val firstParts = first.split(".")
        val secondParts = second.split(".")

        // Header is deterministic; encrypted key, IV and ciphertext must differ (random CEK + IV).
        assertEquals(firstParts[0], secondParts[0])
        assertNotEquals("Encrypted CEK should differ", firstParts[1], secondParts[1])
        assertNotEquals("IV should differ", firstParts[2], secondParts[2])
        assertNotEquals("Ciphertext should differ", firstParts[3], secondParts[3])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `malformed JWK modulus throws IllegalArgumentException`() {
        val badJwk = jwk.copy(n = "this is not valid base64url @@@")
        service.createJweEncryptedPayload(cardData, badJwk, testClientId)
    }

    private fun decryptCek(encryptedKey: ByteArray): ByteArray {
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaep = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        )
        rsa.init(Cipher.DECRYPT_MODE, keyPair.private as RSAPrivateKey, oaep)
        return rsa.doFinal(encryptedKey)
    }
}
