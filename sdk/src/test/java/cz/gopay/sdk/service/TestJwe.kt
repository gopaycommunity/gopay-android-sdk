package cz.gopay.sdk.service

import cz.gopay.sdk.model.CardJwePayload
import cz.gopay.sdk.model.Jwk
import cz.gopay.sdk.util.Base64Utils
import cz.gopay.sdk.util.JsonUtils
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/** A throwaway RSA keypair standing in for the merchant key; [jwk] is what the SDK gets. */
internal class TestJwe {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    val jwk: Jwk = (keyPair.public as RSAPublicKey).let { pub ->
        Jwk(
            kty = "RSA",
            kid = "test-key-id-1",
            use = "enc",
            alg = "RSA-OAEP-256",
            n = Base64Utils.encodeUrlSafe(pub.modulus.toByteArray()),
            e = Base64Utils.encodeUrlSafe(pub.publicExponent.toByteArray())
        )
    }

    /** Recovers the content encryption key with RSA-OAEP-256 and the private half. */
    fun decryptCek(encryptedKey: ByteArray): ByteArray {
        val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val oaep = OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
        )
        rsa.init(Cipher.DECRYPT_MODE, keyPair.private, oaep)
        return rsa.doFinal(encryptedKey)
    }

    /**
     * Opens [jwe] the way the gateway would: RSA-OAEP-256 for the content key, then AES-256-GCM
     * with the encoded header as AAD (RFC 7516 §5.1), and reads the claims.
     */
    fun decrypt(jwe: String): CardJwePayload {
        val parts = jwe.split(".")
        val cek = decryptCek(Base64Utils.decodeUrlSafe(parts[1]))
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64Utils.decodeUrlSafe(parts[2])
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, iv))
        aes.updateAAD(parts[0].toByteArray(Charsets.UTF_8))
        val plaintext = String(
            aes.doFinal(Base64Utils.decodeUrlSafe(parts[3]) + Base64Utils.decodeUrlSafe(parts[4])),
            Charsets.UTF_8
        )
        return requireNotNull(JsonUtils.fromJson<CardJwePayload>(plaintext)) {
            "Payload must deserialize"
        }
    }
}
