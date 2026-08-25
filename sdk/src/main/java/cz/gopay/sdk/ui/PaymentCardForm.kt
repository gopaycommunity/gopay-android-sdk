package cz.gopay.sdk.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.locales.GopayLocaleStrings
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.ui.utils.CardNumberInputValidator
import cz.gopay.sdk.ui.utils.CardNumberMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CardNumberVisualTransformation
import cz.gopay.sdk.ui.utils.CardValidator
import cz.gopay.sdk.ui.utils.CvvMaskedVisualTransformation
import cz.gopay.sdk.ui.utils.CvvValidator
import cz.gopay.sdk.ui.utils.ExpirationDateInputValidator
import cz.gopay.sdk.ui.utils.ExpirationDateVisualTransformation
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
    data class Error(val message: String, val exception: Throwable? = null) : CardEncryptionResult()

    companion object {
        /**
         * [Error.message] when submit ran on a completely empty form — typically after the SDK
         * cleared the fields following a successful encryption (GPMOB-140). Hosts can match on
         * this to show a "please re-enter your card" prompt: the gateway accepts each JWE only
         * once, so a retry needs a fresh entry, not a replay of the previous JWE.
         * Mirrors the iOS SDK's `GopaySDKErrors.noCardFormData`.
         */
        const val NO_CARD_DATA_MESSAGE =
            "No card form data available. Please use GopayCardForm to enter card data."
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
         * remain host-driven (see [PaymentCardForm]'s `onValidationError`).
         */
        fun from(strings: GopayLocaleStrings): PaymentFormInputs = PaymentFormInputs(
            cardNumber = InputFieldConfig(label = strings.panLabel, placeholder = strings.panPlaceholder),
            expirationDate = InputFieldConfig(label = strings.expLabel, placeholder = strings.expPlaceholder),
            cvv = InputFieldConfig(label = strings.cvvLabel, placeholder = strings.cvvPlaceholder)
        )
    }
}

/**
 * Helper function to format expiration date digits for validation
 */
private fun formatExpirationForValidation(digits: String): String {
    return when {
        digits.length <= 2 -> digits
        else -> "${digits.substring(0, 2)}/${digits.substring(2)}"
    }
}

/**
 * Helper function to parse expiration date
 */
private fun parseExpirationDate(expirationDate: String): Pair<Int, Int>? {
    val parsed = CardValidator.parseExpirationDate(expirationDate) ?: return null
    val month = parsed.first.toIntOrNull() ?: return null
    val year = parsed.second.toIntOrNull() ?: return null
    if (month < 1 || month > 12) return null
    return Pair(month, 2000 + year)
}

/**
 * Theme interface for customizing the appearance of the PaymentCardForm
 */
data class PaymentCardFormTheme(
    // Text styles
    val labelTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 14.sp),
    val inputTextStyle: TextStyle = TextStyle(fontSize = 16.sp),
    val helperTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 12.sp),
    val errorTextStyle: TextStyle = TextStyle(color = Color.Red, fontSize = 12.sp),
    val loadingTextStyle: TextStyle = TextStyle(color = Color.Gray, fontSize = 14.sp),
    /**
     * Style for the empty-field placeholder. `null` keeps the historical default — [inputTextStyle]
     * tinted `LightGray` — which is legible on a light form but reads as real input on a dark one,
     * so dark themes should set this explicitly.
     */
    val placeholderTextStyle: TextStyle? = null,
    
    // Colors
    val inputBorderColor: Color = Color.Gray,
    val inputErrorBorderColor: Color = Color.Red,
    val inputBackgroundColor: Color = Color.White,
    
    // Sizes and shapes
    val inputBorderWidth: Dp = 1.dp,
    val inputShape: Shape = RoundedCornerShape(4.dp),
    val inputPadding: PaddingValues = PaddingValues(12.dp),
    val fieldSpacing: Dp = 2.dp,
    val groupSpacing: Dp = 16.dp
)

/**
 * A secure payment card form that handles card data input and JWE encryption.
 *
 * The form never exposes raw card data to the parent — it validates input and encrypts it into a
 * JWE that the parent forwards to its merchant backend. The backend (which holds merchant
 * credentials) then calls `POST /cards/tokens` to obtain the final card token.
 *
 * Security:
 * - In non-debug builds, the form sets `FLAG_SECURE` on the window to prevent screen capture.
 * - Card data is validated locally and JWE-encrypted using the merchant's public key (fetched
 *   from `GET /cards/public-key` with shareable-key auth and cached in memory).
 *
 * Localization: field labels and placeholders default to the locale resolved from [locale] /
 * [localeStrings] (falling back to the SDK-wide default, then the device language, then Czech).
 * Pass [inputFields] to override the resolved labels entirely.
 *
 * @param onEncryptionComplete Callback invoked with the JWE (success) or an error.
 * @param modifier Modifier for the form layout.
 * @param onFormReady Provides a submit function for external triggering.
 * @param onValidationError Invoked when validation errors are present.
 * @param locale Locale code (e.g. `"cs"`, `"de"`) for the field labels. `null` uses the SDK default.
 *              Ignored when [localeStrings] or [inputFields] is supplied.
 * @param localeStrings Explicit locale strings to use, bypassing [locale] resolution.
 * @param inputFields Configuration for input field labels, helper texts, error states. When `null`
 *                    (default) the labels are derived from the resolved locale.
 * @param theme Theme configuration for customizing the form appearance.
 */
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
    val resolvedStrings = localeStrings ?: GopayLocales.resolve(locale)
    val fields = inputFields ?: PaymentFormInputs.from(resolvedStrings)
    // Store clean input values (digits only)
    var cardNumberDigits by remember { mutableStateOf("") }
    var expirationDateDigits by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var isCardNumberFocused by remember { mutableStateOf(false) }
    var isCvvFocused by remember { mutableStateOf(false) }
    val isCardNumberValid = CardValidator.validateCardNumber(cardNumberDigits).isValid

    // Reset function to clear all form fields
    val resetForm = {
        cardNumberDigits = ""
        expirationDateDigits = ""
        cvv = ""
        isCardNumberFocused = false
        isCvvFocused = false
    }

    // FocusRequesters for each field
    val cardNumberFocusRequester = remember { FocusRequester() }
    val expirationFocusRequester = remember { FocusRequester() }
    val cvvFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Get the view to access the window for FLAG_SECURE
    val view = LocalView.current

    // Create the submit function
    val submitCardData: suspend () -> CardEncryptionResult = {
        submitCardDataImpl(
            cardNumberDigits = cardNumberDigits,
            expirationDateDigits = expirationDateDigits,
            cvv = cvv,
            onEncryptionComplete = onEncryptionComplete,
            onValidationError = onValidationError,
            resetForm = resetForm
        )
    }

    // Provide the submit function to parent if callback is provided
    LaunchedEffect(Unit) {
        onFormReady?.invoke(submitCardData)
    }

    // Set FLAG_SECURE for production builds to prevent screen capture. Disable for debug builds.
    LaunchedEffect(Unit) {
        try {
            val sdk = GopaySDK.getInstance()
            if (!sdk.isDebugEnabled()) {
                val activity = view.context as? Activity
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (e: Exception) {
            // Silently handle any errors accessing the window
            // This prevents crashes if the view context is not an Activity
        }
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

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(theme.fieldSpacing)
    ) {
        // Card Number Input
        LabeledInputField(
            value = cardNumberDigits,
            onValueChange = { newValue ->
                val validated = CardNumberInputValidator.validateInput(newValue)
                cardNumberDigits = validated
                if (CardNumberInputValidator.isValidLength(validated) && CardNumberInputValidator.isValidLuhn(validated)) {
                    coroutineScope.launch {
                        expirationFocusRequester.requestFocus()
                    }
                }
            },
            config = LabeledInputFieldConfig(
                label = fields.cardNumber.label,
                error = fields.cardNumber.errorText,
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
        // Expiration Date and CVV Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(theme.groupSpacing)
        ) {
            // Expiration Date
            LabeledInputField(
                value = expirationDateDigits,
                onValueChange = { newValue ->
                    val validated = ExpirationDateInputValidator.validateInput(newValue)
                    expirationDateDigits = validated
                    if (validated.length == 4) {
                        coroutineScope.launch {
                            cvvFocusRequester.requestFocus()
                        }
                    }
                },
                config = LabeledInputFieldConfig(
                    label = fields.expirationDate.label,
                    error = fields.expirationDate.errorText,
                    helperText = fields.expirationDate.helperText,
                    placeholder = fields.expirationDate.placeholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ExpirationDateVisualTransformation(),
                    textFieldModifier = Modifier.focusRequester(expirationFocusRequester)
                ),
                modifier = Modifier.weight(1f),
                theme = theme
            )
            // CVV Input
            LabeledInputField(
                value = cvv,
                onValueChange = { newValue ->
                    cvv = CvvValidator.validateInput(newValue, cvv)
                },
                config = LabeledInputFieldConfig(
                    label = fields.cvv.label,
                    error = fields.cvv.errorText,
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
                theme = theme
            )
        }
    }
}

private suspend fun submitCardDataImpl(
    cardNumberDigits: String,
    expirationDateDigits: String,
    cvv: String,
    onEncryptionComplete: (CardEncryptionResult) -> Unit,
    onValidationError: ((CardValidator.CardValidationResult) -> Unit)?,
    resetForm: () -> Unit
): CardEncryptionResult {
    return try {
        val validation = CardValidator.validateCard(
            cardNumber = cardNumberDigits,
            expirationDate = formatExpirationForValidation(expirationDateDigits),
            cvv = cvv
        )
        if (!validation.isAllValid) {
            onValidationError?.invoke(validation)
            // An all-empty form is reported distinctly: it is the expected state right after a
            // successful encryption cleared the fields, and hosts key their JWE-reuse retry on it.
            val formIsEmpty = cardNumberDigits.isEmpty() && expirationDateDigits.isEmpty() && cvv.isEmpty()
            throw IllegalArgumentException(
                if (formIsEmpty) CardEncryptionResult.NO_CARD_DATA_MESSAGE else "Please fix the validation errors"
            )
        }

        val formattedExpDate = formatExpirationForValidation(expirationDateDigits)
        val expParts = parseExpirationDate(formattedExpDate)
            ?: throw IllegalArgumentException("Invalid expiration date format. Use MM/YY")
        val (expMonth, expYear) = expParts

        val cardData = CardData(
            cardPan = cardNumberDigits,
            expMonth = expMonth.toString().padStart(2, '0'),
            expYear = expYear.toString(),
            cvv = cvv
        )

        val jwe = GopaySDK.getInstance().encryptCardData(cardData)
        val result = CardEncryptionResult.Success(jwe)
        resetForm()
        onEncryptionComplete(result)
        result
    } catch (e: Exception) {
        val result = CardEncryptionResult.Error(
            message = e.message ?: "Card encryption failed",
            exception = e
        )
        onEncryptionComplete(result)
        result
    }
}
