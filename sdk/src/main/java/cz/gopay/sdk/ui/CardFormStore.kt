package cz.gopay.sdk.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.telemetry.CardFormProgress
import cz.gopay.sdk.telemetry.CardFormSteps
import cz.gopay.sdk.ui.utils.CardValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What a [PaymentCardForm] holds, as digits: the SDK's copy of the fields, kept in
 * [CardFormStore] under the form's id so `GopaySDK.submitCardForm(formId)` can read it without
 * the host ever touching the PAN.
 */
internal data class CardFormData(
    val cardNumberDigits: String = "",
    val expirationDigits: String = "",
    val cvv: String = ""
) {
    /** Nothing typed at all: the state right after the form was emptied. */
    val isEmpty: Boolean
        get() = cardNumberDigits.isEmpty() && expirationDigits.isEmpty() && cvv.isEmpty()

    /** The form's own validation, field by field, the same the old submit ran. */
    fun validate(): CardValidator.CardValidationResult = CardValidator.validateCard(
        cardNumber = cardNumberDigits,
        expirationDate = formattedExpiration(),
        cvv = cvv
    )

    val isValid: Boolean get() = validate().isAllValid

    /**
     * Each field as the operational data describes it: empty, complete when [validate] accepts
     * it, partial for anything between. A state, never the digits or how many there are.
     */
    fun progress(): CardFormProgress.Progress {
        val validation = validate()
        fun state(digits: String, valid: Boolean) = when {
            digits.isEmpty() -> CardFormProgress.State.EMPTY
            valid -> CardFormProgress.State.COMPLETE
            else -> CardFormProgress.State.PARTIAL
        }
        return CardFormProgress.Progress(
            pan = state(cardNumberDigits, validation.cardNumber.isValid),
            expiry = state(expirationDigits, validation.expirationDate.isValid),
            cvv = state(cvv, validation.cvv.isValid)
        )
    }

    /** `MMYY` as `MM/YY`, the shape [CardValidator] reads. */
    fun formattedExpiration(): String = when {
        expirationDigits.length <= 2 -> expirationDigits
        else -> "${expirationDigits.substring(0, 2)}/${expirationDigits.substring(2)}"
    }

    /**
     * The card as `encryptCardData` takes it, with the four-digit year; null when the
     * expiration does not parse, which [validate] rules out first.
     */
    fun toCardData(): CardData? {
        val parsed = CardValidator.parseExpirationDate(formattedExpiration()) ?: return null
        val month = parsed.first.toIntOrNull() ?: return null
        val year = parsed.second.toIntOrNull() ?: return null
        if (month < 1 || month > 12) return null
        return CardData(
            cardPan = cardNumberDigits,
            expMonth = month.toString().padStart(2, '0'),
            expYear = (2000 + year).toString(),
            cvv = cvv
        )
    }
}

/**
 * What a [PaymentCardForm] knows about how far the user got, besides the card: whether a submit
 * ran and which fields the user typed in and left. Parked with the card across a configuration
 * change, so the errors on show come back with it; never kept in saved state, where they would
 * outlive the card.
 */
internal data class CardFormFlags(
    val submitAttempted: Boolean = false,
    val cardNumberLeft: Boolean = false,
    val expirationLeft: Boolean = false,
    val cvvLeft: Boolean = false
)

/** What a form parked for the form that comes back after a configuration change. */
internal data class ParkedForm(val data: CardFormData, val flags: CardFormFlags)

/**
 * What a live [PaymentCardForm] lets the store do to it. Both run on the main thread.
 *
 * @param reset empties the visible fields, the edited flags and the submit-attempted flag.
 * @param submitAttempted tells the form a submit ran, so [CardFormValidationDisplay.ON_SUBMIT]
 *        shows its errors.
 * @param steps the form's card form steps, which a submit reports to; kept here beside the
 *        form's data and dropped with it when the form leaves.
 */
internal class CardFormHandlers(
    val reset: () -> Unit,
    val submitAttempted: () -> Unit,
    val steps: CardFormSteps? = null
)

/**
 * The SDK's copy of every [PaymentCardForm] on screen, keyed by form id, which
 * `GopaySDK.submitCardForm(formId)` reads and `GopaySDK.clearCardFormData(formId)` wipes. The
 * form writes into it on every edit and takes itself out when it leaves the composition, so the
 * PAN and CVV never outlive the form (PCI DSS 4.0.1, req. 3.3.1). Mirrors the
 * `internalCardFormData` map on the iOS `GopaySDK`.
 *
 * Process-wide rather than on the `GopaySDK` instance: a form stays on screen across
 * `GopaySDK.initialize` being called again, and syncing must not need an initialized SDK at
 * all, since `submitCardForm` is the call that throws `CONFIG_001` for that.
 *
 * Two forms can hold the same id for a moment, a screen transition composing the new one before
 * the old one leaves, so a registration names its owner, the composition that made it. The copy
 * belongs to the registered form: a form leaving takes only its own registration with it, and a
 * form that lost its registration to a newer one under the same id syncs nothing more, so it
 * leaves nothing behind when it goes and never becomes the form a submit without an id reads.
 * The iOS `GopaySDK` keeps the same rule.
 *
 * A configuration change, a rotation above all, recreates the activity and with it the form;
 * the card would be lost with the composition. So a form leaving while its activity is being
 * recreated parks its card here, under its id, for the form that comes back to take over at its
 * first composition, see [park] and [takeParked]. The card never goes into saved state: a
 * `Bundle` may be written out, the process memory is not. A parked card waits for a form of the
 * same screen to take it, a form in a bottom sheet or dialog included, which composes a frame or
 * more after the activity, but at most [PARK_TIMEOUT_MS] after the recreated screen is up. It is
 * dropped then, when the user leaves that screen (it pauses) or it is destroyed for good, and by
 * [clear] like the SDK's copy, so it does not outlive the screen it was typed on, nor the JWE
 * made from it.
 *
 * One lock around the data and the handlers together: the form writes from the main thread,
 * `submitCardForm` reads and clears from whatever dispatcher resumes it. The handlers write
 * Compose state, so they run on the main thread, outside the lock.
 */
internal object CardFormStore {

    private class Registration(val owner: Any, val handlers: CardFormHandlers)

    /**
     * The screen a card was typed on: an activity is identified by its class and task, the only
     * identity that survives its recreation.
     */
    data class Screen(val activityClass: Class<*>, val taskId: Int) {
        companion object {
            fun of(activity: Activity) = Screen(activity.javaClass, activity.taskId)
        }
    }

    private class ParkedCard(val form: ParkedForm, val screen: Screen)

    /** Runs a block later on the main thread; returns what cancels it. Swapped in tests. */
    fun interface Timer {
        fun schedule(delayMs: Long, block: () -> Unit): () -> Unit
    }

    private object MainTimer : Timer {
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun schedule(delayMs: Long, block: () -> Unit): () -> Unit {
            val runnable = Runnable(block)
            handler.postDelayed(runnable, delayMs)
            return { handler.removeCallbacks(runnable) }
        }
    }

    /** How long a parked card waits for its form once the recreated screen is up. */
    const val PARK_TIMEOUT_MS = 10_000L

    @VisibleForTesting
    var timer: Timer = MainTimer

    private val lock = Any()

    /** In order of last sync: the last entry is the form a submit without an id reads. */
    private val data = LinkedHashMap<String, CardFormData>()
    private val registrations = HashMap<String, Registration>()
    private val parked = HashMap<String, ParkedCard>()

    /** Watches the activities while something is parked; null when nothing is. */
    private var parkingWatch: Pair<Application, Application.ActivityLifecycleCallbacks>? = null

    /** What cancels the timeout of each recreated screen that has a card waiting. */
    private val timeouts = HashMap<Screen, () -> Unit>()

    /**
     * Runs the handlers. On `Dispatchers.Main.immediate`, resolved at each call rather than held
     * in a field, so a caller already on the main thread sees them done, and a test that swaps
     * the main dispatcher sees the swap whenever it happens.
     */
    private val handlerScope = CoroutineScope(SupervisorJob())

    /**
     * The form's current fields; called on every edit and once when the form appears, which
     * makes the form the most recent one either way. Only the form registered under [formId] by
     * [owner] is heard: an edit in a form that lost its registration goes nowhere.
     */
    fun update(formId: String, owner: Any, formData: CardFormData) = synchronized(lock) {
        if (registrations[formId]?.owner !== owner) return@synchronized
        data.remove(formId)
        data[formId] = formData
    }

    /**
     * Registers how to reach a form that is on screen, for the composition [owner] that made it.
     * A later registration under the same id replaces an earlier one.
     */
    fun register(formId: String, owner: Any, formHandlers: CardFormHandlers) = synchronized(lock) {
        registrations[formId] = Registration(owner, formHandlers)
    }

    /**
     * Forgets a form that is leaving the screen, its data included, unless another composition
     * has registered under the same id since: then the data is that form's and stays. The form
     * empties its own fields; nothing is called back into it.
     *
     * @return whether [owner] was the form registered under [formId].
     */
    fun unregister(formId: String, owner: Any): Boolean = synchronized(lock) {
        if (registrations[formId]?.owner !== owner) return@synchronized false
        registrations.remove(formId)
        data.remove(formId)
        true
    }

    /** Whether [owner] is the form registered under [formId], the one whose card the SDK holds. */
    fun isRegistered(formId: String, owner: Any): Boolean =
        synchronized(lock) { registrations[formId]?.owner === owner }

    /**
     * The form a submit reads: [formId] when given, else the most recently active form, the one
     * that synced last. The data is null when the form is not on screen; only a registered form
     * ever has any.
     */
    fun resolve(formId: String?): Pair<String?, CardFormData?> = synchronized(lock) {
        val id: String? = formId ?: data.keys.lastOrNull()
        if (id == null) return@synchronized null to null
        id to data[id]
    }

    /** The card form steps of the form registered under [formId], null when none is. */
    fun steps(formId: String?): CardFormSteps? =
        synchronized(lock) { formId?.let { registrations[it]?.handlers?.steps } }

    /** Tells the form with [formId] that a submit ran, for [CardFormValidationDisplay.ON_SUBMIT]. */
    fun submitAttempted(formId: String?) {
        val handler = synchronized(lock) { formId?.let { registrations[it]?.handlers?.submitAttempted } }
        if (handler != null) handlerScope.launch(Dispatchers.Main.immediate) { handler() }
    }

    /**
     * Drops the stored data of [formId], or of every form when null, a card parked for a
     * recreated activity included, and empties the visible fields of each form concerned through
     * its reset handler, on the main thread. The data is dropped again right before the resets
     * run: a keystroke between the call and the hop to the main thread would otherwise sync the
     * old card back in behind the reset.
     */
    fun clear(formId: String?) {
        val (ids, resets) = synchronized(lock) {
            val ids = if (formId != null) listOf(formId) else (data.keys + registrations.keys).distinct()
            ids.forEach { data.remove(it) }
            // A submit whose JWE arrives while the activity is recreated finds the card parked:
            // it must not come back on screen after the JWE.
            if (formId != null) parked.remove(formId) else parked.clear()
            parkedChanged()
            ids to ids.mapNotNull { registrations[it]?.handlers?.reset }
        }
        if (resets.isEmpty()) return
        handlerScope.launch(Dispatchers.Main.immediate) {
            synchronized(lock) { ids.forEach { data.remove(it) } }
            resets.forEach { it() }
        }
    }

    /**
     * Parks the card and [flags] of the form registered under [formId] by [owner] while
     * [activity] is recreated for a configuration change. Nothing is parked for an empty card,
     * for a form that is not the registered one, or when the SDK holds no copy of the card any
     * more: a submit that cleared it off the main thread got there first. Called on the main
     * thread, from the form's disposal.
     */
    fun park(formId: String, owner: Any, formData: CardFormData, flags: CardFormFlags, activity: Activity) =
        park(formId, owner, ParkedForm(formData, flags), Screen.of(activity), activity.application)

    /** [park] for [screen]; [application] is watched until the card is taken or dropped. */
    fun park(formId: String, owner: Any, form: ParkedForm, screen: Screen, application: Application) {
        if (form.data.isEmpty) return
        synchronized(lock) {
            if (registrations[formId]?.owner !== owner || data[formId] == null) return
            parked[formId] = ParkedCard(form, screen)
            watchActivities(application)
        }
    }

    /**
     * The card and flags parked under [formId] for [screen], removed; null when there is none,
     * or when the one there was typed on another screen.
     */
    fun takeParked(formId: String, screen: Screen?): ParkedForm? = synchronized(lock) {
        val card = parked[formId] ?: return@synchronized null
        if (card.screen != screen) return@synchronized null
        parked.remove(formId)
        parkedChanged()
        card.form
    }

    /** Drops every card parked for [screen]: the user left it, it is gone, or it waited too long. */
    fun dropParked(screen: Screen) = synchronized(lock) {
        parked.values.removeAll { it.screen == screen }
        parkedChanged()
    }

    /** The recreated [screen] is up: what is parked for it waits [PARK_TIMEOUT_MS] at most. */
    private fun screenResumed(screen: Screen) = synchronized(lock) {
        if (screen in timeouts || parked.values.none { it.screen == screen }) return@synchronized
        timeouts[screen] = timer.schedule(PARK_TIMEOUT_MS) { dropParked(screen) }
    }

    private fun watchActivities(application: Application) {
        if (parkingWatch != null) return
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            // Not dropped at the recreated screen's first frame: a form in a bottom sheet or a
            // dialog composes in a window of its own, a frame or more after the activity. The
            // card waits up to ten seconds for it, or until the user leaves the screen. The activity
            // the card was parked by has paused already, so only the recreated one ends the wait.
            override fun onActivityResumed(activity: Activity) = screenResumed(Screen.of(activity))

            override fun onActivityPaused(activity: Activity) = dropParked(Screen.of(activity))

            override fun onActivityDestroyed(activity: Activity) {
                if (!activity.isChangingConfigurations) dropParked(Screen.of(activity))
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        parkingWatch = application to callbacks
    }

    /**
     * After a card was taken or dropped, under the lock: cancels the timeout of a screen with
     * nothing left waiting, and stops watching the activities once nothing is parked.
     */
    private fun parkedChanged() {
        timeouts.keys.filter { screen -> parked.values.none { it.screen == screen } }
            .forEach { timeouts.remove(it)?.invoke() }
        if (parked.isNotEmpty()) return
        parkingWatch?.let { (application, callbacks) -> application.unregisterActivityLifecycleCallbacks(callbacks) }
        parkingWatch = null
    }

    /** Which forms hold data, in order of last sync. */
    @VisibleForTesting
    fun storedFormIds(): Set<String> = synchronized(lock) { data.keys.toSet() }

    /** Which forms have a card parked for a recreated activity. */
    @VisibleForTesting
    fun parkedFormIds(): Set<String> = synchronized(lock) { parked.keys.toSet() }
}
