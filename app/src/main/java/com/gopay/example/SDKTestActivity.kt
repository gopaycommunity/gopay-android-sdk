package com.gopay.example

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gopay.example.ui.theme.ExampleAppTheme
import com.gopay.sdk.GopaySDK
import com.gopay.sdk.config.Environment
import com.gopay.sdk.config.GopayConfig
import com.gopay.sdk.exception.GopaySDKException
import com.gopay.sdk.exception.GopayErrorCodes
import com.gopay.sdk.model.AuthenticationResponse
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
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isAuthenticated by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("Ready to test SDK methods") }
    var paymentMethodId by remember { mutableStateOf("card") }
    var paymentAmount by remember { mutableStateOf("100.0") }
    
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
                                    GopaySDK.getInstance().tokenStorage().clear()
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
            scope = "payment:create payment:read"
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