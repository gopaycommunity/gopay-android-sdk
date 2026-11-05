package cz.gopay.sdk.ui

import android.app.Activity
import android.app.Application
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.util.SdkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** The SDK's copy of the forms on screen, which `submitCardForm(formId)` reads. */
@OptIn(ExperimentalCoroutinesApi::class)
class CardFormStoreTest {

    private val a = CardFormData("4532015112830366", "1230", "123")
    private val b = CardFormData("4444444444444448", "1228", "321")
    private val owner = Any()

    /** A second form under the same id, the one that does not hold the registration. */
    private val stranger = Any()

    /** The screen a card is parked for: an activity class, the mocked one, and its task. */
    private val checkout = CardFormStore.Screen(mock<Activity>().javaClass, taskId = 7)
    private val application = mock<Application>()
    private val defaultTimer = CardFormStore.timer

    /** The parking timeout, run by hand: the main looper is not there on the JVM. */
    private val timer = ManualTimer()

    @Before
    fun setUp() {
        // The handlers run on the main thread; on the JVM that is this one.
        Dispatchers.setMain(Dispatchers.Unconfined)
        CardFormStore.timer = timer
        CardFormStore.clear(null)
    }

    @After
    fun clean() {
        CardFormStore.unregister("a", owner)
        CardFormStore.unregister("a", stranger)
        CardFormStore.unregister("b", owner)
        CardFormStore.clear(null)
        CardFormStore.timer = defaultTimer
        Dispatchers.resetMain()
    }

    private fun register(formId: String, owner: Any = this.owner, reset: () -> Unit = {}, submitAttempted: () -> Unit = {}) =
        CardFormStore.register(formId, owner, CardFormHandlers(reset = reset, submitAttempted = submitAttempted))

    @Test
    fun `a submit without an id reads the most recently active form`() {
        register("a")
        register("b")
        CardFormStore.update("a", owner, a)
        CardFormStore.update("b", owner, b)
        assertEquals("b" to b, CardFormStore.resolve(null))

        CardFormStore.update("a", owner, a)
        assertEquals("a" to a, CardFormStore.resolve(null))
        assertEquals("b" to b, CardFormStore.resolve("b"))
        assertEquals(listOf("b", "a"), CardFormStore.storedFormIds().toList())
    }

    @Test
    fun `a form that is not on screen resolves to no data`() {
        assertEquals(null to null, CardFormStore.resolve(null))
        assertEquals("x" to null, CardFormStore.resolve("x"))
    }

    @Test
    fun `clearing a form drops its data and empties its fields through the handler`() {
        var resets = 0
        register("a", reset = { resets++ })
        register("b")
        CardFormStore.update("a", owner, a)
        CardFormStore.update("b", owner, b)

        CardFormStore.clear("a")

        assertEquals(1, resets)
        assertEquals(setOf("b"), CardFormStore.storedFormIds())
        assertEquals("the other form is now the most recent", "b" to b, CardFormStore.resolve(null))

        CardFormStore.clear(null)
        assertEquals(2, resets)
        assertTrue(CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun `a sync that lands between clear and its reset does not bring the card back`() {
        // The reset hops to the main thread; a keystroke in between syncs the old fields again.
        // The hop drops the data once more before the reset runs, so the store ends empty.
        val main = StandardTestDispatcher()
        Dispatchers.setMain(main)
        var resets = 0
        register("a", reset = { resets++ })
        CardFormStore.update("a", owner, a)

        CardFormStore.clear("a")
        assertTrue("dropped at the call", CardFormStore.storedFormIds().isEmpty())
        CardFormStore.update("a", owner, a)
        assertEquals("the keystroke in between", setOf("a"), CardFormStore.storedFormIds())

        main.scheduler.runCurrent()

        assertEquals(1, resets)
        assertTrue("dropped again before the reset", CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun `a form leaving the screen takes its data with it and calls nothing back`() {
        var resets = 0
        register("a", reset = { resets++ })
        CardFormStore.update("a", owner, a)

        CardFormStore.unregister("a", owner)

        assertEquals(0, resets)
        assertNull(CardFormStore.resolve("a").second)
        CardFormStore.clear("a")
        assertEquals("the handler is gone too", 0, resets)
    }

    @Test
    fun `a form leaving takes only its own registration, not a newer form's under the same id`() {
        // A screen transition composes the new form before the old one leaves: the old one's
        // departure must not wipe the new one's data or handlers, or the card would stay on
        // screen after the JWE and ON_SUBMIT would never show.
        val resets = mutableListOf<String>()
        val first = Any()
        val second = Any()
        register("a", first, reset = { resets += "first" })
        CardFormStore.update("a", first, a)
        register("a", second, reset = { resets += "second" })
        CardFormStore.update("a", second, b)

        CardFormStore.unregister("a", first)

        assertEquals("the newer form's data stays", "a" to b, CardFormStore.resolve("a"))
        CardFormStore.clear("a")
        assertEquals("and the newer form is the one reset", listOf("second"), resets)
        CardFormStore.unregister("a", second)
        assertNull(CardFormStore.resolve("a").second)
    }

    @Test
    fun `a newer form leaving first leaves nothing of the older form in the SDK`() {
        // Two live forms under one id, the newer one took the registration and leaves first.
        // The older one is still on screen, unregistered: what it holds must not reach the
        // SDK, and a submit without an id must not find it.
        val first = Any()
        val second = Any()
        register("a", first)
        CardFormStore.update("a", first, a)
        register("a", second)
        CardFormStore.update("a", second, b)

        CardFormStore.unregister("a", second)
        assertNull("the newer form took its data with it", CardFormStore.resolve("a").second)

        CardFormStore.update("a", first, a)
        assertNull("the older form is no longer heard", CardFormStore.resolve("a").second)
        assertEquals(null to null, CardFormStore.resolve(null))

        CardFormStore.unregister("a", first)
        assertTrue(CardFormStore.storedFormIds().isEmpty())
    }

    @Test
    fun `an unregistered form's edits never reach the SDK`() {
        CardFormStore.update("a", owner, a)
        assertTrue("no registration, no data", CardFormStore.storedFormIds().isEmpty())

        register("a", stranger)
        CardFormStore.update("a", owner, a)
        assertNull("registered to someone else", CardFormStore.resolve("a").second)
    }

    @Test
    fun `a replaced form's sync warns once per id`() {
        val defaultWarnSink = SdkLog.warnSink
        val warnings = mutableListOf<String>()
        SdkLog.warnSink = { warnings.add(it) }
        try {
            register("a", stranger)
            CardFormStore.update("a", owner, a)
            CardFormStore.update("a", owner, b)
            CardFormStore.update("a", stranger, a)
            assertEquals("one warning for the id, however often the replaced form syncs", 1, warnings.size)
            assertTrue(warnings.first(), warnings.first().contains("'a'"))

            register("a", owner)
            CardFormStore.update("a", stranger, a)
            assertEquals("a new registration opens the warning again", 2, warnings.size)
        } finally {
            SdkLog.warnSink = defaultWarnSink
        }
    }

    @Test
    fun `a card parked for a recreated screen goes to the form that comes back there, once`() {
        val leaving = Any()
        val flags = CardFormFlags(submitAttempted = true, cvvLeft = true)
        registerWithCard("a", a, leaving)

        // The activity is being recreated: the form parks its card and flags, then leaves.
        CardFormStore.park("a", leaving, ParkedForm(a, flags), checkout, application)
        CardFormStore.unregister("a", leaving)
        assertNull("the SDK's copy went with the form", CardFormStore.resolve("a").second)

        assertNull("another screen does not get it", CardFormStore.takeParked("a", checkout.copy(taskId = 8)))
        assertNull("nor a form outside an activity", CardFormStore.takeParked("a", null))
        assertEquals(ParkedForm(a, flags), CardFormStore.takeParked("a", checkout))
        assertNull("taken once", CardFormStore.takeParked("a", checkout))
        assertTrue(CardFormStore.parkedFormIds().isEmpty())
    }

    @Test
    fun `a parked card nobody takes is dropped with its screen`() {
        registerWithCard("a", a)
        park("a", a)
        assertEquals(setOf("a"), CardFormStore.parkedFormIds())

        // The user left the recreated screen, or it went away.
        CardFormStore.dropParked(checkout)

        assertTrue(CardFormStore.parkedFormIds().isEmpty())
        assertNull(CardFormStore.takeParked("a", checkout))
    }

    @Test
    fun `nothing is parked for an empty form, one that lost its registration, or one the SDK already cleared`() {
        val older = Any()
        register("a", older)
        registerWithCard("a", a)

        park("a", CardFormData())
        park("a", a, owner = older)
        assertTrue(CardFormStore.parkedFormIds().isEmpty())

        // A submit that cleared the card off the main thread before the form's disposal parks it.
        CardFormStore.clear("a")
        park("a", a)
        assertTrue("the JWE was made: nothing comes back", CardFormStore.parkedFormIds().isEmpty())
    }

    @Test
    fun `clearing a form drops the card it parked for a recreated screen too`() {
        // A submit whose JWE arrives while the activity is recreated: the form that comes back
        // must not get the card after the JWE was made from it.
        registerWithCard("a", a)
        registerWithCard("b", b)
        park("a", a)
        park("b", b)

        CardFormStore.clear("a")
        assertNull(CardFormStore.takeParked("a", checkout))
        assertEquals("the other form's stays", setOf("b"), CardFormStore.parkedFormIds())

        CardFormStore.clear(null)
        assertNull(CardFormStore.takeParked("b", checkout))
        verify(application).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `the activities are watched only while a card is parked`() {
        registerWithCard("a", a)
        registerWithCard("b", b)
        park("a", a)
        park("b", b)
        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application, times(1)).registerActivityLifecycleCallbacks(callbacks.capture())

        CardFormStore.takeParked("a", checkout)
        verify(application, never()).unregisterActivityLifecycleCallbacks(any())
        CardFormStore.dropParked(checkout)
        verify(application).unregisterActivityLifecycleCallbacks(callbacks.firstValue)
    }

    @Test
    fun `a parked card waits for its screen and goes when the user leaves it or it is gone`() {
        registerWithCard("a", a)
        park("a", a)
        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(callbacks.capture())
        val watch = callbacks.firstValue
        val recreated = activity(checkout.taskId, changingConfigurations = true)

        // The old activity going for the change and the new one coming up keep it waiting, for
        // a form in a bottom sheet that composes a frame later.
        watch.onActivityDestroyed(recreated)
        watch.onActivityResumed(recreated)
        watch.onActivityPaused(activity(taskId = 8))
        assertEquals(setOf("a"), CardFormStore.parkedFormIds())

        watch.onActivityPaused(activity(checkout.taskId))
        assertTrue("the user left the screen", CardFormStore.parkedFormIds().isEmpty())

        // Parked again: watched by a callback registered anew.
        park("a", a)
        verify(application, times(2)).registerActivityLifecycleCallbacks(callbacks.capture())
        callbacks.secondValue.onActivityDestroyed(activity(checkout.taskId, changingConfigurations = false))
        assertTrue("the screen is gone for good", CardFormStore.parkedFormIds().isEmpty())
    }

    @Test
    fun `a parked card waits ten seconds once its screen is up, and no longer`() {
        registerWithCard("a", a)
        registerWithCard("b", b)
        park("a", a)
        val callbacks = argumentCaptor<Application.ActivityLifecycleCallbacks>()
        verify(application).registerActivityLifecycleCallbacks(callbacks.capture())

        callbacks.firstValue.onActivityResumed(activity(checkout.taskId))
        assertEquals(listOf(10_000L), timer.scheduled.map { it.first })
        assertEquals(setOf("a"), CardFormStore.parkedFormIds())

        timer.fire()
        assertTrue("no form came for it in time", CardFormStore.parkedFormIds().isEmpty())

        // Taken in time: the timeout is called off.
        park("b", b)
        verify(application, times(2)).registerActivityLifecycleCallbacks(callbacks.capture())
        callbacks.secondValue.onActivityResumed(activity(checkout.taskId))
        assertEquals(ParkedForm(b, CardFormFlags()), CardFormStore.takeParked("b", checkout))
        assertEquals(1, timer.cancelled)
    }

    /** Runs nothing by itself: [fire] runs what is pending. */
    private class ManualTimer : CardFormStore.Timer {
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        var cancelled = 0
        override fun schedule(delayMs: Long, block: () -> Unit): () -> Unit {
            val entry = delayMs to block
            scheduled += entry
            return { if (scheduled.remove(entry)) cancelled++ }
        }

        fun fire() = scheduled.toList().forEach { scheduled.remove(it); it.second() }
    }

    private fun registerWithCard(formId: String, card: CardFormData, owner: Any = this.owner) {
        register(formId, owner)
        CardFormStore.update(formId, owner, card)
    }

    private fun park(formId: String, card: CardFormData, owner: Any = this.owner) =
        CardFormStore.park(formId, owner, ParkedForm(card, CardFormFlags()), checkout, application)

    /** An activity of the [checkout] screen's class, in [taskId]. */
    private fun activity(taskId: Int, changingConfigurations: Boolean = false): Activity {
        val activity = mock<Activity>()
        whenever(activity.taskId).thenReturn(taskId)
        whenever(activity.isChangingConfigurations).thenReturn(changingConfigurations)
        return activity
    }

    @Test
    fun `only the registered form counts as registered, and its leaving says so`() {
        register("a", stranger)
        register("a")

        assertTrue(CardFormStore.isRegistered("a", owner))
        assertFalse(CardFormStore.isRegistered("a", stranger))
        assertFalse("not the registered one", CardFormStore.unregister("a", stranger))
        assertTrue(CardFormStore.unregister("a", owner))
        assertFalse(CardFormStore.isRegistered("a", owner))
    }

    @Test
    fun `a submit attempt reaches the form it was made for`() {
        val attempted = mutableListOf<String>()
        register("a", submitAttempted = { attempted += "a" })
        register("b", submitAttempted = { attempted += "b" })

        CardFormStore.submitAttempted("b")
        CardFormStore.submitAttempted(null)
        CardFormStore.submitAttempted("missing")

        assertEquals(listOf("b"), attempted)
    }

    @Test
    fun `the stored digits become the card encryptCardData takes, with the four-digit year`() {
        assertEquals(CardData("4532015112830366", "12", "2030", "123"), a.toCardData())
        assertEquals("12/30", a.formattedExpiration())
        assertTrue(a.isValid)
        assertTrue(CardFormData().isEmpty)
        assertTrue(!CardFormData(cvv = "1").isEmpty)
        assertNull("no expiration parses to no card", CardFormData("4532015112830366", "", "123").toCardData())
    }
}
