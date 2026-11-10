package cz.gopay.sdk.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import java.util.Collections
import java.util.WeakHashMap

/**
 * The SDK's one registration of activity lifecycle callbacks, shared by everything in it that
 * follows the host's screens: [ForegroundScreen], which knows the screen to open a challenge or
 * the wallet sheet from, and [CardFormStore], which carries a half-typed card across a rotation.
 *
 * Registered once per application, by `GopaySDK.initialize(context, config)` or, for an app that
 * still initializes without a Context, the first time a card form parks a card. It stays
 * registered for the life of the process: the callbacks hold no activity, and what they tell
 * holds only weak references.
 */
internal object ActivityWatch {

    private val watched: MutableSet<Application> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** Starts watching the activities of [application], once. */
    fun ensure(application: Application) {
        if (watched.add(application)) application.registerActivityLifecycleCallbacks(Callbacks)
    }

    @VisibleForTesting
    fun reset() = watched.clear()

    private object Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            ForegroundScreen.resumed(activity)
            CardFormStore.activityResumed(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            ForegroundScreen.paused(activity)
            CardFormStore.activityPaused(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            ForegroundScreen.paused(activity)
            CardFormStore.activityDestroyed(activity)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
