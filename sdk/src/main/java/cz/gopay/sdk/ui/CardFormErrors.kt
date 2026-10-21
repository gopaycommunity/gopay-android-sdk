package cz.gopay.sdk.ui

import cz.gopay.sdk.locales.GopayLocaleStrings

/**
 * Which localized validation errors [PaymentCardForm] shows under its fields, as a pure
 * computation so the display rules can be unit-tested. Mirrors `GopayCardFormErrors` on iOS:
 * what is shown depends on the [CardFormValidationDisplay] mode, on whether a submit was
 * attempted, and on whether the user has edited the field at all.
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
         * @param data what the fields hold.
         * @param strings the locale the messages come from.
         * @param mode when errors are shown at all.
         * @param submitAttempted whether a submit ran since the fields were last emptied; read
         *        only in [CardFormValidationDisplay.ON_SUBMIT].
         * @param cardNumberEdited whether the user has typed in the card number; read only in
         *        [CardFormValidationDisplay.LIVE], which never flags a field left alone.
         * @param expirationEdited the same for the expiration.
         * @param cvvEdited the same for the CVV.
         */
        fun of(
            data: CardFormData,
            strings: GopayLocaleStrings,
            mode: CardFormValidationDisplay,
            submitAttempted: Boolean,
            cardNumberEdited: Boolean,
            expirationEdited: Boolean,
            cvvEdited: Boolean
        ): CardFormErrors {
            if (mode == CardFormValidationDisplay.HIDDEN) return NONE
            val validation = data.validate()
            fun message(edited: Boolean, isEmpty: Boolean, isValid: Boolean, pattern: String): String? =
                if (mode == CardFormValidationDisplay.LIVE) {
                    // Live: never nag about an empty field, only about invalid content being typed.
                    if (edited && !isEmpty && !isValid) pattern else null
                } else {
                    when {
                        !submitAttempted || isValid -> null
                        isEmpty -> strings.requiredErrorMessage
                        else -> pattern
                    }
                }
            return CardFormErrors(
                cardNumber = message(
                    edited = cardNumberEdited,
                    isEmpty = data.cardNumberDigits.isEmpty(),
                    isValid = validation.cardNumber.isValid,
                    pattern = strings.panErrorPattern
                ),
                expiration = message(
                    edited = expirationEdited,
                    isEmpty = data.expirationDigits.isEmpty(),
                    isValid = validation.expirationDate.isValid,
                    pattern = strings.expErrorPattern
                ),
                cvv = message(
                    edited = cvvEdited,
                    isEmpty = data.cvv.isEmpty(),
                    isValid = validation.cvv.isValid,
                    pattern = strings.cvvErrorPattern
                )
            )
        }
    }
}
