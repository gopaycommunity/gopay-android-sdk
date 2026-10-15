# Gopay SDK Error Codes Reference

This document provides a comprehensive reference for all error codes used in the Gopay SDK. Each error code follows the pattern `DOMAIN_XXX` where `DOMAIN` represents the functional area and `XXX` is a unique 3-digit identifier.

Codes marked "the SDK does not throw this code" are never thrown by either SDK. Their constants in
`GopayErrorCodes` stay deprecated for 2.0, so code written against 1.x that names them still
compiles with a warning, and 3.0 removes them.

## Error Code Structure

- **AUTH_XXX**: Authentication and authorization errors
- **NETWORK_XXX**: Network connectivity and HTTP errors  
- **CONFIG_XXX**: Configuration and initialization errors
- **PAYMENT_XXX**: Payment processing errors
- **CARD_XXX**: Card tokenization errors
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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_002: Refresh Token Expired  
- **Description**: The refresh token has expired, requiring full re-authentication
- **Common Causes**:
  - Long period of inactivity
  - Token revoked on server side
- **Developer Action**: 
  - Redirect user to authentication flow
  - Clear stored tokens
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_003: Both Tokens Expired
- **Description**: Both access and refresh tokens are expired
- **Common Causes**:
  - Extended period of inactivity
  - Tokens manually cleared
- **Developer Action**:
  - Redirect user to authentication flow
  - Clear stored tokens
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_004: Token Refresh Failed
- **Description**: Failed to refresh the access token using the refresh token
- **Common Causes**:
  - Network connectivity issues
  - Server-side token validation failure
  - Invalid refresh token
- **Developer Action**:
  - Check network connectivity
  - If persistent, redirect to authentication flow
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0. A 2xx answer without a body, which 1.x reported here, is `INTERNAL_003`

### AUTH_006: Invalid Client ID
- **Description**: Cannot extract client ID from the access token
- **Common Causes**:
  - Malformed JWT token
  - Token not issued by expected server
- **Developer Action**:
  - Re-authenticate user
  - Verify token source
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_007: No Tokens Available
- **Description**: No access token or refresh token is available
- **Common Causes**:
  - User not authenticated
  - Tokens manually cleared
  - First-time app usage
- **Developer Action**:
  - Redirect user to authentication flow
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_008: Invalid Credentials
- **Description**: Authentication failed due to invalid credentials
- **Common Causes**:
  - Wrong username/password
  - Account suspended
  - Invalid API keys
- **Developer Action**:
  - Prompt user to re-enter credentials
  - Check account status
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### AUTH_009: Payment Token Expired
- **Description**: The payment-scoped JWT expired and could not be re-acquired from the cached payment credentials
- **Common Causes**:
  - Session left idle past the token lifetime
  - `payment_secret` no longer valid for the payment
- **Developer Action**:
  - The SDK re-authenticates once automatically; a second 401 surfaces this code
  - Start a new `PaymentSession` for the payment

### AUTH_010: Invalid Payment Credentials
- **Description**: The `payment_id` / `payment_secret` pair was rejected by `/oauth2/token` with HTTP 400, 401 or 403. Any other failure of that request keeps its own code: `NETWORK_003` for a 5xx, `NETWORK_002` for another 4xx, a transport code (`NETWORK_001`, `004`, `005`, `007`) when the gateway was not reached, and `INTERNAL_*` when the answer cannot be read. The gateway's HTTP error is the `cause`
- **Common Causes**:
  - Pair belongs to a different environment
  - Payment already closed or expired on the gateway
  - `payment_secret` older than seven days, which the gateway refuses with a 403 according to the GoPay guide
  - A `scope` the gateway does not accept for payment credentials, passed to `startPaymentSession` (only `payment:read` and `payment:charge` are)
- **Developer Action**:
  - Re-fetch the pair from your merchant backend
  - Confirm the SDK points at the same environment that created the payment

### AUTH_011: Shareable Key Missing
- **Description**: `clientId` / `shareableKey` are absent from `GopayConfig` but required by the requested operation
- **Common Causes**:
  - SDK initialized without them, then a card was encrypted (`encryptCardData` / `PaymentCardForm` fetch `GET /cards/public-key` every time); no request is sent
  - A charge, which first fetches `GET /cards/browser-data` with the same credentials; the charge is not sent
- **Developer Action**:
  - Supply both in `GopayConfig` — they are safe to embed in the app

### AUTH_012: Payment Session Already Exists
- **Description**: A `PaymentSession` for the given `payment_id` is already registered
- **Common Causes**:
  - `startPaymentSession` called twice for the same payment
- **Developer Action**:
  - Reuse the existing session, or close it before starting another

### AUTH_013: Payment Session Closed
- **Description**: An operation was invoked on a `PaymentSession` that has been closed
- **Common Causes**:
  - Session used after `close()` or `closeAllPaymentSessions()`
- **Developer Action**:
  - Start a new session for the payment

## Network Errors (NETWORK_XXX)

### NETWORK_001: Connection Timeout
- **Description**: Network request timed out; the timeout exception is the `cause`
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
  - Read `httpContext.gatewayError`: the gateway's `error`, `message` and `detail` say what was wrong; the `message` is in the exception's message too
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
- **Description**: The gateway could not be reached: no connection, a DNS failure or a refused connection; the original exception is the `cause`
- **Common Causes**:
  - Device offline
  - WiFi/cellular disabled
  - Network configuration issues
- **Developer Action**:
  - Check device connectivity
  - Prompt user to enable network
  - Queue requests for later retry

### NETWORK_005: SSL/TLS Error
- **Description**: The gateway's certificate failed validation, or it does not match the gateway's host name; the original exception is the `cause`. A TLS connection that is reset or dropped is `NETWORK_007`
- **Common Causes**:
  - Certificate issues
  - A proxy or captive portal presenting its own certificate
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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: Any transport failure that is not a timeout, a missing connection or a certificate failure, a TLS connection reset or dropped midway included. The `IOException` is the exception's `cause`. No call lets a transport failure through bare.

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### CONFIG_003: Missing Required Parameter
- **Description**: Required configuration parameter is missing
- **Common Causes**:
  - Incomplete configuration object
  - Null values for required fields
- **Developer Action**:
  - Provide all required configuration parameters
  - Check configuration builder usage
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### CONFIG_005: Invalid Environment
- **Description**: Invalid environment configuration
- **Common Causes**:
  - Unsupported environment value
  - Custom environment misconfiguration
- **Developer Action**:
  - Use predefined Environment constants
  - Verify custom environment settings
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the Android SDK does not throw this code; the iOS SDK does, so it stays in the shared catalog

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: The SDK raises it when the wallet sheet fails (Google Pay, and Apple Pay on iOS): an error from the wallet, no payment data, a token the SDK cannot read, or the SDK's own Google Pay activity missing from the merged manifest. The wallet's own error is the `cause`. Any error Google Pay returns from the sheet lands here, whatever its cause, a setup problem of the app included, except Google Play services missing, disabled or outdated, which is `PAYMENT_004`. The iOS SDK reports a wallet the app is not set up for as `PAYMENT_004` instead.

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
- **Note**: The SDK raises it when the wallet cannot show its sheet on the device, e.g. Google Play services are missing, disabled or outdated (Apple Pay not set up on iOS). Hide the wallet button. On iOS it also covers an app missing the Apple Pay capability or merchant ID; on Android anything Google Pay returns as an error, other than Google Play services missing, disabled or outdated, is `PAYMENT_003`.

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### PAYMENT_008: Verification Already In Progress
- **Description**: A 3DS verification is already running; only one can run at a time
- **Common Causes**:
  - `handle3dsVerification` invoked while a verification sheet is open
- **Developer Action**:
  - Await the in-flight verification before starting another; to drop it, cancel the coroutine awaiting it, which closes the challenge
- **Note**: A cancelled coroutine ends the running verification with its `CancellationException` and frees the slot, so this code does not outlive it.

### PAYMENT_009: Wallet Sheet Already In Progress
- **Description**: A Google Pay sheet is already running; only one can run at a time (the iOS SDK uses this code for Apple Pay). The SDK raises it for nothing else: a closed sheet is `PAYMENT_011`, other wallet failures are `PAYMENT_003` or `PAYMENT_004`
- **Common Causes**:
  - `chargeWithGooglePay` invoked twice, e.g. from a double tap
- **Developer Action**:
  - Disable the pay button while a charge is in flight

### PAYMENT_010: Verification Unreachable
- **Description**: The 3DS challenge never reached the user, so there was nothing for them to answer
- **Common Causes**:
  - The redirect URL has been retired by the gateway, e.g. the charge it belonged to is long finished
  - The redirect URL is not an `http(s)` address, so the WebView could never have loaded it
  - No connectivity while the challenge was opening
  - The challenge asked to hand off to a banking app and nothing on the device opened it
- **Developer Action**:
  - Read the charge state and report the payment as unverified rather than as abandoned
  - Do not treat it as a user dismissal, which arrives as `PAYMENT_011` instead
  - Charge again to obtain a fresh redirect URL
  - A hand-off nobody took usually means the banking app is not installed; offer the user another way to authorize

### PAYMENT_011: Payment Cancelled
- **Description**: The payment window closed before it finished: the Google Pay sheet or the 3DS screen was closed, or the 3DS page failed after it had started rendering
- **Common Causes**:
  - The Google Pay sheet was closed without paying
  - The 3DS verification screen was closed, by the user or by the app's task being swiped away
  - The 3DS page failed after it had started rendering, when the user may already have answered
- **Developer Action**:
  - Not a failed payment, and not necessarily one that did not happen: read `getChargeState()` before offering the payment again. A 3DS challenge may have been answered
  - When there is no charge (`NETWORK_002`, HTTP 404) or it did not go through, let the user pick a method again
- **Note**: not reported through `errorCallback`. Cancelling the coroutine that awaits the call is not this code: it stays a `CancellationException`, closes the 3DS screen and frees `PAYMENT_008`. With a Google Pay sheet up the call ends at once with the `CancellationException`; the sheet stays until the user closes it, what they do in it is discarded and nothing is charged. The iOS SDK reports the same code for Apple Pay and its 3DS screen

## Card Errors (CARD_XXX)

### CARD_001: Card Tokenization Failed
- **Description**: Card tokenization failed
- **Common Causes**:
  - The merchant backend rejected or failed the `POST /cards/tokens` call
- **Developer Action**:
  - Handle tokenization failures on your backend and surface a retry to the user
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### VALIDATION_007: Invalid Input
- **Description**: Invalid input data was provided to an SDK call
- **Common Causes**:
  - Empty `paymentId` / `paymentSecret`
  - Card form submitted with fields that fail validation
- **Developer Action**:
  - Validate inputs before calling, and surface the form's own validation state
- **Note**: the Android SDK does not throw this code; the iOS SDK does, so it stays in the shared catalog

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

### INTERNAL_003: Serialization Error
- **Description**: Data serialization/deserialization failed
- **Common Causes**:
  - Invalid data format
  - A 2xx answer that is empty, has no body at all (204, 205) or is not JSON, e.g. a captive portal's page
  - Version incompatibility
  - Encoding issues
- **Developer Action**:
  - Check data format
  - Verify SDK version compatibility
  - Review data handling
- **Note**: a new value in one of the response enums is not a decoding error: it decodes as `UNKNOWN` and the original value goes to the debug log

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
- **Note**: the SDK does not throw this code. The constant is deprecated and will be removed in 3.0

## Error Handling Best Practices

### 1. Catch Specific Errors
```kotlin
try {
    session.charge(request)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED -> refreshCredsFromBackend()
        GopayErrorCodes.NETWORK_NO_CONNECTION -> showOffline()
        else -> showGenericError(e)
    }
}
```

### 2. Use Error Codes for Logic
```kotlin
try {
    session.chargeWithGooglePay(activity)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS -> Unit // a sheet is already open
        GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE -> hideGooglePayButton()
        GopayErrorCodes.PAYMENT_CANCELLED -> Unit // the user closed the sheet; not a failure
        // The charge may have gone through before the answer was lost: read it, do not repeat it.
        GopayErrorCodes.NETWORK_TIMEOUT -> readChargeBeforeRetrying(session)
        else -> showGenericError(e) // PAYMENT_PROCESSING_FAILED and the charge's own errors
    }
}

suspend fun readChargeBeforeRetrying(session: PaymentSession) {
    try {
        showResult(session.getChargeState())
    } catch (e: GopaySDKException) {
        if (e.getHttpStatusCode() == 404) offerPaymentAgain() // no charge went out
        else askBackendForState() // still unknown: the charge may be through
    }
}
```

The codes worth a branch of their own are the ones in the README's
[Error handling](../../README.md#error-handling); every code the SDK does not throw is marked so
above.

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
        // What the gateway said, from its error body: error type, message and detail.
        log(httpContext.gatewayError)
        when (httpContext.statusCode) {
            401 -> handleUnauthorized()
            429 -> handleRateLimit()
            500 -> handleServerError()
        }
    }
}
```

`httpContext.gatewayError` holds the gateway's error body (`error`, `message`, `detail`, each
nullable) when the response carried one, and the gateway's `message` is appended to the
exception's own message. The gateway has no finer business codes than `error`, which follows the
HTTP status.

## Support

If you encounter errors not covered in this documentation or need additional assistance:

1. Check the [SDK documentation](../../README.md)
2. Review your implementation against the examples
3. Enable debug logging to get more details
4. Contact support with the error code and context

To receive every exception the SDK throws, except `PAYMENT_011`, which is not an error, register an
analytics callback via `GopayConfig.errorCallback`.
 