package cz.gopay.sdk.modules.network

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
