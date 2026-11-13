package cz.gopay.sdk.ui

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardMetadata
import cz.gopay.sdk.model.EncryptedCardPayloadWithMetadata
import cz.gopay.sdk.model.GopayCardData
import cz.gopay.sdk.telemetry.GwLoggerTelemetry
import cz.gopay.sdk.telemetry.Telemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive

/**
 * The submit of a [PaymentCardForm], the body behind [GopaySDK.submitCardForm]: reads the SDK's
 * copy of the form, tells the form a submit ran, validates, encrypts with [encrypt], and empties
 * the form once the JWE exists. Reports no error: the public call it runs inside does. Reports the card form
 * steps of the submit: the press and the errors it found, before anything is checked, then how
 * the encryption ended, the visit's summary when the JWE was made or a failure the summary will
 * carry if nothing is typed after it. A cancellation is neither.
 *
 * @param formId the form to read, or the most recently active one when null.
 * @param encrypt `GopaySDK.encryptCardData` in production; a stub in tests.
 * @throws GopaySDKException [GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY] when no form is on
 *         screen or the form is empty, [GopayErrorCodes.VALIDATION_INVALID_INPUT] when what it
 *         holds fails validation; then whatever [encrypt] throws.
 */
internal suspend fun submitCardForm(formId: String?, encrypt: suspend (GopayCardData) -> String): String =
    submitForm(formId, GwLoggerTelemetry.CARD_FORM_FLOW, encrypt)

/**
 * The body behind [GopaySDK.submitCardFormWithMetadata]: [submitCardForm] with the card's
 * metadata looked up beside the encryption. Both start together and the result is returned once
 * both have ended, so a slow lookup can hold the result back, up to its own limit of 3 seconds
 * from the start.
 *
 * The lookup decides nothing: whatever it does, [lookUp] answers null and the JWE is returned.
 * An encryption that fails cancels the lookup and throws what [submitCardForm] would throw, with
 * the card left in the form. A cancellation of the caller ends both and leaves the card too, also
 * when only the lookup is still running. The form is validated, reported and emptied exactly as
 * there.
 *
 * @param lookUp `CardMetadataLookup.lookUp` in production, which keeps its own time limit and
 *        throws nothing but the caller's cancellation; given the card number, it sends only
 *        the first eight digits.
 */
internal suspend fun submitCardFormWithMetadata(
    formId: String?,
    encrypt: suspend (GopayCardData) -> String,
    lookUp: suspend (cardNumber: String) -> CardMetadata?
): EncryptedCardPayloadWithMetadata =
    submitForm(formId, GwLoggerTelemetry.CARD_FORM_WITH_METADATA_FLOW) { cardData ->
        coroutineScope {
            val metadata = async {
                try {
                    lookUp(cardData.cardPan)
                } catch (e: CancellationException) {
                    // The submit's own cancellation goes on. One from anywhere else is a lookup
                    // that did not answer, and never the submit's failure.
                    ensureActive()
                    null
                } catch (e: Throwable) {
                    // The production lookup throws no failure; one is never the submit's anyway.
                    null
                }
            }
            val jwe = try {
                encrypt(cardData)
            } catch (e: Throwable) {
                // The scope would cancel the lookup on its own once this failure leaves it; said
                // here because it is the rule: no JWE, no lookup.
                metadata.cancel()
                throw e
            }
            EncryptedCardPayloadWithMetadata(encryptedPayload = jwe, cardMetadata = metadata.await())
        }
    }

/** The submit both public calls share; [flow] is what the operational data calls this one. */
private suspend fun <T> submitForm(
    formId: String?,
    flow: String,
    encrypt: suspend (GopayCardData) -> T
): T {
    val (id, data) = CardFormStore.resolve(formId)
    val steps = CardFormStore.steps(id)
    // With the validity the pay button was pressed on, then the errors the press found, whether
    // or not the form shows them.
    steps?.submitRequested()
    CardFormStore.submitAttempted(id)
    steps?.submitted { (data ?: CardFormData()).progress() }
    val cardData = data?.takeUnless { it.isEmpty }?.takeIf { it.isValid }?.toCardData()
        ?: throw invalidCardForm(data)
    val result = try {
        encrypt(cardData)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        steps?.encryptFailed()
        throw e
    }
    // That the user submitted and the payload exists, timed from the form's ready: nothing about
    // the card, the JWE or its length.
    Telemetry.current?.submit("card-form", GwLoggerTelemetry.Method.CARD, "card", flow = flow)
    // The visit ends here, with its summary, before the form is emptied.
    steps?.encrypted()
    // Only clear after encryption succeeded: on failure (e.g. key fetch offline) the user
    // shouldn't have to retype the card; authorization hasn't happened yet. Clearing also
    // empties the fields the form still shows, so the card does not outlive the JWE made
    // from it (PCI DSS 4.0.1, req. 3.3.1).
    CardFormStore.clear(id)
    return result
}

/**
 * The error for a form that cannot be submitted. An all-empty form has a code of its own,
 * [GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY]: it is the expected state right after a successful
 * encryption cleared the fields, and hosts key their "enter the card again" prompt on it. The
 * iOS SDK throws the same two codes with the same two messages.
 */
internal fun invalidCardForm(data: CardFormData?): GopaySDKException =
    if (data == null || data.isEmpty) {
        GopaySDKException(code = GopayErrorCodes.VALIDATION_CARD_FORM_EMPTY, message = NO_CARD_DATA_MESSAGE)
    } else {
        GopaySDKException(code = GopayErrorCodes.VALIDATION_INVALID_INPUT, message = INVALID_CARD_FORM_DATA_MESSAGE)
    }

/**
 * The message of a submit with no form on screen or with an all-empty one, typically after the
 * SDK cleared the fields following a successful encryption. Mirrors the iOS SDK's, word for word.
 */
internal const val NO_CARD_DATA_MESSAGE =
    "No card form data available. Please use PaymentCardForm to enter card data."

/** The message of a submit of a form whose data fails validation. Mirrors the iOS SDK's. */
internal const val INVALID_CARD_FORM_DATA_MESSAGE =
    "Card form data is invalid. Please check all fields."
