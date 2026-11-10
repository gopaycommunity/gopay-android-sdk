package cz.gopay.sdk.ui

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import cz.gopay.sdk.telemetry.Telemetry
import kotlinx.coroutines.CancellationException

/**
 * The submit of a [PaymentCardForm], the body behind [GopaySDK.submitCardForm]: reads the SDK's
 * copy of the form, tells the form a submit ran, validates, encrypts with [encrypt], and empties the form once
 * the JWE exists. Reports no error: the public call it runs inside does. Reports the card form
 * steps of the submit: the press and the errors it found, before anything is checked, then how
 * the encryption ended, the visit's summary when the JWE was made or a failure the summary will
 * carry if nothing is typed after it. A cancellation is neither.
 *
 * @param formId the form to read, or the most recently active one when null.
 * @param encrypt `GopaySDK.encryptCardData` in production; a stub in tests.
 * @throws GopaySDKException [GopayErrorCodes.VALIDATION_INVALID_INPUT] with
 *         [NO_CARD_DATA_MESSAGE] when no form is on screen or the form is empty, and with
 *         [INVALID_CARD_FORM_DATA_MESSAGE] when what it holds fails validation; then whatever [encrypt] throws.
 */
internal suspend fun submitCardForm(formId: String?, encrypt: suspend (CardData) -> String): String {
    val (id, data) = CardFormStore.resolve(formId)
    val steps = CardFormStore.steps(id)
    // With the validity the pay button was pressed on, then the errors the press found, whether
    // or not the form shows them.
    steps?.submitRequested()
    CardFormStore.submitAttempted(id)
    steps?.submitted { (data ?: CardFormData()).progress() }
    val cardData = data?.takeUnless { it.isEmpty }?.takeIf { it.isValid }?.toCardData()
        ?: throw invalidCardForm(data)
    val jwe = try {
        encrypt(cardData)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        steps?.encryptFailed()
        throw e
    }
    // That the user submitted and the payload exists, timed from the form's ready: nothing about
    // the card, the JWE or its length.
    Telemetry.current?.submit(
        "card-form", GwLoggerTelemetry.Method.CARD, "card", flow = GwLoggerTelemetry.CARD_FORM_FLOW
    )
    // The visit ends here, with its summary, before the form is emptied.
    steps?.encrypted()
    // Only clear after encryption succeeded: on failure (e.g. key fetch offline) the user
    // shouldn't have to retype the card; authorization hasn't happened yet. Clearing also
    // empties the fields the form still shows, so the card does not outlive the JWE made
    // from it (PCI DSS 4.0.1, req. 3.3.1).
    CardFormStore.clear(id)
    return jwe
}

/**
 * The error for a form that cannot be submitted. An all-empty form is reported distinctly: it is
 * the expected state right after a successful encryption cleared the fields, and hosts key their
 * JWE-reuse retry on it.
 */
internal fun invalidCardForm(data: CardFormData?): GopaySDKException = GopaySDKException(
    errorCode = GopayErrorCodes.VALIDATION_INVALID_INPUT,
    message = if (data == null || data.isEmpty) {
        NO_CARD_DATA_MESSAGE
    } else {
        INVALID_CARD_FORM_DATA_MESSAGE
    }
)

/**
 * The message of a submit with no form on screen or with an all-empty one, typically after the
 * SDK cleared the fields following a successful encryption. Mirrors the iOS SDK's, word for word.
 */
internal const val NO_CARD_DATA_MESSAGE =
    "No card form data available. Please use GopayCardForm to enter card data."

/** The message of a submit of a form whose data fails validation. Mirrors the iOS SDK's. */
internal const val INVALID_CARD_FORM_DATA_MESSAGE =
    "Card form data is invalid. Please check all fields."
