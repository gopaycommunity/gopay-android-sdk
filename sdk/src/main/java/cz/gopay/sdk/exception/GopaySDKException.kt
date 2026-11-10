package cz.gopay.sdk.exception

import cz.gopay.sdk.model.ChargePaymentResponse
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified exception class for all Gopay SDK errors.
 * Provides structured error codes, HTTP context, and optional error reporting.
 *
 * One the SDK throws reaches `GopayConfig.errorCallback` once, where it leaves the SDK; see
 * [ErrorReporter]. Constructing one reports nothing.
 *
 * @param code Structured error code (e.g., "AUTH_009", "NETWORK_002"), one of [GopayErrorCodes];
 *        `code` on iOS too.
 * @param message Human-readable error message
 * @param cause Original exception that caused this error (if any)
 * @param httpContext HTTP-specific error context (status code, response body, etc.)
 * @param additionalData Additional context data for debugging
 * @param chargeState The charge as [cz.gopay.sdk.session.PaymentSession.awaitChargeState] last
 *        read it, set on [GopayErrorCodes.PAYMENT_DECLINED] (the `FAILED` state, with its
 *        `failReason`) and [GopayErrorCodes.PAYMENT_TIMEOUT] (the undecided state the deadline
 *        read returned). Null on every other error. The iOS SDK exposes the same field.
 */
class GopaySDKException(
    val code: String,
    message: String,
    cause: Throwable? = null,
    val httpContext: HttpErrorContext? = null,
    val additionalData: Map<String, Any>? = null,
    val chargeState: ChargePaymentResponse? = null
) : Exception(message, cause) {

    /**
     * The 2.x constructor, with `errorCode` for [code]. A call that names `errorCode` lands here
     * and is told the new name; `cause` sits second, where the primary constructor takes the
     * message, so the two never compete for a call with positional arguments.
     */
    @Deprecated(
        "errorCode was renamed to code, the name the iOS SDK uses",
        ReplaceWith(
            "GopaySDKException(code = errorCode, message = message, cause = cause, " +
                "httpContext = httpContext, additionalData = additionalData, chargeState = chargeState)"
        )
    )
    constructor(
        errorCode: String,
        cause: Throwable? = null,
        message: String,
        httpContext: HttpErrorContext? = null,
        additionalData: Map<String, Any>? = null,
        chargeState: ChargePaymentResponse? = null
    ) : this(errorCode, message, cause, httpContext, additionalData, chargeState)

    /** Set by [ErrorReporter.report] the first time, so the callback hears of this one once. */
    internal val reported = AtomicBoolean(false)

    /**
     * Checks if this is an authentication-related error
     */
    fun isAuthenticationError(): Boolean = code.startsWith("AUTH_")

    /**
     * Checks if this is a network-related error
     */
    fun isNetworkError(): Boolean = code.startsWith("NETWORK_")

    /**
     * Checks if this is a configuration-related error
     */
    fun isConfigurationError(): Boolean = code.startsWith("CONFIG_")

    /**
     * Checks if this is a payment-related error
     */
    fun isPaymentError(): Boolean = code.startsWith("PAYMENT_")

    /**
     * Checks if this is a validation-related error
     */
    fun isValidationError(): Boolean = code.startsWith("VALIDATION_")

    /** The HTTP status the gateway answered with, when this is an HTTP error; `httpStatus` on iOS too. */
    val httpStatus: Int? get() = httpContext?.statusCode

    /** The 2.x name of [code]. */
    @Deprecated("Renamed to code, the name the iOS SDK uses", ReplaceWith("code"))
    val errorCode: String get() = code

    /** The 2.x name of [httpStatus]. */
    @Deprecated("Renamed to httpStatus, the name the iOS SDK uses", ReplaceWith("httpStatus"))
    fun getHttpStatusCode(): Int? = httpStatus

    override fun toString(): String = buildString {
        append("GopaySDKException(code='$code', message='$message')")
        if (httpContext != null) append(", httpContext=$httpContext")
        if (chargeState != null) append(", chargeState=$chargeState")
    }
}

/**
 * HTTP-specific error context information
 *
 * @param statusCode HTTP status code (e.g., 401, 500)
 * @param responseBody Response body content (truncated if too long)
 * @param requestUrl The URL that was requested
 * @param requestMethod HTTP method (GET, POST, etc.)
 * @param gatewayError What the gateway said about the failure, read from [responseBody] when it
 *        holds the gateway's error body; its `message` is part of the exception's message too.
 */
data class HttpErrorContext(
    val statusCode: Int,
    val responseBody: String? = null,
    val requestUrl: String? = null,
    val requestMethod: String? = null,
    val gatewayError: GopayGatewayError? = null
) {
    override fun toString(): String {
        return "HttpErrorContext(statusCode=$statusCode, requestMethod=$requestMethod, " +
            "requestUrl=$requestUrl, gatewayError=$gatewayError)"
    }
}

/**
 * The body the gateway answers a failed request with (`Error-Response-Body` in the GoPay API).
 * Every field is optional: the SDK reads what is there and does not fail on what is not. The iOS
 * SDK exposes the same three fields as `GopaySDKError.gatewayError`.
 *
 * @param error The error type, which follows the HTTP status, e.g. `BAD_REQUEST` or `FORBIDDEN`.
 *        The gateway has no finer business codes.
 * @param message A human-readable explanation from the gateway.
 * @param detail Further detail, e.g. which field of the request was wrong.
 */
data class GopayGatewayError(
    val error: String? = null,
    val message: String? = null,
    val detail: String? = null
)
