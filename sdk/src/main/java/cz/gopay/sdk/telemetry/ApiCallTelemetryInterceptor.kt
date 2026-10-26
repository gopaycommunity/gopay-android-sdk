package cz.gopay.sdk.telemetry

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/**
 * The one place every gateway request is reported from, as an `api_call`: its method, its path as
 * a template, its status or none, how long it took and both bodies, which [GwLoggerTelemetry]
 * redacts.
 *
 * Added last to each of the SDK's API clients, after the auth interceptors, so it sees each
 * request that really goes out (the retry after a 401 too) and never an SDK error raised before
 * one does. A request that ends without a response is reported with no status and the failure
 * passes on untouched; nothing here changes what the caller gets.
 *
 * Mirrors the iOS SDK's `api_call` reporting in its network client.
 *
 * @param basePath the encoded path of the gateway's base URL, cut from the front of every path.
 */
internal class ApiCallTelemetryInterceptor(
    private val telemetry: GwLoggerTelemetry,
    basePath: String
) : Interceptor {

    private val prefix: List<String> = basePath.trim('/').split('/').filter { it.isNotEmpty() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = telemetry.now()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            report(request, null, startedAt)
            throw e
        }
        report(request, response, startedAt)
        return response
    }

    private fun report(request: Request, response: Response?, startedAt: Long) {
        try {
            val durationMs = telemetry.msSince(startedAt) ?: 0
            val segments = relativeSegments(request.url)
            val template = template(segments)
            telemetry.apiCall(
                httpMethod = request.method,
                template = template,
                statusCode = response?.code,
                durationMs = durationMs,
                requestBody = requestBody(request),
                responseBody = response?.takeIf { template !in WITHOUT_RESPONSE_BODY }?.let { responseBody(it) },
                paymentId = paymentIdOf(segments)
            )
        } catch (e: Throwable) {
            // Logging is never worth a thrown error in a payment flow.
        }
    }

    /** The path's segments below the base URL, decoded. */
    private fun relativeSegments(url: HttpUrl): List<String> {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        return if (segments.take(prefix.size) == prefix) segments.drop(prefix.size) else segments
    }

    /** The id a payment-scoped path carries, `payments/{payment_id}/…`. */
    private fun paymentIdOf(segments: List<String>): String? =
        segments.takeIf { it.size >= 2 && it[0] == "payments" }?.get(1)

    /**
     * The body as it went out: a form as its fields, which is how the browser SDK hands its token
     * request over and what lets the key rules reach them, anything else as text.
     */
    private fun requestBody(request: Request): Any? {
        val body = request.body ?: return null
        if (body is FormBody) {
            return LinkedHashMap<String, Any?>().apply {
                for (i in 0 until body.size) put(body.name(i), body.value(i))
            }
        }
        if (body.isOneShot() || body.isDuplex()) return null
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8(minOf(buffer.size, MAX_BODY_BYTES))
    }

    /** Peeked, not read: the converter still gets the whole body. */
    private fun responseBody(response: Response): String =
        response.peekBody(MAX_BODY_BYTES).string()

    /**
     * `/payments/{id}/charge`: every all-digit or UUID segment as `{id}`, no query. Only those
     * two shapes, as in the browser SDK: every literal segment of the API is a word.
     */
    private fun template(segments: List<String>): String =
        "/" + segments.joinToString("/") { if (it.matches(ALL_DIGITS) || it.matches(UUID)) "{id}" else it }

    private companion object {
        /**
         * As much of a body as is read for the report: what the 16 KB event can carry, and enough
         * that the 2000-character cut is made after redaction rather than before it.
         */
        const val MAX_BODY_BYTES = 16L * 1024

        /**
         * Responses never reported: the token request's carries only the token, and the public
         * key's is 2000 characters of a key everyone can fetch. As on iOS.
         */
        val WITHOUT_RESPONSE_BODY = setOf("/oauth2/token", "/cards/public-key")

        val ALL_DIGITS = Regex("[0-9]+")
        val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", RegexOption.IGNORE_CASE)
    }
}
