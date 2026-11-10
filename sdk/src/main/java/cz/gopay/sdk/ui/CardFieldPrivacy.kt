package cz.gopay.sdk.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.semantics.contentDataType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import cz.gopay.sdk.ui.utils.ExpirationDateInputValidator

/** The three inputs of the card form. */
internal enum class CardField { CARD_NUMBER, EXPIRATION, CVV }

/** What the autofill framework is told a card form field holds. */
internal enum class CardFieldAutofill { NONE, CARD_NUMBER, EXPIRATION_DATE }

/**
 * What the card form tells the system about each field, mirroring the iOS `PaymentCardForm`.
 *
 * Autofill is off unless the host passes `allowsAutofill = true`. Off, every field is declared
 * unfillable to autofill services, with no hints. On, the card number and the expiry are announced
 * as such; the CVV is never, on either platform.
 *
 * No field carries password semantics, the CVV included: a password field is where a password
 * manager offers its logins, which one did on the CVV, and the declared data type already keeps
 * the field out of autofill. The CVV is masked by its visual transformation instead, and its
 * keyboard is told not to learn from it. The card number stays readable, to the user and to
 * TalkBack alike, so the user can check what they copied from the card. No label is taken out of
 * the accessibility tree. iOS keeps its secure text entry on the CVV, where a password manager
 * cannot reach a field without a content type.
 */
internal object CardFieldPrivacy {

    fun autofillFor(field: CardField, allowsAutofill: Boolean): CardFieldAutofill =
        if (!allowsAutofill) {
            CardFieldAutofill.NONE
        } else {
            when (field) {
                CardField.CARD_NUMBER -> CardFieldAutofill.CARD_NUMBER
                CardField.EXPIRATION -> CardFieldAutofill.EXPIRATION_DATE
                CardField.CVV -> CardFieldAutofill.NONE
            }
        }
}

/**
 * The semantics [CardFieldPrivacy] decides for [field].
 *
 * Compose has no per-field `importantForAutofill`; the view-level one would reach every field of
 * the host's screen, not just the form's. The field's data type is the switch instead:
 * [ContentDataType.None] declares it unfillable. That is a declaration, not a barrier: focusing the
 * field still starts an autofill request, and a service that ignores the declared type can still
 * see the field. It has to be set here, outside the text field, because the text field sets
 * [ContentDataType.Text] on its own and the outer modifier wins.
 */
internal fun Modifier.cardFieldSemantics(field: CardField, allowsAutofill: Boolean): Modifier =
    semantics {
        when (CardFieldPrivacy.autofillFor(field, allowsAutofill)) {
            CardFieldAutofill.NONE -> contentDataType = ContentDataType.None
            CardFieldAutofill.CARD_NUMBER -> contentType = ContentType.CreditCardNumber
            CardFieldAutofill.EXPIRATION_DATE -> contentType = ContentType.CreditCardExpirationDate
        }
    }

/**
 * Asks the keyboard not to learn from what is typed in [content]: no personalized suggestions,
 * no words added to its dictionary (`IME_FLAG_NO_PERSONALIZED_LEARNING`, honoured from API 26).
 *
 * `KeyboardOptions` cannot carry the flag, its platform options hold a string for the keyboard,
 * not `imeOptions` bits, so it is added to the `EditorInfo` on the way to the keyboard. This is
 * the use Compose documents the interceptor for. It is experimental API; it changes nothing
 * outside [content].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NoPersonalizedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val withoutLearning = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions =
                        outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            }
            nextHandler.startInputMethod(withoutLearning)
        },
        content = content
    )
}

/**
 * The expiry digits the form keeps from what arrives in the field, by the same rule as the iOS
 * form's `CardTextFormatter.expirationDigits`.
 *
 * Typing reaches the field one digit at a time, so it never holds more than four digits and the
 * one just typed, which is cut off as before. An autofill service or a paste can bring the whole
 * date at once with a four-digit year, `12/2027` or `122027`; the plain digit filter reads that as
 * `1220`, an expired card. So the input is split into runs of digits: two runs, the first of one or
 * two digits and the second of four, are month and year; a single run of six digits is the same
 * without a separator. Everything else goes through
 * [ExpirationDateInputValidator.validateInput] unchanged.
 */
internal object ExpiryEntry {

    private val digitRun = Regex("""\d+""")

    fun digits(input: String): String {
        val runs = digitRun.findAll(input).map { it.value }.toList()
        if (runs.size == 2 && runs[0].length in 1..2 && runs[1].length == 4) {
            return runs[0].padStart(2, '0') + runs[1].takeLast(2)
        }
        if (runs.size == 1 && runs[0].length == 6) {
            return runs[0].take(2) + runs[0].takeLast(2)
        }
        return ExpirationDateInputValidator.validateInput(input)
    }
}
