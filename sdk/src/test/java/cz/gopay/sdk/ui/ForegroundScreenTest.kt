package cz.gopay.sdk.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.testing.SharedSdkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.ref.WeakReference

/**
 * The screen the SDK opens a challenge or the wallet sheet from when a call names no Activity:
 * the host's activity that is resumed, as the lifecycle callbacks report it. The waits here are
 * for a state, an `onResume`, never for a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundScreenTest {

    @get:Rule
    val sharedState = SharedSdkState()

    private val application = mock<Application>()
    private val context = mock<Context> { on { applicationContext }.thenReturn(application) }

    private val afterCurrentCallback = ForegroundScreen.afterCurrentCallback

    /** What `initialize` asked to run once the callback it was called from has returned. */
    private val posted = mutableListOf<() -> Unit>()

    @Before
    fun setUp() {
        ForegroundScreen.reset()
        ForegroundScreen.afterCurrentCallback = { posted += it }
    }

    @After
    fun tearDown() {
        ForegroundScreen.afterCurrentCallback = afterCurrentCallback
        ForegroundScreen.reset()
    }

    /** A screen of the host, a plain `android.app.Activity`: it cannot say whether it is resumed. */
    private fun activity(finishing: Boolean = false, destroyed: Boolean = false) = mock<Activity> {
        on { isFinishing }.thenReturn(finishing)
        on { isDestroyed }.thenReturn(destroyed)
    }

    /** A lifecycle-aware screen that says it is in [state]. */
    private fun lifecycleActivity(state: Lifecycle.State): Activity {
        val lifecycle = mock<Lifecycle> { on { currentState }.thenReturn(state) }
        val activity = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((activity as LifecycleOwner).lifecycle).thenReturn(lifecycle)
        return activity
    }

    /** The callbacks `initialize(context, config)` registered, as the system would call them. */
    private fun installedCallbacks(): Application.ActivityLifecycleCallbacks {
        ForegroundScreen.install(context)
        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(callbacks.capture())
        return callbacks.firstValue
    }

    /**
     * An instance built without its constructor, which an activity cannot run on a plain JVM;
     * through the Objenesis that Mockito brings along at run time.
     */
    private fun <T> withoutConstructor(type: Class<T>): T {
        val objenesis = Class.forName("org.objenesis.ObjenesisStd").getDeclaredConstructor().newInstance()
        return type.cast(objenesis.javaClass.getMethod("newInstance", Class::class.java).invoke(objenesis, type))
    }

    @Test
    fun `a new process knows no screen and no Context`() {
        assertNull(ForegroundScreen.current())
        assertNull(ForegroundScreen.appContext)
    }

    @Test
    fun `install keeps the application Context and registers the callbacks once`() {
        ForegroundScreen.install(context)
        ForegroundScreen.install(context)

        assertSame("never the Context it was handed", application, ForegroundScreen.appContext)
        verify(application, times(1)).registerActivityLifecycleCallbacks(any())
        verify(application, never()).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `the screen in front is the one resumed, until it pauses`() {
        val system = installedCallbacks()
        val checkout = activity()

        system.onActivityResumed(checkout)
        assertSame(checkout, ForegroundScreen.current())

        system.onActivityPaused(checkout)
        assertNull("in the background there is no screen in front", ForegroundScreen.current())

        system.onActivityResumed(checkout)
        assertSame("back from the background", checkout, ForegroundScreen.current())
    }

    @Test
    fun `a rotation hands the screen over to the recreated activity`() = runTest {
        val system = installedCallbacks()
        val old = activity()
        system.onActivityResumed(old)

        // The old one goes; until the new one is up there is no screen, and a call waits.
        system.onActivityPaused(old)
        system.onActivityDestroyed(old)
        val waiting = async { ForegroundScreen.await() }
        runCurrent()
        assertFalse("nothing to open from in between", waiting.isCompleted)

        val recreated = activity()
        system.onActivityResumed(recreated)

        assertSame(recreated, waiting.await())
        assertSame(recreated, ForegroundScreen.current())
    }

    @Test
    fun `with more screens, the one on top is in front, and the one beneath comes back`() {
        val system = installedCallbacks()
        val list = activity()
        val detail = activity()

        system.onActivityResumed(list)
        system.onActivityPaused(list)
        system.onActivityResumed(detail)
        assertSame(detail, ForegroundScreen.current())

        system.onActivityPaused(detail)
        system.onActivityResumed(list)
        system.onActivityDestroyed(detail)
        assertSame(list, ForegroundScreen.current())
    }

    @Test
    fun `in multi-window the one resumed last is in front, and the other stays known`() {
        val system = installedCallbacks()
        val left = activity()
        val right = activity()

        system.onActivityResumed(left)
        system.onActivityResumed(right)
        assertSame(right, ForegroundScreen.current())

        system.onActivityPaused(right)
        assertSame(left, ForegroundScreen.current())
    }

    @Test
    fun `the SDK's own screens are not the host's screen`() {
        val system = installedCallbacks()
        val checkout = activity()
        val challenge = withoutConstructor(PaymentVerificationActivity::class.java)
        val launcher = withoutConstructor(GooglePayLauncherActivity::class.java)

        system.onActivityResumed(challenge)
        system.onActivityResumed(launcher)
        assertNull(ForegroundScreen.current())

        // Under the challenge the host's screen is paused: nothing in front, as on a real device.
        system.onActivityResumed(checkout)
        system.onActivityPaused(checkout)
        system.onActivityResumed(challenge)
        assertNull(ForegroundScreen.current())
    }

    @Test
    fun `an activity that is finishing or destroyed is never handed out`() {
        val system = installedCallbacks()
        val finishing = activity(finishing = true)
        val destroyed = activity(destroyed = true)
        val alive = activity()

        system.onActivityResumed(finishing)
        assertNull(ForegroundScreen.current())

        system.onActivityResumed(alive)
        system.onActivityResumed(destroyed)
        assertSame("the dead one on top is skipped", alive, ForegroundScreen.current())
    }

    @Test
    fun `an Activity the call names is used while it is in front, the one in front otherwise`() {
        val system = installedCallbacks()
        val inFront = activity()
        system.onActivityResumed(inFront)

        val named = lifecycleActivity(Lifecycle.State.RESUMED)
        assertSame(named, ForegroundScreen.screenFor(named))
        assertSame(inFront, ForegroundScreen.screenFor(activity(finishing = true)))
        assertSame(inFront, ForegroundScreen.screenFor(activity(destroyed = true)))
        assertSame(inFront, ForegroundScreen.screenFor(null))
        assertSame(
            "one that says it is not resumed gives way",
            inFront,
            ForegroundScreen.screenFor(lifecycleActivity(Lifecycle.State.STARTED))
        )
    }

    @Test
    fun `a plain Activity the watch has not seen yet is taken to be in front, as 2_x took it`() {
        val system = installedCallbacks()
        // A plain android.app.Activity, resumed before the watch began, with its own dialog
        // open: it has no window focus and no lifecycle to ask.
        val plain = mock<Activity> { on { hasWindowFocus() }.thenReturn(false) }

        assertSame(plain, ForegroundScreen.screenFor(plain))

        // Once the watch reports it, the reports decide.
        system.onActivityPaused(plain)
        assertNull(ForegroundScreen.screenFor(plain))
        system.onActivityResumed(plain)
        assertSame(plain, ForegroundScreen.screenFor(plain))
    }

    @Test
    fun `an Activity the call names is not opened from while it is in the background`() {
        val system = installedCallbacks()
        val named = activity()
        system.onActivityResumed(named)
        assertSame("watched, and resumed", named, ForegroundScreen.screenFor(named))

        // Alive, but paused: a start from it would be dropped by the system without a word.
        system.onActivityPaused(named)
        assertNull("nothing in front, so nothing to open from", ForegroundScreen.screenFor(named))

        val inFront = activity()
        system.onActivityResumed(inFront)
        assertSame("the screen in front instead", inFront, ForegroundScreen.screenFor(named))

        system.onActivityPaused(inFront)
        system.onActivityResumed(named)
        assertSame("and the named one again once it is back", named, ForegroundScreen.screenFor(named))
    }

    @Test
    fun `the device configuration is read from a named Activity in the background when there is no Context`() {
        // The deprecated initialize(config), and the named one paused under the Google Pay sheet.
        val named = activity()
        ForegroundScreen.resumed(named)
        ForegroundScreen.paused(named)

        assertNull("nothing to open from", ForegroundScreen.screenFor(named))
        assertSame("but alive, so enough to read from", named, ForegroundScreen.contextFor(named))
    }

    @Test
    fun `a named Activity that is finishing or destroyed is not read from, and without the Context there is nothing`() {
        assertNull(ForegroundScreen.contextFor(activity(finishing = true)))
        assertNull(ForegroundScreen.contextFor(activity(destroyed = true)))
        assertNull(ForegroundScreen.contextFor(null))

        ForegroundScreen.install(context)
        assertSame(application, ForegroundScreen.contextFor(activity(destroyed = true)))
    }

    @Test
    fun `the device configuration is read from the screen in front before a named Activity in the background`() {
        val system = installedCallbacks()
        val named = activity()
        system.onActivityResumed(named)
        system.onActivityPaused(named)
        val inFront = activity()
        system.onActivityResumed(inFront)

        assertSame(inFront, ForegroundScreen.contextFor(named))
        assertSame(inFront, ForegroundScreen.contextFor(null))

        system.onActivityPaused(inFront)
        assertSame("then the named one, before the application Context", named, ForegroundScreen.contextFor(named))
        assertSame(application, ForegroundScreen.contextFor(null))
    }

    @Test
    fun `waiting with a named Activity in the background ends when it, or another screen, comes to the front`() = runTest {
        val system = installedCallbacks()
        val named = activity()
        system.onActivityResumed(named)
        system.onActivityPaused(named)
        val forNamed = async { ForegroundScreen.await(named) }
        val forOther = async { ForegroundScreen.await(named) }
        runCurrent()
        assertFalse(forNamed.isCompleted)

        system.onActivityResumed(named)
        assertSame(named, forNamed.await())
        assertSame(named, forOther.await())
    }

    @Test
    fun `a call that names an Activity starts the watch, with or without the Context`() = runTest {
        // The deprecated initialize(config): no Context, so nothing is watched yet.
        val named = lifecycleActivity(Lifecycle.State.STARTED)
        verify(application, never()).registerActivityLifecycleCallbacks(any())

        ForegroundScreen.requireContextOr(named, "handle3dsVerification()")

        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(callbacks.capture())
        assertNull("the Context is still not the SDK's to use", ForegroundScreen.appContext)

        // The named one is destroyed in a rotation; the wait ends with the recreated one, where
        // without the watch nothing would ever end it.
        val waiting = async { ForegroundScreen.await(named) }
        runCurrent()
        whenever(named.isDestroyed).thenReturn(true)
        callbacks.firstValue.onActivityDestroyed(named)
        runCurrent()
        assertFalse(waiting.isCompleted)
        val recreated = activity()
        callbacks.firstValue.onActivityResumed(recreated)

        assertSame(recreated, waiting.await())
    }

    @Test
    fun `the count of changes is not lost when screens change from two threads at once`() {
        val system = installedCallbacks()
        val changes = ForegroundScreen::class.java.getDeclaredField("changes").apply { isAccessible = true }
            .get(ForegroundScreen) as kotlinx.coroutines.flow.MutableStateFlow<*>
        val before = changes.value as Long
        val screens = List(2) { activity() }

        // Every resume and every pause that removes one is a change; none may go missing.
        val threads = screens.map { screen ->
            Thread { repeat(5_000) { system.onActivityResumed(screen); system.onActivityPaused(screen) } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(20_000L, changes.value as Long - before)
    }

    @Test
    fun `waiting for a screen ends with the first one resumed, or with the caller`() = runTest {
        val system = installedCallbacks()

        val waiting = async { ForegroundScreen.await() }
        val given = async { ForegroundScreen.await(activity()) }
        val givenUp = async { ForegroundScreen.await() }
        runCurrent()
        assertTrue("a named Activity that is in front needs no wait", given.isCompleted)
        assertFalse(waiting.isCompleted)

        givenUp.cancelAndJoin()
        val checkout = activity()
        system.onActivityResumed(checkout)

        assertSame(checkout, waiting.await())
        assertTrue(givenUp.isCancelled)
    }

    @Test
    fun `only weak references are kept`() {
        val system = installedCallbacks()
        system.onActivityResumed(activity())

        val held = ForegroundScreen::class.java.getDeclaredField("resumed")
            .apply { isAccessible = true }
            .get(ForegroundScreen) as List<*>

        assertEquals(1, held.size)
        assertTrue("an activity must not be kept alive by the SDK", held.all { it is WeakReference<*> })
        assertFalse(ForegroundScreen::class.java.declaredFields.any { Activity::class.java.isAssignableFrom(it.type) })
    }

    @Test
    fun `initialize from a resumed Activity takes it as the screen at once`() {
        // A lifecycle-aware activity says so itself.
        val lifecycle = mock<Lifecycle> { on { currentState }.thenReturn(Lifecycle.State.RESUMED) }
        val resumed = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((resumed as LifecycleOwner).lifecycle).thenReturn(lifecycle)

        ForegroundScreen.install(resumed)

        assertSame(resumed, ForegroundScreen.current())
        assertSame(application, ForegroundScreen.appContext)
    }

    @Test
    fun `initialize from a plain Activity goes by its window focus`() {
        val focused = mock<Activity> {
            on { applicationContext }.thenReturn(application)
            on { hasWindowFocus() }.thenReturn(true)
        }

        ForegroundScreen.install(focused)

        assertSame(focused, ForegroundScreen.current())
    }

    @Test
    fun `initialize from an Activity's own onResume takes it once that onResume has returned`() {
        // Inside onResume a lifecycle-aware activity is still only STARTED, and the callback
        // that reports a resumed screen has already run, before the watch was registered.
        var state = Lifecycle.State.STARTED
        val lifecycle = mock<Lifecycle> { on { currentState }.thenAnswer { state } }
        val resuming = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((resuming as LifecycleOwner).lifecycle).thenReturn(lifecycle)

        ForegroundScreen.install(resuming)
        assertNull("not yet, as far as it can be asked", ForegroundScreen.current())

        // onResume returns, the activity is RESUMED, and what initialize left behind runs.
        state = Lifecycle.State.RESUMED
        posted.forEach { it() }

        assertSame(resuming, ForegroundScreen.current())
    }

    @Test
    fun `initialize from an Activity that stays in the background takes nothing later either`() {
        val lifecycle = mock<Lifecycle> { on { currentState }.thenReturn(Lifecycle.State.CREATED) }
        val created = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((created as LifecycleOwner).lifecycle).thenReturn(lifecycle)

        ForegroundScreen.install(created)
        posted.forEach { it() }

        assertNull(ForegroundScreen.current())
    }

    @Test
    fun `initialize from an Activity that is not resumed waits for its onResume`() {
        val lifecycle = mock<Lifecycle> { on { currentState }.thenReturn(Lifecycle.State.CREATED) }
        val created = mock<Activity>(extraInterfaces = arrayOf(LifecycleOwner::class)) {
            on { applicationContext }.thenReturn(application)
        }
        whenever((created as LifecycleOwner).lifecycle).thenReturn(lifecycle)

        ForegroundScreen.install(created)
        assertNull(ForegroundScreen.current())

        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(callbacks.capture())
        callbacks.firstValue.onActivityResumed(created)
        assertSame(created, ForegroundScreen.current())
    }

    @Test
    fun `a call without an Activity and without the Context is CONFIG_004`() {
        val e = assertThrows(GopaySDKException::class.java) {
            ForegroundScreen.requireContextOr(named = null, call = "handle3dsVerification()")
        }
        assertEquals(GopayErrorCodes.CONFIG_MISSING_CONTEXT, e.code)
        assertTrue(e.message!!.contains("GopaySDK.initialize(context, config)"))

        assertThrows(GopaySDKException::class.java) {
            ForegroundScreen.requireContextOr(activity(destroyed = true), "handle3dsVerification()")
        }

        // Either one is enough; the named one only has to be alive, a wait can follow.
        ForegroundScreen.requireContextOr(activity(), "handle3dsVerification()")
        ForegroundScreen.install(context)
        ForegroundScreen.requireContextOr(null, "handle3dsVerification()")
    }

    @Test
    fun `initializing again without a Context forgets the Context, not the callbacks`() {
        ForegroundScreen.install(context)

        ForegroundScreen.uninstall()

        assertNull(ForegroundScreen.appContext)
        verify(application, never()).unregisterActivityLifecycleCallbacks(any())
    }
}
