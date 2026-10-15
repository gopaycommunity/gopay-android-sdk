package cz.gopay.sdk.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.window.DialogWindowProvider
import cz.gopay.sdk.util.SdkLog
import java.util.WeakHashMap

/**
 * Keeps the window that shows the card form out of screenshots and screen recordings with
 * `FLAG_SECURE`, for as long as the form is there.
 *
 * The flag goes on the window the form is actually drawn in, which is not always the activity's:
 * - a Compose `Dialog`, and Material3's `ModalBottomSheet` from 1.3 on, draw in a dialog window
 *   whose context is a `ContextThemeWrapper`, not the activity;
 * - Material3's `ModalBottomSheet` up to 1.2 draws in a window of its own added through the
 *   `WindowManager`, which took its flags from the activity once, when it was created.
 * Setting the flag on the activity alone leaves the sheet, card number and all, in the
 * screenshot. Setting it on the sheet alone does not last either: Material3's sheet, from 1.3 on,
 * copies the flag from the window that owns it every time it recomposes, as its default
 * `SecureFlagPolicy.Inherit` says, and so takes it off again. A form in a window of its own
 * therefore secures that window and the activity window that owns it; the sheet then inherits the
 * flag rather than dropping it. A host that sets `SecureFlagPolicy.SecureOff` on the sheet still
 * wins there.
 *
 * One nesting is not covered: a sheet or dialog opened from another dialog. The owner secured here
 * is always the activity, found through the context, but such a sheet inherits the flag from the
 * dialog under it, which stays unsecured, so Material3 1.3 takes the flag off the sheet again at
 * its next recomposition. A host that nests the form like that sets `SecureFlagPolicy.SecureOn` on
 * the sheet, or `FLAG_SECURE` on the dialog under it, itself.
 *
 * The flag is taken off again when the last form in that window leaves, unless the window had it
 * before the form came, in which case it is the host's and stays.
 */
internal object SecureWindow {

    /** Undoes one [protect]. */
    fun interface Release {
        fun release()
    }

    private val holds = SecureFlagHolds()

    /**
     * Whether the form should protect its window at all: not in a debuggable build of the host,
     * so screenshots of the form keep working while it is being developed and tested.
     */
    fun shouldProtect(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0

    /** Sets `FLAG_SECURE` on the window that hosts [view] until the returned [Release]. */
    fun protect(view: View): Release {
        val targets = targetsFor(view)
        if (targets.isEmpty()) {
            SdkLog.w(
                "The card form could not find the window it is shown in, so it cannot keep " +
                    "the card data out of screenshots"
            )
            return Release { }
        }
        targets.forEach(holds::acquire)
        return Release { targets.forEach(holds::release) }
    }

    /**
     * The window [view] is drawn in and, when that is a dialog or a popup over an activity, the
     * activity's window as well, which the window on top inherits the flag from.
     */
    private fun targetsFor(view: View): List<SecureFlagHolds.Target> {
        val root = view.rootView
        val activityWindow = activityOf(view.context)?.window
        val own = dialogWindowOf(view)?.let { WindowTarget(it) }
            ?: activityWindow?.takeIf { it.decorView === root }?.let { WindowTarget(it) }
            ?: (root.layoutParams as? WindowManager.LayoutParams)?.let { RootViewTarget(root) }
            ?: return emptyList()
        val owner = activityWindow?.takeIf { it !== own.key }?.let { WindowTarget(it) }
        return listOfNotNull(own, owner)
    }

    /** The dialog window around [view], when it sits in one Compose knows about. */
    private fun dialogWindowOf(view: View): Window? {
        var parent = view.parent
        while (parent != null) {
            if (parent is DialogWindowProvider) return parent.window
            parent = parent.parent
        }
        return null
    }

    private tailrec fun activityOf(context: Context): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> activityOf(context.baseContext)
        else -> null
    }

    /** An activity or dialog window, changed through [Window] so its own attributes stay right. */
    private class WindowTarget(private val window: Window) : SecureFlagHolds.Target {
        override val key: Any get() = window
        override val isSecure: Boolean
            get() = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

        override fun applySecure(secure: Boolean) {
            if (secure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    /**
     * A window without a [Window] object, such as a popup added straight to the `WindowManager`:
     * its flags live in the root view's layout params.
     */
    private class RootViewTarget(private val root: View) : SecureFlagHolds.Target {
        override val key: Any get() = root
        private val params get() = root.layoutParams as? WindowManager.LayoutParams
        override val isSecure: Boolean
            get() = ((params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0

        override fun applySecure(secure: Boolean) {
            val params = params ?: return
            if (!root.isAttachedToWindow) return
            params.flags = if (secure) {
                params.flags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            }
            val windowManager =
                root.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.updateViewLayout(root, params)
        }
    }
}

/**
 * Who set `FLAG_SECURE` on which window, so that it comes off only when the last form leaves and
 * never when the window had it before. Kept apart from [SecureWindow] so it can be tested without
 * the Android runtime.
 */
internal class SecureFlagHolds {

    interface Target {
        /** The window itself, the same for every form in it. */
        val key: Any
        val isSecure: Boolean
        fun applySecure(secure: Boolean)
    }

    private class Hold(var count: Int, val setBySdk: Boolean)

    private val holds = WeakHashMap<Any, Hold>()

    @Synchronized
    fun acquire(target: Target) {
        val hold = holds[target.key]
        if (hold != null) {
            hold.count++
            return
        }
        val setBySdk = !target.isSecure
        if (setBySdk) target.applySecure(true)
        holds[target.key] = Hold(count = 1, setBySdk = setBySdk)
    }

    @Synchronized
    fun release(target: Target) {
        val hold = holds[target.key] ?: return
        if (--hold.count > 0) return
        holds.remove(target.key)
        if (hold.setBySdk) target.applySecure(false)
    }
}
