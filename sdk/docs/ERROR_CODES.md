# Gopay SDK Error Codes Reference

This document provides a comprehensive reference for all error codes used in the Gopay SDK. Each error code follows the pattern `DOMAIN_XXX` where `DOMAIN` represents the functional area and `XXX` is a unique 3-digit identifier.

3.0 removed the codes the SDK never threw; the codes below are the whole contract, the same on both platforms.

The codes are a **shared cross-platform contract**: they mirror the iOS SDK's `GopaySDKError.Code`
so both platforms report the same code for the same condition. Never add, rename, or repurpose a
code on one platform alone.

Every code the SDK throws also says whether **a charge may exist** on the gateway after it, and
whether the call is **safe to retry**. A charge is created by `charge` and the `chargeWith*` calls;
`awaitChargeState`, `handle3dsVerification` and the reads come after one. Where the answer is "read
the charge state first", ask your backend or `getChargeState()` before charging again, because the
gateway may have created the charge even though the answer never reached the device.

## The `errorCallback` rule

`GopayConfig.errorCallback` is for central logging and monitoring, not a substitute for catching
the exception at the call. The rule is the same on both platforms:

- Every `GopaySDKException` a public call of `GopaySDK` or of a `PaymentSession` throws reaches the
  callback **once**. A call made of
  several steps, such as `chargeWithWallet`, which reads the wallet configuration and then
  charges, or `awaitChargeState`, which reads the charge again and again, reports the one error
  it ends with, not each step on the way; a re-authentication the session runs in the middle of
  a call is one of those steps. An error wrapped into another on the way out, the gateway's 401
  behind `AUTH_010` or the browser-data fetch behind a charge error, is reported as the one you
  catch; the inner one is its `cause`.
- Two codes never reach it, because they are outcomes of a payment window rather than failures:
  `PAYMENT_007` (the charge is still undecided) and `PAYMENT_011` (the sheet or the 3DS screen
  closed early).
- A `CancellationException` from a coroutine the host cancelled never reaches it, and no exception
  of a type other than `GopaySDKException` does. Constructing a `GopaySDKException` yourself
  reports nothing.
- `CONFIG_001` is thrown before `initialize`, when no callback is installed yet, so it does not
  reach one. Neither does a `GopaySDKException` a constructor throws, `Environment.DEVELOPMENT`
  with a bad URL: a constructor is not a call of the SDK.

The callback runs on whatever thread the failing call ends on, not necessarily the main thread.

## Error Code Structure

- **AUTH_XXX**: Authentication and authorization errors
- **NETWORK_XXX**: Network connectivity and HTTP errors  
- **CONFIG_XXX**: Configuration and initialization errors
- **PAYMENT_XXX**: Payment processing errors
- **VALIDATION_XXX**: Input validation errors
- **INTERNAL_XXX**: Internal SDK errors

## Authentication Errors (AUTH_XXX)

### AUTH_009: Payment Token Expired
- **Description**: The payment-scoped JWT expired and could not be re-acquired from the cached payment credentials
- **Common Causes**:
  - Session left idle past the token lifetime
  - `payment_secret` no longer valid for the payment
- **Developer Action**:
  - The SDK re-authenticates once automatically on a 401 and retries; this code is thrown when the retry gets a second 401 after that re-authentication succeeded, on both platforms. A re-authentication the gateway refuses is `AUTH_010`
  - Start a new `PaymentSession` for the payment
- **A charge may exist**: Not from this call: the gateway refused the request with the stale token before handling it. A charge an earlier call made on the payment is unaffected
- **Safe to retry**: Yes, on a new `PaymentSession` for the payment

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
- **A charge may exist**: No: the token exchange failed and nothing was sent to the payment
- **Safe to retry**: Yes, with a pair fetched again from your backend

### AUTH_011: Shareable Key Missing
- **Description**: `clientId` / `shareableKey` are absent from `GopayConfig` but required by the requested operation
- **Common Causes**:
  - SDK initialized without them, then a card was encrypted (`encryptCardData` / `PaymentCardForm` fetch `GET /cards/public-key` every time); no request is sent
  - A charge, which first fetches `GET /cards/browser-data` with the same credentials; the charge is not sent
- **Developer Action**:
  - Supply both in `GopayConfig` — they are safe to embed in the app
- **A charge may exist**: No: the call stops before anything is sent
- **Safe to retry**: Yes, once `clientId` and `shareableKey` are set

### AUTH_012: Payment Session Already Exists
- **Description**: A `PaymentSession` for the given `payment_id` is already registered
- **Common Causes**:
  - `startPaymentSession` called twice for the same payment
- **Developer Action**:
  - Reuse the existing session, or close it before starting another
- **A charge may exist**: No: the second start sent nothing to the payment
- **Safe to retry**: Not needed: use the session that exists

### AUTH_013: Payment Session Closed
- **Description**: An operation was invoked on a `PaymentSession` that has been closed
- **Common Causes**:
  - Session used after `close()` or `closeAllPaymentSessions()`; every method of the session that sends a request throws it before anything goes out, and a token that arrives after the close is not kept. `handle3dsVerification` alone still runs, since it sends nothing on the session
- **Developer Action**:
  - Start a new session for the payment
- **A charge may exist**: Not from this call: nothing goes out on a closed session. A charge made before `close()` may exist; read it through your backend or a new session's `getChargeState()`
- **Safe to retry**: Yes, on a new session, after reading the charge state
- **Note**: The same on iOS

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
- **A charge may exist**: Yes, when the call was a charge: the request may have reached the gateway and only the answer was lost
- **Safe to retry**: Read the charge state first when the call was a charge; any other call can be retried as it is

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
- **A charge may exist**: For a charge: no, the gateway refused the request; since 2.7 a 404 is `PAYMENT_012` and a 409 is `PAYMENT_013`. No other call makes a charge
- **Safe to retry**: After fixing what `httpContext.gatewayError` names

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
- **A charge may exist**: Yes, when the call was a charge: a 5xx can come after the gateway has created it
- **Safe to retry**: Read the charge state first when the call was a charge; any other call can be retried with backoff

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
- **A charge may exist**: Possibly, when the call was a charge: read the charge state first. The HTTP client retries a request once on a pooled connection that turned out dead, and a failure of that retry ends here although the first attempt may have reached the gateway. On iOS, whose client does not retry, this stays "No"
- **Safe to retry**: Once the device is online; when the call was a charge, after reading the charge state

### NETWORK_005: SSL/TLS Error
- **Description**: The gateway's certificate failed validation, or it does not match the gateway's host name; the original exception is the `cause`. A TLS connection that is reset or dropped is `NETWORK_007`
- **Common Causes**:
  - Certificate issues
  - A proxy or captive portal presenting its own certificate
- **Developer Action**:
  - Check certificate validity
  - Update TLS configuration
  - Verify network settings
- **A charge may exist**: Possibly, when the call was a charge: read the charge state first. As with `NETWORK_004`, a failed retry on a fresh connection can follow a first attempt that reached the gateway. On iOS this stays "No"
- **Safe to retry**: Once the certificate problem is gone, behind a captive portal once the user is through it; when the call was a charge, after reading the charge state

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
- **A charge may exist**: Yes, when the call was a charge: the connection may have dropped after the request left the device
- **Safe to retry**: Read the charge state first when the call was a charge; any other call can be retried
- **Note**: Any transport failure that is not a timeout, a missing connection or a certificate failure, a TLS connection reset or dropped midway included. The `IOException` is the exception's `cause`. No call lets a transport failure through bare.

## Configuration Errors (CONFIG_XXX)

### CONFIG_001: SDK Not Initialized
- **Description**: SDK has not been initialized before use
- **Common Causes**:
  - `GopaySDK.getInstance()`, and so `startPaymentSession`, `encryptCardData` or `submitCardForm`, called on app start before `GopaySDK.initialize()`
  - Initialization called after SDK usage
- **Developer Action**:
  - Call `GopaySDK.initialize(config)` before using SDK
  - Ensure initialization happens in Application.onCreate()
- **A charge may exist**: No
- **Safe to retry**: Yes, after `initialize`
- **Note**: never reported through `errorCallback`, see the rule at the top. The iOS SDK throws the same code from the same three calls since 2.2

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
- **A charge may exist**: No: nothing is sent
- **Safe to retry**: Yes, with a usable URL
- **Note**: thrown by `Environment.DEVELOPMENT` for an empty URL or one without an `http(s)` scheme, and by `GopaySDK.initialize` for a URL the HTTP client refuses, say one with a space in the host, with that refusal as the `cause`; nothing is sent either way, and a failed `initialize` leaves the SDK as an earlier one set it up. From `Environment.DEVELOPMENT(url)` the code is thrown by the constructor, before `initialize`, and is not reported; from `initialize` it is, to that config's own `errorCallback`. `SANDBOX` and `PRODUCTION` carry their own hosts and cannot hit this. The iOS SDK throws it when the first request is built instead

## Payment Errors (PAYMENT_XXX)

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
- **A charge may exist**: No: the wallet failed before the charge was sent
- **Safe to retry**: Yes: the user can try the wallet again or pay another way
- **Note**: The SDK raises it when the wallet sheet fails (Google Pay, and Apple Pay on iOS): an error from the wallet, no payment data, a token the SDK cannot read (`GooglePayHelper.parseGooglePayToken` throws it, with the parse failure as the `cause`), or the SDK's own Google Pay activity missing from the merged manifest. The wallet's own error is the `cause`. Any error Google Pay returns from the sheet lands here, whatever its cause, a setup problem of the app included, except Google Play services missing, disabled or outdated, which is `PAYMENT_004`. The iOS SDK reports a wallet the app is not set up for as `PAYMENT_004` instead.

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
- **A charge may exist**: No
- **Safe to retry**: Not on this device: hide the wallet button and offer another method
- **Note**: The SDK raises it when the wallet cannot show its sheet on the device, e.g. Google Play services are missing, disabled or outdated (Apple Pay not set up on iOS). Hide the wallet button. On iOS it also covers an app missing the Apple Pay capability or merchant ID; on Android anything Google Pay returns as an error, other than Google Play services missing, disabled or outdated, is `PAYMENT_003`.

### PAYMENT_006: Charge Failed
- **Description**: The charge ended `FAILED`. Thrown by `awaitChargeState()` only, which polls the charge for you; the manual path reads the same state from `getChargeState()` without an error
- **Common Causes**:
  - The issuer declined the card
  - 3DS authentication failed or was refused
  - Fraud prevention
- **Developer Action**:
  - Read the charge on `GopaySDKException.chargeState`, set on `PAYMENT_006` and `PAYMENT_007`; its `failReason`, when the gateway gives one, is also in the message
  - Inform the user and offer another payment method
  - Create a new payment to try again: a payment is charged once, so a retry means a new `payment_id`
- **A charge may exist**: Yes: the charge exists and is `FAILED`
- **Safe to retry**: Not on this payment: create a new payment to try again
- **Note**: thrown by both SDKs since 2.1; the iOS SDK exposes the same `chargeState`

### PAYMENT_007: Charge Timeout
- **Description**: The charge stayed undecided, `REQUESTED` / `PROCESSING` / `UNKNOWN` or `ACTION_REQUIRED` without a new address (no `redirect_url`, an empty one, or one already reported), for the whole initial timeout of `awaitChargeState()` (`initialTimeoutMs`, 30 s by default), counted from the call; the state the deadline read returned is on `GopaySDKException.chargeState`. A 3DS challenge has no time limit: in `ThreeDSMode.AUTOMATIC` the timeout starts over once the challenge returns, in `ThreeDSMode.MANUAL` the first reported address cancels it for good
- **Common Causes**:
  - A slow issuer or acquirer
  - Gateway load
- **Developer Action**:
  - Not a failed payment: the gateway may still decide it. Do not offer a new payment right away
  - Read `getChargeState()` again, or call `awaitChargeState()` again, or wait for the notification GoPay sends your backend (`notification_url`), which the GoPay guide treats as the authoritative signal
- **A charge may exist**: Yes: the charge exists and is still undecided
- **Safe to retry**: Do not charge again: keep reading the state, or wait for the notification to your backend
- **Note**: thrown by both SDKs since 2.1, and not reported through `errorCallback`, like `PAYMENT_011`: nothing failed. The read on the deadline decides: a charge still undecided there is this code, one that moved is handled as that state

### PAYMENT_008: Verification Already In Progress
- **Description**: A 3DS verification is already running; only one can run at a time
- **Common Causes**:
  - `handle3dsVerification` invoked while a verification sheet is open
- **Developer Action**:
  - Await the in-flight verification before starting another; to drop it, cancel the coroutine awaiting it, which closes the challenge
- **A charge may exist**: Not from this call, refused before anything was sent; the verification already running belongs to a charge that exists
- **Safe to retry**: Yes, after the running verification ends
- **Note**: A cancelled coroutine ends the running verification with its `CancellationException` and frees the slot, so this code does not outlive it.

### PAYMENT_009: Wallet Sheet Already In Progress
- **Description**: A Google Pay sheet is already running; only one can run at a time (the iOS SDK uses this code for Apple Pay). The SDK raises it for nothing else: a closed sheet is `PAYMENT_011`, other wallet failures are `PAYMENT_003` or `PAYMENT_004`
- **Common Causes**:
  - `chargeWithGooglePay` invoked twice, e.g. from a double tap
- **Developer Action**:
  - Disable the pay button while a charge is in flight
- **A charge may exist**: Not from this call, refused before the sheet; the sheet already up may still end in a charge
- **Safe to retry**: Yes, after the running call ends

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
- **A charge may exist**: Yes: the challenge comes after the charge was sent, so the charge exists, unverified
- **Safe to retry**: Read the charge state first; charge again for a fresh redirect URL only when it did not go through

### PAYMENT_011: Payment Cancelled
- **Description**: The payment window closed before it finished: the Google Pay sheet or the 3DS screen was closed, or the 3DS page failed after it had started rendering
- **Common Causes**:
  - The Google Pay sheet was closed without paying
  - The 3DS verification screen was closed, by the user or by the app's task being swiped away
  - The 3DS page failed after it had started rendering, when the user may already have answered
- **Developer Action**:
  - Not a failed payment, and not necessarily one that did not happen: read `getChargeState()` before offering the payment again. A 3DS challenge may have been answered
  - When there is no charge (`PAYMENT_012`) or it did not go through, let the user pick a method again
- **A charge may exist**: From the 3DS screen, yes: the charge exists and may have been authorized. From the wallet sheet, no
- **Safe to retry**: Read the charge state first; with no charge, let the user pick a method again
- **Note**: not reported through `errorCallback`. Cancelling the coroutine that awaits the call is not this code: it stays a `CancellationException`, closes the 3DS screen and frees `PAYMENT_008`. With a Google Pay sheet up the call ends at once with the `CancellationException`; the sheet stays until the user closes it, what they do in it is discarded and nothing is charged. The iOS SDK reports the same code for Apple Pay and its 3DS screen

### PAYMENT_012: Payment Not Found
- **Description**: The gateway answered HTTP 404 to a call on the payment: a charge (`POST /payments/{id}/charge`), `getChargeState()` or a read of `awaitChargeState()` (`GET /payments/{id}/charge`), `getStatus()` or `getPaymentOutcome()` (`GET /payments/{id}`). The payment or its charge does not exist, or the session's token has no access to the payment
- **Common Causes**:
  - No charge has been made on the payment yet, e.g. the Google Pay sheet closed without paying, or a charge call failed before the charge went out
  - A `payment_id` the session's token has no access to
- **Developer Action**:
  - From `getChargeState()` after a charge call that ended without an HTTP status: no charge exists, so charge again
  - From a charge or `getStatus()`: check the `payment_id` and the environment the app uses
  - `httpContext.statusCode` is 404 and `httpContext.gatewayError` carries the gateway's error body, as on `NETWORK_002`
- **A charge may exist**: No: the gateway found no charge, or no payment, for the call
- **Safe to retry**: After a 404 from `getChargeState()`, yes: charge, while the payment is not `CANCELED` or `TIMEOUTED`. After any other call, no: this session has no such payment
- **Note**: a contract of the SDK over the gateway's documented 404, the same on both platforms since 2.7; until then the same answer was `NETWORK_002` with HTTP 404. A 404 from any other call, such as the token, the public key, the browser data or the QR and wallet info, stays `NETWORK_002`

### PAYMENT_013: Charge Already Exists
- **Description**: The gateway answered HTTP 409 to a charge (`POST /payments/{id}/charge`): the payment already has a charge, and a payment is charged once
- **Common Causes**:
  - A double tap on the pay button sent a second charge
  - A charge sent again after a code without an HTTP status, when the first one had reached the gateway after all
- **Developer Action**:
  - Do not charge again: read the charge that exists with `getChargeState()`, `awaitChargeState()` or `getPaymentOutcome()` and follow its state
  - Disable the pay button while a charge is in flight
- **A charge may exist**: Yes: the first charge exists; this one was not made
- **Safe to retry**: No: read the charge state instead
- **Note**: a contract of the SDK over the gateway's documented 409, the same on both platforms since 2.7; until then the same answer was `NETWORK_002` with HTTP 409. `httpContext.statusCode` is 409 and `httpContext.gatewayError` carries the gateway's error body

## Validation Errors (VALIDATION_XXX)

### VALIDATION_007: Invalid Input
- **Description**: Invalid input data was provided to an SDK call
- **Common Causes**:
  - Empty `paymentId` / `paymentSecret` passed to `startPaymentSession`
  - `encryptCardData` called with a card that fails validation: the PAN is not 13 to 19 digits, the month is not `01` to `12`, the year is not 2 to 4 digits, or the CVV is not 3 or 4 digits. The same rules apply on iOS
  - `submitCardForm` called while no `PaymentCardForm` holds a card (after a successful submit, or before one was typed), or while the held card is invalid
  - `awaitChargeState()` called with an `intervalMs` or `initialTimeoutMs` that is not positive, or in `ThreeDSMode.AUTOMATIC` without an `activity`; nothing is sent
- **Developer Action**:
  - Validate before calling, and let the form show its own validation state (`validation = LIVE` or `ON_SUBMIT`)
- **A charge may exist**: No: nothing is sent
- **Safe to retry**: Yes, with valid input

## Internal Errors (INTERNAL_XXX)

### INTERNAL_001: Unexpected Error
- **Description**: An unexpected internal error occurred
- **Common Causes**:
  - The card encryption itself failed in `encryptCardData` or `submitCardForm`: the merchant's public key could not be read, or a step of building the JWE failed. The message names the step and `cause` carries the platform's exception. The key fetch failing keeps its own `NETWORK_*` or `AUTH_011` code; this one is only the encryption. The iOS SDK throws the same code for the same failure
  - The token request failed with something that is neither an HTTP answer nor a transport failure
  - The 3DS screen could not be started
- **Developer Action**:
  - Enable `debug` and report the message
  - Check for SDK updates
- **A charge may exist**: From the encryption or the token request, no: both fail before a charge is sent. From the 3DS screen failing to start, the charge exists already
- **Safe to retry**: Yes for the encryption and the token request; for the 3DS screen, read the charge state first

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
- **A charge may exist**: Yes, when the call was a charge: the gateway answered and only its answer could not be read
- **Safe to retry**: Read the charge state first when the call was a charge; any other call can be retried
- **Note**: a new value in one of the response enums is not a decoding error: it decodes as `UNKNOWN` and the original value goes to the debug log

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
        if (e.errorCode == GopayErrorCodes.PAYMENT_NOT_FOUND) offerPaymentAgain() // no charge went out
        else askBackendForState() // still unknown: the charge may be through
    }
}
```

The codes worth a branch of their own are the ones in the README's
[Error handling](../../README.md#error-handling).

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

To receive every exception the SDK throws, once each, except `PAYMENT_011` and `PAYMENT_007`,
which are not errors, register an analytics callback via `GopayConfig.errorCallback`; the rule is
at the top of this document.
 