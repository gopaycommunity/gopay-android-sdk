package com.gopay.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.ui.CardEncryptionResult
import cz.gopay.sdk.ui.InputFieldConfig
import cz.gopay.sdk.ui.PaymentCardForm
import cz.gopay.sdk.ui.PaymentCardFormTheme
import cz.gopay.sdk.ui.PaymentFormInputs
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
    // Session inputs — in a real app, your backend creates a payment via POST /eshops/{goid}/payments
    // (with merchant_credentials) and returns the payment_id + payment_secret to the device.
    var paymentId by remember { mutableStateOf("") }
    var paymentSecret by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(PaymentSession.DEFAULT_SCOPE) }
    var session by remember { mutableStateOf<PaymentSession?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Charge inputs
    var cardTokenForCharge by remember { mutableStateOf("") }
    var threeDsRedirectUrl by remember { mutableStateOf("") }

    // Per-section results
    var sessionResult by remember { mutableStateOf("") }
    var publicKeyResult by remember { mutableStateOf("") }
    var paymentStatusResult by remember { mutableStateOf("") }
    var chargeResult by remember { mutableStateOf("") }
    var threeDsResult by remember { mutableStateOf("") }
    var chargeStateResult by remember { mutableStateOf("") }
    var qrResult by remember { mutableStateOf("") }
    var googlePayInfoResult by remember { mutableStateOf("") }
    var googlePayChargeResult by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var lastJwe by remember { mutableStateOf("") }

    val scopeCoroutine = rememberCoroutineScope()
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

        // === PAYMENT SESSION ===
        SectionCard(title = "Payment Session") {
            Text(
                "Your merchant backend creates the payment with merchant_credentials and " +
                "returns payment_id + payment_secret. Paste them here to start a session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session == null) {
                OutlinedTextField(
                    value = paymentId, onValueChange = { paymentId = it },
                    label = { Text("Payment ID") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                OutlinedTextField(
                    value = paymentSecret, onValueChange = { paymentSecret = it },
                    label = { Text("Payment Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                OutlinedTextField(
                    value = scope, onValueChange = { scope = it },
                    label = { Text("Scope (default: ${PaymentSession.DEFAULT_SCOPE})") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        isLoading = true
                        scopeCoroutine.launch {
                            try {
                                val s = withContext(Dispatchers.IO) {
                                    GopaySDK.getInstance().startPaymentSession(
                                        paymentId = paymentId.trim(),
                                        paymentSecret = paymentSecret.trim(),
                                        scope = scope.trim().ifEmpty { PaymentSession.DEFAULT_SCOPE }
                                    )
                                }
                                session = s
                                sessionResult = "✅ Session started for ${s.paymentId}\nScope: $scope"
                            } catch (e: GopaySDKException) {
                                sessionResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                sessionResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && paymentId.isNotBlank() && paymentSecret.isNotBlank()
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Start Payment Session")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✅ Session live: ${session!!.paymentId}", color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = {
                        session?.close()
                        session = null
                        sessionResult = "Session closed"
                    }) { Text("Close") }
                }
            }
            ResultBox(sessionResult)
        }

        // === ENCRYPTION KEY === (shareable_key auth — works without a session)
        SectionCard(title = "Encryption Key") {
            Text(
                "Fetched via GET /cards/public-key using shareable_key basic auth — " +
                "no payment session required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    scopeCoroutine.launch {
                        isLoading = true
                        try {
                            val jwk = withContext(Dispatchers.IO) {
                                GopaySDK.getInstance().getPublicEncryptionKey()
                            }
                            publicKeyResult = "✅ kid: ${jwk.kid}\nalg: ${jwk.alg}\nuse: ${jwk.use}\nn: ${jwk.n.take(40)}…"
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

        // The rest of the screen only makes sense once a session exists.
        val s = session
        if (s != null) {

            // === PAYMENT STATUS ===
            SectionCard(title = "Payment Status") {
                Button(
                    onClick = {
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) { s.getStatus() }
                                val chargeInfo = resp.charge?.let {
                                    "Charge ID: ${it.id}\nCharge State: ${it.state}"
                                } ?: "Charge: none"
                                paymentStatusResult =
                                    "✅ State: ${resp.state}\nID: ${resp.id}\n" +
                                    "Amount: ${resp.amount} ${resp.currency}\n$chargeInfo"
                            } catch (e: GopaySDKException) {
                                paymentStatusResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                paymentStatusResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Get Payment Status") }
                ResultBox(paymentStatusResult)
            }

            // === QR PAYMENT INFO ===
            SectionCard(title = "QR Payment Info") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scopeCoroutine.launch {
                                isLoading = true
                                qrBitmap = null
                                try {
                                    val resp = withContext(Dispatchers.IO) { s.getQrPaymentInfo() }
                                    qrResult = buildString {
                                        appendLine("✅ QR Info retrieved!")
                                        appendLine("Amount: ${resp.amount} ${resp.currency}")
                                        appendLine("Recipient: ${resp.recipient.name ?: "N/A"}")
                                        resp.recipient.bankAccount?.local?.let {
                                            appendLine("Account: ${it.accountNumber}/${it.bankCode}")
                                            appendLine("Variable symbol: ${it.variableSymbol}")
                                        }
                                        resp.recipient.bankAccount?.international?.let {
                                            appendLine("IBAN: ${it.iban ?: "N/A"}")
                                            appendLine("BIC: ${it.bic ?: "N/A"}")
                                        }
                                        val formats = listOfNotNull(
                                            resp.qrCode.spayd?.let { "SPAYD" },
                                            resp.qrCode.paybysquare?.let { "PayBySquare" },
                                            resp.qrCode.sepa?.let { "SEPA" },
                                            resp.qrCode.mnbQr?.let { "MNB" }
                                        )
                                        append("QR formats: ${formats.joinToString(", ").ifEmpty { "none" }}")
                                    }
                                    val base64 = resp.qrCode.spayd
                                        ?: resp.qrCode.sepa
                                        ?: resp.qrCode.paybysquare
                                        ?: resp.qrCode.mnbQr
                                    if (base64 != null) {
                                        runCatching {
                                            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                            qrBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        }
                                    }
                                } catch (e: GopaySDKException) {
                                    qrResult = "❌ ${formatError(e)}"
                                } catch (e: Exception) {
                                    qrResult = "❌ ${e.message}"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = !isLoading
                    ) { Text("Get QR (PNG)") }
                    Button(
                        onClick = {
                            scopeCoroutine.launch {
                                isLoading = true
                                qrBitmap = null
                                try {
                                    val resp = withContext(Dispatchers.IO) { s.getQrPaymentInfo(QrCodeFormat.SVG) }
                                    qrResult = "✅ SVG QR Info\nAmount: ${resp.amount} ${resp.currency}\nRecipient: ${resp.recipient.name ?: "N/A"}"
                                } catch (e: GopaySDKException) {
                                    qrResult = "❌ ${formatError(e)}"
                                } catch (e: Exception) {
                                    qrResult = "❌ ${e.message}"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.weight(1f), enabled = !isLoading
                    ) { Text("Get QR (SVG)") }
                }
                ResultBox(qrResult)
                qrBitmap?.let { bitmap ->
                    Text("QR Code Preview:", style = MaterialTheme.typography.labelMedium)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(200.dp)
                    )
                }
            }

            // === GOOGLE PAY INFO ===
            SectionCard(title = "Google Pay Info") {
                Button(
                    onClick = {
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) { s.getGooglePayInfo() }
                                val available = GopaySDK.getInstance().isGooglePayAvailable(
                                    context as Activity, resp
                                )
                                googlePayInfoResult = buildString {
                                    appendLine("✅ Google Pay Info retrieved!")
                                    appendLine("Environment: ${resp.environment}")
                                    appendLine("Merchant: ${resp.paymentDataRequest.merchantInfo.merchantName}")
                                    appendLine("Merchant ID: ${resp.paymentDataRequest.merchantInfo.merchantId ?: "N/A"}")
                                    appendLine("Currency: ${resp.paymentDataRequest.transactionInfo.currencyCode}")
                                    appendLine("Amount: ${resp.paymentDataRequest.transactionInfo.totalPrice}")
                                    val methods = resp.paymentDataRequest.allowedPaymentMethods.joinToString(", ") { it.type }
                                    appendLine("Payment methods: $methods")
                                    append("Google Pay available: $available")
                                }
                            } catch (e: GopaySDKException) {
                                googlePayInfoResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                googlePayInfoResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Get Google Pay Info") }
                ResultBox(googlePayInfoResult)
            }

            // === GOOGLE PAY CHARGE ===
            SectionCard(title = "Google Pay Charge") {
                Text(
                    "Launches the Google Pay sheet and charges the payment in one step. " +
                    "Requires a real device with Google Pay configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                val resp = s.chargeWithGooglePay(context as Activity)
                                resp.action?.redirectUrl?.let { threeDsRedirectUrl = it }
                                val actionInfo = resp.action?.let {
                                    "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                                } ?: "Action: none"
                                googlePayChargeResult =
                                    "✅ Google Pay charge submitted!\nCharge ID: ${resp.id}\nState: ${resp.state}\n$actionInfo"
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                googlePayChargeResult = "⚠️ Cancelled by user"
                            } catch (e: GopaySDKException) {
                                googlePayChargeResult = "❌ ${formatError(e)}"
                            } catch (e: Exception) {
                                googlePayChargeResult = "❌ ${e.message}"
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Charge with Google Pay") }
                ResultBox(googlePayChargeResult)
            }

            // === CHARGE WITH CARD TOKEN ===
            SectionCard(title = "Charge with Card Token") {
                Text(
                    "Submit a card token (obtained server-side from the JWE generated below).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cardTokenForCharge, onValueChange = { cardTokenForCharge = it },
                    label = { Text("Card Token") },
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                )
                Button(
                    onClick = {
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    s.charge(
                                        ChargePaymentRequest.cardToken(
                                            cardToken = cardTokenForCharge.trim(),
                                            browserData = BrowserData(
                                                language = "en-US",
                                                timezone = 0,
                                                screenWidth = 1080,
                                                screenHeight = 1920,
                                                colorDepth = 24,
                                                javascriptEnabled = true
                                            ),
                                            challengePreference = ChallengePreference.AUTO
                                        )
                                    )
                                }
                                resp.action?.redirectUrl?.let { threeDsRedirectUrl = it }
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
                    enabled = !isLoading && cardTokenForCharge.isNotBlank()
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
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                s.handle3dsVerification(context as Activity, threeDsRedirectUrl.trim())
                                threeDsResult = "✅ 3DS verification completed"
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                threeDsResult = "⚠️ 3DS cancelled by user"
                            } catch (e: GopaySDKException) {
                                threeDsResult = "❌ ${formatError(e)}"
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
            SectionCard(title = "Charge State") {
                Button(
                    onClick = {
                        scopeCoroutine.launch {
                            isLoading = true
                            try {
                                val resp = withContext(Dispatchers.IO) { s.getChargeState() }
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
                    modifier = Modifier.fillMaxWidth(), enabled = !isLoading
                ) { Text("Get Charge State") }
                ResultBox(chargeStateResult)
            }
        }

        // === CARD FORM → JWE === (shareable_key path — works without a session)
        SectionCard(title = "Card Form (JWE for merchant backend)") {
            Text(
                "Card data is JWE-encrypted on the device using GET /cards/public-key. " +
                "Send the resulting JWE to your backend, which calls POST /cards/tokens to " +
                "obtain the card token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PaymentCardFormDemo(onJweObtained = { jwe -> lastJwe = jwe })
            if (lastJwe.isNotEmpty()) {
                ResultBox(
                    "✅ JWE produced (${lastJwe.length} chars):\n${lastJwe.take(120)}…\n" +
                    "Forward this to your merchant backend, which calls POST /cards/tokens " +
                    "and returns the card_token. Paste that token (not the JWE) into the " +
                    "Charge with Card Token section above."
                )
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
fun PaymentCardFormDemo(onJweObtained: (String) -> Unit) {
    var jwePayload by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var submitCardData: (suspend () -> CardEncryptionResult)? by remember { mutableStateOf(null) }

    var inputFields by remember {
        mutableStateOf(
            PaymentFormInputs(
                cardNumber = InputFieldConfig(label = "Card Number", helperText = "", placeholder = "1234 1234 1234 1234"),
                expirationDate = InputFieldConfig(label = "Expiry Date", helperText = "", placeholder = "12/27"),
                cvv = InputFieldConfig(label = "Security Code", helperText = "", placeholder = "123")
            )
        )
    }

    val coroutineScope = rememberCoroutineScope()

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
                onEncryptionComplete = { result ->
                    isProcessing = false
                    when (result) {
                        is CardEncryptionResult.Success -> {
                            jwePayload = result.jwe
                            errorMessage = null
                            onJweObtained(result.jwe)
                            inputFields = inputFields.copy(
                                cardNumber = inputFields.cardNumber.copy(hasError = false, errorText = null),
                                expirationDate = inputFields.expirationDate.copy(hasError = false, errorText = null),
                                cvv = inputFields.cvv.copy(hasError = false, errorText = null)
                            )
                        }
                        is CardEncryptionResult.Error -> {
                            jwePayload = null
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
                theme = cardFormTheme
            )
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    isProcessing = true
                    jwePayload = null
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
                    Text("Encrypting…")
                }
            } else {
                Text("Encrypt card data")
            }
        }

        errorMessage?.let { error ->
            ResultBox("❌ $error")
        }
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
