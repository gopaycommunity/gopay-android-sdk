package com.gopay.sdk.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * ContentProvider that automatically initializes GopaySDK with Application context.
 * This runs automatically when the app starts, before any Activities are created.
 * 
 * This approach is used by Firebase, Glide, and other modern Android SDKs to
 * automatically obtain Application context without requiring manual initialization.
 */
internal class GopayInitProvider : ContentProvider() {
    
    override fun onCreate(): Boolean {
        // This runs automatically when the app starts
        // context here is guaranteed to be the Application context
        context?.let { appContext ->
            GopayContextProvider.setApplicationContext(appContext.applicationContext)
        }
        return true
    }
    
    // Required ContentProvider methods (not used, but must be implemented)
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null
    
    override fun getType(uri: Uri): String? = null
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
} 