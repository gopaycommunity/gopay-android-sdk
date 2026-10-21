package cz.gopay.sdk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.ui.utils.CardNumberInputValidator
import cz.gopay.sdk.ui.utils.CardNumberMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CardNumberVisualTransformation
import cz.gopay.sdk.ui.utils.CardValidator
import cz.gopay.sdk.ui.utils.CvvMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CvvValidator
import cz.gopay.sdk.ui.utils.ExpirationDateInputValidator
import cz.gopay.sdk.ui.utils.ExpirationDateVisualTransformation
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Result of card encryption performed by the form.
 *
 * The form encrypts the entered card data into a JWE that the host app forwards to the merchant
 * backend; the merchant backend then calls `POST /cards/tokens` (which requires merchant
 * credentials and so cannot run on-device). The SDK never sees the resulting card token.
 */
sealed class CardEncryptionResult {
    /** JWE compact serialization (RFC 7516) suitable for submission to the merchant backend. */
    data class Success(val jwe: String) : CardEncryptionResult()

    /**
     * @param exception what went wrong: a [GopaySDKException] with
     *        [GopayErrorCodes.VALIDATION_INVALID_INPUT] for a form that failed validation, or the
     *        exception `encryptCardData` threw. It reached `GopayConfig.errorCallback` once, as
     *        an error the SDK throws would have.
     */
    data class Error(val message: String, val exception: Throwable? = null) : CardEncryptionResult()

    companion object {
        /**
         * [Error.message] when submit ran on a completely empty form — typically after the SDK
         * cleared the fields following a successful encryption (GPMOB-140). Hosts can match on
         * this to show a "please re-enter your card" prompt: the gateway accepts each JWE only
         * once, so a retry needs a fresh entry, not a replay of the previous JWE.
         * [GopaySDK.submitCardForm] throws it as the message of its
         * [GopayErrorCodes.VALIDATION_INVALID_INPUT] when no form is on screen or the form is
         * empty. Mirrors the iOS SDK's `GopaySDKErrors.noCardFormData`, word for word.
         */
        const val NO_CARD_DATA_MESSAGE =
            "No card form data available. Please use GopayCardForm to enter card data."

        /**
         * [Error.message], and the message of the [GopayErrorCodes.VALIDATION_INVALID_INPUT]
         * from [GopaySDK.submitCardForm], when the form holds something that fails validation.
         * Mirrors the iOS SDK's `GopaySDKErrors.invalidCardFormData`, word for word.
         */
        const val INVALID_CARD_FORM_DATA_MESSAGE =
            "Card form data is invalid. Please check all fields."
    }
}

/**
 * Input field configuration for custom labels and helper texts
 */
data class InputFieldConfig(
    val label: String,
    val helperText: String? = null,
    val errorText: String? = null,
    val hasError: Boolean = false,
    val placeholder: String? = null
)

/**
 * Configuration for all input fields in the payment form
 */
data class PaymentFormInputs(
    val cardNumber: InputFieldConfig = InputFieldConfig(label = "Card Number", placeholder = "1234 1234 1234 1234"),
    val expirationDate: InputFieldConfig = InputFieldConfig(label = "MM/YY", placeholder = "MM/YY"),
    val cvv: InputFieldConfig = InputFieldConfig(label = "CVV", placeholder = "123")
) {
    companion object {
        /**
         * Builds a [PaymentFormInputs] from a [GopayLocaleStrings], populating each field's label
         * and placeholder from the locale. Error / helper texts are left unset — validation errors
         * remain host-driven (see [PaymentCardForm]'s `onValidationError`), unless the form's
         * `validation` mode renders them.
         */
        fun from(strings: GopayLocaleStrings): PaymentFormInputs = PaymentFormInputs(
            cardNumber = InputFieldConfig(label = strings.panLabel, placeholder = strings.panPlaceholder),
            expirationDate = InputFieldConfig(label = strings.expLabel, placeholder = strings.expPlaceholder),
            cvv = InputFieldConfig(label = strings.cvvLabel, placeholder = strings.cvvPlaceholder)
        )
    }
}

/**
 * A secure payment card form that handles card data input and JWE encryption.
 *
 * The form never exposes raw card data to the parent — it validates input and encrypts it into a
 * JWE that the parent forwards to its merchant backend. The backend (which holds merchant
 * credentials) then calls `POST /cards/tokens` to obtain the final card token.
 *
 * Submitting, the shared way: the form keeps the SDK's copy of its fields under [formId], and
 * [GopaySDK.submitCardForm] encrypts that copy into the JWE, the model the iOS `GopayCardForm`
 * uses. After a successful submit the SDK's copy is dropped **and the visible fields are
 * emptied**, so the card does not outlive the JWE made from it; a failed encryption leaves
 * everything as the user typed it, since nothing has been authorized yet. The copy also goes
 * when the form leaves the composition, and on demand through [GopaySDK.clearCardFormData].
 * The older way, the submit function from [onFormReady] reporting through
 * [onEncryptionComplete] and [onValidationError], keeps working and does the same.
 *
 * Security:
 * - Unless the host app is debuggable, the form sets `FLAG_SECURE` on the window it is drawn in,
 *   a dialog or bottom sheet included, to prevent screen capture, and takes it off when it
 *   leaves. A window that had the flag before keeps it.
 * - Card data is validated locally and JWE-encrypted using the merchant's public key, fetched
 *   from `GET /cards/public-key` with shareable-key auth on every submit. The JWE's issue time
 *   is the gateway's, read from that response's `Date` header, not the device clock. Without a
 *   usable header the device clock is used, with a warning in the debug log, as in
 *   [GopaySDK.encryptCardData].
 *
 * Localization: field labels and placeholders default to the locale resolved from [locale] /
 * [localeStrings] (falling back to the SDK-wide default, then the device language, then Czech).
 * Pass [inputFields] to override the resolved labels entirely.
 *
 * @param onEncryptionComplete Callback invoked with the JWE (success) or an error, by the submit
 *                             function from [onFormReady]. Not by [GopaySDK.submitCardForm],
 *                             which returns the JWE and throws instead; leave it out then.
 * @param modifier Modifier for the form layout.
 * @param onFormReady Provides a submit function for external triggering; the same as calling
 *                    [GopaySDK.submitCardForm] with this form's [formId], reported as a
 *                    [CardEncryptionResult] rather than thrown.
 * @param onValidationError Invoked when validation errors are present, by the submit function
 *                          from [onFormReady].
 * @param locale Locale code (e.g. `"cs"`, `"de"`) for the field labels. `null` uses the SDK default.
 *              Ignored when [localeStrings] or [inputFields] is supplied.
 * @param localeStrings Explicit locale strings to use, bypassing [locale] resolution.
 * @param inputFields Configuration for input field labels, helper texts, error states. When `null`
 *                    (default) the labels are derived from the resolved locale.
 * @param theme Theme configuration for customizing the form appearance.
 * @param validation Whether and when the form renders localized inline validation errors
 *                   beneath each field, from the resolved locale strings. The default,
 *                   [CardFormValidationDisplay.HIDDEN], keeps error display host-driven. An
 *                   error [inputFields] sets shows while the form has none of its own to show.
 * @param formId Identifies this form to [GopaySDK.submitCardForm] and
 *               [GopaySDK.clearCardFormData] when several are on screen; generated when null.
 *               A submit without an id reads the most recently active form.
 */
// The parameter order is the 2.1 one, with onEncryptionComplete first for the hosts that pass it
// by position; giving it a default puts modifier second among the optional ones.
@Suppress("ModifierParameter")
@Composable
fun PaymentCardForm(
    onEncryptionComplete: (CardEncryptionResult) -> Unit = {},
    modifier: Modifier = Modifier,
    onFormReady: ((suspend () -> CardEncryptionResult) -> Unit)? = null,
    onValidationError: ((CardValidator.CardValidationResult) -> Unit)? = null,
    locale: String? = null,
    localeStrings: GopayLocaleStrings? = null,
    inputFields: PaymentFormInputs? = null,
    theme: PaymentCardFormTheme = PaymentCardFormTheme(),
    validation: CardFormValidationDisplay = CardFormValidationDisplay.HIDDEN,
    formId: String? = null
) {
    theme.WarnWhenTheHostThemeFightsDarkMode()

    val resolvedStrings = localeStrings ?: GopayLocales.resolve(locale)
    val fields = inputFields ?: PaymentFormInputs.from(resolvedStrings)
    // The id the SDK's copy is kept under: the host's, or one minted once for this composition.
    val resolvedFormId = remember(formId) { formId ?: UUID.randomUUID().toString() }
    // The submit function handed out once reads whichever id the form carries now.
    val currentFormId by rememberUpdatedState(resolvedFormId)
    // This composition, as the store knows it: a second form under the same id, composed before
    // this one leaves, must not have its registration taken away by this one's departure.
    val owner = remember { Any() }
    // Store clean input values (digits only)
    var cardNumberDigits by remember { mutableStateOf("") }
    var expirationDateDigits by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var isCardNumberFocused by remember { mutableStateOf(false) }
    var isCvvFocused by remember { mutableStateOf(false) }
    // Whether the user has typed in each field, for LIVE; whether a submit ran, for ON_SUBMIT.
    var cardNumberEdited by remember { mutableStateOf(false) }
    var expirationEdited by remember { mutableStateOf(false) }
    var cvvEdited by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    val isCardNumberValid = CardValidator.validateCardNumber(cardNumberDigits).isValid

    // What the fields hold right now, as the SDK's copy stores it.
    val snapshot = { CardFormData(cardNumberDigits, expirationDateDigits, cvv) }
    val formData = snapshot()

    // Reset function to clear all form fields. The edited and attempted flags go with the
    // digits, so the emptied fields come back pristine instead of lit up with "required".
    val resetForm = {
        cardNumberDigits = ""
        expirationDateDigits = ""
        cvv = ""
        isCardNumberFocused = false
        isCvvFocused = false
        cardNumberEdited = false
        expirationEdited = false
        cvvEdited = false
        submitAttempted = false
    }

    // FocusRequesters for each field
    val cardNumberFocusRequester = remember { FocusRequester() }
    val expirationFocusRequester = remember { FocusRequester() }
    val cvvFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // The view the form is drawn in, for FLAG_SECURE on its window
    val view = LocalView.current

    // The older submit path: the shared submit of this form, reported through the callbacks.
    val submitCardData: suspend () -> CardEncryptionResult = {
        submitCardDataImpl(
            formId = currentFormId,
            onEncryptionComplete = onEncryptionComplete,
            onValidationError = onValidationError
        )
    }

    // Provide the submit function to parent if callback is provided
    LaunchedEffect(Unit) {
        onFormReady?.invoke(submitCardData)
    }

    // The SDK's copy of the fields, under the form's id, for GopaySDK.submitCardForm. Registered
    // while the form is on screen, with the reset the SDK runs after a successful submit or
    // clearCardFormData, and synced with every edit below. Dropped when the form leaves.
    DisposableEffect(resolvedFormId) {
        CardFormStore.register(
            resolvedFormId,
            owner,
            CardFormHandlers(reset = resetForm, submitAttempted = { submitAttempted = true })
        )
        CardFormStore.update(resolvedFormId, snapshot())
        onDispose { CardFormStore.unregister(resolvedFormId, owner) }
    }

    // Keep the window the form is drawn in, a dialog or bottom sheet included, out of screenshots
    // while the form is there. Not in a debuggable build of the host. See SecureWindow.
    DisposableEffect(view) {
        val protection = if (SecureWindow.shouldProtect(view.context)) {
            SecureWindow.protect(view)
        } else {
            null
        }
        onDispose { protection?.release() }
    }

    // Drop the card-data references when the form leaves the composition, so PAN/CVV don't
    // outlive their use (PCI DSS 4.0.1, req. 3.3.1; the iOS SDK clears its stored copy on
    // onDisappear for the same reason). Note this fires on ANY removal from composition —
    // hosts must not place the form in a recycling container (LazyColumn, pager), where
    // scrolling would discard the user's input; that also holds without this effect, since
    // the plain `remember` state above doesn't survive leaving the composition either.
    // Deliberately NOT cleared on a failed encryption: authorization hasn't happened yet,
    // and the user shouldn't have to retype the card after a network drop.
    DisposableEffect(Unit) {
        onDispose { resetForm() }
    }

    val errors = CardFormErrors.of(
        data = formData,
        strings = resolvedStrings,
        mode = validation,
        submitAttempted = submitAttempted,
        cardNumberEdited = cardNumberEdited,
        expirationEdited = expirationEdited,
        cvvEdited = cvvEdited
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.formBackgroundColor)
            .padding(theme.formPadding.orZero()),
        verticalArrangement = Arrangement.spacedBy(theme.groupSpacing.orZero())
    ) {
        // Card Number Input
        LabeledInputField(
            value = cardNumberDigits,
            onValueChange = { newValue ->
                val validated = CardNumberInputValidator.validateInput(newValue)
                cardNumberDigits = validated
                cardNumberEdited = true
                CardFormStore.update(resolvedFormId, snapshot())
                if (CardNumberInputValidator.isValidLength(validated) && CardNumberInputValidator.isValidLuhn(validated)) {
                    coroutineScope.launch {
                        expirationFocusRequester.requestFocus()
                    }
                }
            },
            config = LabeledInputFieldConfig(
                label = fields.cardNumber.label,
                error = errors.cardNumber ?: fields.cardNumber.errorText,
                helperText = fields.cardNumber.helperText,
                placeholder = fields.cardNumber.placeholder,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = if (!isCardNumberFocused && isCardNumberValid) CardNumberMaskedVisualTransformation() else CardNumberVisualTransformation(),
                textFieldModifier = Modifier
                    .focusRequester(cardNumberFocusRequester)
                    .onFocusChanged { isCardNumberFocused = it.isFocused }
            ),
            theme = theme
        )
        // Expiration Date and CVV Row. The row settles how tall the label slot is for both
        // fields, so a label that wraps in one of them cannot push its input out of line.
        SharedLabelHeightRow(
            theme = theme,
            labels = listOf(fields.expirationDate.label, fields.cvv.label),
            horizontalSpacing = theme.groupSpacing.orZero(),
            modifier = Modifier.fillMaxWidth()
        ) { labelMinHeight ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.groupSpacing.orZero())
            ) {
                // Expiration Date
                LabeledInputField(
                    value = expirationDateDigits,
                    onValueChange = { newValue ->
                        val validated = ExpirationDateInputValidator.validateInput(newValue)
                        expirationDateDigits = validated
                        expirationEdited = true
                        CardFormStore.update(resolvedFormId, snapshot())
                        if (validated.length == 4) {
                            coroutineScope.launch {
                                cvvFocusRequester.requestFocus()
                            }
                        }
                    },
                    config = LabeledInputFieldConfig(
                        label = fields.expirationDate.label,
                        error = errors.expiration ?: fields.expirationDate.errorText,
                        helperText = fields.expirationDate.helperText,
                        placeholder = fields.expirationDate.placeholder,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = ExpirationDateVisualTransformation(),
                        textFieldModifier = Modifier
                            .focusRequester(expirationFocusRequester)
                    ),
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    labelMinHeight = labelMinHeight
                )
                // CVV Input
                LabeledInputField(
                    value = cvv,
                    onValueChange = { newValue ->
                        val validated = CvvValidator.validateInput(newValue, cvv)
                        cvv = validated
                        cvvEdited = true
                        CardFormStore.update(resolvedFormId, snapshot())
                    },
                    config = LabeledInputFieldConfig(
                        label = fields.cvv.label,
                        error = errors.cvv ?: fields.cvv.errorText,
                        helperText = fields.cvv.helperText,
                        placeholder = fields.cvv.placeholder,
                        // NumberPassword: same numeric layout, but tells the IME not to cache or
                        // learn the CVV (keyboards may retain plain Number input for suggestions).
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (isCvvFocused) VisualTransformation.None else CvvMaskedVisualTransformation(),
                        textFieldModifier = Modifier
                            .focusRequester(cvvFocusRequester)
                            .onFocusChanged { isCvvFocused = it.isFocused }
                    ),
                    modifier = Modifier.weight(1f),
                    theme = theme,
                    labelMinHeight = labelMinHeight
                )
            }
        }
    }
}

/**
 * The signature 2.1 shipped, kept in the bytecode so a host compiled against it keeps running on
 * 2.2 without a `NoSuchMethodError`; new code sees only the one above.
 */
@Deprecated("Binary compatibility with 2.1.0", level = DeprecationLevel.HIDDEN)
@Composable
fun PaymentCardForm(
    onEncryptionComplete: (CardEncryptionResult) -> Unit,
    modifier: Modifier = Modifier,
    onFormReady: ((suspend () -> CardEncryptionResult) -> Unit)? = null,
    onValidationError: ((CardValidator.CardValidationResult) -> Unit)? = null,
    locale: String? = null,
    localeStrings: GopayLocaleStrings? = null,
    inputFields: PaymentFormInputs? = null,
    theme: PaymentCardFormTheme = PaymentCardFormTheme()
) {
    PaymentCardForm(
        onEncryptionComplete = onEncryptionComplete,
        modifier = modifier,
        onFormReady = onFormReady,
        onValidationError = onValidationError,
        locale = locale,
        localeStrings = localeStrings,
        inputFields = inputFields,
        theme = theme,
        validation = CardFormValidationDisplay.HIDDEN,
        formId = null
    )
}
