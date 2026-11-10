package cz.gopay.sdk.ui

import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.ui.utils.CardNumberInputValidator
import cz.gopay.sdk.ui.utils.CvvValidator
import cz.gopay.sdk.ui.utils.ExpirationDateInputValidator

/**
 * Which localized validation errors [PaymentCardForm] shows under its fields, as a pure
 * computation so the display rules can be unit-tested. Mirrors `GopayCardFormErrors` on iOS:
 * what is shown depends on the [PaymentCardFormValidationDisplay] mode, on whether a submit was
 * attempted, and on how far the user has got with the field.
 *
 * @property cardNumber the card-number error, or null when nothing is shown.
 * @property expiration the expiration error, or null when nothing is shown.
 * @property cvv the CVV error, or null when nothing is shown.
 */
internal data class CardFormErrors(
    val cardNumber: String?,
    val expiration: String?,
    val cvv: String?
) {
    companion object {
        val NONE = CardFormErrors(null, null, null)

        /**
         * Whether a focus change means the user left a field: it had the focus, lost it, and the
         * user had typed in it. Not the first report a field gets, unfocused, as it appears, and
         * not the focus going away because the activity is being recreated
         * ([changingConfigurations]).
         */
        fun isLeft(
            wasFocused: Boolean,
            isFocused: Boolean,
            edited: Boolean,
            changingConfigurations: Boolean
        ): Boolean = wasFocused && !isFocused && edited && !changingConfigurations

        /**
         * In [PaymentCardFormValidationDisplay.LIVE] a field that holds something invalid is flagged
         * once the user is done with it, not while it is first being typed: when it holds its
         * full length and still fails (a card number that fails its check, an expired date),
         * when the user has typed in it and left it, or once a submit ran. Once flagged, a field
         * stays flagged while the user goes back to fix it, until it is valid or the form is
         * emptied. An empty field is never flagged in LIVE; only
         * [PaymentCardFormValidationDisplay.ON_SUBMIT] says "required". The same rule as the iOS form's
         * `.live`.
         *
         * @param data what the fields hold.
         * @param strings the locale the messages come from.
         * @param mode when errors are shown at all.
         * @param submitAttempted whether a submit ran since the fields were last emptied.
         * @param cardNumberLeft whether the user has typed in the card number and left it since;
         *        read only in [PaymentCardFormValidationDisplay.LIVE].
         * @param expirationLeft the same for the expiration.
         * @param cvvLeft the same for the CVV.
         */
        fun of(
            data: CardFormData,
            strings: GopayLocaleStrings,
            mode: PaymentCardFormValidationDisplay,
            submitAttempted: Boolean,
            cardNumberLeft: Boolean,
            expirationLeft: Boolean,
            cvvLeft: Boolean
        ): CardFormErrors {
            if (mode == PaymentCardFormValidationDisplay.HIDDEN) return NONE
            val validation = data.validate()
            fun message(left: Boolean, isFull: Boolean, isEmpty: Boolean, isValid: Boolean, pattern: String): String? =
                if (mode == PaymentCardFormValidationDisplay.LIVE) {
                    // Live: never nag about an empty field or one still being typed, only about
                    // invalid content the user is done with.
                    if (!isEmpty && !isValid && (isFull || left || submitAttempted)) pattern else null
                } else {
                    when {
                        !submitAttempted || isValid -> null
                        isEmpty -> strings.requiredErrorMessage
                        else -> pattern
                    }
                }
            return CardFormErrors(
                cardNumber = message(
                    left = cardNumberLeft,
                    isFull = CardNumberInputValidator.isValidLength(data.cardNumberDigits),
                    isEmpty = data.cardNumberDigits.isEmpty(),
                    isValid = validation.cardNumber.isValid,
                    pattern = strings.panErrorPattern
                ),
                expiration = message(
                    left = expirationLeft,
                    isFull = ExpirationDateInputValidator.isComplete(data.expirationDigits),
                    isEmpty = data.expirationDigits.isEmpty(),
                    isValid = validation.expirationDate.isValid,
                    pattern = strings.expErrorPattern
                ),
                cvv = message(
                    left = cvvLeft,
                    isFull = CvvValidator.isComplete(data.cvv),
                    isEmpty = data.cvv.isEmpty(),
                    isValid = validation.cvv.isValid,
                    pattern = strings.cvvErrorPattern
                )
            )
        }
    }
}
