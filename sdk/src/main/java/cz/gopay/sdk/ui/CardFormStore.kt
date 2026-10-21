package cz.gopay.sdk.ui

import androidx.annotation.VisibleForTesting
import cz.gopay.sdk.model.CardData
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
 * What a live [PaymentCardForm] lets the store do to it. Both run on the main thread.
 *
 * @param reset empties the visible fields, the edited flags and the submit-attempted flag.
 * @param submitAttempted tells the form a submit ran, so [CardFormValidationDisplay.ON_SUBMIT]
 *        shows its errors.
 */
internal class CardFormHandlers(
    val reset: () -> Unit,
    val submitAttempted: () -> Unit
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
 * the old one leaves, so a registration names its owner, the composition that made it, and a
 * form leaving takes only its own registration with it.
 *
 * One lock around the data and the handlers together: the form writes from the main thread,
 * `submitCardForm` reads and clears from whatever dispatcher resumes it. The handlers write
 * Compose state, so they run on the main thread, outside the lock.
 */
internal object CardFormStore {

    private class Registration(val owner: Any, val handlers: CardFormHandlers)

    private val lock = Any()

    /** In order of last sync: the last entry is the form a submit without an id reads. */
    private val data = LinkedHashMap<String, CardFormData>()
    private val registrations = HashMap<String, Registration>()

    /**
     * Runs the handlers. On `Dispatchers.Main.immediate`, resolved at each call rather than held
     * in a field, so a caller already on the main thread sees them done, and a test that swaps
     * the main dispatcher sees the swap whenever it happens.
     */
    private val handlerScope = CoroutineScope(SupervisorJob())

    /**
     * The form's current fields; called on every edit and once when the form appears, which
     * makes the form the most recent one either way.
     */
    fun update(formId: String, formData: CardFormData) = synchronized(lock) {
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
     */
    fun unregister(formId: String, owner: Any) {
        synchronized(lock) {
            if (registrations[formId]?.owner !== owner) return
            registrations.remove(formId)
            data.remove(formId)
        }
    }

    /**
     * The form a submit reads: [formId] when given, else the most recently active form, the one
     * that synced last. The data is null when the form is not on screen.
     */
    fun resolve(formId: String?): Pair<String?, CardFormData?> = synchronized(lock) {
        val id = formId ?: data.keys.lastOrNull()
        id to id?.let { data[it] }
    }

    /** Tells the form with [formId] that a submit ran, for [CardFormValidationDisplay.ON_SUBMIT]. */
    fun submitAttempted(formId: String?) {
        val handler = synchronized(lock) { formId?.let { registrations[it]?.handlers?.submitAttempted } }
        if (handler != null) handlerScope.launch(Dispatchers.Main.immediate) { handler() }
    }

    /**
     * Drops the stored data of [formId], or of every form when null, and empties the visible
     * fields of each form concerned through its reset handler, on the main thread. The data is
     * dropped again right before the resets run: a keystroke between the call and the hop to the
     * main thread would otherwise sync the old card back in behind the reset.
     */
    fun clear(formId: String?) {
        val (ids, resets) = synchronized(lock) {
            val ids = if (formId != null) listOf(formId) else (data.keys + registrations.keys).distinct()
            ids.forEach { data.remove(it) }
            ids to ids.mapNotNull { registrations[it]?.handlers?.reset }
        }
        if (resets.isEmpty()) return
        handlerScope.launch(Dispatchers.Main.immediate) {
            synchronized(lock) { ids.forEach { data.remove(it) } }
            resets.forEach { it() }
        }
    }

    /** Which forms hold data, in order of last sync. */
    @VisibleForTesting
    fun storedFormIds(): Set<String> = synchronized(lock) { data.keys.toSet() }
}
