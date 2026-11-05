package cz.gopay.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity [this] belongs to, through however many wrappers; null outside one. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
