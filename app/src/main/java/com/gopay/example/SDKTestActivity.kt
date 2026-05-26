package com.gopay.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.Currency
import cz.gopay.sdk.model.PaymentCallback
import cz.gopay.sdk.model.PaymentCreateRequest
import cz.gopay.sdk.model.PaymentCustomer
import cz.gopay.sdk.model.PaymentInstrumentInput
import cz.gopay.sdk.ui.InputFieldConfig
import cz.gopay.sdk.ui.PaymentCardForm
import cz.gopay.sdk.ui.PaymentCardFormTheme
import cz.gopay.sdk.ui.PaymentFormInputs
import cz.gopay.sdk.ui.TokenizationResult
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
    // Auth
    var username by remember { mutableStateOf("SDK") }
    var password by remember { mutableStateOf("uKmnhCnb") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var authResult by remember { mutableStateOf("") }

    // Shared inputs — auto-populated from responses
    var goid by remember { mutableStateOf("8761908826") }
    var paymentStatusId by remember { mutableStateOf("") }
    var chargePaymentId by remember { mutableStateOf("") }
    var cardTokenForCharge by remember { mutableStateOf("") }
    var chargeStatePaymentId by remember { mutableStateOf("") }
    var threeDsRedirectUrl by remember { mutableStateOf("") }

    // Per-section results
    var tokenMgmtResult by remember { mutableStateOf("") }
    var publicKeyResult by remember { mutableStateOf("") }
    var tokenizeResult by remember { mutableStateOf("") }
    var createPaymentResult by remember { mutableStateOf("") }
    var paymentStatusResult by remember { mutableStateOf("") }
    var chargeResult by remember { mutableStateOf("") }
    var threeDsResult by remember { mutableStateOf("") }
    var chargeStateResult by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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

        // === AUTHENTICATION ===
        SectionCard(title = "Authentication") {
            if (!isAuthenticated) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().authenticate(
                                        clientId = username,
                                        clientSecret = password,
                                        scope = "payment:create payment:read card:read card:save"
                                    )
                                }
                                isAuthenticated = true
                                authResult = "✅ Authenticated!\nAccess Token: ${resp.accessToken.take(20)}...\nScope: ${resp.scope ?: "N/A"}"
                            } catch (e: GopaySDKException) {
                                authResult = "❌ Auth failed:\n${formatError(e)}"
                            } catch (e: Exception) {
                                authResult = "❌ Unexpected error: ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && username.isNotEmpty() && password.isNotEmpty()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Authenticate")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅ Authenticated as: $username", color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        isAuthenticated = false
                        authResult = "Logged out"
                        try { GopaySDK.getInstance().getTokenStorage().clear() } catch (_: Exception) {}
                    }) { Text("Logout") }
                }
            }
            ResultBox(authResult)
        }

        if (isAuthenticated) {

            // === TOKEN MANAGEMENT ===
            SectionCard(title = "Token Management") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            try {
                                val ts = GopaySDK.getInstance().getTokenStorage()
                                val at = ts.getAccessToken()
                                val rt = ts.getRefreshToken()
                                tokenMgmtResult = "Access: ${if (at != null) "${at.take(20)}..." else "none"}\nRefresh: ${if (rt != null) "${rt.take(20)}..." else "none"}"
                            } catch (e: Exception) {
                                tokenMgmtResult = "❌ ${e.message}"
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = !isLoading
                    ) { Text("Check Tokens") }
                    Button(
                        onClick = {
                            try {
                                GopaySDK.getInstance().getTokenStorage().clear()
                                isAuthenticated = false
                                tokenMgmtResult = "✅ Tokens cleared"
                            } catch (e: Exception) {
                                tokenMgmtResult = "❌ ${e.message}"
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = !isLoading
                    ) { Text("Clear Tokens") }
                }
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) { GopaySDK.getInstance().refreshToken() }
                                tokenMgmtResult = "✅ Token refreshed!\nNew token: ${resp.accessToken.take(20)}...\nScope: ${resp.scope ?: "N/A"}"
                            } catch (e: GopaySDKException) {
                                tokenMgmtResult = "❌ Refresh failed:\n${formatError(e)}"
                            } catch (e: Exception) {
                                tokenMgmtResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Refresh Token") }
                ResultBox(tokenMgmtResult)
            }

            // === ENCRYPTION KEY ===
            SectionCard(title = "Encryption Key (DEV)") {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val jwk = withContext(Dispatchers.IO) { GopaySDK.getInstance().getPublicKey() }
                                publicKeyResult = "✅ Key retrieved!\nType: ${jwk.kty}\nID: ${jwk.kid}\nAlg: ${jwk.alg}\nn: ${jwk.n.take(40)}..."
                            } catch (e: GopaySDKException) {
                                publicKeyResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                publicKeyResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Get Public Key") }
                ResultBox(publicKeyResult)
            }

            // === CARD TOKENIZATION ===
            SectionCard(title = "Card Tokenization (DEV)") {
                Text(
                    "Test card: 4444444444444448 · 06/27 · 123",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().tokenizeCard(
                                        CardData(cardPan = "4444444444444448", expMonth = "06", expYear = "27", cvv = "123")
                                    )
                                }
                                cardTokenForCharge = resp.token
                                tokenizeResult = "✅ Token: ${resp.token}\nPAN: ${resp.maskedPan}\nBrand: ${resp.brand}\nExp: ${resp.expirationMonth}/${resp.expirationYear}"
                            } catch (e: GopaySDKException) {
                                tokenizeResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                tokenizeResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Tokenize Card") }
                ResultBox(tokenizeResult)
            }

            // === CREATE PAYMENT ===
            SectionCard(title = "Create Payment") {
                OutlinedTextField(
                    value = goid, onValueChange = { goid = it },
                    label = { Text("Eshop GOID") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().createPayment(
                                        goid = goid.trim(),
                                        request = PaymentCreateRequest(
                                            amount = 10000,
                                            currency = Currency.CZK,
                                            orderNumber = "SDK-TEST-${System.currentTimeMillis()}",
                                            orderDescription = "SDK test payment",
                                            customer = PaymentCustomer(
                                                email = "john.doe@example.com",
                                                firstName = "John",
                                                lastName = "Doe"
                                            ),
                                            callback = PaymentCallback(
                                                notificationUrl = "https://example.com/notify",
                                                returnUrl = "https://example.com/return"
                                            )
                                        )
                                    )
                                }
                                // Auto-populate downstream inputs
                                paymentStatusId = resp.id
                                chargePaymentId = resp.id
                                chargeStatePaymentId = resp.id
                                createPaymentResult = "✅ Created!\nID: ${resp.id}\nOrder: ${resp.orderNumber}\nState: ${resp.state}\nAmount: ${resp.amount} ${resp.currency}\nGW URL: ${resp.gwUrl}"
                            } catch (e: GopaySDKException) {
                                createPaymentResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                createPaymentResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && goid.isNotBlank()
                ) { Text("Create Payment") }
                ResultBox(createPaymentResult)
            }

            // === GET PAYMENT STATUS ===
            SectionCard(title = "Get Payment Status") {
                OutlinedTextField(
                    value = paymentStatusId, onValueChange = { paymentStatusId = it },
                    label = { Text("Payment ID") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().getPaymentStatus(paymentStatusId.trim())
                                }
                                val chargeInfo = resp.charge?.let {
                                    "Charge ID: ${it.id}\nCharge State: ${it.state}"
                                } ?: "Charge: none"
                                paymentStatusResult = "✅ State: ${resp.state}\nID: ${resp.id}\nAmount: ${resp.amount} ${resp.currency}\n$chargeInfo"
                            } catch (e: GopaySDKException) {
                                paymentStatusResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                paymentStatusResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && paymentStatusId.isNotBlank()
                ) { Text("Get Payment Status") }
                ResultBox(paymentStatusResult)
            }

            // === CHARGE PAYMENT ===
            SectionCard(title = "Charge Payment") {
                OutlinedTextField(
                    value = chargePaymentId, onValueChange = { chargePaymentId = it },
                    label = { Text("Payment ID") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                OutlinedTextField(
                    value = cardTokenForCharge, onValueChange = { cardTokenForCharge = it },
                    label = { Text("Card Token") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().chargePayment(
                                        paymentId = chargePaymentId.trim(),
                                        request = ChargePaymentRequest(
                                            paymentInstrument = PaymentInstrumentInput.cardToken(
                                                cardToken = cardTokenForCharge.trim(),
                                                challengePreference = ChallengePreference.AUTO
                                            ),
                                            browserData = BrowserData(
                                                language = "en-US",
                                                timezone = 0,
                                                screenWidth = 1080,
                                                screenHeight = 1920,
                                                colorDepth = 24,
                                                javascriptEnabled = true
                                            )
                                        )
                                    )
                                }
                                // Auto-populate 3DS section if action is present
                                resp.action?.redirectUrl?.let { threeDsRedirectUrl = it }
                                chargeStatePaymentId = chargePaymentId.trim()
                                val actionInfo = resp.action?.let {
                                    "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                                } ?: "Action: none"
                                chargeResult = "✅ Charged!\nCharge ID: ${resp.id}\nState: ${resp.state}\n$actionInfo"
                            } catch (e: GopaySDKException) {
                                chargeResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                chargeResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && chargePaymentId.isNotBlank() && cardTokenForCharge.isNotBlank()
                ) { Text("Charge Payment") }
                ResultBox(chargeResult)
            }

            // === HANDLE 3DS VERIFICATION ===
            SectionCard(title = "Handle 3DS Verification") {
                Text(
                    "Auto-populated from charge response when a redirect action is present.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = threeDsRedirectUrl, onValueChange = { threeDsRedirectUrl = it },
                    label = { Text("3DS Redirect URL") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                GopaySDK.getInstance().handle3dsVerification(
                                    activity = context as Activity,
                                    redirectUrl = threeDsRedirectUrl.trim()
                                )
                                threeDsResult = "✅ 3DS verification completed"
                            } catch (e: GopaySDKException) {
                                threeDsResult = "❌ ${formatError(e)}"
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                threeDsResult = "⚠️ 3DS cancelled by user"
                            } catch (e: Exception) {
                                threeDsResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && threeDsRedirectUrl.isNotBlank()
                ) { Text("Handle 3DS Verification") }
                ResultBox(threeDsResult)
            }

            // === GET CHARGE STATE ===
            SectionCard(title = "Get Charge State") {
                OutlinedTextField(
                    value = chargeStatePaymentId, onValueChange = { chargeStatePaymentId = it },
                    label = { Text("Payment ID") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().getChargeState(chargeStatePaymentId.trim())
                                }
                                // Auto-populate 3DS section if action is present
                                resp.action?.redirectUrl?.let { threeDsRedirectUrl = it }
                                val actionInfo = resp.action?.let {
                                    "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                                } ?: "Action: none"
                                chargeStateResult = "✅ State: ${resp.state}\nCharge ID: ${resp.id}\n$actionInfo"
                            } catch (e: GopaySDKException) {
                                chargeStateResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                chargeStateResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && chargeStatePaymentId.isNotBlank()
                ) { Text("Get Charge State") }
                ResultBox(chargeStateResult)
            }

            // === PAYMENT CARD FORM ===
            SectionCard(title = "Payment Card Form") {
                Text(
                    "On success the card token is auto-populated into Charge Payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaymentCardFormDemo(onTokenObtained = { token -> cardTokenForCharge = token })
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun ResultBox(text: String) {
    if (text.isEmpty()) return
    val isError = text.startsWith("❌")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PaymentCardFormDemo(onTokenObtained: (String) -> Unit) {
    var cardToken by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var submitCardData: (suspend () -> TokenizationResult)? by remember { mutableStateOf(null) }

    var inputFields by remember {
        mutableStateOf(
            PaymentFormInputs(
                cardNumber = InputFieldConfig(label = "Card Number", helperText = "", placeholder = "1234 1234 1234 1234"),
                expirationDate = InputFieldConfig(label = "Expiry Date", helperText = "", placeholder = "12/27"),
                cvv = InputFieldConfig(label = "Security Code", helperText = "", placeholder = "123")
            )
        )
    }

    val scope = rememberCoroutineScope()

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PaymentCardForm(
                onTokenizationComplete = { result ->
                    isProcessing = false
                    when (result) {
                        is TokenizationResult.Success -> {
                            val token = result.tokenResponse.token
                            cardToken = token
                            errorMessage = null
                            onTokenObtained(token)
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
                onFormReady = { submitFn -> submitCardData = submitFn },
                onValidationError = { validation ->
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
                    errorMessage = null
                },
                inputFields = inputFields,
                theme = cardFormTheme,
                permanent = false
            )
        }

        Button(
            onClick = {
                scope.launch {
                    isProcessing = true
                    cardToken = null
                    errorMessage = null
                    inputFields = inputFields.copy(
                        cardNumber = inputFields.cardNumber.copy(hasError = false, errorText = null),
                        expirationDate = inputFields.expirationDate.copy(hasError = false, errorText = null),
                        cvv = inputFields.cvv.copy(hasError = false, errorText = null)
                    )
                    try {
                        submitCardData?.invoke()
                    } catch (e: Exception) {
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Processing...")
                }
            } else {
                Text("Submit Payment Card")
            }
        }

        cardToken?.let { token ->
            ResultBox("✅ Card token generated!\nToken: ${token.take(30)}...\n(Auto-populated into Charge Payment)")
        }
        errorMessage?.let { error ->
            ResultBox("❌ $error")
        }
    }
}

private suspend fun authenticateUser(
    username: String,
    password: String,
    onResult: (Boolean, String) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val authResponse = GopaySDK.getInstance().authenticate(
            clientId = username,
            clientSecret = password,
            scope = "payment:create payment:read card:read card:save"
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
        withContext(Dispatchers.Main) { onResult(false, "Authentication failed:\n${formatError(e)}") }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onResult(false, "Unexpected error: ${e.message}") }
    }
}

private fun translateValidationError(fieldType: String, originalMessage: String?): String {
    if (originalMessage == null) {
        return when (fieldType) {
            "cardNumber" -> "Invalid card number"
            "expirationDate" -> "Invalid expiry date"
            "cvv" -> "Invalid security code"
            else -> "Validation error"
        }
    }
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
        else -> originalMessage
    }
}

private fun formatError(e: GopaySDKException): String {
    val parts = mutableListOf<String>()
    parts.add("Code: ${e.errorCode}")
    parts.add("Message: ${e.message}")
    e.httpContext?.let { http ->
        parts.add("HTTP: ${http.statusCode} ${http.requestMethod} ${http.requestUrl}")
        http.responseBody?.let { body ->
            if (body.isNotEmpty()) parts.add("Response: ${body.take(200)}")
        }
    }
    e.additionalData?.let { data ->
        if (data.isNotEmpty()) parts.add("Additional: $data")
    }
    return parts.joinToString("\n")
}
