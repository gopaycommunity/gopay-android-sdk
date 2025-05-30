package com.gopay.sdk.internal

import android.app.Application
import android.content.Context
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.exception.GopayErrorCodes

/**
 * Internal utility for managing Application context in GopaySDK.
 * This ensures we always have access to Application context for token storage
 * and other SDK features without requiring manual context passing.
 */
internal object GopayContextProvider {
    
    @Volatile
    private var applicationContext: Context? = null
    
    /**
     * Sets the Application context. Called automatically by GopayInitProvider.
     * 
     * @param context The Application context
     */
    internal fun setApplicationContext(context: Context) {
        applicationContext = context.applicationContext
    }
    
    /**
     * Gets the Application context with fallback mechanisms.
     * 
     * @return Application context
     * @throws IllegalStateException if context cannot be obtained
     */
    internal fun getApplicationContext(): Context {
        // First try: Use the context set by GopayInitProvider
        applicationContext?.let { return it }
        
        // Fallback: Try to get context via reflection (ActivityThread.currentApplication)
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            val application = currentApplicationMethod.invoke(null) as? Application
            
            application?.let { app ->
                val context = app.applicationContext
                setApplicationContext(context) // Cache it for future use
                return context
            }
        } catch (e: Exception) {
            // Reflection failed, continue to error
        }
        
        // If we get here, we couldn't obtain context
        throw GopaySDKException(
            errorCode = GopayErrorCodes.CONFIG_MISSING_CONTEXT,
            message = "Cannot obtain Application context. This usually means:\n" +
            "1. GopaySDK is being used in a library module without proper initialization\n" +
            "2. The app's manifest is missing the GopayInitProvider\n" +
            "3. You're calling SDK methods before Application.onCreate()\n\n" +
            "If you're using GopaySDK in a library, consider calling GopaySDK.initialize(config, context) manually."
        )
    }
    
    /**
     * Checks if Application context is available.
     * 
     * @return true if context is available, false otherwise
     */
    internal fun isContextAvailable(): Boolean {
        return try {
            getApplicationContext()
            true
        } catch (e: GopaySDKException) {
            false
        }
    }
    
    /**
     * Clears the stored context. Used primarily for testing.
     */
    internal fun clearContext() {
        applicationContext = null
    }
} 