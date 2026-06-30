package cz.gopay.sdk.service

import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.CardJwePayload
import cz.gopay.sdk.model.JweHeader
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JsonUtils
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Service for creating JWE (JSON Web Encryption) payloads for GoPay card tokenization
 * Implements RFC 7516 JWE standard with RSA-OAEP-256 key encryption and A256GCM content encryption
 * Compatible with Nimbus JOSE+JWT library used by GoPay servers.
 *
 * The JWK is supplied at call time — fetched in-memory from `GET /cards/public-key` by
 * [cz.gopay.sdk.service.PublicKeyCache] — and never persisted by the SDK.
 */
class EncryptionService {

    companion object {
        // Use explicit OAEP parameters for RSA-OAEP-256 compatibility with Nimbus JOSE+JWT
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CEK_SIZE_BITS = 256
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128
        private const val TOKEN_VALIDITY_SECONDS = 600L
    }

    /**
     * Creates a JWE encrypted payload using the provided JWK. The merchant backend submits the
     * returned JWE to `POST /cards/tokens` with merchant credentials.
     */
    fun createJweEncryptedPayload(cardData: CardData, jwk: Jwk, clientId: String): String {
        val publicKey = jwkToPublicKey(jwk)

        val jweHeader = JweHeader(kid = jwk.kid)
        val headerJson = JsonUtils.toJson(jweHeader)
            ?: throw IllegalStateException("Failed to serialize JWE header")
        val encodedHeader = base64UrlEncode(headerJson.toByteArray(Charsets.UTF_8))

        val cek = generateContentEncryptionKey()
        val encryptedKey = encryptContentEncryptionKey(cek, publicKey)
        val iv = generateInitializationVector()

        val issuedAt = System.currentTimeMillis() / 1000L
        val payload = CardJwePayload(
            cardPan = cardData.cardPan,
            expMonth = cardData.expMonth,
            expYear = cardData.expYear,
            cvv = cardData.cvv,
            clientId = clientId,
            iat = issuedAt,
            exp = issuedAt + TOKEN_VALIDITY_SECONDS,
            jti = "android-${UUID.randomUUID()}"
        )
        val cardDataJson = JsonUtils.toJson(payload)
            ?: throw IllegalStateException("Failed to serialize card data")
        val (ciphertext, authTag) = encryptCardDataWithAAD(
            cardDataJson,
            cek,
            iv,
            encodedHeader.toByteArray(Charsets.UTF_8)
        )
        return createJweCompactSerialization(encodedHeader, encryptedKey, iv, ciphertext, authTag)
    }

    /**
     * Converts JWK to Java PublicKey for RSA encryption
     */
    private fun jwkToPublicKey(jwk: Jwk): PublicKey {
        try {
            // Decode base64url-encoded modulus and exponent
            val modulus = BigInteger(1, base64UrlDecode(jwk.n))
            val exponent = BigInteger(1, base64UrlDecode(jwk.e))
            
            val rsaPublicKeySpec = RSAPublicKeySpec(modulus, exponent)
            val keyFactory = KeyFactory.getInstance("RSA")
            
            return keyFactory.generatePublic(rsaPublicKeySpec)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to create public key from JWK", e)
        }
    }

    /**
     * Generates a random 256-bit Content Encryption Key for AES-GCM
     */
    private fun generateContentEncryptionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(CEK_SIZE_BITS)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the CEK using RSA-OAEP-256
     * Uses explicit OAEP parameters to ensure compatibility with Nimbus JOSE+JWT
     */
    private fun encryptContentEncryptionKey(cek: SecretKey, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        
        // Create OAEP parameter specification for RSA-OAEP-256
        val oaepParameterSpec = OAEPParameterSpec(
            "SHA-256",                    // Hash algorithm
            "MGF1",                       // Mask generation function
            MGF1ParameterSpec.SHA256,     // MGF1 parameter spec
            PSource.PSpecified.DEFAULT    // Encoding input P (empty)
        )
        
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParameterSpec)
        return cipher.doFinal(cek.encoded)
    }

    /**
     * Generates a random 96-bit initialization vector for AES-GCM
     */
    private fun generateInitializationVector(): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * Encrypts card data using AES-GCM with Additional Authenticated Data (AAD)
     * The encoded JWE header is used as AAD as per RFC 7516 Section 5.1
     * @return Pair of (ciphertext, authentication tag)
     */
    private fun encryptCardDataWithAAD(
        plaintext: String, 
        cek: SecretKey, 
        iv: ByteArray, 
        aad: ByteArray
    ): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, cek, gcmParameterSpec)
        
        // Set Additional Authenticated Data - this is crucial for JWE compatibility
        cipher.updateAAD(aad)
        
        val encryptedData = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Split encrypted data and authentication tag
        val ciphertext = encryptedData.sliceArray(0 until encryptedData.size - (GCM_TAG_SIZE_BITS / 8))
        val authTag = encryptedData.sliceArray(encryptedData.size - (GCM_TAG_SIZE_BITS / 8) until encryptedData.size)
        
        return Pair(ciphertext, authTag)
    }

    /**
     * Creates JWE compact serialization format
     * Format: BASE64URL(header).BASE64URL(encrypted_key).BASE64URL(iv).BASE64URL(ciphertext).BASE64URL(tag)
     */
    private fun createJweCompactSerialization(
        encodedHeader: String,
        encryptedKey: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        authTag: ByteArray
    ): String {
        val encodedEncryptedKey = base64UrlEncode(encryptedKey)
        val encodedIv = base64UrlEncode(iv)
        val encodedCiphertext = base64UrlEncode(ciphertext)
        val encodedAuthTag = base64UrlEncode(authTag)
        
        return "$encodedHeader.$encodedEncryptedKey.$encodedIv.$encodedCiphertext.$encodedAuthTag"
    }

    /**
     * Base64URL encode without padding
     */
    private fun base64UrlEncode(data: ByteArray): String {
        return Base64Utils.encodeUrlSafe(data)
    }

    /**
     * Base64URL decode
     */
    private fun base64UrlDecode(encoded: String): ByteArray {
        return Base64Utils.decodeUrlSafe(encoded)
    }
} 