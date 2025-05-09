package com.gopay.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Test

class EncodingUtilsTest {
    @Test
    fun testEncodeToBase64() {
        val input = "Hello world!"
        val expectedBase64 = "SGVsbG8gd29ybGQh"
        val actualBase64 = EncodingUtils.encodeToBase64(input)
        assertEquals(expectedBase64, actualBase64)
    }

    @Test
    fun testDecodeFromBase64() {
        val base64 = "SGVsbG8gd29ybGQh"
        val expectedInput = "Hello world!"
        val decoded = EncodingUtils.decodeFromBase64(base64)
        assertEquals(expectedInput, decoded)
    }

    @Test
    fun testEncodeAndDecodeBase64() {
        val original = "Kotlin Base64 Test!"
        val encoded = EncodingUtils.encodeToBase64(original)
        val decoded = EncodingUtils.decodeFromBase64(encoded)
        assertEquals(original, decoded)
    }
} 