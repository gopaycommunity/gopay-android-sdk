package cz.gopay.sdk.modules.network

import com.squareup.moshi.JsonReader
import cz.gopay.sdk.exception.GopayGatewayError
import okio.Buffer

/**
 * Reads the gateway's error body into a [GopayGatewayError].
 *
 * Read by hand rather than through a Moshi adapter: the result lives outside the model package
 * the R8 rules keep, and a reflective adapter would break in a minified host. Anything that is
 * not the gateway's error body, an HTML page from a proxy or an empty body, gives `null`, and a
 * field of another type is skipped rather than failing the rest.
 */
internal object GatewayErrorBody {

    fun parse(body: String?): GopayGatewayError? {
        if (body.isNullOrBlank()) return null
        return try {
            val reader = JsonReader.of(Buffer().writeUtf8(body))
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) return null
            var error: String? = null
            var message: String? = null
            var detail: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() != JsonReader.Token.STRING) {
                    reader.skipValue()
                    continue
                }
                when (name) {
                    "error" -> error = reader.nextString()
                    "message" -> message = reader.nextString()
                    "detail" -> detail = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (error == null && message == null && detail == null) null
            else GopayGatewayError(error = error, message = message, detail = detail)
        } catch (e: Exception) {
            null
        }
    }

    /** `": <message>"` from the gateway, trimmed, or nothing when it sent none or only blanks. */
    fun messageSuffix(gatewayError: GopayGatewayError?): String =
        gatewayError?.message?.trim()?.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
}
