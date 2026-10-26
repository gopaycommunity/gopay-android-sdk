package cz.gopay.sdk.telemetry

import com.squareup.moshi.JsonReader
import okio.Buffer
import java.math.BigDecimal

/**
 * JSON read into plain values and written back the way a browser's `JSON.stringify` writes it,
 * so a body redacted here reads the same as one the browser SDK redacted.
 *
 * Read with Moshi's streaming reader into [Map] (insertion order kept), [List], [String],
 * [Boolean], [JsonNumber] and null. Written by hand rather than through an adapter: no reflection
 * for R8 to break, and the escaping and number format are the browser's, which is what the
 * length caps on `req_body` and `res_body` are counted in.
 */
internal object JsonText {

    /** A number as the text carried it; written back in the browser's shortest form. */
    class JsonNumber(val literal: String)

    /**
     * [text] as values. Throws on anything that is not exactly one JSON document, trailing
     * content included, like `JSON.parse`.
     */
    fun parse(text: String): Any? {
        val reader = JsonReader.of(Buffer().writeUtf8(text))
        val value = read(reader)
        require(reader.peek() == JsonReader.Token.END_DOCUMENT) { "Trailing content after JSON value" }
        return value
    }

    private fun read(reader: JsonReader): Any? = when (reader.peek()) {
        JsonReader.Token.BEGIN_OBJECT -> {
            val map = LinkedHashMap<String, Any?>()
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                // A repeated key keeps its first place and its last value, as in a browser.
                map[key] = read(reader)
            }
            reader.endObject()
            map
        }
        JsonReader.Token.BEGIN_ARRAY -> {
            val list = ArrayList<Any?>()
            reader.beginArray()
            while (reader.hasNext()) list.add(read(reader))
            reader.endArray()
            list
        }
        JsonReader.Token.STRING -> reader.nextString()
        JsonReader.Token.NUMBER -> JsonNumber(reader.nextString())
        JsonReader.Token.BOOLEAN -> reader.nextBoolean()
        JsonReader.Token.NULL -> reader.nextNull<Any>()
        else -> throw IllegalArgumentException("Unexpected JSON token ${reader.peek()}")
    }

    /** [value] as JSON text. Anything but the types [parse] produces, numbers and maps is a bug. */
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is JsonNumber -> out.append(numberText(value.literal))
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Not a JSON value: ${value.javaClass.name}")
        }
    }

    /**
     * `JSON.stringify`'s escaping: the quote, the backslash and control characters, and a lone
     * surrogate as `\uXXXX`, so a string cut in the middle of a pair still goes out as valid
     * UTF-8. Everything else, non-ASCII included, as it is.
     */
    private fun writeString(out: StringBuilder, text: String) {
        out.append('"')
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append(unicodeEscape(c))
                Character.isHighSurrogate(c) &&
                    i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    out.append(c).append(text[i + 1])
                    i++
                }
                Character.isSurrogate(c) -> out.append(unicodeEscape(c))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    private fun unicodeEscape(c: Char): String = "\\u" + c.code.toString(16).padStart(4, '0')

    /**
     * A number the way a browser prints one after `JSON.parse`: read as a double, written by
     * ECMAScript's Number::toString. `1.50` as `1.5`, `1e2` as `100`, `-0` as `0`, an integer past
     * 2^53 rounded as the browser rounds it, `1e-7` as `1e-7`, and anything too large for a
     * double as `null`, which is what `JSON.stringify` makes of an infinity.
     */
    private fun numberText(literal: String): String {
        val value = literal.toDoubleOrNull() ?: return "null"
        if (value.isInfinite() || value.isNaN()) return "null"
        if (value == 0.0) return "0"
        // The shortest digits that read back as the same double, and where the point goes.
        val decimal = BigDecimal(value.toString()).stripTrailingZeros()
        val digits = decimal.unscaledValue().abs().toString()
        val k = digits.length
        val n = k - decimal.scale()
        val sign = if (value < 0) "-" else ""
        val body = when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val exponent = n - 1
                val mantissa = if (k == 1) digits else digits[0] + "." + digits.substring(1)
                mantissa + "e" + (if (exponent >= 0) "+" else "-") + kotlin.math.abs(exponent)
            }
        }
        return sign + body
    }
}
