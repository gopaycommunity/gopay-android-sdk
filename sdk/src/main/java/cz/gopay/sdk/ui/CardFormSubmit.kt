package cz.gopay.sdk.ui

import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.ErrorReporter
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.ui.utils.CardValidator
import kotlinx.coroutines.CancellationException

/**
 * The submit of a [PaymentCardForm], the one body behind [GopaySDK.submitCardForm] and the
 * older submit function the form hands to `onFormReady`: reads the SDK's copy of the form,
 * tells the form a submit ran, validates, encrypts with [encrypt], and empties the form once
 * the JWE exists. Reports nothing: the public call it runs inside does.
 *
 * @param formId the form to read, or the most recently active one when null.
 * @param encrypt `GopaySDK.encryptCardData` in production; a stub in tests.
 * @throws GopaySDKException [GopayErrorCodes.VALIDATION_INVALID_INPUT] with
 *         [CardEncryptionResult.NO_CARD_DATA_MESSAGE] when no form is on screen or the form is
 *         empty, and with [CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE] when what it
 *         holds fails validation; then whatever [encrypt] throws.
 */
internal suspend fun submitCardForm(formId: String?, encrypt: suspend (CardData) -> String): String {
    val (id, data) = CardFormStore.resolve(formId)
    CardFormStore.submitAttempted(id)
    val cardData = data?.takeUnless { it.isEmpty }?.takeIf { it.isValid }?.toCardData()
        ?: throw invalidCardForm(data)
    val jwe = encrypt(cardData)
    // Only clear after encryption succeeded: on failure (e.g. key fetch offline) the user
    // shouldn't have to retype the card; authorization hasn't happened yet. Clearing also
    // empties the fields the form still shows, so the card does not outlive the JWE made
    // from it (PCI DSS 4.0.1, req. 3.3.1).
    CardFormStore.clear(id)
    return jwe
}

/**
 * The older submit path, behind the function [PaymentCardForm] hands to `onFormReady`:
 * [submitCardForm] reported as a [CardEncryptionResult] instead of thrown.
 *
 * A form that fails validation is a [GopayErrorCodes.VALIDATION_INVALID_INPUT] in the result,
 * with [CardEncryptionResult.NO_CARD_DATA_MESSAGE] for an all-empty form and
 * [CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE] otherwise, and reaches
 * `errorCallback` once like an error the SDK throws; so does whatever [encrypt] throws, from
 * its own entry point. A cancellation is rethrown: the host left the screen or cancelled the
 * submit while the key was being fetched, which is not a failed encryption.
 */
internal suspend fun submitCardDataImpl(
    formId: String,
    onEncryptionComplete: (CardEncryptionResult) -> Unit,
    onValidationError: ((CardValidator.CardValidationResult) -> Unit)?,
    encrypt: suspend (CardData) -> String = { GopaySDK.getInstance().encryptCardData(it) }
): CardEncryptionResult {
    return try {
        // The field-by-field result the older path reports, before the shared submit refuses.
        val validation = (CardFormStore.resolve(formId).second ?: CardFormData()).validate()
        if (!validation.isAllValid) onValidationError?.invoke(validation)

        val result = CardEncryptionResult.Success(submitCardForm(formId, encrypt))
        onEncryptionComplete(result)
        result
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Returned rather than thrown, so reported here, as the public entry point would have.
        if (e is GopaySDKException) ErrorReporter.report(e)
        val result = CardEncryptionResult.Error(
            message = e.message ?: "Card encryption failed",
            exception = e
        )
        onEncryptionComplete(result)
        result
    }
}

/**
 * The error for a form that cannot be submitted. An all-empty form is reported distinctly: it is
 * the expected state right after a successful encryption cleared the fields, and hosts key their
 * JWE-reuse retry on it.
 */
internal fun invalidCardForm(data: CardFormData?): GopaySDKException = GopaySDKException(
    errorCode = GopayErrorCodes.VALIDATION_INVALID_INPUT,
    message = if (data == null || data.isEmpty) {
        CardEncryptionResult.NO_CARD_DATA_MESSAGE
    } else {
        CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE
    }
)
