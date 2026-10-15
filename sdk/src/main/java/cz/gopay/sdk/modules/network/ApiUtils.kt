package cz.gopay.sdk.modules.network

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.exception.HttpErrorContext
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Unwraps a Retrofit [Response]:
 * - on success, returns the body; a 2xx without one (a 204 or 205, which Retrofit hands over as
 *   `null`) is [GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR], as on iOS: the SDK expected a
 *   payload and got none;
 * - on HTTP error, throws [GopaySDKException] with [HttpErrorContext] populated from the
 *   request and error body: [GopayErrorCodes.NETWORK_SERVER_ERROR] for a 5xx status,
 *   [GopayErrorCodes.NETWORK_CLIENT_ERROR] for any other. The iOS SDK maps statuses the same way.
 *   The gateway's error body, when the response carries one, goes into
 *   [HttpErrorContext.gatewayError] and its `message` into the exception's, so "HTTP 403" says
 *   why.
 *
 * Used by [cz.gopay.sdk.session.PaymentSession] and [cz.gopay.sdk.service.CardEncryption] so
 * every API error in the SDK has the same shape.
 *
 * @param action short verb phrase used in error messages, e.g. `"charge payment"`.
 */
internal fun <T> Response<T>.unwrap(action: String): T {
    if (!isSuccessful) {
        val body = errorBody()?.string()
        val gatewayError = GatewayErrorBody.parse(body)
        throw GopaySDKException(
            errorCode = if (code() in 500..599) {
                GopayErrorCodes.NETWORK_SERVER_ERROR
            } else {
                GopayErrorCodes.NETWORK_CLIENT_ERROR
            },
            message = "Failed to $action: HTTP ${code()}" +
                GatewayErrorBody.messageSuffix(gatewayError),
            httpContext = HttpErrorContext(
                statusCode = code(),
                responseBody = body,
                requestUrl = raw().request.url.toString(),
                requestMethod = raw().request.method,
                gatewayError = gatewayError
            )
        )
    }
    return body() ?: throw GopaySDKException(
        errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
        message = "Empty response body from $action: HTTP ${code()}"
    )
}

/**
 * Runs an API [call] and [unwrap]s it, turning a body the SDK cannot read into a
 * [GopaySDKException].
 *
 * Retrofit decodes the body before [unwrap] ever runs, so a response that does not match the
 * model throws Moshi's [JsonDataException], and an empty or syntactically broken one, a captive
 * portal or a WAF answering 200 with an HTML page, throws [UnreadableBodyException] from
 * [ReadingConverterFactory]. Both arrive here as [GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR]
 * rather than as a transport failure.
 *
 * An [SdkIOException] from [SessionAuthInterceptor] is unwrapped back to the
 * [GopaySDKException] it carries.
 *
 * A transport failure, an [IOException] from OkHttp, is mapped by [transportError], with the
 * original as the cause. A coroutine cancellation is not an error and passes through untouched.
 *
 * Every call that reaches the gateway goes through here; [unwrap] on its own is only for a
 * caller that needs the [Response] first.
 *
 * @param action short verb phrase used in error messages, e.g. `"charge payment"`.
 */
internal suspend fun <T> apiCall(action: String, call: suspend () -> Response<T>): T =
    try {
        call().unwrap(action)
    } catch (e: SdkIOException) {
        // An SDK error raised inside an interceptor, wrapped only to get it out of OkHttp.
        throw e.sdkException
    } catch (e: JsonDataException) {
        throw GopaySDKException(
            errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
            message = "Malformed response from $action: ${e.message}",
            cause = e
        )
    } catch (e: JsonEncodingException) {
        // A broken body from a converter not wrapped in ReadingConverterFactory; every one the
        // SDK builds is, so this is a safety net. It extends IOException, so it has to come first.
        throw GopaySDKException(
            errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
            message = "Unreadable response from $action: ${e.message}",
            cause = e
        )
    } catch (e: UnreadableBodyException) {
        // An empty or broken body rather than a shape mismatch. It arrived whole, so it is not
        // the transport's failure, which the catch below is for.
        throw GopaySDKException(
            errorCode = GopayErrorCodes.INTERNAL_SERIALIZATION_ERROR,
            message = "Unreadable response from $action: ${e.message}",
            cause = e.cause
        )
    } catch (e: IOException) {
        throw transportError(action, e)
    }

/**
 * The SDK error for a transport failure, with [e] as its cause:
 * - a timeout is [GopayErrorCodes.NETWORK_TIMEOUT];
 * - no route to the gateway (offline, DNS, connection refused) is
 *   [GopayErrorCodes.NETWORK_NO_CONNECTION];
 * - the gateway's certificate failing the check, or its host name not matching, is
 *   [GopayErrorCodes.NETWORK_SSL_ERROR];
 * - anything else is [GopayErrorCodes.NETWORK_IO_ERROR], a TLS connection reset or dropped
 *   midway included: that is the network, not the certificate.
 *
 * The iOS SDK maps a `URLError` to the same codes, except that it reads any failed TLS handshake
 * (`secureConnectionFailed`) as [GopayErrorCodes.NETWORK_SSL_ERROR].
 */
internal fun transportError(action: String, e: IOException): GopaySDKException {
    val code = when (e) {
        is SocketTimeoutException -> GopayErrorCodes.NETWORK_TIMEOUT
        // OkHttp's call timeout reports itself this way rather than as a SocketTimeoutException.
        is InterruptedIOException -> if (e.message == "timeout") {
            GopayErrorCodes.NETWORK_TIMEOUT
        } else {
            GopayErrorCodes.NETWORK_IO_ERROR
        }
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            GopayErrorCodes.NETWORK_NO_CONNECTION
        is SSLPeerUnverifiedException -> GopayErrorCodes.NETWORK_SSL_ERROR
        is SSLHandshakeException -> if (e.hasCertificateCause()) {
            GopayErrorCodes.NETWORK_SSL_ERROR
        } else {
            GopayErrorCodes.NETWORK_IO_ERROR
        }
        else -> GopayErrorCodes.NETWORK_IO_ERROR
    }
    return GopaySDKException(
        errorCode = code,
        message = "Failed to $action: ${e.javaClass.simpleName}: ${e.message}",
        cause = e
    )
}

/** Whether a certificate check is somewhere in the chain of causes, not just the TLS layer. */
private fun Throwable.hasCertificateCause(): Boolean =
    generateSequence(cause) { it.cause }.take(10)
        .any { it is CertificateException || it is CertPathValidatorException }
