package com.gopay.sdk.util

/**
 * Base64 utility that works in both Android runtime and unit test environments
 */
object Base64Utils {
    
    /**
     * Decodes a Base64 URL-safe encoded string
     * 
     * @param input The Base64 URL-safe encoded string
     * @return The decoded byte array
     */
    fun decodeUrlSafe(input: String): ByteArray {
        return try {
            // Try Android's Base64 first (available in Android runtime)
            android.util.Base64.decode(input, android.util.Base64.URL_SAFE)
        } catch (e: Throwable) {
            // Fallback to Java's Base64 (available in unit tests and API 26+)
            // Using reflection to avoid compile-time dependency on API 26
            decodeUsingJavaBase64Decoder(input, e)
        }
    }

    private fun decodeUsingJavaBase64Decoder(input: String, e: Throwable): ByteArray = try {
        // Use reflection to access java.util.Base64 (available in unit tests and API 26+)
        val base64Class = Class.forName("java.util.Base64")
        val getUrlDecoderMethod = base64Class.getMethod("getUrlDecoder")
        val decoder = getUrlDecoderMethod.invoke(null)
        val decodeMethod = decoder.javaClass.getMethod("decode", String::class.java)
        decodeMethod.invoke(decoder, input) as ByteArray
    } catch (reflectionException: Throwable) {
        // If both fail, throw the original exception
        throw RuntimeException("Base64 decoding failed in both Android and Java environments", e)
    }

    /**
     * Encodes a string to Base64 URL-safe format without padding
     */
    fun encodeUrlSafe(input: String): String {
        return encodeUrlSafe(input.toByteArray())
    }

    /**
     * Encodes a byte array to Base64 URL-safe format without padding
     */
    fun encodeUrlSafe(input: ByteArray): String {
        return try {    
            android.util.Base64.encodeToString(input, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            // Fallback to Java's Base64 using reflection
            encodeUsingJavaBase64Encoder(input, e)
        }
    }
    
    private fun encodeUsingJavaBase64Encoder(input: ByteArray, e: Throwable): String = try {
        // Use reflection to access java.util.Base64 (available in unit tests and API 26+)
        val base64Class = Class.forName("java.util.Base64")
        val getUrlEncoderMethod = base64Class.getMethod("getUrlEncoder")
        val encoder = getUrlEncoderMethod.invoke(null)
        val withoutPaddingMethod = encoder.javaClass.getMethod("withoutPadding")
        val encoderWithoutPadding = withoutPaddingMethod.invoke(encoder)
        val encodeToStringMethod = encoderWithoutPadding.javaClass.getMethod("encodeToString", ByteArray::class.java)
        encodeToStringMethod.invoke(encoderWithoutPadding, input) as String
    } catch (reflectionException: Throwable) {
        // If both fail, throw the original exception
        throw RuntimeException("Base64 encoding failed in both Android and Java environments", e)
    }
} 