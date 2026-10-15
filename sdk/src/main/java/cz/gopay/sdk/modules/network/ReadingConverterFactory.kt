package cz.gopay.sdk.modules.network

import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Type

/**
 * Wraps the JSON converter so that a body the SDK cannot read is told apart from a transport
 * failure.
 *
 * Moshi reports an empty or broken body with an [IOException] (`EOFException`, Moshi's own
 * `JsonEncodingException`), the same type a connection dropped mid-body throws, and [apiCall] would
 * read both as a transport failure. Here the body is read in full first, where an [IOException]
 * is still the transport's, and only then decoded, where one is a body the SDK cannot read and
 * leaves as [UnreadableBodyException]. The iOS SDK has the bytes in hand before it decodes, so it
 * makes the same distinction.
 */
internal class ReadingConverterFactory(private val delegate: Converter.Factory) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val decode = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any?> { body ->
            val contentType = body.contentType()
            val bytes = body.use { it.bytes() }
            try {
                decode.convert(bytes.toResponseBody(contentType))
            } catch (e: IOException) {
                throw UnreadableBodyException(e)
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ) = delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
}

/**
 * A response body that arrived whole but could not be decoded, e.g. empty or not JSON. Not an
 * [IOException] on purpose, see [ReadingConverterFactory].
 */
internal class UnreadableBodyException(cause: IOException) :
    RuntimeException(cause.message ?: cause.javaClass.simpleName, cause)
