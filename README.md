# GoPay Android SDK

Android SDK for charging GoPay payments from a mobile app. Maps to the Payments 4.0 API
(`Payments.yaml`).

## Overview

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

- Per-payment `PaymentSession` with eager auth, single-flight Mutex re-auth on 401, and one
  bounded retry. Multiple sessions can run concurrently — credentials never leak between them.
- In-memory only — no `SharedPreferences`, no encrypted token storage, no `ContentProvider`
  auto-init. `payment_secret` and JWT live for the lifetime of the session and are wiped on
  `close()`.
- Managed Google Pay and 3DS flows directly on the session.
- JWE card encryption using the merchant public key (cached in-memory).
- Compose `PaymentCardForm` composable that emits a JWE.
- Localized form labels in 20 languages (device language, falling back to Czech), with per-form
  overrides and custom locale registration.

## Requirements

- JDK 17+ (Gradle 8.7 / AGP 8.6)
- Android SDK Platform 35 (`compileSdk = 35`), `minSdk = 24`
- Kotlin, Gradle Kotlin DSL

## Installation

Inside this repo the demo app consumes the SDK as a Gradle module:

```kotlin
dependencies {
    implementation(project(":sdk"))
}
```

To consume it from another project, publish it to a Maven repo first:

```bash
./gradlew :sdk:publishToLocalRepo   # -> sdk/build/repo
./gradlew :sdk:publishToMavenLocal  # -> ~/.m2/repository
```

then depend on `cz.gopay:sdk:<version>`. Group/artifact/version come from the `sdk.groupId`,
`sdk.artifactId`, and `sdk.version` Gradle properties (defaults `cz.gopay` / `sdk` / `1.0.0`);
`./gradlew :sdk:showPublishingInfo` prints the resolved values.

> **Publishing currently fails on a fresh clone.** `sdk/gradle.properties` is committed with
> `signing.secretKeyRingFile` pointing at one developer's home directory, so the signing plugin
> cannot build a signatory and the task dies before publishing:
>
> ```
> Could not evaluate spec for 'Signing is required, or signatory is set'.
> Unable to retrieve secret key from key ring file '/Users/<someone>/.gnupg/secring.gpg'
> ```
>
> Commenting out the three `signing.*` lines in `sdk/gradle.properties` makes both tasks succeed
> and produce a working (unsigned) AAR. Overriding them with `-P` does **not** help — an empty
> value still creates a signatory. This needs fixing properly: the keyring path and password
> don't belong in a committed file.

> **Pass the version explicitly when you publish.** Nothing bumps `sdk.version` in
> `sdk/gradle.properties`, so the Maven coordinates and `BuildConfig.VERSION_NAME` do not match
> the released tag unless you pass `-Psdk.version=<x>`.

## Quick start

### 1. Local setup

`local.properties` is gitignored, so a fresh clone has no Android SDK location and every Gradle
command fails with `SDK location not found`. Android Studio writes it on first open; from the CLI:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

You also need JDK 17+ (Gradle 8.7 / AGP 8.6; JDK 21 verified) and Android SDK Platform 35
(`compileSdk = 35`). To run the bundled demo app see [`app/README.md`](app/README.md).

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
    // chargeWithCardToken derives the spec-required BrowserData from the activity for you.
    val charge = session.chargeWithCardToken(activity, cardToken)
    charge.action?.redirectUrl?.let { session.handle3dsVerification(activity, it) }
    val finalState = session.getChargeState()
} finally {
    session.close()  // wipes the secret + JWT from memory
}
```

Prefer `chargeWithCardToken` / `chargeWithEncryptedCard` / `chargeWithGooglePay` over the raw
`charge(ChargePaymentRequest)`: the spec requires `browser_data` on every card charge, and the
wrappers fill it in from the activity (locale, screen metrics, timezone, user agent). Drop to
`charge(...)` only when you have collected more accurate browser data yourself.

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
| `charge(ChargePaymentRequest)` | `POST /payments/{payment_id}/charge` — low level; you supply `BrowserData` |
| `chargeWithCardToken(activity, cardToken, browserData?, challengePreference?, returnUrl?)` | `charge(...)` with a card token + device-derived `BrowserData` |
| `chargeWithEncryptedCard(activity, payload, browserData?, challengePreference?, returnUrl?)` | `charge(...)` with a JWE + device-derived `BrowserData` |
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

## Card form → JWE

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

### Charge directly with the encrypted card

If you don't need a reusable card token, charge the JWE directly with the `ENCRYPTED_CARD`
input — this skips the server-side `POST /cards/tokens` round-trip entirely. The JWE is sent as
the charge's `payload`; nothing else changes about the charge or 3DS flow.

```kotlin
val jwe = GopaySDK.getInstance().encryptCardData(
    CardData(cardPan = "4444…", expMonth = "06", expYear = "27", cvv = "123")
)
val charge = session.chargeWithEncryptedCard(
    activity = activity,
    payload = jwe,
    challengePreference = ChallengePreference.AUTO
)
charge.action?.redirectUrl?.let { session.handle3dsVerification(activity, it) }
val finalState = session.getChargeState()
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

### Localizing the form

Form labels and placeholders are localized. By default the form uses the **device language and
falls back to Czech (`cs`)** when the language has no translation. 20 languages ship built in
(`bg cs de en es et fr hr hu it lt lv nl pl pt ro ru sk sl uk`).

Set a preferred locale globally on the config, or per form:

```kotlin
// Global default for every form (null = follow the device language)
GopaySDK.initialize(GopayConfig(environment = …, locale = "de"))

// Per-form override (wins over the global default)
PaymentCardForm(onEncryptionComplete = { … }, locale = "cs")
```

Add your own translation with the same structure and select it by code:

```kotlin
import cz.gopay.sdk.locales.GopayLocales
import cz.gopay.sdk.locales.GopayLocaleStrings

val brandEnglish = GopayLocales.EN.copy(panLabel = "Your card number")

GopaySDK.initialize(
    GopayConfig(environment = …, customLocales = mapOf("en" to brandEnglish))
)
// or at runtime: GopayLocales.register("en", brandEnglish)
PaymentCardForm(onEncryptionComplete = { … }, locale = "en")
```

Validation error strings are localized too, but the form keeps error *display* host-driven: read
them from the resolved locale in your `onValidationError` handler:

```kotlin
val strings = GopaySDK.getInstance().currentLocaleStrings(locale = "cs")
// strings.panErrorPattern, strings.expErrorPattern, strings.cvvErrorPattern, …
```

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

| Environment | Base URL | Status |
| --- | --- | --- |
| `Environment.DEVELOPMENT.create(url)` | Custom — must start with `http://` or `https://` | Use this |
| `Environment.SANDBOX` | `https://api.sandbox.gopay.com/v1/` | Not reachable on the 4.0 API |
| `Environment.PRODUCTION` | `https://api.gopay.com/v1/` | Not reachable on the 4.0 API |

Point `DEVELOPMENT` at the gateway URL you were given — see
[`ENVIRONMENT_USAGE.md`](ENVIRONMENT_USAGE.md).

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

Every code, with its causes and what to do about it, is in
[`sdk/docs/ERROR_CODES.md`](sdk/docs/ERROR_CODES.md). The codes are shared with the iOS SDK, which
throws a subset of the same catalog.

## Example app

A demo app that fakes the merchant backend and exercises every session operation lives in
[`app/`](app/) — see [`app/README.md`](app/README.md) for setup and a walkthrough.

## Security notes

- Neither the `payment_secret` nor the JWT is ever written to disk. Both live in memory inside
  `PaymentSession` and are wiped on `close()`. The SDK no longer reads or writes
  `SharedPreferences`.
- The merchant public key is cached only in memory; clears on process death.
- `PaymentCardForm` enables `FLAG_SECURE` on the host window in non-debug builds to block
  screenshots of card data.
- Network: HTTPS only.
- Certificate pinning is plumbed all the way through `NetworkManager`/`NetworkModule`, but is
  **not reachable from the public API today**: the `CertificatePinner` is a parameter of
  `GopaySDK`'s private constructor and `GopaySDK.initialize(config)` never passes one.
  `GopayConfig` has no pinning field. Exposing it needs a small API change.

## Testing

```bash
./gradlew :sdk:testDebugUnitTest
```

Requires `local.properties` (see [Quick start](#1-local-setup)). `:sdk:jacocoTestReport` is wired
as a `finalizedBy` of the test task, so a coverage report is generated automatically afterwards:
`sdk/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` plus a browsable
`.../jacocoTestReport/html/index.html`.

To check the whole project compiles, including the demo app:

```bash
./gradlew :app:assembleDebug
```

## Releasing

semantic-release cuts versions on `master` from the commit messages. Publishing the AAR is a
separate, manual step — see [`sdk/docs/PUBLISHING.md`](sdk/docs/PUBLISHING.md).

## License

Not yet determined. Not in production.
