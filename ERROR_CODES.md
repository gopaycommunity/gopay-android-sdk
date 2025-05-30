# Gopay SDK Error Codes Reference

This document provides a comprehensive reference for all error codes used in the Gopay SDK. Each error code follows the pattern `DOMAIN_XXX` where `DOMAIN` represents the functional area and `XXX` is a unique 3-digit identifier.

## Error Code Structure

- **AUTH_XXX**: Authentication and authorization errors
- **NETWORK_XXX**: Network connectivity and HTTP errors  
- **CONFIG_XXX**: Configuration and initialization errors
- **PAYMENT_XXX**: Payment processing errors
- **VALIDATION_XXX**: Input validation errors
- **SECURITY_XXX**: Security-related errors
- **INTERNAL_XXX**: Internal SDK errors

## Authentication Errors (AUTH_XXX)

### AUTH_001: Access Token Expired
- **Description**: The access token has expired and needs to be refreshed
- **Common Causes**: 
  - Token lifetime exceeded
  - System clock drift
- **Developer Action**: 
  - The SDK will automatically attempt to refresh the token
  - If refresh fails, re-authenticate the user

### AUTH_002: Refresh Token Expired  
- **Description**: The refresh token has expired, requiring full re-authentication
- **Common Causes**:
  - Long period of inactivity
  - Token revoked on server side
- **Developer Action**: 
  - Redirect user to authentication flow
  - Clear stored tokens

### AUTH_003: Both Tokens Expired
- **Description**: Both access and refresh tokens are expired
- **Common Causes**:
  - Extended period of inactivity
  - Tokens manually cleared
- **Developer Action**:
  - Redirect user to authentication flow
  - Clear stored tokens

### AUTH_004: Token Refresh Failed
- **Description**: Failed to refresh the access token using the refresh token
- **Common Causes**:
  - Network connectivity issues
  - Server-side token validation failure
  - Invalid refresh token
- **Developer Action**:
  - Check network connectivity
  - If persistent, redirect to authentication flow

### AUTH_005: Invalid Authentication Response
- **Description**: The authentication response format is invalid or malformed
- **Common Causes**:
  - Server API changes
  - Network corruption
  - Invalid server response
- **Developer Action**:
  - Retry authentication
  - Check server API documentation
  - Contact support if persistent

### AUTH_006: Invalid Client ID
- **Description**: Cannot extract client ID from the access token
- **Common Causes**:
  - Malformed JWT token
  - Token not issued by expected server
- **Developer Action**:
  - Re-authenticate user
  - Verify token source

### AUTH_007: No Tokens Available
- **Description**: No access token or refresh token is available
- **Common Causes**:
  - User not authenticated
  - Tokens manually cleared
  - First-time app usage
- **Developer Action**:
  - Redirect user to authentication flow

### AUTH_008: Invalid Credentials
- **Description**: Authentication failed due to invalid credentials
- **Common Causes**:
  - Wrong username/password
  - Account suspended
  - Invalid API keys
- **Developer Action**:
  - Prompt user to re-enter credentials
  - Check account status

## Network Errors (NETWORK_XXX)

### NETWORK_001: Connection Timeout
- **Description**: Network request timed out
- **Common Causes**:
  - Slow network connection
  - Server overload
  - Network congestion
- **Developer Action**:
  - Retry with exponential backoff
  - Check network connectivity
  - Consider increasing timeout values

### NETWORK_002: HTTP Client Error (4xx)
- **Description**: HTTP client error (status codes 400-499)
- **Common Causes**:
  - Invalid request parameters
  - Authentication issues
  - Rate limiting
- **Developer Action**:
  - Check request parameters
  - Verify authentication
  - Implement rate limiting handling

### NETWORK_003: HTTP Server Error (5xx)
- **Description**: HTTP server error (status codes 500-599)
- **Common Causes**:
  - Server maintenance
  - Server overload
  - Backend service issues
- **Developer Action**:
  - Retry with exponential backoff
  - Check service status
  - Contact support if persistent

### NETWORK_004: No Internet Connection
- **Description**: No internet connection is available
- **Common Causes**:
  - Device offline
  - WiFi/cellular disabled
  - Network configuration issues
- **Developer Action**:
  - Check device connectivity
  - Prompt user to enable network
  - Queue requests for later retry

### NETWORK_005: SSL/TLS Error
- **Description**: SSL/TLS handshake failed
- **Common Causes**:
  - Certificate issues
  - Outdated TLS version
  - Network proxy interference
- **Developer Action**:
  - Check certificate validity
  - Update TLS configuration
  - Verify network settings

### NETWORK_006: Certificate Pinning Failed
- **Description**: Certificate pinning validation failed
- **Common Causes**:
  - Certificate rotation
  - Man-in-the-middle attack
  - Network proxy
- **Developer Action**:
  - Update certificate pins
  - Verify network security
  - Check for proxy settings

### NETWORK_007: Network I/O Error
- **Description**: General network input/output error
- **Common Causes**:
  - Connection interrupted
  - Socket errors
  - Network hardware issues
- **Developer Action**:
  - Retry request
  - Check network stability
  - Implement robust error handling

### NETWORK_008: Request Cancelled
- **Description**: Network request was cancelled
- **Common Causes**:
  - User cancelled operation
  - App backgrounded
  - Timeout reached
- **Developer Action**:
  - Handle cancellation gracefully
  - Provide user feedback
  - Allow retry if appropriate

## Configuration Errors (CONFIG_XXX)

### CONFIG_001: SDK Not Initialized
- **Description**: SDK has not been initialized before use
- **Common Causes**:
  - Missing `GopaySDK.initialize()` call
  - Initialization called after SDK usage
- **Developer Action**:
  - Call `GopaySDK.initialize(config)` before using SDK
  - Ensure initialization happens in Application.onCreate()

### CONFIG_002: Invalid Configuration Parameter
- **Description**: Invalid configuration parameter provided
- **Common Causes**:
  - Invalid environment setting
  - Malformed URLs
  - Invalid timeout values
- **Developer Action**:
  - Verify configuration parameters
  - Check documentation for valid values
  - Use provided constants where available

### CONFIG_003: Missing Required Parameter
- **Description**: Required configuration parameter is missing
- **Common Causes**:
  - Incomplete configuration object
  - Null values for required fields
- **Developer Action**:
  - Provide all required configuration parameters
  - Check configuration builder usage

### CONFIG_004: Missing Application Context
- **Description**: Cannot obtain Android application context
- **Common Causes**:
  - SDK used before Application.onCreate()
  - Missing ContentProvider in manifest
  - Library usage without manual context
- **Developer Action**:
  - Ensure proper app initialization
  - Use manual context initialization if needed
  - Check manifest configuration

### CONFIG_005: Invalid Environment
- **Description**: Invalid environment configuration
- **Common Causes**:
  - Unsupported environment value
  - Custom environment misconfiguration
- **Developer Action**:
  - Use predefined Environment constants
  - Verify custom environment settings

### CONFIG_006: Invalid Base URL
- **Description**: Invalid API base URL provided
- **Common Causes**:
  - Malformed URL
  - Missing protocol
  - Invalid domain
- **Developer Action**:
  - Verify URL format
  - Ensure protocol is included (https://)
  - Check domain validity

## Payment Errors (PAYMENT_XXX)

### PAYMENT_001: Invalid Payment Method ID
- **Description**: Invalid payment method ID provided
- **Common Causes**:
  - Non-existent payment method
  - Typo in method ID
  - Outdated method reference
- **Developer Action**:
  - Verify payment method ID
  - Refresh available payment methods
  - Check for typos

### PAYMENT_002: Invalid Payment Amount
- **Description**: Invalid payment amount (negative, zero, or too large)
- **Common Causes**:
  - Negative amount
  - Zero amount
  - Amount exceeds limits
- **Developer Action**:
  - Validate amount before processing
  - Check payment limits
  - Ensure positive, non-zero values

### PAYMENT_003: Payment Processing Failed
- **Description**: Payment processing failed
- **Common Causes**:
  - Insufficient funds
  - Payment method declined
  - Technical issues
- **Developer Action**:
  - Check payment details
  - Verify account balance
  - Retry with different payment method

### PAYMENT_004: Payment Method Unavailable
- **Description**: Selected payment method is not available
- **Common Causes**:
  - Method temporarily disabled
  - Geographic restrictions
  - Account limitations
- **Developer Action**:
  - Refresh available methods
  - Offer alternative payment methods
  - Check account status

### PAYMENT_005: Insufficient Funds
- **Description**: Insufficient funds for the payment
- **Common Causes**:
  - Low account balance
  - Credit limit reached
  - Temporary holds on funds
- **Developer Action**:
  - Inform user of insufficient funds
  - Suggest adding funds
  - Offer alternative payment methods

### PAYMENT_006: Payment Declined
- **Description**: Payment was declined by the payment processor
- **Common Causes**:
  - Bank declined transaction
  - Fraud prevention
  - Invalid payment details
- **Developer Action**:
  - Inform user of decline
  - Suggest verifying payment details
  - Offer alternative payment methods

### PAYMENT_007: Payment Timeout
- **Description**: Payment processing timed out
- **Common Causes**:
  - Slow payment processor
  - Network issues
  - Server overload
- **Developer Action**:
  - Retry payment
  - Check payment status
  - Implement timeout handling

## Validation Errors (VALIDATION_XXX)

### VALIDATION_001: Invalid JWT Format
- **Description**: JWT token format is invalid
- **Common Causes**:
  - Malformed token structure
  - Missing token parts
  - Invalid encoding
- **Developer Action**:
  - Re-authenticate user
  - Verify token source
  - Check token handling code

### VALIDATION_002: Missing Required Parameter
- **Description**: Required parameter is missing
- **Common Causes**:
  - Null parameter values
  - Empty required fields
  - Incomplete API calls
- **Developer Action**:
  - Provide all required parameters
  - Check API documentation
  - Validate inputs before API calls

### VALIDATION_003: Invalid Parameter Format
- **Description**: Parameter format is invalid
- **Common Causes**:
  - Wrong data type
  - Invalid format string
  - Encoding issues
- **Developer Action**:
  - Verify parameter format
  - Check data type requirements
  - Use proper encoding

### VALIDATION_004: Parameter Out of Range
- **Description**: Parameter value is outside allowed range
- **Common Causes**:
  - Value too large or small
  - Exceeds system limits
  - Invalid enum value
- **Developer Action**:
  - Check parameter limits
  - Validate ranges before API calls
  - Use allowed values only

### VALIDATION_005: Invalid Email Format
- **Description**: Email address format is invalid
- **Common Causes**:
  - Missing @ symbol
  - Invalid domain
  - Special characters
- **Developer Action**:
  - Validate email format
  - Use email validation libraries
  - Prompt user to correct

### VALIDATION_006: Invalid Phone Format
- **Description**: Phone number format is invalid
- **Common Causes**:
  - Missing country code
  - Invalid characters
  - Wrong length
- **Developer Action**:
  - Validate phone format
  - Use phone validation libraries
  - Provide format examples

## Security Errors (SECURITY_XXX)

### SECURITY_001: Certificate Validation Failed
- **Description**: SSL certificate validation failed
- **Common Causes**:
  - Expired certificate
  - Self-signed certificate
  - Certificate chain issues
- **Developer Action**:
  - Check certificate validity
  - Update certificate configuration
  - Verify certificate chain

### SECURITY_002: SSL Context Failed
- **Description**: SSL context initialization failed
- **Common Causes**:
  - Invalid SSL configuration
  - Missing security providers
  - Incompatible TLS versions
- **Developer Action**:
  - Check SSL configuration
  - Update security providers
  - Verify TLS compatibility

### SECURITY_003: Trust Manager Failed
- **Description**: Trust manager configuration failed
- **Common Causes**:
  - Invalid trust store
  - Missing certificates
  - Configuration errors
- **Developer Action**:
  - Verify trust manager setup
  - Check certificate store
  - Review security configuration

### SECURITY_004: Security Policy Violation
- **Description**: Security policy violation detected
- **Common Causes**:
  - Insecure network
  - Policy enforcement
  - Security constraints
- **Developer Action**:
  - Review security policies
  - Use secure connections
  - Update security settings

## Internal Errors (INTERNAL_XXX)

### INTERNAL_001: Unexpected Error
- **Description**: An unexpected internal error occurred
- **Common Causes**:
  - Programming errors
  - Unexpected conditions
  - System issues
- **Developer Action**:
  - Report to SDK maintainers
  - Check for SDK updates
  - Implement fallback handling

### INTERNAL_002: Not Implemented
- **Description**: Feature is not yet implemented
- **Common Causes**:
  - Using preview features
  - Incomplete SDK version
  - Platform limitations
- **Developer Action**:
  - Check SDK documentation
  - Use alternative approaches
  - Wait for feature implementation

### INTERNAL_003: Serialization Error
- **Description**: Data serialization/deserialization failed
- **Common Causes**:
  - Invalid data format
  - Version incompatibility
  - Encoding issues
- **Developer Action**:
  - Check data format
  - Verify SDK version compatibility
  - Review data handling

### INTERNAL_004: Concurrency Error
- **Description**: Thread safety or concurrency error
- **Common Causes**:
  - Race conditions
  - Improper synchronization
  - Concurrent access issues
- **Developer Action**:
  - Review threading usage
  - Ensure proper synchronization
  - Use SDK from main thread when required

## Error Handling Best Practices

### 1. Catch Specific Errors
```kotlin
try {
    sdk.processPayment(methodId, amount)
} catch (e: GopaySDKException) {
    when {
        e.isAuthenticationError() -> handleAuthError(e)
        e.isNetworkError() -> handleNetworkError(e)
        e.isPaymentError() -> handlePaymentError(e)
        else -> handleGenericError(e)
    }
}
```

### 2. Use Error Codes for Logic
```kotlin
catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.AUTH_ACCESS_TOKEN_EXPIRED -> refreshToken()
        GopayErrorCodes.NETWORK_TIMEOUT -> retryWithBackoff()
        GopayErrorCodes.PAYMENT_INSUFFICIENT_FUNDS -> showAddFundsDialog()
        else -> showGenericError(e.message)
    }
}
```

### 3. Implement Error Reporting
```kotlin
val config = GopayConfig(
    environment = Environment.PRODUCTION,
    errorCallback = { error ->
        analytics.trackError(error.errorCode, mapOf(
            "message" to error.message,
            "httpStatus" to error.getHttpStatusCode()?.toString()
        ))
    }
)
```

### 4. Handle HTTP Context
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

## Support

If you encounter errors not covered in this documentation or need additional assistance:

1. Check the [SDK documentation](README.md)
2. Review your implementation against the examples
3. Enable debug logging to get more details
4. Contact support with the error code and context

For error reporting and analytics integration, see the [Error Reporting Guide](ERROR_REPORTING.md). 