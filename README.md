# GoPay Android SDK

Android SDK for charging GoPay payments from a mobile app. Maps to the Payments 4.0 API
(`Payments.yaml`).

## Auth model — what the SDK does and doesn't do

The SDK runs on the **device**, so it never holds merchant credentials. There are two auth
schemes the SDK is allowed to use:

- **`payment_credentials`** — issued per payment. Your merchant backend creates the payment via
  `POST /eshops/{goid}/payments` (with merchant credentials) and returns `payment_id` +
  `payment_secret` to the app. The SDK exchanges those at `POST /oauth2/token` with
  `grant_type=payment_credentials` to obtain a payment-scoped JWT. Every charge/status/Google
  Pay/QR/3DS call goes through this JWT.
- **`shareable_key`** — long-lived `client_id:shareable_key` basic auth for the two public
  resource endpoints: `GET /cards/public-key` (used for JWE card encryption) and
  `GET /cards/card-form-url`. Safe to embed in the app — it can't charge anything on its own.

Card tokenization (`POST /cards/tokens`) requires merchant credentials and **runs on your
backend**, not on the device. The SDK encrypts card data into a JWE and gives it to you to
forward; the backend submits the JWE and gets back the card token.

```
┌────────┐  payment_id + payment_secret  ┌────────┐  payment_credentials JWT  ┌────────┐
│ Mobile │ ◄──────────────────────────── │ Merch. │ ◄──────────────────────── │ GoPay  │
│  app   │                               │ backend│                           │  API   │
└────────┘ ────────────────────────────► └────────┘ ────────────────────────► └────────┘
   │      charge / status / GP / 3DS via payment_credentials JWT (direct)         ▲
   └──────────────────────────────────────────────────────────────────────────────┘
```

## Features

- Per-payment `PaymentSession` with eager auth, single-flight Mutex re-auth on 401, and one
  bounded retry. Multiple sessions can run concurrently — credentials never leak between them.
- In-memory only — no `SharedPreferences`, no encrypted token storage, no `ContentProvider`
  auto-init. `payment_secret` and JWT live for the lifetime of the session and are wiped on
  `close()`.
- Managed Google Pay and 3DS flows directly on the session.
- JWE card encryption using the merchant public key (cached in-memory).
- Compose `PaymentCardForm` composable that emits a JWE.

## Quick start

### 1. Dependency

```kotlin
dependencies {
    implementation(project(":sdk"))
}
```

### 2. Initialize

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GopaySDK.initialize(
            GopayConfig(
                environment = Environment.SANDBOX,        // or PRODUCTION / DEVELOPMENT
                clientId = BuildConfig.GOPAY_CLIENT_ID,   // for shareable-key endpoints
                shareableKey = BuildConfig.GOPAY_SHAREABLE_KEY,
                debug = BuildConfig.DEBUG
            )
        )
    }
}
```

`clientId` and `shareableKey` are optional — only required if you call
`getPublicEncryptionKey()`, `encryptCardData()`, or use `PaymentCardForm`.

### 3. Start a session and charge

```kotlin
// payment_id + payment_secret come from YOUR backend after it creates the payment.
val session = GopaySDK.getInstance().startPaymentSession(
    paymentId = paymentId,
    paymentSecret = paymentSecret
    // scope defaults to PaymentSession.DEFAULT_SCOPE ("payment:charge payment:read"),
    // override only if you need a wider scope.
)

try {
    val charge = session.charge(
        ChargePaymentRequest(
            paymentInstrument = PaymentInstrumentInput.cardToken(cardToken),
            browserData = BrowserData(...)
        )
    )
    charge.action?.redirectUrl?.let { session.handle3dsVerification(activity, it) }
    val finalState = session.getChargeState()
} finally {
    session.close()  // wipes the secret + JWT from memory
}
```

## `GopaySDK` API

| Method | Purpose |
| --- | --- |
| `initialize(GopayConfig)` | Sets up the SDK singleton. Call once on app start. |
| `getInstance()` | Returns the singleton; throws if not initialized. |
| `isInitialized()` | Quick check before calling `getInstance()`. |
| `isDebugEnabled()` | Reflects `GopayConfig.debug`. |
| `startPaymentSession(paymentId, paymentSecret, scope?)` | Eagerly authenticates and returns a [`PaymentSession`](sdk/src/main/java/cz/gopay/sdk/session/PaymentSession.kt). Throws `AUTH_PAYMENT_SESSION_ALREADY_EXISTS` if one is already live for the same `paymentId`. |
| `getPaymentSession(paymentId)` | Looks up a live session by id; `null` if absent. |
| `closeAllPaymentSessions()` | Closes every session — wipes secrets and tokens. |
| `getPublicEncryptionKey(forceRefresh = false)` | Fetches the merchant JWK via shareable-key auth. In-memory cache. |
| `encryptCardData(CardData)` | Validates the card, fetches the JWK if needed, returns a JWE string. |
| `isGooglePayAvailable(activity, info)` | Checks Google Pay readiness on the device using the methods from a `GooglePayInfoResponse`. |

## `PaymentSession` API

A `PaymentSession` is the per-payment auth context. Construct it via `startPaymentSession`;
**never** instantiate directly. All methods are `suspend` and propagate `GopaySDKException` on
auth or HTTP errors.

| Method | Maps to |
| --- | --- |
| `getStatus()` | `GET /payments/{payment_id}` |
| `charge(ChargePaymentRequest)` | `POST /payments/{payment_id}/charge` |
| `getChargeState()` | `GET /payments/{payment_id}/charge` |
| `getQrPaymentInfo(format?)` | `GET /payments/{payment_id}/qr-payment/info` |
| `getGooglePayInfo()` | `GET /payments/{payment_id}/google-pay/info` |
| `chargeWithGooglePay(activity)` | Managed flow: GP info → Google Pay sheet → `charge(...)` |
| `handle3dsVerification(activity, redirectUrl)` | Managed WebView; suspends until done or cancelled |
| `close()` | Wipes `payment_secret`, JWT; unregisters from the SDK |

Concurrent calls on the same session re-use the cached JWT. On a 401 the session invalidates
the token and re-acquires once from the cached `payment_secret`; a second 401 surfaces as
`AUTH_PAYMENT_TOKEN_EXPIRED` so the caller can fetch fresh credentials from its backend.

### Concurrent payments

Multiple `PaymentSession` instances may live in parallel. They have isolated tokens and
secrets — keyed in the SDK by `paymentId`. Google Pay and 3DS use a process-wide bridge so
only one sheet/WebView can be visible at a time; collisions surface as
`PAYMENT_GOOGLE_PAY_IN_PROGRESS` / `PAYMENT_VERIFICATION_IN_PROGRESS`.

## Card collection (JWE flow)

The SDK collects card data and encrypts it into a JWE. The merchant backend then submits the
JWE to `POST /cards/tokens` and receives a card token, which the app can pass to
`session.charge(...)`.

### Programmatic

```kotlin
val jwe: String = GopaySDK.getInstance().encryptCardData(
    CardData(cardPan = "4444…", expMonth = "06", expYear = "27", cvv = "123")
)
// POST `jwe` to your backend; backend returns a card_token.
```

### `PaymentCardForm` composable

```kotlin
import cz.gopay.sdk.ui.CardEncryptionResult
import cz.gopay.sdk.ui.PaymentCardForm

@Composable
fun MyCardSheet(onJwe: (String) -> Unit) {
    PaymentCardForm(
        onEncryptionComplete = { result ->
            when (result) {
                is CardEncryptionResult.Success -> onJwe(result.jwe)
                is CardEncryptionResult.Error -> showError(result.message)
            }
        }
    )
}
```

The form validates input, sets `FLAG_SECURE` on non-debug builds, and never exposes raw card
data to the host. Theming is controlled by `PaymentCardFormTheme`; the form callback contract
also offers `onFormReady { submitFn -> … }` for external submit triggers and
`onValidationError { … }` for inline error display.

## Google Pay flow

```kotlin
val info = session.getGooglePayInfo()
if (GopaySDK.getInstance().isGooglePayAvailable(activity, info)) {
    val charge = session.chargeWithGooglePay(activity)
    charge.action?.redirectUrl?.let { session.handle3dsVerification(activity, it) }
    val finalState = session.getChargeState()
}
```

`chargeWithGooglePay` launches the Google Pay sheet inside an SDK-managed activity, parses the
returned token, and posts the charge in one step. User dismissal surfaces as
`kotlinx.coroutines.CancellationException`.

Add the Google Pay dependency to your app:

```kotlin
implementation("com.google.android.gms:play-services-wallet:19.4.0")
```

## Environments

| Environment | Base URL |
| --- | --- |
| `Environment.DEVELOPMENT.create(url)` | Custom — must start with `http://` or `https://` |
| `Environment.SANDBOX` | `https://api.sandbox.gopay.com/v1/` |
| `Environment.PRODUCTION` | `https://api.gopay.com/v1/` |

## Error handling

Every API call may throw `GopaySDKException` with a structured error code (`AUTH_*`,
`NETWORK_*`, `PAYMENT_*`, `CONFIG_*`, …). Codes most likely to surface in the per-payment flow:

| Code | When |
| --- | --- |
| `AUTH_PAYMENT_CREDENTIALS_INVALID` | `payment_id`/`payment_secret` rejected by `/oauth2/token` |
| `AUTH_PAYMENT_TOKEN_EXPIRED` | JWT still rejected after a single re-auth retry — fetch fresh creds from backend |
| `AUTH_PAYMENT_SESSION_ALREADY_EXISTS` | `startPaymentSession` called for a `paymentId` that already has a live session |
| `AUTH_PAYMENT_SESSION_CLOSED` | API call on a session after `close()` |
| `AUTH_SHAREABLE_KEY_MISSING` | `encryptCardData` / `getPublicEncryptionKey` called without `clientId`+`shareableKey` |
| `PAYMENT_GOOGLE_PAY_IN_PROGRESS` | A second Google Pay sheet attempted while one is visible |
| `PAYMENT_VERIFICATION_IN_PROGRESS` | A second 3DS WebView attempted while one is open |

```kotlin
try {
    session.charge(request)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.AUTH_PAYMENT_TOKEN_EXPIRED -> refreshCredsFromBackend()
        GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS -> showAlreadyInProgress()
        else -> showGenericError(e)
    }
}
```

Plug an analytics callback via `GopayConfig.errorCallback` to receive every exception the SDK
throws.

## Security notes

- Neither the `payment_secret` nor the JWT is ever written to disk. Both live in memory inside
  `PaymentSession` and are wiped on `close()`. The SDK no longer reads or writes
  `SharedPreferences`.
- The merchant public key is cached only in memory; clears on process death.
- `PaymentCardForm` enables `FLAG_SECURE` on the host window in non-debug builds to block
  screenshots of card data.
- Network: HTTPS only. Optional certificate pinning is forwarded from `GopayConfig`.

## Testing

```bash
./gradlew :sdk:testDebugUnitTest
```

## License

Not yet determined. Not in production.
