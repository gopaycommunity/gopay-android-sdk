package com.gopay.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import com.gopay.sdk.GopaySDK
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.model.CardData
import com.gopay.sdk.ui.PaymentCardForm
import com.gopay.sdk.ui.PaymentCardFormTheme
import com.gopay.sdk.ui.PaymentFormInputs
import com.gopay.sdk.ui.InputFieldConfig
import com.gopay.sdk.ui.TokenizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SDKTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            ExampleAppTheme {
                SDKTestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SDKTestScreen() {
    var username by remember { mutableStateOf("SDK") }
    var password by remember { mutableStateOf("hE8e8KNP") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Ready to test SDK methods") }
    
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        
        // Header
        Text(
            text = "Gopay SDK Test App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = "Environment: ${GopaySDK.getInstance().config.environment}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        HorizontalDivider()
        
        // Authentication Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Authentication",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (!isAuthenticated) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    )
                    
                    Button(
                        onClick = {
                            isLoading = true  // Set loading BEFORE launching coroutine
                            scope.launch {
                                authenticateUser(username, password) { success, message ->
                                    isAuthenticated = success
                                    resultText = message
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && username.isNotEmpty() && password.isNotEmpty()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Authenticate")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✅ Authenticated as: $username",
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        TextButton(
                            onClick = {
                                isAuthenticated = false
                                username = ""
                                password = ""
                                isLoading = false  // Reset loading state
                                resultText = "Logged out successfully"
                                // Clear tokens
                                try {
                                    GopaySDK.getInstance().getTokenStorage().clear()
                                } catch (e: Exception) {
                                    resultText = "Error clearing tokens: ${e.message}"
                                }
                            }
                        ) {
                            Text("Logout")
                        }
                    }
                }
            }
        }
        
        // SDK Methods Section
        if (isAuthenticated) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SDK Methods",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Token Storage Methods
                    Text(
                        text = "Token Management",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val tokenStorage = GopaySDK.getInstance().getTokenStorage()
                                    val accessToken = tokenStorage.getAccessToken()
                                    val refreshToken = tokenStorage.getRefreshToken()
                                    
                                    resultText = "Token Status:\n" +
                                            "Access Token: ${if (accessToken != null) "Present (${accessToken.take(20)}...)" else "None"}\n" +
                                            "Refresh Token: ${if (refreshToken != null) "Present (${refreshToken.take(20)}...)" else "None"}"
                                } catch (e: GopaySDKException) {
                                    resultText = "Error checking tokens:\n${formatError(e)}"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("Check Tokens")
                        }
                        
                        Button(
                            onClick = {
                                try {
                                    GopaySDK.getInstance().getTokenStorage().clear()
                                    resultText = "✅ Tokens cleared successfully"
                                    isAuthenticated = false
                                } catch (e: GopaySDKException) {
                                    resultText = "Error clearing tokens:\n${formatError(e)}"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading
                        ) {
                            Text("Clear Tokens")
                        }
                    }
                    
                    // Refresh Token button as a separate row
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val newAuthResponse = withContext(Dispatchers.IO) {
                                        GopaySDK.getInstance().refreshToken()
                                    }
                                    resultText = "✅ Token refresh successful!\n" +
                                            "New Access Token: ${newAuthResponse.accessToken.take(20)}...\n" +
                                            "New Refresh Token: ${newAuthResponse.refreshToken.take(20)}...\n" +
                                            "Token Type: ${newAuthResponse.tokenType}\n" +
                                            "Scope: ${newAuthResponse.scope ?: "N/A"}"
                                } catch (e: GopaySDKException) {
                                    resultText = "Token refresh failed:\n${formatError(e)}"
                                } catch (e: Exception) {
                                    resultText = "Unexpected error during refresh: ${e.message}"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Refresh Token")
                    }
                    
                    HorizontalDivider()
                    
                    // Public Key Section
                    Text(
                        text = "Encryption Key (DEV/Testing Only)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val jwkResponse = withContext(Dispatchers.IO) {
                                        GopaySDK.getInstance().getPublicKey()
                                    }
                                    resultText = "✅ Public key retrieved successfully!\n" +
                                            "Key Type: ${jwkResponse.kty}\n" +
                                            "Key ID: ${jwkResponse.kid}\n" +
                                            "Usage: ${jwkResponse.use}\n" +
                                            "Algorithm: ${jwkResponse.alg}\n" +
                                            "Modulus (n): ${jwkResponse.n.take(50)}...\n" +
                                            "Exponent (e): ${jwkResponse.e}"
                                } catch (e: GopaySDKException) {
                                    resultText = "Failed to retrieve public key:\n${formatError(e)}"
                                } catch (e: Exception) {
                                    resultText = "Unexpected error getting public key: ${e.message}"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Get Public Key (DEV)")
                    }
                    
                    HorizontalDivider()
                    
                    // Card Tokenization Section
                    Text(
                        text = "Card Tokenization (DEV/Testing Only)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Show hardcoded card data being used
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Test Card Data (Hardcoded)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "PAN: 4444444444444448\nExp: 01/27\nCVV: 258",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Full Card Tokenization Test
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    val cardData = CardData(
                                        cardPan = "4444444444444448",
                                        expMonth = "06",
                                        expYear = "27",
                                        cvv = "123"
                                    )
                                    
                                    // Use the SDK entrypoint for card tokenization
                                    val response = withContext(Dispatchers.IO) {
                                        GopaySDK.getInstance().tokenizeCard(cardData, permanent = false)
                                    }
                                    resultText = "✅ Full card tokenization successful!\n\n" +
                                            "🏦 API Response:\n" +
                                            "Token: ${response.token}\n" +
                                            "Masked PAN: ${response.maskedPan}\n" +
                                            "Brand: ${response.brand}\n" +
                                            "Expiration: ${response.expirationMonth}/${response.expirationYear}\n" +
                                            "Fingerprint: ${response.fingerprint}\n" +
                                            "Expires In: ${response.expiresIn} seconds\n" +
                                            "Card Art: ${response.cardArtUrl ?: "N/A"}"
                                } catch (e: GopaySDKException) {
                                    resultText = "Card tokenization failed:\n${formatError(e)}"
                                } catch (e: Exception) {
                                    resultText = "Unexpected error during tokenization: ${e.message}\n${e.stackTraceToString().take(300)}"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Tokenize Card (DEV)")
                    }
                }
            }
        }
        
        // PaymentCardForm Demo Section
        if (isAuthenticated) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Payment Card Form Demo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "Secure card form with separate submit button",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    PaymentCardFormDemo()
                }
            }
        }
        
        // Results Section
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PaymentCardFormDemo() {
    var cardToken by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var submitCardData: (suspend () -> TokenizationResult)? by remember { mutableStateOf(null) }
    
    // State for managing input field configurations and validation errors
    var inputFields by remember { 
        mutableStateOf(
            PaymentFormInputs(
                cardNumber = InputFieldConfig(
                    label = "Card Number",
                    helperText = "",
                    placeholder = "1234 1234 1234 1234"
                ),
                expirationDate = InputFieldConfig(
                    label = "Expiry Date",
                    helperText = "",
                    placeholder = "12/27"
                ),
                cvv = InputFieldConfig(
                    label = "Security Code",
                    helperText = "",
                    placeholder = "123"
                )
            )
        )
    }
    
    val scope = rememberCoroutineScope()

    // Create a Material theme for the payment card form
    val cardFormTheme = PaymentCardFormTheme(
        labelTextStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        ),
        inputTextStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        helperTextStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        ),
        errorTextStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.error
        ),
        loadingTextStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.primary
        ),
        inputBorderColor = MaterialTheme.colorScheme.outline,
        inputErrorBorderColor = MaterialTheme.colorScheme.error,
        inputBackgroundColor = MaterialTheme.colorScheme.surface,
        inputShape = MaterialTheme.shapes.small,
        inputBorderWidth = 1.dp
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Payment Card Form
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            PaymentCardForm(
                onTokenizationComplete = { result ->
                    isProcessing = false
                    when (result) {
                        is TokenizationResult.Success -> {
                            cardToken = result.tokenResponse.token
                            errorMessage = null
                            // Clear any validation errors on success
                            inputFields = inputFields.copy(
                                cardNumber = inputFields.cardNumber.copy(hasError = false, errorText = null),
                                expirationDate = inputFields.expirationDate.copy(hasError = false, errorText = null),
                                cvv = inputFields.cvv.copy(hasError = false, errorText = null)
                            )
                        }
                        is TokenizationResult.Error -> {
                            cardToken = null
                            errorMessage = result.message
                        }
                    }
                },
                onFormReady = { submitFn ->
                    submitCardData = submitFn
                },
                onValidationError = { validation ->
                    // Handle validation errors by updating input field states
                    inputFields = inputFields.copy(
                        cardNumber = inputFields.cardNumber.copy(
                            hasError = !validation.cardNumber.isValid,
                            errorText = if (!validation.cardNumber.isValid) 
                                translateValidationError("cardNumber", validation.cardNumber.errorMessage)
                            else null
                        ),
                        expirationDate = inputFields.expirationDate.copy(
                            hasError = !validation.expirationDate.isValid,
                            errorText = if (!validation.expirationDate.isValid) 
                                translateValidationError("expirationDate", validation.expirationDate.errorMessage) 
                            else null
                        ),
                        cvv = inputFields.cvv.copy(
                            hasError = !validation.cvv.isValid,
                            errorText = if (!validation.cvv.isValid) 
                                translateValidationError("cvv", validation.cvv.errorMessage) 
                            else null
                        )
                    )
                    
                    // Clear any previous general error message
                    errorMessage = null
                },
                inputFields = inputFields,
                theme = cardFormTheme,
                permanent = false
            )
        }
        
        // Separate Submit Button
        Button(
            onClick = {
                scope.launch {
                    isProcessing = true
                    cardToken = null
                    errorMessage = null
                    
                    // Clear all validation errors and error states on submit
                    inputFields = inputFields.copy(
                        cardNumber = inputFields.cardNumber.copy(hasError = false, errorText = null),
                        expirationDate = inputFields.expirationDate.copy(hasError = false, errorText = null),
                        cvv = inputFields.cvv.copy(hasError = false, errorText = null)
                    )
                    
                    try {
                        submitCardData?.invoke()
                        // Note: The result will be handled by onTokenizationComplete callback
                        // so we don't need to do anything here
                    } catch (e: Exception) {
                        // Handle any unexpected errors
                        isProcessing = false
                        errorMessage = "Unexpected error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = submitCardData != null && !isProcessing
        ) {
            if (isProcessing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Processing...")
                }
            } else {
                Text("Submit Payment Card")
            }
        }
        
        // Results Display
        cardToken?.let { token ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "✅ Card Token Generated Successfully!",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Token: ${token.take(30)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "❌ Error",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

private suspend fun authenticateUser(
    username: String,
    password: String,
    onResult: (Boolean, String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        // Use the username as clientId and password as clientSecret for demo purposes
        // In a real app, these would be your actual GoPay client credentials
        val authResponse = GopaySDK.getInstance().authenticate(
            clientId = username,
            clientSecret = password,
            scope = "payment:create payment:read card:read"
        )
        
        withContext(Dispatchers.Main) {
            onResult(
                true, 
                "✅ Authentication successful!\n" +
                "Access Token: ${authResponse.accessToken.take(20)}...\n" +
                "Token Type: ${authResponse.tokenType}\n" +
                "Scope: ${authResponse.scope ?: "N/A"}"
            )
        }
    } catch (e: GopaySDKException) {
        withContext(Dispatchers.Main) {
            onResult(false, "Authentication failed:\n${formatError(e)}")
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            onResult(false, "Unexpected error: ${e.message}")
        }
    }
}

/**
 * Helper function to translate validation error messages.
 * In a real app, this would use proper localization resources.
 */
private fun translateValidationError(fieldType: String, originalMessage: String?): String {
    // Return generic message if originalMessage is null
    if (originalMessage == null) {
        return when (fieldType) {
            "cardNumber" -> "Invalid card number"
            "expirationDate" -> "Invalid expiry date"
            "cvv" -> "Invalid security code"
            else -> "Validation error"
        }
    }
    
    // Example translation logic - in a real app this would use string resources
    return when {
        fieldType == "cardNumber" && originalMessage.contains("invalid", ignoreCase = true) -> 
            "Please enter a valid card number"
        fieldType == "cardNumber" && originalMessage.contains("required", ignoreCase = true) -> 
            "Card number is required"
        fieldType == "expirationDate" && originalMessage.contains("invalid", ignoreCase = true) -> 
            "Please enter a valid expiry date (MM/YY)"
        fieldType == "expirationDate" && originalMessage.contains("expired", ignoreCase = true) -> 
            "This card has expired"
        fieldType == "expirationDate" && originalMessage.contains("required", ignoreCase = true) -> 
            "Expiry date is required"
        fieldType == "cvv" && originalMessage.contains("invalid", ignoreCase = true) -> 
            "Please enter a valid security code"
        fieldType == "cvv" && originalMessage.contains("required", ignoreCase = true) -> 
            "Security code is required"
        else -> originalMessage // Fall back to original message
    }
}

private fun formatError(e: GopaySDKException): String {
    val parts = mutableListOf<String>()
    parts.add("Code: ${e.errorCode}")
    parts.add("Message: ${e.message}")
    
    e.httpContext?.let { http ->
        parts.add("HTTP: ${http.statusCode} ${http.requestMethod} ${http.requestUrl}")
        http.responseBody?.let { body ->
            if (body.isNotEmpty()) {
                parts.add("Response: ${body.take(200)}")
            }
        }
    }
    
    e.additionalData?.let { data ->
        if (data.isNotEmpty()) {
            parts.add("Additional: $data")
        }
    }
    
    return parts.joinToString("\n")
} 