# GoPay Android SDK

A modern Android SDK for GoPay payment processing with automatic context initialization.

## Features

- ✅ **Auto-Context Initialization** - No need to manually pass Android Context
- ✅ **Secure Token Storage** - Automatic JWT token management with SharedPreferences
- ✅ **Automatic Token Refresh** - Handles expired tokens transparently
- ✅ **Multiple Environments** - Support for Development, Staging, Sandbox, and Production
- ✅ **Type-Safe Configuration** - Kotlin-first API design
- ✅ **Comprehensive Testing** - Full unit test coverage

## Quick Start

### 1. Add Dependency

Add the SDK to your app's `build.gradle`:

```kotlin
dependencies {
    implementation project(':sdk')
}
```

### 2. Initialize the SDK

The SDK automatically obtains Application context - no manual context passing required!

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Simple initialization - context is handled automatically
        val config = GopayConfig(
            environment = Environment.SANDBOX, // or PRODUCTION
            debugLoggingEnabled = true
        )
        
        GopaySDK.initialize(config)
    }
}
```

### 3. Set Authentication

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sdk = GopaySDK.getInstance()
        
        // Get authentication response from your server
        val authResponse = getAuthFromServer()
        
        // Set authentication - tokens are automatically stored
        sdk.setAuthenticationResponse(authResponse)
        
        // Check if authenticated
        val isAuthenticated = sdk.isAuthenticated()
    }
}
```


## GopaySDK API Overview

The `GopaySDK` class is the main entry point for all SDK operations. Below are the most important public methods and their usage:

### Configuration

Allows to select environment, other properties are optional

```kotlin
val config = GopayConfig(
    environment = Environment.SANDBOX,
    debugLoggingEnabled = true,
    requestTimeoutMs = 30000
)
```

### Initialization

```kotlin
// Automatic context (recommended)
GopaySDK.initialize(config)

// Manual context (for special cases)
GopaySDK.initialize(config, context)
```

### Authentication

Authenticate with your GoPay credentials (client credentials flow):

```kotlin
val sdk = GopaySDK.getInstance()

// This should be called from a coroutine context (e.g., inside a suspend function or coroutine scope)
val authResponse = sdk.authenticate(clientId, clientSecret, scope = null)
// Tokens are automatically stored and managed by the SDK
```

#### Setting Authentication Response
If you obtain tokens from your backend, you can set them directly:

```kotlin
sdk.setAuthenticationResponse(authResponse)
```

#### Checking Authentication

```kotlin
val isAuthenticated = sdk.isAuthenticated()
```

#### Logging Out

```kotlin
sdk.logout()
```

### Automatic Token Refresh

The SDK automatically handles token expiration and refresh. When you make API calls (such as tokenizing a card), if the access token is expired, the SDK will transparently use the stored refresh token to obtain a new access token. If the refresh token is missing or invalid, an authentication error will be thrown.

You can also manually trigger a token refresh if needed:

```kotlin
val newAuthResponse = sdk.refreshToken()
```

### Token Storage Access

You can access the underlying token storage for advanced use cases:

```kotlin
val tokenStorage = sdk.getTokenStorage()
val accessToken = tokenStorage.getAccessToken()
val refreshToken = tokenStorage.getRefreshToken()
```

---

## PaymentCardForm UI Component

The SDK provides a secure, ready-to-use Jetpack Compose UI component for collecting and tokenizing card data:

### Usage Example

```kotlin
import com.gopay.sdk.ui.PaymentCardForm
import com.gopay.sdk.ui.TokenizationResult

@Composable
fun MyPaymentScreen() {
    PaymentCardForm(
        onTokenizationComplete = { result ->
            when (result) {
                is TokenizationResult.Success -> {
                    val token = result.tokenResponse.token
                    // Use the token for payment/charging
                }
                is TokenizationResult.Error -> {
                    // Show error to user
                }
            }
        }
    )
}
```

- The form handles all validation and never exposes raw card data to your app.
- On successful tokenization, you receive a `CardTokenResponse` containing the secure token.
- You can customize field labels, error handling, and theme.

#### Advanced: Manual Submission
You can obtain a submit function for external triggering (e.g., from a button):

```kotlin
var submitCardForm: (suspend () -> TokenizationResult)? by remember { mutableStateOf(null) }

PaymentCardForm(
    onTokenizationComplete = { /* ... */ },
    onFormReady = { submit -> submitCardForm = submit }
)

// Later, trigger submission:
LaunchedEffect(submitCardForm) {
    val result = submitCardForm?.invoke()
    // Handle result
}
```

### Theming and Customization

The appearance of `PaymentCardForm` can be fully customized using the `PaymentCardFormTheme` parameter. This allows you to adjust colors, text styles, shapes, and spacing to match your app's design system.

#### Example: Custom Theme

```kotlin
import com.gopay.sdk.ui.PaymentCardForm
import com.gopay.sdk.ui.PaymentCardFormTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val customTheme = PaymentCardFormTheme(
    labelTextStyle = TextStyle(color = Color(0xFF333333), fontSize = 16.sp),
    inputTextStyle = TextStyle(fontSize = 18.sp),
    helperTextStyle = TextStyle(color = Color(0xFF888888), fontSize = 12.sp),
    errorTextStyle = TextStyle(color = Color(0xFFD32F2F), fontSize = 12.sp),
    loadingTextStyle = TextStyle(color = Color(0xFF333333), fontSize = 14.sp),
    inputBorderColor = Color(0xFFCCCCCC),
    inputErrorBorderColor = Color(0xFFD32F2F),
    inputBackgroundColor = Color(0xFFF5F5F5),
    inputBorderWidth = 2.dp,
    inputShape = RoundedCornerShape(8.dp),
    inputPadding = PaddingValues(16.dp),
    fieldSpacing = 8.dp,
    groupSpacing = 24.dp
)

PaymentCardForm(
    onTokenizationComplete = { /* ... */ },
    theme = customTheme
)
```

You can override any or all properties of `PaymentCardFormTheme` to achieve the desired look and feel. See the `PaymentCardFormTheme` data class in the SDK for all available options.

## Environments

| Environment | Description | API Base URL |
|-------------|-------------|--------------|
| `DEVELOPMENT` | Local development, local network access only | `"https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/"` |
| `STAGING` | Pre-production testing | `not yet configured` |
| `SANDBOX` | Sandbox testing | `not yet configured` |
| `PRODUCTION` | Live production | `not yet configured` |

## Error Handling

The SDK uses a unified exception system with structured error codes for better error handling and analytics integration.

### Basic Error Handling

```kotlin
try {
    sdk.setAuthenticationResponse(authResponse)
} catch (e: GopaySDKException) {
    when {
        e.isAuthenticationError() -> handleAuthError(e)
        e.isNetworkError() -> handleNetworkError(e)
        e.isConfigurationError() -> handleConfigError(e)
        else -> handleGenericError(e)
    }
}
```

### Error Code-Based Handling

```kotlin
try {
    sdk.authenticate(clientId, clientSecret)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.AUTH_ACCESS_TOKEN_EXPIRED -> refreshToken()
        GopayErrorCodes.NETWORK_TIMEOUT -> retryWithBackoff()
        GopayErrorCodes.AUTH_INVALID_CREDENTIALS -> showLoginError()
        else -> showGenericError(e.message)
    }
}
```

### Error Reporting Integration

```kotlin
val config = GopayConfig(
    environment = Environment.PRODUCTION,
    errorCallback = { error ->
        // Integrate with your analytics system
        analytics.trackError(error.errorCode, mapOf(
            "message" to error.message,
            "httpStatus" to error.getHttpStatusCode()?.toString()
        ))
    }
)
```

### HTTP Error Context

```kotlin
catch (e: GopaySDKException) {
    e.httpContext?.let { httpContext ->
        when (httpContext.statusCode) {
            401 -> handleUnauthorized()
            429 -> handleRateLimit()
            500 -> handleServerError()
        }
    }
}
```

For a complete list of error codes and handling recommendations, see [ERROR_CODES.md](ERROR_CODES.md).

## Security Considerations

### App Backup Settings

The GoPay SDK stores authentication tokens securely using Android Keystore encryption. However, as an app developer, you should consider your backup policy:

#### Recommended: Disable Backup for Sensitive Data
```xml
<!-- In your app's AndroidManifest.xml -->
<application
    android:allowBackup="false"
    ... >
```

#### Alternative: Selective Backup Rules
If you need backup functionality, exclude sensitive data:

**res/xml/backup_rules.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="gopay_sdk_secure_prefs.xml"/>
    <!-- Exclude other sensitive preferences -->
</full-backup-content>
```

**AndroidManifest.xml:**
```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    ... >
```

#### Why This Matters
- **Backup exposure**: `android:allowBackup="true"` backs up app data to Google Drive
- **Token security**: Even encrypted tokens shouldn't be unnecessarily exposed
- **Compliance**: Some regulations require preventing data backup

### SDK Security Features

The GoPay SDK implements multiple security layers:

1. **Android Keystore Encryption**: Tokens are encrypted using hardware-backed keys when available
2. **Secure Storage**: Uses Android's secure SharedPreferences mechanisms
3. **Token Validation**: Automatic JWT validation and refresh
4. **Network Security**: HTTPS-only communication with certificate pinning support

## Testing

The SDK includes comprehensive unit tests:

```bash
./gradlew :sdk:test
```

## Auto-Context Implementation

The SDK uses a modern approach similar to Firebase and Glide:

### How It Works

1. **ContentProvider Auto-Initialization**: A `GopayInitProvider` runs automatically when your app starts
2. **Application Context Capture**: The provider captures the Application context before any Activities
3. **Fallback Mechanisms**: If the provider fails, reflection-based fallbacks are attempted
4. **Thread-Safe Storage**: Context is stored safely for use throughout the SDK

### Benefits

- **Better Developer Experience**: No need to remember to pass Context
- **Fewer Errors**: Can't pass wrong Context type (Activity vs Application)
- **Memory Leak Prevention**: Always uses Application context
- **Industry Standard**: Same pattern used by Firebase, Glide, and other major SDKs

## Migration from Manual Context

If you were previously using manual context initialization:

### Before (Manual Context)
```kotlin
GopaySDK.initialize(config, this) // Had to remember to pass context
```

### After (Auto Context)
```kotlin
GopaySDK.initialize(config) // Context handled automatically
```

The manual context method is still available for special use cases, but the auto-context approach is recommended for most applications.

## License

Not yet determined. Not in production.

---

## Upcoming Features

### Google Pay Integration
A future release will add a Google Pay button and integration, allowing users to pay with their saved Google Pay cards directly in the PaymentCardForm UI.

### Charging a Payment Method
After obtaining a card token (via `PaymentCardForm`), you will be able to use a new SDK method to charge the payment method securely. This will allow you to complete the payment flow end-to-end using only the token, without handling sensitive card data yourself.

Stay tuned for updates in the SDK changelog!