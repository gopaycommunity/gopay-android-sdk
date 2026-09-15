package cz.gopay.sdk.modules.network

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import retrofit2.Response

/**
 * Unwraps a Retrofit [Response]:
 * - on success, returns the body or throws if it's null;
 * - on HTTP error, throws [GopaySDKException] with [HttpErrorContext] populated from the
 *   request and error body.
 *
 * Used by [cz.gopay.sdk.session.PaymentSession] and [cz.gopay.sdk.service.PublicKeyCache] so
 * every API error in the SDK has the same shape.
 *
 * @param action short verb phrase used in error messages, e.g. `"charge payment"`.
 */
internal fun <T> Response<T>.unwrap(action: String): T {
    if (!isSuccessful) {
        throw GopaySDKException(
            errorCode = GopayErrorCodes.NETWORK_CLIENT_ERROR,
            message = "Failed to $action: HTTP ${code()}",
            httpContext = HttpErrorContext(
                statusCode = code(),
                responseBody = errorBody()?.string(),
                requestUrl = raw().request.url.toString(),
                requestMethod = raw().request.method
            )
        )
    }
    return body() ?: throw GopaySDKException(
        errorCode = GopayErrorCodes.AUTH_INVALID_RESPONSE,
        message = "Empty response body from $action"
    )
}

/**
 * Runs an API [call] and [unwrap]s it, turning a body the SDK cannot read into a
 * [GopaySDKException].
 *
 * Retrofit decodes the body before [unwrap] ever runs, so a response that does not match the
 * model throws Moshi's [JsonDataException], and a syntactically broken one throws
 * [JsonEncodingException], straight past the SDK's error contract and into the host's
 * `catch (e: GopaySDKException)`, where neither is caught. Both arrive here as
 * [GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR] instead.
 *
 * Transport failures are not covered: an [java.io.IOException] from OkHttp still reaches the
 * caller bare, although [GopayErrorCodes.NETWORK_NO_CONNECTION] and
 * [GopayErrorCodes.NETWORK_IO_ERROR] exist for it. That mapping is tracked separately.
 *
 * Every call that reaches the gateway goes through here; [unwrap] on its own is only for a
 * caller that needs the [Response] first.
 *
 * @param action short verb phrase used in error messages, e.g. `"charge payment"`.
 */
internal suspend fun <T> apiCall(action: String, call: suspend () -> Response<T>): T =
    try {
        call().unwrap(action)
    } catch (e: JsonDataException) {
        throw GopaySDKException(
            errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
            message = "Malformed response from $action: ${e.message}",
            cause = e
        )
    } catch (e: JsonEncodingException) {
        // Syntactically broken body rather than a shape mismatch, e.g. a captive portal or a WAF
        // answering 200 with an HTML page. It extends IOException, not JsonDataException, so the
        // catch above never saw it and the host got a bare exception.
        throw GopaySDKException(
            errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
            message = "Unreadable response from $action: ${e.message}",
            cause = e
        )
    }
