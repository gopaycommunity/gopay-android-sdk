package cz.gopay.sdk.locales

/**
 * Localized strings for the payment card form ([cz.gopay.sdk.ui.PaymentCardForm]).
 *
 * A locale must provide every field — there are no defaults, so a partially translated locale
 * cannot leak an untranslated fallback string into the middle of an otherwise localized form.
 *
 * The label / placeholder / error-pattern fields mirror the shared GoPay web SDK locale keys;
 * [panPlaceholder], [cvvLabel] and [cvvPlaceholder] are additional fields the native form renders
 * that the web key set does not localize (they are kept constant across the built-in locales).
 *
 * To supply your own translation, build a [GopayLocaleStrings] with the same structure and register
 * it via [GopayLocales.register] (or `GopayConfig.customLocales`), then select it by code.
 */
data class GopayLocaleStrings(
    /** Card number field label. Web key `cc.pan.label`. */
    val panLabel: String,
    /** Card number field placeholder (not localized in the web SDK). */
    val panPlaceholder: String,
    /** Expiration field label. Web key `cc.exp.label`. */
    val expLabel: String,
    /** Expiration field placeholder, e.g. `MM/YY`. Web key `cc.exp.placeholder`. */
    val expPlaceholder: String,
    /** CVV field label (not localized in the web SDK). */
    val cvvLabel: String,
    /** CVV field placeholder (not localized in the web SDK). */
    val cvvPlaceholder: String,
    /** Pay-button label. Web key `cc.pay`. The SDK form has no button; exposed for host apps. */
    val pay: String,
    /** Invalid card-number error message. Web key `cc.pan.error.pattern`. */
    val panErrorPattern: String,
    /** Invalid expiration error message. Web key `cc.exp.error.pattern`. */
    val expErrorPattern: String,
    /** Invalid CVV error message. Web key `cc.cvv.error.pattern`. */
    val cvvErrorPattern: String,
    /** Generic "value has the wrong format" message. Web key `cc.patternErrorMessage`. */
    val patternErrorMessage: String,
    /** Generic "this field is required" message. Web key `cc.requiredErrorMessage`. */
    val requiredErrorMessage: String,
)
