package cz.gopay.sdk.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * The host's screen in front, which the SDK opens the 3DS challenge and the Google Pay sheet
 * from, and the application Context `GopaySDK.initialize(context, config)` was given. The iOS SDK
 * finds its presenting view controller the same way. An Activity a call names is used while it
 * is the one in front; in the background or gone, it gives way to the screen that is.
 *
 * A screen is in front from its `onResume` to its `onPause`, as [ActivityWatch] reports them.
 * Only weak references are kept, so nothing here keeps an activity alive, and an activity that
 * is finishing or destroyed is never handed out. The SDK's own screens do not count: the
 * challenge and the wallet launcher sit on top of the host's screen, they are not it. Nothing is
 * stored outside the process: a new process starts without a screen and learns it from the first
 * `onResume` after `initialize`.
 */
internal object ForegroundScreen {

    /** The application Context from `initialize(context, config)`; null after `initialize(config)`. */
    @Volatile
    var appContext: Context? = null
        private set

    private val lock = Any()

    /** The host's resumed activities, the latest last; more than one in multi-window. */
    private val resumed = ArrayList<WeakReference<Activity>>()

    /** Every activity the watch has reported, resumed or paused; weakly held. */
    private val seen: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    /** Bumped whenever [resumed] changes, for [await]. */
    private val changes = MutableStateFlow(0L)

    /**
     * Runs a block once the callback the main thread is in has returned. Replaced in unit tests,
     * which have no main looper.
     */
    @VisibleForTesting
    @Volatile
    var afterCurrentCallback: (() -> Unit) -> Unit = { block ->
        Handler(Looper.getMainLooper()).post(block)
    }

    /**
     * Takes the Context of `GopaySDK.initialize(context, config)`: keeps its application Context,
     * starts [ActivityWatch], and, when [context] is itself an activity that is resumed, takes
     * it as the screen in front right away, since its `onResume` has already passed.
     *
     * An activity that calls `initialize` from its own `onResume` is not resumed yet as far as
     * it can be asked, and the callback that would report it has already run. It is asked again
     * once that `onResume` has returned, and taken then.
     */
    fun install(context: Context) {
        val application = context.applicationContext ?: context
        appContext = application
        (application as? Application)?.let(ActivityWatch::ensure)
        val activity = context.findActivity()
        when {
            activity == null -> warnIfInitializedLate()
            isResumed(activity) -> resumed(activity)
            else -> runCatching {
                afterCurrentCallback {
                    if (isResumed(activity)) resumed(activity)
                    warnIfInitializedLate()
                }
            }.onFailure { warnIfInitializedLate() }
        }
    }

    /** The SDK was initialized again without a Context: calls that need one say so. */
    fun uninstall() {
        appContext = null
    }

    /**
     * Whether `initialize` came after a screen was already up and did not learn it: the app has
     * activities, and none is known to be in front. Then the SDK learns the screen only at its
     * next `onResume`, and a call that opens a challenge waits until then.
     */
    fun missedTheScreenInFront(): Boolean {
        if (current() != null) return false
        val manager = appContext?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return runCatching { manager?.appTasks.orEmpty().any { it.taskInfo.numActivities > 0 } }
            .getOrDefault(false)
    }

    fun resumed(activity: Activity) {
        if (isSdkScreen(activity)) return
        synchronized(lock) {
            seen.add(activity)
            resumed.removeAll { it.get().let { known -> known == null || known === activity } }
            resumed.add(WeakReference(activity))
        }
        changes.update { it + 1 }
    }

    /** [activity] paused or was destroyed: it is not in front any more. */
    fun paused(activity: Activity) {
        val removed = synchronized(lock) {
            if (!isSdkScreen(activity)) seen.add(activity)
            resumed.removeAll { it.get().let { known -> known == null || known === activity } }
        }
        if (removed) changes.update { it + 1 }
    }

    /** The host's screen in front right now, the one resumed last; null when there is none. */
    fun current(): Activity? = synchronized(lock) {
        resumed.asReversed().firstNotNullOfOrNull { it.get()?.takeIf(::isAlive) }
    }

    /**
     * The screen to open from: [named], the Activity the call was given, while it is in front,
     * otherwise the one that is; null when there is neither. An Activity in the background
     * cannot open a screen: the system drops the start without a word.
     */
    fun screenFor(named: Activity?): Activity? = named?.takeIf(::isInFront) ?: current()

    /** Suspends until there is a screen to open from, [screenFor]; cancellable. */
    suspend fun await(named: Activity? = null): Activity =
        changes.mapNotNull { screenFor(named) }.first()

    /**
     * Refuses a call that names no usable Activity when the SDK has no Context to find one with:
     * it was initialized through the deprecated `initialize(config)` only.
     *
     * A call that does name one starts [ActivityWatch] through it, whichever way the SDK was
     * initialized. Without that, nothing would say when [named] comes back to the front or is
     * replaced, after a rotation say, and a call waiting for a screen would wait for good.
     */
    fun requireContextOr(named: Activity?, call: String) {
        val usable = named?.takeIf(::isAlive)
        if (usable == null && appContext == null) throw missingContext(call)
        (usable?.applicationContext as? Application)?.let(ActivityWatch::ensure)
    }

    fun missingContext(call: String) = GopaySDKException(
        code = GopayErrorCodes.CONFIG_MISSING_CONTEXT,
        message = "$call needs the Context the SDK was not given: call " +
            "GopaySDK.initialize(context, config), or pass an Activity to the call"
    )

    private fun isAlive(activity: Activity): Boolean = !activity.isFinishing && !activity.isDestroyed

    /**
     * Whether [activity] is a screen in front. One the watch reported is in front from its
     * `onResume` to its `onPause`. One the watch has not reported yet, because it resumed before
     * the watch began, is asked: a lifecycle-aware activity says whether it is resumed. A plain
     * `android.app.Activity` cannot be asked, its window focus is lost to its own dialog, so
     * while it is alive it is taken to be in front, as 2.x took every Activity it was given.
     */
    private fun isInFront(activity: Activity): Boolean {
        if (!isAlive(activity)) return false
        val (tracked, reported) = synchronized(lock) {
            resumed.any { it.get() === activity } to (activity in seen)
        }
        return when {
            tracked -> true
            reported -> false
            activity is LifecycleOwner -> isResumed(activity)
            else -> true
        }
    }

    private fun isSdkScreen(activity: Activity): Boolean =
        activity is PaymentVerificationActivity || activity is GooglePayLauncherActivity

    /** Whether [activity] is resumed now, as far as an activity lets itself be asked. */
    private fun isResumed(activity: Activity): Boolean = isAlive(activity) && runCatching {
        (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
            ?: activity.hasWindowFocus()
    }.getOrDefault(false)

    @VisibleForTesting
    fun reset() {
        appContext = null
        synchronized(lock) {
            resumed.clear()
            seen.clear()
        }
        changes.update { it + 1 }
        ActivityWatch.reset()
    }

    /** Says in the debug log that `initialize` came too late to see the screen in front. */
    private fun warnIfInitializedLate() {
        if (!missedTheScreenInFront()) return
        SdkLog.w(
            "GopaySDK.initialize(context, config) ran after a screen was already up, so the SDK " +
                "learns the screen in front only at its next onResume. Call it in " +
                "Application.onCreate, or pass the resumed Activity as the context."
        )
    }
}
