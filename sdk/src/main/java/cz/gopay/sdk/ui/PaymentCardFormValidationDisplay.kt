package cz.gopay.sdk.ui

/**
 * Whether and when [PaymentCardForm] renders localized inline validation errors beneath its
 * fields. The messages come from the form's resolved [cz.gopay.sdk.locales.GopayLocaleStrings].
 * `PaymentCardFormValidationDisplay` on iOS too; 2.x called it `CardFormValidationDisplay`.
 */
enum class PaymentCardFormValidationDisplay {
    /**
     * Render no inline errors; display is host-driven (the default, and the only behaviour
     * before 2.2). Read the localized strings with `GopaySDK.currentLocaleStrings(...)` to show
     * your own, from the `VALIDATION_008` or `VALIDATION_007` a submit throws.
     */
    HIDDEN,

    /**
     * Show a field's error live, as soon as it has been edited and holds non-empty, invalid
     * content. An empty field is never flagged while typing.
     */
    LIVE,

    /**
     * Show errors only while the form's `submitAttempted` flag is `true`: an empty required
     * field shows the locale's `requiredErrorMessage`, an invalid one the field's pattern
     * message. The SDK raises the flag itself when [cz.gopay.sdk.GopaySDK.submitCardForm] is
     * called for the form, before it judges the card, and lowers it when the form empties its
     * fields, after a successful submit or [cz.gopay.sdk.GopaySDK.clearCardFormData]. Pass
     * [PaymentCardForm] a `submitAttempted` and an `onSubmitAttemptedChange` to raise or lower
     * it yourself. `.onSubmit` on iOS, with the same flag as a binding.
     */
    ON_SUBMIT
}

/** The 2.x name of [PaymentCardFormValidationDisplay]. */
@Deprecated(
    "Renamed to PaymentCardFormValidationDisplay, the name the iOS SDK uses",
    ReplaceWith("PaymentCardFormValidationDisplay")
)
typealias CardFormValidationDisplay = PaymentCardFormValidationDisplay
