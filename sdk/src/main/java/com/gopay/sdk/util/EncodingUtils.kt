import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


object EncodingUtils {
    /**
     * Encodes the given string to a Base64-encoded string.
     *
     * @param data The string to encode.
     * @return The Base64-encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodeToBase64(data: String): String {
        return Base64.Default.encode(data.encodeToByteArray())
    }

    /**
     * Decodes the given Base64-encoded string back to a regular string.
     *
     * @param data The Base64-encoded string to decode.
     * @return The decoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeFromBase64(data: String): String {
        return String(Base64.Default.decode(data))
    }
}




