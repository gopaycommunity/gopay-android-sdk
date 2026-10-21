package cz.gopay.sdk.ui

/**
 * Whether and when [PaymentCardForm] renders localized inline validation errors beneath its
 * fields. The messages come from the form's resolved [cz.gopay.sdk.locales.GopayLocaleStrings].
 * Mirrors `GopayCardFormValidationDisplay` on iOS.
 */
enum class CardFormValidationDisplay {
    /**
     * Render no inline errors; display is host-driven (the default, and the only behaviour
     * before 2.2). Read the localized strings with `GopaySDK.currentLocaleStrings(...)` to show
     * your own, from `onValidationError` or from the `VALIDATION_007` a submit throws.
     */
    HIDDEN,

    /**
     * Show a field's error live, as soon as it has been edited and holds non-empty, invalid
     * content. An empty field is never flagged while typing.
     */
    LIVE,

    /**
     * Show errors only once a submit was attempted, through
     * [cz.gopay.sdk.GopaySDK.submitCardForm] or the form's own submit function: an empty
     * required field shows the locale's `requiredErrorMessage`, an invalid one the field's
     * pattern message. The form hides them again when it empties its fields, after a
     * successful submit or [cz.gopay.sdk.GopaySDK.clearCardFormData].
     */
    ON_SUBMIT
}
