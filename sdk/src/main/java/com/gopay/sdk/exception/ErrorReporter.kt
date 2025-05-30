package com.gopay.sdk.exception

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
 * This is used internally by GopaySDKException to report errors when they occur.
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
     * Reports an error to the configured callback.
     * This method is called automatically when GopaySDKException is created.
     * 
     * @param exception The exception to report
     */
    fun report(exception: GopaySDKException) {
        try {
            errorCallback?.onError(exception)
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