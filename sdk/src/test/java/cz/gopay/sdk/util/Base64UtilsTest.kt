package cz.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Exercises [Base64Utils]. In unit tests `android.util.Base64` is a non-functional stub, so every
 * method here drives the java.util.Base64 reflection fallback — which is exactly the path that runs
 * on real devices below API 26's direct support and the one most likely to regress silently.
 */
class Base64UtilsTest {

    @Test
    fun `encodeUrlSafe of a string round-trips through decodeUrlSafe`() {
        val original = "Hello, GoPay! +/="

        val encoded = Base64Utils.encodeUrlSafe(original)
        val decoded = String(Base64Utils.decodeUrlSafe(encoded), Charsets.UTF_8)

        assertEquals(original, decoded)
    }

    @Test
    fun `encodeUrlSafe produces url-safe alphabet without padding`() {
        // Bytes chosen so standard base64 would yield '+' , '/' and '=' padding.
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte(), 0x00)

        val encoded = Base64Utils.encodeUrlSafe(bytes)

        assertFalse("must not contain '+'", encoded.contains('+'))
        assertFalse("must not contain '/'", encoded.contains('/'))
        assertFalse("must not be padded", encoded.contains('='))
    }

    @Test
    fun `decodeUrlSafe reverses a known url-safe encoding`() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte())
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        assertArrayEquals(bytes, Base64Utils.decodeUrlSafe(encoded))
    }

    @Test
    fun `encodeBasicAuth uses standard padded base64`() {
        val encoded = Base64Utils.encodeBasicAuth("user:secret")

        // Standard alphabet, padded — must match java.util.Base64 standard encoder exactly.
        val expected = Base64.getEncoder().encodeToString("user:secret".toByteArray(Charsets.UTF_8))
        assertEquals(expected, encoded)
    }

    @Test
    fun `basicAuthHeader builds an RFC 7617 Authorization value`() {
        val header = Base64Utils.basicAuthHeader("aladdin", "opensesame")

        assertTrue(header.startsWith("Basic "))
        val credentials = header.removePrefix("Basic ")
        val decoded = String(Base64.getDecoder().decode(credentials), Charsets.UTF_8)
        assertEquals("aladdin:opensesame", decoded)
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        org.junit.Assert.assertArrayEquals(expected, actual)
    }
}
