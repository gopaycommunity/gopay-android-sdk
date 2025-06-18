# Gopay SDK Test App

This is a comprehensive test application for the Gopay Android SDK that demonstrates all available features and provides a testing interface for developers.

## Features

### 🏠 Main Menu
- Navigation hub for accessing different demo screens
- Overview of available SDK capabilities

### 🔧 SDK Test Suite
A comprehensive testing interface that includes:

#### Authentication Testing
- **Username/Password Input**: Enter any credentials (demo accepts any non-empty values)
- **Mock Authentication**: Simulates a real authentication flow with JWT token generation
- **Token Management**: View, check, and clear stored authentication tokens
- **Error Handling**: Demonstrates the unified exception system with detailed error reporting

#### Payment Methods
- **Get Payment Methods**: Retrieve available payment options
- **Method Information**: Display payment method details (ID, name, etc.)

#### Payment Processing
- **Amount Input**: Enter payment amounts with validation
- **Method Selection**: Choose payment method by ID
- **Process Payments**: Execute payment transactions
- **Result Display**: Show success/failure with detailed information

#### Token Management
- **Token Status**: Check current token availability and content
- **Token Clearing**: Clear stored tokens and reset authentication state
- **Token Validation**: Automatic JWT validation and expiration checking

### 🛒 Checkout Demo
- Realistic checkout flow demonstration
- Payment method selection interface
- Integration example for production apps

## SDK Features Demonstrated

### ✅ Unified Exception System
- **Structured Error Codes**: All errors use standardized codes (AUTH_001, NETWORK_002, etc.)
- **HTTP Context**: Detailed HTTP error information including status codes, URLs, and response bodies
- **Error Categorization**: Helper methods to check error types (authentication, network, payment, etc.)
- **Error Reporting**: Callback-based analytics integration

### ✅ Automatic Context Management
- **Auto-Initialization**: SDK automatically obtains Android Application context
- **No Manual Context**: Developers don't need to pass context manually
- **Memory Leak Prevention**: Always uses Application context, never Activity context

### ✅ Secure Token Storage
- **SharedPreferences**: Secure local storage for JWT tokens
- **Automatic Refresh**: Transparent token refresh when access tokens expire
- **Token Validation**: JWT parsing and expiration checking

### ✅ Network Management
- **HTTP Client**: Pre-configured OkHttp client with authentication
- **Request Interceptors**: Automatic token attachment to API requests
- **Error Handling**: Network error detection and reporting

## How to Use

### 1. Launch the App
```bash
./gradlew :app:installDebug
adb shell am start -n com.gopay.example/.MainActivity
```

### 2. Choose SDK Test Suite
- Tap "Open SDK Test Suite" from the main menu
- This opens the comprehensive testing interface

### 3. Test Authentication
1. Enter any username and password (demo mode accepts any values)
2. Tap "Authenticate"
3. Observe the authentication flow and token generation
4. Check the results section for detailed feedback

### 4. Test SDK Methods
After authentication, you can:
- **Get Payment Methods**: Test the payment method retrieval
- **Process Payments**: Try different payment amounts and methods
- **Manage Tokens**: Check token status and clear tokens

### 5. Monitor Error Reporting
- All SDK errors are displayed in the results section
- Error codes, messages, and HTTP context are shown
- Global error reporting is logged to console

## Error Testing

To test the unified exception system, try:

1. **Invalid Authentication**: Use expired tokens or invalid credentials
2. **Network Errors**: Simulate network issues (airplane mode)
3. **Payment Errors**: Use invalid payment amounts (negative, zero)
4. **Configuration Errors**: Try to use SDK before initialization

## Code Examples

### Basic SDK Usage
```kotlin
// SDK is automatically initialized in ExampleApplication
val sdk = GopaySDK.getInstance()

// Authenticate with tokens
sdk.setAuthenticationResponse(authResponse)

// Check authentication status
val isAuthenticated = sdk.isAuthenticated()

// Access token storage
val tokenStorage = sdk.getTokenStorage()
val accessToken = tokenStorage.getAccessToken()
```

### Error Handling
```kotlin
try {
    sdk.processPayment("card", 100.0)
} catch (e: GopaySDKException) {
    when {
        e.isAuthenticationError() -> handleAuthError(e)
        e.isNetworkError() -> handleNetworkError(e)
        e.isPaymentError() -> handlePaymentError(e)
        else -> handleGenericError(e)
    }
}
```

### Error Reporting
```kotlin
val config = GopayConfig(
    environment = Environment.SANDBOX,
    errorCallback = { error ->
        analytics.trackError(error.errorCode, mapOf(
            "message" to error.message,
            "httpStatus" to error.getHttpStatusCode()?.toString()
        ))
    }
)
```

## Environment Configuration

The app is configured for testing:
- **Environment**: Sandbox
- **Debug Logging**: Enabled
- **Request Timeout**: 30 seconds
- **Error Reporting**: Console logging + callback demonstration

## Security Notes

- This is a **demo/test app** - not for production use
- Authentication uses mock tokens for testing
- Real implementations should use proper backend authentication
- Always validate inputs and handle errors appropriately

## Next Steps

After testing the SDK features:
1. Review the error codes in [ERROR_CODES.md](../ERROR_CODES.md)
2. Check the SDK documentation in [README.md](../README.md)
3. Implement the SDK in your production app
4. Set up proper error reporting and analytics integration

## Troubleshooting

### Common Issues

**SDK Not Initialized Error**
- Ensure ExampleApplication is properly configured in AndroidManifest.xml
- Check that the app has proper Application context access

**Authentication Failures**
- Verify token format and expiration
- Check network connectivity
- Review error codes and messages

**Payment Processing Issues**
- Validate payment amounts (must be positive)
- Ensure authentication is completed first
- Check payment method IDs

### Debug Information

Enable detailed logging:
```kotlin
val config = GopayConfig(
    environment = Environment.SANDBOX,
    debugLoggingEnabled = true
)
```

All errors are automatically reported to the console with full context information. 