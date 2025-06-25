package com.gopay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gopay.sdk.GopaySDK
import com.gopay.sdk.model.CardData
import com.gopay.sdk.model.CardTokenResponse
import com.gopay.sdk.ui.utils.CardNumberInputValidator
import com.gopay.sdk.ui.utils.ExpirationDateInputValidator
import com.gopay.sdk.ui.utils.CvvValidator
import com.gopay.sdk.ui.utils.CardValidator
import com.gopay.sdk.ui.utils.CardNumberVisualTransformation
import com.gopay.sdk.ui.utils.ExpirationDateVisualTransformation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.VisualTransformation
import com.gopay.sdk.ui.utils.CardNumberMaskedVisualTransformation
import com.gopay.sdk.ui.utils.CvvMaskedVisualTransformation

/**
 * Result of card tokenization operation
 */
sealed class TokenizationResult {
    data class Success(val tokenResponse: CardTokenResponse) : TokenizationResult()
    data class Error(val message: String, val exception: Throwable? = null) : TokenizationResult()
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
)

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
 * A secure payment card form that handles card data input and tokenization.
 * This component never exposes raw card data to the parent - it handles tokenization internally
 * and only returns the secure token to the caller.
 *
 * @param onTokenizationComplete Callback called when tokenization completes (success or error)
 * @param modifier Modifier for the form layout
 * @param onFormReady Callback that provides a submit function for external triggering
 * @param onValidationError Callback called when validation errors occur
 * @param inputFields Configuration for input field labels, helper texts, and error states
 * @param theme Theme configuration for customizing the form appearance
 * @param permanent Whether to save the card for permanent usage (default: false)
 */
@Composable
fun PaymentCardForm(
    onTokenizationComplete: (TokenizationResult) -> Unit,
    modifier: Modifier = Modifier,
    onFormReady: ((suspend () -> TokenizationResult) -> Unit)? = null,
    onValidationError: ((CardValidator.CardValidationResult) -> Unit)? = null,
    inputFields: PaymentFormInputs = PaymentFormInputs(),
    theme: PaymentCardFormTheme = PaymentCardFormTheme(),
    permanent: Boolean = false
) {
    // Store clean input values (digits only)
    var cardNumberDigits by remember { mutableStateOf("") }
    var expirationDateDigits by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isCardNumberFocused by remember { mutableStateOf(false) }
    var isCvvFocused by remember { mutableStateOf(false) }
    val isCardNumberValid = CardValidator.validateCardNumber(cardNumberDigits).isValid

    // FocusRequesters for each field
    val cardNumberFocusRequester = remember { FocusRequester() }
    val expirationFocusRequester = remember { FocusRequester() }
    val cvvFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Create the submit function
    val submitCardData: suspend () -> TokenizationResult = {
        val result = try {
            isLoading = true
            
            // Validate all fields first
            val validation = CardValidator.validateCard(
                cardNumber = cardNumberDigits,
                expirationDate = formatExpirationForValidation(expirationDateDigits),
                cvv = cvv
            )
            
            if (!validation.isAllValid) {
                // Call validation error callback if provided
                onValidationError?.invoke(validation)
                throw IllegalArgumentException("Please fix the validation errors")
            }
            
            // Parse expiration date using utility
            val formattedExpDate = formatExpirationForValidation(expirationDateDigits)
            val expParts = parseExpirationDate(formattedExpDate)
                ?: throw IllegalArgumentException("Invalid expiration date format. Use MM/YY")
            
            val (expMonth, expYear) = expParts
            
            // Create card data - this stays within the SDK
            val cardData = CardData(
                cardPan = cardNumberDigits,
                expMonth = expMonth.toString().padStart(2, '0'),
                expYear = expYear.toString(),
                cvv = cvv
            )
            
            // Use SDK to tokenize card (using existing method)
            val tokenResponse = GopaySDK.getInstance().tokenizeCard(cardData, permanent)
            
            TokenizationResult.Success(tokenResponse)
        } catch (e: Exception) {
            TokenizationResult.Error(
                message = e.message ?: "Card tokenization failed",
                exception = e
            )
        } finally {
            isLoading = false
        }
        
        // Call the completion callback with the result
        onTokenizationComplete(result)
        result
    }

    // Provide the submit function to parent if callback is provided
    LaunchedEffect(Unit) {
        onFormReady?.invoke(submitCardData)
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(theme.fieldSpacing)
    ) {
        // Card Number Input
        Column {
            BasicText(
                text = inputFields.cardNumber.label,
                style = if (inputFields.cardNumber.hasError) {
                    theme.labelTextStyle.copy(color = theme.errorTextStyle.color)
                } else {
                    theme.labelTextStyle
                },
                modifier = Modifier.padding(bottom = 4.dp)
            )
            BasicTextField(
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = if (!isCardNumberFocused && isCardNumberValid) CardNumberMaskedVisualTransformation() else CardNumberVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = theme.inputBackgroundColor, shape = theme.inputShape)
                    .border(
                        width = theme.inputBorderWidth,
                        color = if (inputFields.cardNumber.hasError) theme.inputErrorBorderColor else theme.inputBorderColor,
                        shape = theme.inputShape
                    )
                    .padding(theme.inputPadding)
                    .focusRequester(cardNumberFocusRequester)
                    .onFocusChanged { isCardNumberFocused = it.isFocused },
                singleLine = true,
                textStyle = theme.inputTextStyle,
                decorationBox = { innerTextField ->
                    if (cardNumberDigits.isEmpty() && inputFields.cardNumber.placeholder != null) {
                        BasicText(
                            text = inputFields.cardNumber.placeholder,
                            style = theme.inputTextStyle.copy(color = Color.LightGray)
                        )
                    }
                    innerTextField()
                }
            )
            
            // Helper text or error text for card number
            when {
                inputFields.cardNumber.hasError && inputFields.cardNumber.errorText != null -> {
                    BasicText(
                        text = inputFields.cardNumber.errorText!!,
                        style = theme.errorTextStyle,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                inputFields.cardNumber.helperText != null -> {
                    BasicText(
                        text = inputFields.cardNumber.helperText!!,
                        style = theme.helperTextStyle,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // Expiration Date and CVV Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(theme.groupSpacing)
        ) {
            // Expiration Date
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = inputFields.expirationDate.label,
                    style = if (inputFields.expirationDate.hasError) {
                        theme.labelTextStyle.copy(color = theme.errorTextStyle.color)
                    } else {
                        theme.labelTextStyle
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                BasicTextField(
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = ExpirationDateVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = theme.inputBackgroundColor, shape = theme.inputShape)
                        .border(
                            width = theme.inputBorderWidth,
                            color = if (inputFields.expirationDate.hasError) theme.inputErrorBorderColor else theme.inputBorderColor,
                            shape = theme.inputShape
                        )
                        .padding(theme.inputPadding)
                        .focusRequester(expirationFocusRequester),
                    singleLine = true,
                    textStyle = theme.inputTextStyle,
                    decorationBox = { innerTextField ->
                        if (expirationDateDigits.isEmpty() && inputFields.expirationDate.placeholder != null) {
                            BasicText(
                                text = inputFields.expirationDate.placeholder,
                                style = theme.inputTextStyle.copy(color = Color.LightGray)
                            )
                        }
                        innerTextField()
                    }
                )
                
                // Helper text or error text for expiration date
                when {
                    inputFields.expirationDate.hasError && inputFields.expirationDate.errorText != null -> {
                        BasicText(
                            text = inputFields.expirationDate.errorText!!,
                            style = theme.errorTextStyle,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    inputFields.expirationDate.helperText != null -> {
                        BasicText(
                            text = inputFields.expirationDate.helperText!!,
                            style = theme.helperTextStyle,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // CVV Input
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = inputFields.cvv.label,
                    style = if (inputFields.cvv.hasError) {
                        theme.labelTextStyle.copy(color = theme.errorTextStyle.color)
                    } else {
                        theme.labelTextStyle
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                BasicTextField(
                    value = cvv,
                    onValueChange = { newValue ->
                        cvv = CvvValidator.validateInput(newValue, cvv)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = if (isCvvFocused) VisualTransformation.None else CvvMaskedVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = theme.inputBackgroundColor, shape = theme.inputShape)
                        .border(
                            width = theme.inputBorderWidth,
                            color = if (inputFields.cvv.hasError) theme.inputErrorBorderColor else theme.inputBorderColor,
                            shape = theme.inputShape
                        )
                        .padding(theme.inputPadding)
                        .focusRequester(cvvFocusRequester)
                        .onFocusChanged { isCvvFocused = it.isFocused },
                    singleLine = true,
                    textStyle = theme.inputTextStyle,
                    decorationBox = { innerTextField ->
                        if (cvv.isEmpty() && inputFields.cvv.placeholder != null) {
                            BasicText(
                                text = inputFields.cvv.placeholder,
                                style = theme.inputTextStyle.copy(color = Color.LightGray)
                            )
                        }
                        innerTextField()
                    }
                )
                
                // Helper text or error text for CVV
                when {
                    inputFields.cvv.hasError && inputFields.cvv.errorText != null -> {
                        BasicText(
                            text = inputFields.cvv.errorText!!,
                            style = theme.errorTextStyle,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    inputFields.cvv.helperText != null -> {
                        BasicText(
                            text = inputFields.cvv.helperText!!,
                            style = theme.helperTextStyle,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            BasicText(
                text = "Processing...",
                style = theme.loadingTextStyle,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
} 