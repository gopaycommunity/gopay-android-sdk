package com.gopay.sdk.exception

/**
 * Unified exception class for all Gopay SDK errors.
 * Provides structured error codes, HTTP context, and optional error reporting.
 *
 * @param errorCode Structured error code (e.g., "AUTH_001", "NETWORK_002")
 * @param message Human-readable error message
 * @param cause Original exception that caused this error (if any)
 * @param httpContext HTTP-specific error context (status code, response body, etc.)
 * @param additionalData Additional context data for debugging
 */
class GopaySDKException(
    val errorCode: String,
    message: String,
    cause: Throwable? = null,
    val httpContext: HttpErrorContext? = null,
    val additionalData: Map<String, Any>? = null
) : Exception(message, cause) {

    init {
        // Report error via callback if configured
        ErrorReporter.report(this)
    }

    /**
     * Checks if this is an authentication-related error
     */
    fun isAuthenticationError(): Boolean = errorCode.startsWith("AUTH_")

    /**
     * Checks if this is a network-related error
     */
    fun isNetworkError(): Boolean = errorCode.startsWith("NETWORK_")

    /**
     * Checks if this is a configuration-related error
     */
    fun isConfigurationError(): Boolean = errorCode.startsWith("CONFIG_")

    /**
     * Checks if this is a payment-related error
     */
    fun isPaymentError(): Boolean = errorCode.startsWith("PAYMENT_")

    /**
     * Checks if this is a validation-related error
     */
    fun isValidationError(): Boolean = errorCode.startsWith("VALIDATION_")

    /**
     * Gets the HTTP status code if this is an HTTP error
     */
    fun getHttpStatusCode(): Int? = httpContext?.statusCode

    override fun toString(): String {
        val baseString = "GopaySDKException(errorCode='$errorCode', message='$message')"
        return if (httpContext != null) {
            "$baseString, httpContext=$httpContext"
        } else {
            baseString
        }
    }
}

/**
 * HTTP-specific error context information
 *
 * @param statusCode HTTP status code (e.g., 401, 500)
 * @param responseBody Response body content (truncated if too long)
 * @param requestUrl The URL that was requested
 * @param requestMethod HTTP method (GET, POST, etc.)
 */
data class HttpErrorContext(
    val statusCode: Int,
    val responseBody: String? = null,
    val requestUrl: String? = null,
    val requestMethod: String? = null
) {
    override fun toString(): String {
        return "HttpErrorContext(statusCode=$statusCode, requestMethod=$requestMethod, requestUrl=$requestUrl)"
    }
} 