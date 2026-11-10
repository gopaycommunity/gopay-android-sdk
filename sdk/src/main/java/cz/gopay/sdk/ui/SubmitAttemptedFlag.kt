package cz.gopay.sdk.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether a submit was attempted on a [PaymentCardForm] since its fields were last emptied, the
 * flag [PaymentCardFormValidationDisplay.ON_SUBMIT] and [PaymentCardFormValidationDisplay.LIVE]
 * read.
 *
 * The host may drive it: when it passes the form a `submitAttempted`, that value is the flag, as
 * the host's binding is on iOS, and the SDK asks for a change through `onSubmitAttemptedChange`.
 * When it passes none, the form keeps the flag here.
 *
 * @param initial the flag a form parked across a configuration change left behind.
 */
internal class SubmitAttemptedFlag(initial: Boolean = false) {

    /** The form's own flag: the whole truth without a host value, a shadow of it with one. */
    private var own by mutableStateOf(initial)

    /** The flag as the form shows it: [host] when the host drives it, the form's own otherwise. */
    fun value(host: Boolean?): Boolean = host ?: own

    /**
     * The SDK raising the flag, on `submitCardForm`, or lowering it, when the fields are emptied.
     * [onChange] hears of it only when the flag the form shows is not [newValue] already.
     */
    fun set(newValue: Boolean, host: Boolean?, onChange: ((Boolean) -> Unit)?) {
        val shown = value(host)
        own = newValue
        if (shown != newValue) onChange?.invoke(newValue)
    }

    /** The form leaving the composition: its own flag goes with the card, the host hears nothing. */
    fun forget() {
        own = false
    }
}
