package cz.gopay.sdk.util

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

    /**
     * Builds a full `Authorization: Basic …` header value from a user/password pair, using the
     * standard-alphabet base64 encoding required by RFC 7617.
     */
    fun basicAuthHeader(user: String, secret: String): String =
        "Basic " + encodeBasicAuth("$user:$secret")

    /**
     * Encodes a string to standard Base64 with padding — the encoding required by HTTP Basic
     * authentication (RFC 7617). Use this for `Authorization: Basic …` headers; do not use the
     * URL-safe variant, which substitutes `-`/`_` for `+`/`/` and may be rejected by servers.
     */
    fun encodeBasicAuth(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        return try {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Throwable) {
            encodeStandardUsingJavaBase64(bytes, e)
        }
    }

    private fun encodeStandardUsingJavaBase64(input: ByteArray, e: Throwable): String = try {
        val base64Class = Class.forName("java.util.Base64")
        val getEncoderMethod = base64Class.getMethod("getEncoder")
        val encoder = getEncoderMethod.invoke(null)
        val encodeToStringMethod = encoder.javaClass.getMethod("encodeToString", ByteArray::class.java)
        encodeToStringMethod.invoke(encoder, input) as String
    } catch (reflectionException: Throwable) {
        throw RuntimeException("Standard Base64 encoding failed in both Android and Java environments", e)
    }
} 