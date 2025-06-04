package com.gopay.sdk.service

import com.gopay.sdk.model.CardData
import com.gopay.sdk.model.JweHeader
import com.gopay.sdk.model.Jwk
import com.gopay.sdk.storage.TokenStorage
import com.gopay.sdk.util.JsonUtils
import com.gopay.sdk.util.Base64Utils
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Service for creating JWE (JSON Web Encryption) payloads for GoPay card tokenization
 * Implements RFC 7516 JWE standard with RSA-OAEP-256 key encryption and A256GCM content encryption
 * Compatible with Nimbus JOSE+JWT library used by GoPay servers
 */
class TokenizationService(
    private val tokenStorage: TokenStorage
) {

    companion object {
        // Use explicit OAEP parameters for RSA-OAEP-256 compatibility with Nimbus JOSE+JWT
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val CEK_SIZE_BITS = 256
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128
    }

    /**
     * Creates a JWE encrypted payload for card data that can be used with GoPay's card tokenization API
     * 
     * @param cardData The card information to encrypt
     * @return JWE string in compact serialization format (5 base64url-encoded parts separated by dots)
     * @throws IllegalStateException if no public key is available in storage
     * @throws Exception for encryption or encoding errors
     */
    fun createJweEncryptedPayload(cardData: CardData): String {
        // 1. Get the public key from storage
        val publicKeyJson = tokenStorage.getPublicKey() 
            ?: throw IllegalStateException("No public key available. Please fetch the public key first.")
        
        // 2. Parse the JWK
        val jwk = parseJwk(publicKeyJson)
        
        // 3. Convert JWK to PublicKey
        val publicKey = jwkToPublicKey(jwk)
        
        // 4. Create JWE header
        val jweHeader = JweHeader(kid = jwk.kid)
        
        // 5. Serialize header to JSON and encode (needed for AAD)
        val headerJson = JsonUtils.toJson(jweHeader)
            ?: throw IllegalStateException("Failed to serialize JWE header")
        val encodedHeader = base64UrlEncode(headerJson.toByteArray(Charsets.UTF_8))
        
        // 6. Generate CEK (Content Encryption Key)
        val cek = generateContentEncryptionKey()
        
        // 7. Encrypt the CEK with RSA-OAEP-256
        val encryptedKey = encryptContentEncryptionKey(cek, publicKey)
        
        // 8. Generate IV for AES-GCM
        val iv = generateInitializationVector()
        
        // 9. Prepare the card data payload
        val cardDataJson = cardDataToJson(cardData)
        
        // 10. Encrypt the card data with AES-GCM using encoded header as AAD
        val (ciphertext, authTag) = encryptCardDataWithAAD(cardDataJson, cek, iv, encodedHeader.toByteArray(Charsets.UTF_8))
        
        // 11. Create the JWE compact serialization
        return createJweCompactSerialization(encodedHeader, encryptedKey, iv, ciphertext, authTag)
    }

    /**
     * Parses JWK JSON string into Jwk object
     */
    private fun parseJwk(publicKeyJson: String): Jwk {
        return JsonUtils.fromJson<Jwk>(publicKeyJson)
            ?: throw IllegalArgumentException("Invalid JWK format")
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
     * Converts card data to JSON string
     */
    private fun cardDataToJson(cardData: CardData): String {
        // Use JsonUtils for proper JSON serialization to avoid issues with special characters
        return JsonUtils.toJson(cardData)
            ?: throw IllegalStateException("Failed to serialize card data to JSON")
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