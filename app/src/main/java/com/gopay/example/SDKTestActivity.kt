package com.gopay.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.CardData
import cz.gopay.sdk.model.ChallengePreference
import cz.gopay.sdk.model.ChargePaymentRequest
import cz.gopay.sdk.model.QrCodeFormat
import cz.gopay.sdk.session.PaymentSession
import cz.gopay.sdk.ui.CardEncryptionResult
import cz.gopay.sdk.ui.PaymentCardForm
import cz.gopay.sdk.ui.PaymentCardFormTheme
import cz.gopay.sdk.ui.PaymentFormInputs
import kotlinx.coroutines.launch

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

@Composable
fun SDKTestScreen() {
    var paymentId by remember { mutableStateOf("") }
    var paymentSecret by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<PaymentSession?>(null) }
    var cardToken by remember { mutableStateOf("") }
    var jwe by remember { mutableStateOf("") }
    var pending3dsUrl by remember { mutableStateOf<String?>(null) }
    var responseText by remember { mutableStateOf("Ready.") }
    var busyLabel by remember { mutableStateOf<String?>(null) }

    val isBusy = busyLabel != null
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun log(message: String) {
        responseText = if (responseText == "Ready." || responseText.isEmpty()) message
                       else "$responseText\n\n$message"
    }

    fun run(label: String, block: suspend () -> Unit) {
        coroutineScope.launch {
            responseText = ""
            busyLabel = label
            try {
                block()
            } catch (e: GopaySDKException) {
                log("Error [${e.errorCode}]: ${e.message}" +
                    e.httpContext?.let { "\nHTTP ${it.statusCode} ${it.requestMethod} ${it.requestUrl}" +
                        (it.responseBody?.takeIf { b -> b.isNotEmpty() }?.let { b -> "\n${b.take(300)}" } ?: "") }.orEmpty())
            } catch (e: kotlinx.coroutines.CancellationException) {
                log("Cancelled by user.")
            } catch (e: Exception) {
                log("Error: ${e.message}")
            } finally {
                busyLabel = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "GoPay SDK",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // === SECTION 1: MERCHANT BACKEND (SIMULATED) ===
        SectionCard("1. Merchant backend (simulated)") {
            Text(
                "In production your server does this with merchant credentials and returns " +
                "the pair below. Here it's simulated in-app so the demo is self-contained.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DemoButton("Create payment on \"server\"", enabled = !isBusy) {
                run("Create payment") {
                    val created = MerchantBackendSimulator.createPayment(amount = 1000, currency = "CZK")
                    paymentId = created.paymentId
                    paymentSecret = created.paymentSecret
                    log("// merchant backend created a payment\npayment_id: ${created.paymentId}\npayment_secret: ${created.paymentSecret}")
                }
            }
            LabeledField("payment_id", paymentId, enabled = !isBusy) { paymentId = it }
            LabeledField("payment_secret", paymentSecret, enabled = !isBusy) { paymentSecret = it }
        }

        // === SECTION 2: PAYMENT SESSION ===
        SectionCard("2. Payment session") {
            DemoButton(
                if (session == null) "Start session" else "Restart session",
                enabled = !isBusy && paymentId.isNotBlank() && paymentSecret.isNotBlank()
            ) {
                run("Start session") {
                    session?.close()
                    val started = GopaySDK.getInstance().startPaymentSession(
                        paymentId = paymentId.trim(),
                        paymentSecret = paymentSecret.trim()
                    )
                    session = started
                    log("// startPaymentSession() — eager auth OK\npayment_id: ${paymentId.trim()}\nscope: ${PaymentSession.DEFAULT_SCOPE}")
                }
            }
            if (session != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Session live: ${session!!.paymentId}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        session?.close()
                        session = null
                        log("Session closed.")
                    }, enabled = !isBusy) { Text("Close") }
                }
            }
        }

        // === SECTIONS 3 & 4: only visible when session exists ===
        val s = session
        if (s != null) {

            // === SECTION 3: OPERATIONS ===
            SectionCard("3. Operations") {
                DemoButton("Get status", enabled = !isBusy) {
                    run("Get status") {
                        val resp = s.getStatus()
                        val chargeInfo = resp.charge?.let { "Charge ID: ${it.id}\nCharge state: ${it.state}" } ?: "Charge: none"
                        log("// getStatus() -> PaymentDetails\nState: ${resp.state}\nID: ${resp.id}\nAmount: ${resp.amount} ${resp.currency}\n$chargeInfo")
                    }
                }

                DemoButton("Get Google Pay info", enabled = !isBusy) {
                    run("Get Google Pay info") {
                        val resp = s.getGooglePayInfo()
                        val available = GopaySDK.getInstance().isGooglePayAvailable(context as Activity, resp)
                        log("// getGooglePayInfo() -> GooglePayInfoResponse\nEnvironment: ${resp.environment}\nMerchant: ${resp.paymentDataRequest.merchantInfo.merchantName}\nCurrency: ${resp.paymentDataRequest.transactionInfo.currencyCode}\nAmount: ${resp.paymentDataRequest.transactionInfo.totalPrice}\nGoogle Pay available: $available")
                    }
                }

                DemoButton("Charge with Google Pay", enabled = !isBusy) {
                    run("Charge with Google Pay") {
                        val charge = s.chargeWithGooglePay(context as Activity)
                        val actionInfo = charge.action?.let {
                            "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                        } ?: "Action: none"
                        log("// chargeWithGooglePay() -> ChargePaymentResponse\nCharge ID: ${charge.id}\nState: ${charge.state}\n$actionInfo")
                        charge.action?.redirectUrl?.let { pending3dsUrl = it }
                        if (pending3dsUrl != null) log("3DS required — tap \"Handle 3DS verification\" to continue.")
                    }
                }

                DemoButton("Get test card token (server)", enabled = !isBusy) {
                    run("Get test card token") {
                        val testCard = CardData(
                            cardPan = "4444444444444448",
                            expMonth = "12",
                            expYear = "28",
                            cvv = "123"
                        )
                        val jwe = GopaySDK.getInstance().encryptCardData(testCard)
                        val token = MerchantBackendSimulator.tokenizeCard(jwe)
                        cardToken = token
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("card_token", token))
                        log("// test card 4444…4448 (12/28) tokenized on \"server\"\ncard_token: $token\n(filled into the field below + copied to clipboard)")
                    }
                }

                LabeledField(
                    "card_token (from server-side tokenization)",
                    cardToken,
                    enabled = !isBusy
                ) { cardToken = it }

                DemoButton(
                    "Charge a payment",
                    enabled = !isBusy && cardToken.isNotBlank()
                ) {
                    run("Charge payment") {
                        val charge = s.charge(
                            ChargePaymentRequest.cardToken(
                                cardToken = cardToken.trim(),
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
                        val actionInfo = charge.action?.let {
                            "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                        } ?: "Action: none"
                        log("// charge(.cardToken) -> ChargePaymentResponse\nCharge ID: ${charge.id}\nState: ${charge.state}\n$actionInfo")
                        charge.action?.redirectUrl?.let { pending3dsUrl = it }
                        if (pending3dsUrl != null) log("3DS required — tap \"Handle 3DS verification\" to continue.")
                    }
                }

                DemoButton("Get charge state", enabled = !isBusy) {
                    run("Get charge state") {
                        val resp = s.getChargeState()
                        val actionInfo = resp.action?.let {
                            "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                        } ?: "Action: none"
                        log("// getChargeState() -> ChargePaymentResponse\nState: ${resp.state}\nCharge ID: ${resp.id}\n$actionInfo")
                        resp.action?.redirectUrl?.let { pending3dsUrl = it }
                    }
                }

                DemoButton(
                    "Handle 3DS verification",
                    enabled = !isBusy && pending3dsUrl != null
                ) {
                    run("Handle 3DS verification") {
                        val url = pending3dsUrl ?: return@run
                        pending3dsUrl = null
                        s.handle3dsVerification(context as Activity, url)
                        val finalState = s.getChargeState()
                        log("// getChargeState() after 3DS -> ChargePaymentResponse\nState: ${finalState.state}\nCharge ID: ${finalState.id}")
                    }
                }

                DemoButton("Get QR payment info", enabled = !isBusy) {
                    run("Get QR payment info") {
                        val resp = s.getQrPaymentInfo(QrCodeFormat.PNG)
                        val formats = listOfNotNull(
                            resp.qrCode.spayd?.let { "SPAYD" },
                            resp.qrCode.paybysquare?.let { "PayBySquare" },
                            resp.qrCode.sepa?.let { "SEPA" },
                            resp.qrCode.mnbQr?.let { "MNB" }
                        )
                        log(buildString {
                            appendLine("// getQrPaymentInfo(format: .png) -> QrPaymentDetails")
                            appendLine("Amount: ${resp.amount} ${resp.currency}")
                            appendLine("Recipient: ${resp.recipient?.name ?: "N/A"}")
                            resp.recipient?.bankAccount?.local?.let {
                                appendLine("Account: ${it.accountNumber}/${it.bankCode}")
                                appendLine("Variable symbol: ${it.variableSymbol}")
                            }
                            resp.recipient?.bankAccount?.international?.let {
                                appendLine("IBAN: ${it.iban ?: "N/A"}")
                                appendLine("BIC: ${it.bic ?: "N/A"}")
                            }
                            append("QR formats: ${formats.joinToString(", ").ifEmpty { "none" }}")
                        })
                    }
                }
            }

            // === SECTION 4: CARD FORM → JWE ===
            SectionCard("4. Card form → JWE") {
                Text(
                    "Card data is JWE-encrypted on the device using the public key from GET " +
                    "/cards/public-key. Send the JWE to your backend for POST /cards/tokens, or " +
                    "charge the encrypted card directly below — no tokenization round-trip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CardFormSection(isBusy = isBusy) { encrypted ->
                    jwe = encrypted
                    log("// submitCardForm() -> JWE (filled into the field below)\n${encrypted.take(120)}…")
                }

                LabeledField(
                    "JWE (from on-device card encryption)",
                    jwe,
                    enabled = !isBusy
                ) { jwe = it }

                DemoButton(
                    "Charge with encrypted card (JWE)",
                    enabled = !isBusy && jwe.isNotBlank()
                ) {
                    run("Charge with encrypted card") {
                        // Charge the encrypted card directly — no POST /cards/tokens round-trip.
                        val charge = s.charge(
                            ChargePaymentRequest.encryptedCard(
                                payload = jwe.trim(),
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
                        val actionInfo = charge.action?.let {
                            "Action: ${it.actionType} (${it.state})\nRedirect: ${it.redirectUrl ?: "N/A"}"
                        } ?: "Action: none"
                        log("// charge(.encryptedCard) -> ChargePaymentResponse\nCharge ID: ${charge.id}\nState: ${charge.state}\n$actionInfo")
                        charge.action?.redirectUrl?.let { pending3dsUrl = it }
                        if (pending3dsUrl != null) log("3DS required — tap \"Handle 3DS verification\" to continue.")
                    }
                }
            }
        }

        // === RESPONSE SECTION ===
        SectionCard("Response") {
            busyLabel?.let { label ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
            SelectionContainer {
                Text(
                    responseText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DemoButton(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = if (destructive) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ) else ButtonDefaults.buttonColors()
    ) {
        Text(label)
    }
}

@Composable
fun LabeledField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
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
fun CardFormSection(isBusy: Boolean, onJwe: (String) -> Unit) {
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitCardData: (suspend () -> CardEncryptionResult)? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    // Selected form locale; null follows the SDK/device default (which falls back to Czech).
    var selectedLocale by remember { mutableStateOf<String?>(null) }
    var localeMenuExpanded by remember { mutableStateOf(false) }

    // Per-field validation errors, populated (in the active locale) by onValidationError.
    var cardError by remember { mutableStateOf<String?>(null) }
    var expError by remember { mutableStateOf<String?>(null) }
    var cvvError by remember { mutableStateOf<String?>(null) }

    // Active locale strings — labels come from here, and we also read the localized error messages
    // from it below. (The form exposes the strings; the host decides how to show errors.)
    val localeStrings = GopayLocales.resolve(selectedLocale)

    // Labels/placeholders come from the locale; per-field error text is overlaid from state.
    val baseInputs = PaymentFormInputs.from(localeStrings)
    val inputFields = baseInputs.copy(
        cardNumber = baseInputs.cardNumber.copy(hasError = cardError != null, errorText = cardError),
        expirationDate = baseInputs.expirationDate.copy(hasError = expError != null, errorText = expError),
        cvv = baseInputs.cvv.copy(hasError = cvvError != null, errorText = cvvError)
    )

    val theme = PaymentCardFormTheme(
        labelTextStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)),
        inputTextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        helperTextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
        errorTextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
        loadingTextStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
        inputBorderColor = MaterialTheme.colorScheme.outline,
        inputErrorBorderColor = MaterialTheme.colorScheme.error,
        inputBackgroundColor = MaterialTheme.colorScheme.surface,
        inputShape = MaterialTheme.shapes.small,
        inputBorderWidth = 1.dp
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Locale selector — switch the language of the form labels/placeholders live.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Locale:", style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { localeMenuExpanded = true }) {
                    Text(selectedLocale ?: "System default")
                }
                DropdownMenu(expanded = localeMenuExpanded, onDismissRequest = { localeMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("System default") },
                        onClick = { selectedLocale = null; localeMenuExpanded = false }
                    )
                    GopayLocales.availableCodes().forEach { code ->
                        DropdownMenuItem(
                            text = { Text(code) },
                            onClick = { selectedLocale = code; localeMenuExpanded = false }
                        )
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            PaymentCardForm(
                onEncryptionComplete = { result ->
                    isProcessing = false
                    when (result) {
                        is CardEncryptionResult.Success -> {
                            errorMessage = null
                            onJwe(result.jwe)
                        }
                        is CardEncryptionResult.Error -> {
                            errorMessage = result.message
                        }
                    }
                },
                onFormReady = { submitFn -> submitCardData = submitFn },
                onValidationError = { validation ->
                    cardError = if (!validation.cardNumber.isValid) localeStrings.panErrorPattern else null
                    expError = if (!validation.expirationDate.isValid) localeStrings.expErrorPattern else null
                    cvvError = if (!validation.cvv.isValid) localeStrings.cvvErrorPattern else null
                    errorMessage = null
                },
                inputFields = inputFields,
                theme = theme
            )
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    isProcessing = true
                    errorMessage = null
                    cardError = null
                    expError = null
                    cvvError = null
                    try {
                        submitCardData?.invoke()
                    } catch (e: Exception) {
                        isProcessing = false
                        errorMessage = "Unexpected error: ${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = submitCardData != null && !isProcessing && !isBusy
        ) {
            if (isProcessing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Encrypting…")
                }
            } else {
                Text("Encrypt card → JWE")
            }
        }

        errorMessage?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
