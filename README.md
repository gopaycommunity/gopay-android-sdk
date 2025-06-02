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
        
        // Now you can make payments
        val success = sdk.processPayment("card", 100.0)
    }
}
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

## API Reference

### Initialization

```kotlin
// Automatic context (recommended)
GopaySDK.initialize(config)

// Manual context (for special cases)
GopaySDK.initialize(config, context)
```

### Configuration

```kotlin
val config = GopayConfig(
    environment = Environment.SANDBOX,
    debugLoggingEnabled = true,
    requestTimeoutMs = 30000
)
```

### Authentication

```kotlin
val sdk = GopaySDK.getInstance()

// Set authentication tokens
sdk.setAuthenticationResponse(authResponse)

// Check authentication status
val isAuthenticated = sdk.getTokenStorage().getAccessToken() != null

// Clear authentication
sdk.getTokenStorage().clear()
```

### Payments

```kotlin
val sdk = GopaySDK.getInstance()

// Get available payment methods
val methods = sdk.getPaymentMethods()

// Process a payment
val success = sdk.processPayment("card", 100.0)
```

## Environments

| Environment | Description | API Base URL |
|-------------|-------------|--------------|
| `DEVELOPMENT` | Local development | `https://api.dev.gopay.com/v1/` |
| `STAGING` | Pre-production testing | `https://api.staging.gopay.com/v1/` |
| `SANDBOX` | Sandbox testing | `https://api.sandbox.gopay.com/v1/` |
| `PRODUCTION` | Live production | `https://api.gopay.com/v1/` |

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
    sdk.processPayment("card", 100.0)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.AUTH_ACCESS_TOKEN_EXPIRED -> refreshToken()
        GopayErrorCodes.NETWORK_TIMEOUT -> retryWithBackoff()
        GopayErrorCodes.PAYMENT_INSUFFICIENT_FUNDS -> showAddFundsDialog()
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

## Testing

The SDK includes comprehensive unit tests:

```bash
./gradlew :sdk:test
```

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

## Architecture

```
┌─────────────────────────────────────────┐
│ GopaySDK (Main Entry Point)            │
├─────────────────────────────────────────┤
│ GopayContextProvider (Auto Context)    │
├─────────────────────────────────────────┤
│ NetworkManager (HTTP Client)           │
├─────────────────────────────────────────┤
│ TokenStorage (SharedPreferences)       │
├─────────────────────────────────────────┤
│ AuthenticationInterceptor (Auto Refresh)│
└─────────────────────────────────────────┘
```

## License

[Add your license information here]

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