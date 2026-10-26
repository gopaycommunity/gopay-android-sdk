package cz.gopay.sdk.exception

import cz.gopay.sdk.telemetry.Telemetry

/**
 * Callback interface for error reporting.
 * Implement this interface to receive error notifications and integrate with your analytics system.
 */
fun interface ErrorCallback {
    /**
     * Called when an error occurs in the SDK.
     *
     * @param exception The GopaySDKException that occurred
     */
    fun onError(exception: GopaySDKException)
}

/**
 * Internal error reporter that manages error callbacks.
 *
 * The rule, the same on iOS: every error the SDK throws reaches the callback once, where it
 * leaves the SDK, except [GopayErrorCodes.PAYMENT_CANCELLED] and
 * [GopayErrorCodes.PAYMENT_TIMEOUT], which are not errors, and a coroutine cancellation, which
 * is not a [GopaySDKException]. An error the SDK only returns, such as the one inside
 * `CardEncryptionResult.Error`, is reported the same way. An error wrapped into another on the
 * way out, the gateway's 401 behind `AUTH_010` say, is reported as the one the host gets, not
 * as both. Building a [GopaySDKException] reports nothing: a host constructing one for itself
 * does not hear about it.
 *
 * The public entry points of the SDK run their body through [reportErrors], and an exception
 * remembers that it was reported, so an entry point calling another, `chargeWithCardToken`
 * calling `charge`, still reports once. `CONFIG_001` is thrown before `initialize`, when no
 * callback is installed yet, so it reaches none; a `GopaySDKException` a constructor throws,
 * `Environment.DEVELOPMENT` with a bad URL, is not a call of the SDK and reaches none either.
 */
internal object ErrorReporter {
    
    @Volatile
    private var errorCallback: ErrorCallback? = null
    
    /**
     * Sets the error callback for reporting.
     * 
     * @param callback The callback to receive error notifications, or null to disable reporting
     */
    fun setErrorCallback(callback: ErrorCallback?) {
        errorCallback = callback
    }
    
    /**
     * Reports an error to the configured callback, once per exception: a second call with the
     * same instance, from an outer entry point the error passes through, reaches nothing.
     * 
     * @param exception The exception to report
     * @param paymentId the payment the error belongs to, when it comes from a payment session
     */
    fun report(exception: GopaySDKException, paymentId: String? = null) =
        reportTo(errorCallback, exception, paymentId)

    /**
     * [report] to [callback] rather than the installed one: `GopaySDK.initialize` uses it for a
     * config it could not install, whose own callback still hears why.
     *
     * The same once goes to the operational data, as a `js_event` with the error's code, under
     * [paymentId] when the error has one; see `GwLoggerTelemetry.error`.
     */
    fun reportTo(callback: ErrorCallback?, exception: GopaySDKException, paymentId: String? = null) {
        // A payment window that closed before it finished is not an error; see PAYMENT_CANCELLED.
        // Neither is a charge the gateway has not decided yet; see PAYMENT_TIMEOUT.
        if (exception.errorCode == GopayErrorCodes.PAYMENT_CANCELLED ||
            exception.errorCode == GopayErrorCodes.PAYMENT_TIMEOUT
        ) {
            return
        }
        if (!exception.reported.compareAndSet(false, true)) return
        Telemetry.current?.error(exception, paymentId)
        try {
            callback?.onError(exception)
        } catch (e: Exception) {
            // Silently ignore callback errors to prevent infinite loops
            // In a production environment, you might want to log this to a fallback system
        }
    }
    
    /**
     * Checks if error reporting is currently enabled.
     * 
     * @return true if a callback is configured, false otherwise
     */
    fun isEnabled(): Boolean = errorCallback != null
    
    /**
     * Clears the error callback, disabling error reporting.
     */
    fun clear() {
        errorCallback = null
    }
}

/**
 * Runs [block], the body of a public SDK entry point, and reports the [GopaySDKException] it
 * throws through [ErrorReporter.report] on the way out, unchanged, under [paymentId] when the
 * entry point belongs to a payment session. Anything else, a coroutine cancellation above all,
 * passes through untouched. Inline, so a `suspend` body can be wrapped.
 */
internal inline fun <T> reportErrors(paymentId: String? = null, block: () -> T): T = try {
    block()
} catch (e: GopaySDKException) {
    ErrorReporter.report(e, paymentId)
    throw e
}
