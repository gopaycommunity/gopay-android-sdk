# GoPay Android SDK

[![Maven Central](https://img.shields.io/maven-central/v/cz.gopay/sdk)](https://central.sonatype.com/artifact/cz.gopay/sdk)
[![semantic-release: angular](https://img.shields.io/badge/semantic--release-angular-e10079)](https://github.com/semantic-release/semantic-release)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Android SDK for charging GoPay payments from a mobile app, for the GoPay Payments 4.0 API:
[API documentation](https://api-docs.gopay.com/en/api),
[OpenAPI specification](https://api-docs.gopay.com/spec/en/payments.yaml),
[`cz.gopay:sdk` on Maven Central](https://central.sonatype.com/artifact/cz.gopay/sdk).

## Overview

The SDK runs on the **device**, so it never holds merchant credentials. There are two auth
schemes the SDK is allowed to use:

- **`payment_credentials`** — issued per payment. Your merchant backend creates the payment via
  `POST /eshops/{goid}/payments` (with merchant credentials) and returns `payment_id` +
  `payment_secret` to the app. The SDK exchanges those at `POST /oauth2/token` with
  `grant_type=payment_credentials` to obtain a payment-scoped JWT. Every charge/status/Google
  Pay/QR/3DS call goes through this JWT.
- **`shareable_key`** — long-lived `client_id:shareable_key` basic auth for the public
  resource endpoints: `GET /cards/public-key` (used for JWE card encryption),
  `GET /cards/card-form-url` and `GET /cards/browser-data` (the `ip` every charge needs). Safe to
  embed in the app — it can't charge anything on its own.

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
- JWE card encryption using the merchant public key, fetched for every encryption and stamped
  with the gateway's time.
- Compose `PaymentCardForm` composable that holds the card under a `formId`, which
  `GopaySDK.submitCardForm(formId)` turns into a JWE, with inline validation in three modes.
- Localized form labels in 20 languages (device language, falling back to Czech), with per-form
  overrides and custom locale registration.
- **One API with the iOS SDK.** The card charges, the wallet and the card form have the same
  names and shapes on both platforms since 2.2 (see [Shared API](#shared-api-with-ios)).

## Requirements

Your app:

- `minSdk` 24 or higher.
- `compileSdk` 35 or higher: the AndroidX libraries the SDK builds on require it.
- Any `targetSdk`. From 35 on, Android 15 and later draw every activity edge to edge, the SDK's
  3DS screen included, which keeps itself clear of the system bars and the keyboard. Checked with
  an app targeting 35 on Android 16.
- Kotlin with coroutines, since the session API is `suspend` functions, and Jetpack Compose for
  `PaymentCardForm`.

Building this repository needs JDK 17+ (Gradle 8.7 / AGP 8.6; JDK 21 verified) and Android SDK
Platform 35. `local.properties` is gitignored, so a fresh clone has no Android SDK location and
every Gradle command fails with `SDK location not found`. Android Studio writes it on first open;
from the CLI:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

To run the bundled demo app see [`app/README.md`](app/README.md).

## Installation

The SDK is published to Maven Central as `cz.gopay:sdk`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("cz.gopay:sdk:<version>")
}
```

Use the latest version listed on
[Maven Central](https://central.sonatype.com/artifact/cz.gopay/sdk). Of the 1.x line only 1.0.0
was ever published there; later 1.x versions exist only as tags in this repository.

To publish a build of your own to a local Maven repository, see
[`sdk/docs/PUBLISHING.md`](sdk/docs/PUBLISHING.md#publishing-locally).

### Demo app

By default the demo app in [`app/`](app/) builds against the SDK module next to it
(`implementation(project(":sdk"))`), so it always runs the SDK code in the same checkout. Development
and CI build it that way. To build it against the released `cz.gopay:sdk` from Maven Central
instead, the way an integration following this section does, pass `-Pgopay.sdk.source=released`
or put `gopay.sdk.source=released` in `local.properties`:

```bash
./gradlew :app:installDebug -Pgopay.sdk.source=released
```

The version is `sdk.version` from `sdk/gradle.properties`, the version this repo last released.
Between releases the demo may already use API that version lacks, and the `released` build then
fails to compile.

To try an unreleased build the same way, publish it to the local repository `sdk/build/repo` with
`publishToLocalRepo`, not `publishToMavenLocal` (the demo does not read `~/.m2`), under a version
that is not on Maven Central (the remote repositories are searched before the local one), and pass
that version to both commands:

```bash
./gradlew :sdk:publishToLocalRepo -Psdk.version=dev
./gradlew :app:installDebug -Pgopay.sdk.source=released -Psdk.version=dev
```

## Your backend

The SDK charges a payment your server has created and settles nothing on its own. What your server
does, as the GoPay guide describes it
([Security and credentials](https://api-docs.gopay.com/en/docs/01-security.md),
[Payment states and lifecycle](https://api-docs.gopay.com/en/docs/02-payment-lifecycle.md),
[Saved cards](https://api-docs.gopay.com/en/docs/05-saved-cards.md)):

1. **Create the payment** with `POST /eshops/{goid}/payments`, under a merchant access token with
   the `payment:write` scope. The body needs `amount`, `currency`, `order_number`, `customer` and
   `callback`. `amount` is an integer in the currency's minor unit: 100.00 CZK is `10000`.
2. **Set both callback URLs.** `callback.return_url` and `callback.notification_url` are required.
   According to the GoPay guide both must be absolute HTTPS URLs; in production a `return_url`
   that is not HTTPS gets a 403 when the payment is created, the sandbox is more tolerant. The
   3DS screen closes when the challenge comes back to `return_url` (see
   [3DS verification](#3ds-verification)), so the URL does not have to serve anything to the app.
   `notification_url` must be reachable from GoPay's servers.
3. **Give the app `id`, `payment_secret` and `return_url`**, nothing else; the app passes the
   first two to `startPaymentSession` and `return_url` to `awaitChargeState` or
   `handle3dsVerification`. GoPay is removing `return_url` from the charge response, so the app
   should get it only from you.
   According to the GoPay guide, `payment_secret` must not be stored, logged or put in a URL, and
   it is valid for seven days, after which the gateway refuses it with a 403.
4. **Settle the payment on the server.** What the app reports is not proof of payment. GoPay
   sends a `GET` to `notification_url` with the payment `id` on every state change; answer `204`
   and read the payment with `GET /payments/{id}`. The GoPay guide treats this webhook as the
   authoritative signal. A payment nobody charges times out, after one hour by default.
5. **Save a card, if you need to,** by exchanging the JWE from the card form for a permanent token
   with `POST /cards/tokens` (merchant scope `card:write`). Tokenisation is off until GoPay enables
   it for you through `integration@gopay.com`. A JWE is single-use: it pays one charge or makes one
   token, not both. A Google Pay payload cannot be tokenised.

## Quick start

### 1. Keys

`clientId` and `shareableKey` come from GoPay. They are safe to ship in the app, but keep them out
of version control, for example as Gradle properties in `~/.gradle/gradle.properties` or a CI
secret, and hand them to the code through `BuildConfig`:

```kotlin
// app/build.gradle.kts
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "GOPAY_CLIENT_ID",
            "\"${providers.gradleProperty("gopay.clientId").getOrElse("")}\"")
        buildConfigField("String", "GOPAY_SHAREABLE_KEY",
            "\"${providers.gradleProperty("gopay.shareableKey").getOrElse("")}\"")
    }
}
```

### 2. Initialize

```kotlin
import android.app.Application
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.config.Environment
import cz.gopay.sdk.config.GopayConfig

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

`clientId` and `shareableKey` are needed to charge: every charge first fetches the browser data
under the shareable key (see below). They are also what `encryptCardData()` and
`PaymentCardForm` use. Without them a charge fails with
`AUTH_SHAREABLE_KEY_MISSING` before anything is sent.

### 3. Start a session and charge

Every session call is a `suspend` function. Run them in a scope that outlives a rotation, such as a
ViewModel's `viewModelScope`; [Rotation and process death](#rotation-and-process-death) says why.

```kotlin
import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopayErrorCodes
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.session.AwaitChargeOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CheckoutViewModel : ViewModel() {
    // The activity showing the checkout: set it in onStart and back to null in onStop. Each SDK
    // call waits for the one on screen, so the coroutine never holds one a rotation destroyed.
    val screen = MutableStateFlow<Activity?>(null)
    private suspend fun currentScreen(): Activity = screen.filterNotNull().first()

    // paymentId, paymentSecret and returnUrl (its callback.return_url) come from YOUR backend
    // after it creates the payment.
    fun pay(paymentId: String, paymentSecret: String, returnUrl: String, cardToken: String) {
        viewModelScope.launch {
            val session = try {
                GopaySDK.getInstance().startPaymentSession(
                    paymentId = paymentId,
                    paymentSecret = paymentSecret
                    // scope defaults to PaymentSession.DEFAULT_SCOPE ("payment:charge payment:read")
                )
            } catch (e: CancellationException) {
                throw e                // the ViewModel is going away; nothing to show it on
            } catch (e: Exception) {
                showError(e)
                return@launch
            }
            try {
                try {
                    // chargeWithCardToken derives the spec-required BrowserData from the activity.
                    session.chargeWithCardToken(currentScreen(), cardToken)
                    // Polls the charge once a second and runs the 3DS challenge itself, from the
                    // activity given here, until the charge is SUCCEEDED.
                    val charge = session.awaitChargeState(
                        AwaitChargeOptions(activity = currentScreen(), returnUrl = returnUrl)
                    )
                    showResult(charge)
                } catch (e: GopaySDKException) {
                    when (e.errorCode) {
                        // The charge ended FAILED; e.chargeState carries it, with fail_reason.
                        GopayErrorCodes.PAYMENT_DECLINED -> showDeclined(e.chargeState)
                        // Still REQUESTED, PROCESSING or UNKNOWN after 30 s (e.chargeState): not
                        // a failure, ask again or wait for the notification on your backend, and
                        // do not offer a new payment yet.
                        GopayErrorCodes.PAYMENT_TIMEOUT -> askBackendForState()
                        // The 3DS screen closed before it returned, but the charge was sent:
                        // only its state may say what to offer next (see Reading the result).
                        GopayErrorCodes.PAYMENT_CANCELLED -> showStateAfterClosedChallenge(session)
                        else -> showError(e)   // the charge or a read failed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)           // anything else, so the sample never takes the app down
            } finally {
                session.close()  // wipes the secret + JWT from memory
            }
        }
    }

    // The user closed the challenge, so the SDK will not open the same one again: read the
    // state by hand (settle is in Reading the result) and offer a new payment only when the
    // gateway has no charge at all.
    private suspend fun showStateAfterClosedChallenge(session: PaymentSession) {
        try {
            showResult(settle(session))
        } catch (e: GopaySDKException) {
            if (e.getHttpStatusCode() == 404) offerPaymentAgain()   // no charge went out
            else askBackendForState()   // could not be read: the charge may be through
        } catch (e: PaymentStillPending) {
            askBackendForState()        // not decided yet
        }
    }
}
```

Prefer `chargeWithCardToken` / `chargeWithEncryptedCard` / `chargeWithWallet` over the raw
`charge(ChargePaymentRequest)`: the spec requires `browser_data` on every card charge, and the
wrappers fill it in from the activity (locale, screen metrics, timezone, user agent). Drop to
`charge(...)` only when you have collected more accurate browser data yourself. The card helpers
also come without an `activity`, the shape the iOS SDK has: `chargeWithCardToken(cardToken,
browserData, challengePreference)` and `chargeWithEncryptedCard(payload, browserData,
challengePreference)`, for when you pass the browser data yourself. There `browserData` is a
required parameter: deriving the device's browser data needs a `Context`, which iOS does not
need, so on iOS it is optional.

### Browser data / User-Agent

`BrowserData.deviceDefault(activity)` is the public extension those wrappers call. Call it
yourself when you want to see or adjust what goes out:

```kotlin
import cz.gopay.sdk.model.BrowserData
import cz.gopay.sdk.model.deviceDefault

val browserData = BrowserData.deviceDefault(activity)
session.charge(ChargePaymentRequest.cardToken(cardToken, browserData = browserData))
```

It reads the language and screen metrics off the activity, the timezone off the device clock as
minutes west of UTC, and `user_agent` from `WebSettings.getDefaultUserAgent(activity)` — the same
WebView that renders the 3DS challenge, so the value the issuer weighs in the AReq matches the
browser it will actually see. On a device with the WebView package disabled the lookup fails and a
synthesized User-Agent of the same shape stands in, with a warning in Logcat. `color_depth` is
always 24, the value every mobile browser reports. Construct `BrowserData` directly if you have
collected more accurate values. The iOS SDK exposes the same `BrowserData.deviceDefault()`.

The gateway also requires `ip`, and the device cannot know its own public address, so every
`charge(...)` on a session first calls `GET /cards/browser-data` under the shareable key, the same
authorization as the public encryption key, and fills in `ip` and `accept_header` from the
answer; `javascript_enabled` is sent as `true`, because the challenge runs in a WebView with
JavaScript on. The request carries the WebView's User-Agent from `BrowserData`, so the issuer
sees the same value in the AReq and in the challenge. A `BrowserData` you built without `user_agent` gets a synthesized
WebView-shaped one instead, on the request and in the charge, with a warning in Logcat: the
session holds no `Context` to read the WebView's own, so use `deviceDefault(activity)` to send
that one. Only fields that are still `null` are filled: a value you set on
`BrowserData` stays, and when `ip` and `accept_header` are both set the gateway is not
asked. When the fetch fails the charge is not sent; the error carries the underlying code
(an HTTP or transport code as listed under [Error handling](#error-handling),
`AUTH_SHAREABLE_KEY_MISSING` when `clientId` and `shareableKey` are not configured) with the
original exception as its cause. `session.completeBrowserData(browserData)` runs the same step
on its own when you want to log what goes out; pass its result to the charge unchanged.

## `GopaySDK` API

| Method | Purpose |
| --- | --- |
| `initialize(GopayConfig)` | Sets up the SDK singleton. Call once on app start. |
| `getInstance()` | Returns the singleton; throws if not initialized. |
| `isInitialized()` | Quick check before calling `getInstance()`. |
| `isDebugEnabled()` | Reflects `GopayConfig.debug`. |
| `CHARGE_RETURN_URL` | `https://gopay.com/sdk/charge-return`, the return URL the 3DS WebView watches for when `handle3dsVerification` gets no usable `returnUrl`. A payment verified that way has to be created with it as `callback.return_url`; the gateway accepts only an absolute `http(s)` address there. |
| `startPaymentSession(paymentId, paymentSecret, scope?)` | Eagerly authenticates and returns a [`PaymentSession`](sdk/src/main/java/cz/gopay/sdk/session/PaymentSession.kt). Throws `AUTH_PAYMENT_SESSION_ALREADY_EXISTS` if one is already live for the same `paymentId`. |
| `getPaymentSession(paymentId)` | Looks up a live session by id; `null` if absent. |
| `closeAllPaymentSessions()` | Closes every session — wipes secrets and tokens. |
| `encryptCardData(CardData)` | Validates the card (`VALIDATION_INVALID_INPUT` when it fails), fetches the JWK, returns a JWE string; an encryption that fails is `INTERNAL_UNEXPECTED_ERROR`. |
| `submitCardForm(formId?)` | Encrypts the card the `PaymentCardForm` on screen holds under `formId` (the most recently active form when null) into a JWE and empties the form; see [Card form → JWE](#card-form--jwe). |
| `clearCardFormData(formId?)` | Drops the SDK's copy of a form's card (every form when null) and empties its fields. |
| `isWalletAvailable(activity, info?)` | Whether Google Pay can pay on this device: with the methods from a `GooglePayInfoResponse`, or without one, before a payment exists, with the gateway's defaults in the environment the config implies. |
| `isGooglePayAvailable(activity, info)` | The Android name of `isWalletAvailable`, the same call. |
| `currentLocaleStrings(preferred?)` | The `GopayLocaleStrings` the form resolves for a locale code, for showing its messages yourself. |

## `PaymentSession` API

A `PaymentSession` is the per-payment auth context. Construct it via `startPaymentSession`;
**never** instantiate directly. All methods but `close()` are `suspend`, and every one propagates
`GopaySDKException` on auth, HTTP or transport errors.

| Method | Maps to |
| --- | --- |
| `getStatus()` | `GET /payments/{payment_id}` |
| `charge(ChargePaymentRequest)` | `POST /payments/{payment_id}/charge` — low level; you supply `BrowserData`, the SDK completes `ip` and `accept_header` from `GET /cards/browser-data` first, and a missing `user_agent` before that |
| `completeBrowserData(browserData)` | `GET /cards/browser-data` — fills the null ones of `ip` and `accept_header`, and a missing `user_agent` with a synthesized WebView one; `charge(...)` does this itself |
| `chargeWithCardToken(activity, cardToken, browserData?, challengePreference?)` | `charge(...)` with a card token + device-derived `BrowserData` |
| `chargeWithCardToken(cardToken, browserData, challengePreference?)` | The same without an activity, as on iOS; `browserData` is required here, iOS derives it from the device |
| `chargeWithEncryptedCard(activity, payload, browserData?, challengePreference?)` | `charge(...)` with a JWE + device-derived `BrowserData` |
| `chargeWithEncryptedCard(payload, browserData, challengePreference?)` | The same without an activity, as on iOS; `browserData` is required here, iOS derives it from the device |
| `getChargeState()` | `GET /payments/{payment_id}/charge` |
| `awaitChargeState(options?)` | Polls `GET /payments/{payment_id}/charge` once a second until the charge is `SUCCEEDED` (returned) or `FAILED` (`PAYMENT_DECLINED`), running the 3DS challenge itself; see [Reading the result](#reading-the-result) |
| `getQrPaymentInfo(format?)` | `GET /payments/{payment_id}/qr-payment/info` |
| `getWalletInfo()` | `GET /payments/{payment_id}/google-pay/info`, the wallet's name shared with iOS |
| `getGooglePayInfo()` | The Android name of `getWalletInfo()`, the same call |
| `chargeWithWallet(activity, browserData?, challengePreference?)` | Managed flow: wallet info → Google Pay sheet → `charge(...)`, the name shared with iOS |
| `chargeWithGooglePay(activity, browserData?, challengePreference?)` | The Android name of `chargeWithWallet`, the same call |
| `handle3dsVerification(activity, redirectUrl, returnUrl?)` | Managed WebView; suspends until the challenge is answered, closed or found unreachable. Works after `close()` too: it sends nothing on the session |
| `close()` | Wipes `payment_secret`, JWT; unregisters from the SDK |

`getWalletInfo()` and `chargeWithWallet()` are the wallet's names shared with the iOS SDK;
`getGooglePayInfo()` and `chargeWithGooglePay()` do the same and stay as aliases.

Every enum the gateway sends back (`ChargeState`, `ChargeActionType`, `Emv3dsState`, `CardScheme`,
`PaymentState`, `Currency`) has an `UNKNOWN` constant: a value the gateway added after this SDK
version shipped decodes as `UNKNOWN`, the rest of the response still reads, and the raw value goes
to the debug log.

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
`session.chargeWithCardToken(...)`, or the app charges the JWE itself with
`chargeWithEncryptedCard`.

### The card form

`PaymentCardForm` keeps the PAN and CVV inside the SDK, under a `formId`, and
`GopaySDK.submitCardForm(formId)` turns them into the JWE: the card form model both SDKs share
since 2.2. The form has no pay button of its own; yours calls `submitCardForm`.

```kotlin
import cz.gopay.sdk.GopaySDK
import cz.gopay.sdk.exception.GopaySDKException
import cz.gopay.sdk.ui.CardFormValidationDisplay
import cz.gopay.sdk.ui.PaymentCardForm

@Composable
fun MyCardSheet(onJwe: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    PaymentCardForm(
        formId = "checkout-card",                       // generated when left out
        validation = CardFormValidationDisplay.LIVE     // inline errors as the user types
    )
    Button(onClick = {
        scope.launch {
            try {
                onJwe(GopaySDK.getInstance().submitCardForm("checkout-card"))
            } catch (e: GopaySDKException) {
                showError(e.message)   // VALIDATION_INVALID_INPUT for an empty or invalid form
            }
        }
    }) { Text("Pay") }
}
```

A `submitCardForm()` without an id reads the most recently active form. The SDK drops its copy of
the card after a successful `submitCardForm()`, when the form leaves the composition, and on
demand through `GopaySDK.getInstance().clearCardFormData(formId)`; so call `submitCardForm()`
while the form is on screen and forward the JWE promptly. **The visible fields are emptied too**:
after a successful submit the form returns to its pristine state, so the user sees an empty card
form and your own UI should move on rather than wait for further input. A failed encryption leaves
everything as the user typed it, since nothing has been authorized yet. A second submit on the
emptied form fails with `VALIDATION_INVALID_INPUT` and the message
`CardEncryptionResult.NO_CARD_DATA_MESSAGE`; a form that fails validation with
`CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE`. The gateway accepts each JWE **once** and
its payload expires 10 minutes after creation; to retry a failed charge, have the user confirm the
card again.

`validation` decides whether the form shows its own localized validation errors beneath the
fields, from the resolved locale strings: `HIDDEN` (the default, and the only behaviour before
2.2) shows none and leaves display to you; `LIVE` shows a field's error as soon as it holds
something invalid, never for an empty field; `ON_SUBMIT` shows them, the "required" message for
empty fields included, once a submit ran, and hides them again when the form is emptied. The
modes are the iOS form's `.hidden`, `.live` and `.onSubmit`.

### Programmatic

```kotlin
val jwe: String = GopaySDK.getInstance().encryptCardData(
    CardData(cardPan = "4444…", expMonth = "06", expYear = "27", cvv = "123")
)
// POST `jwe` to your backend; backend returns a card_token.
```

Every encryption, here and in `PaymentCardForm`, fetches the key from `GET /cards/public-key`
again; nothing is cached. The JWE's `iat` is the gateway's time, read from that response's
`Date` header, and `exp` is 600 seconds later, so a device with a wrong clock still produces a
JWE the gateway accepts. If the header is missing or unreadable, the device clock is used, with
a warning in Logcat while `GopayConfig.debug` is on; the encryption does not fail over it.

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
// returnUrl: the callback.return_url your backend created the payment with. Polls the charge
// and runs the 3DS challenge; see Reading the result for the manual path.
val finalState = session.awaitChargeState(AwaitChargeOptions(activity = activity, returnUrl = returnUrl))
```

### 3DS verification

`handle3dsVerification(activity, redirectUrl, returnUrl)` opens the challenge in an SDK-managed
WebView and suspends. Pass `returnUrl` the address your backend created the payment with
(`callback.return_url`), which the app gets from its backend together with `payment_id` and
`payment_secret`. GoPay is removing `return_url` from the charge response, so do not rely on it
there. The ACS comes back to the address once the user has answered. Without a usable one (null,
blank, or not an `http(s)` address with a host) the SDK waits for `GopaySDK.CHARGE_RETURN_URL` instead, and then the payment has to
have been created with that address. The return URL should carry no fragment (`#…`): what the
gateway appends lands in front of it, and the address no longer matches. The call has three ends,
not two:

- **Answered** — the ACS navigates to an address starting with the return URL, which the
  WebView intercepts without loading, and the call returns normally. Read the outcome with
  `getChargeState()` until it is final (see [Reading the result](#reading-the-result)); the SDK
  does not decide whether the payment went through.
- **Closed** — the screen closed before the challenge returned, which throws `GopaySDKException`
  with `PAYMENT_CANCELLED` (`PAYMENT_011`), whether the user backed out or swiped the task away.
  The user may have answered the challenge first, so read the charge before offering to pay
  again; the next verification can start right away.
- **Unreachable** — the challenge never reached the user, so there was nothing for them to
  answer: the page never drew, for instance because the redirect URL has already been retired or
  is not an `http(s)` address, or the challenge asked to hand off to a banking app and nothing on
  the device opened it. It throws `GopaySDKException` with `PAYMENT_VERIFICATION_UNREACHABLE`.
  Charging again is the right answer to a dead redirect URL, and a hand-off nobody took usually
  means the banking app is not installed, so offer the user another way to authorize. This is the
  case that used to arrive as a dismissal, so hosts ticked the verification off as handled and let
  the payment lapse.

Anything that breaks *after* the challenge has drawn ends as `PAYMENT_011` too, because by then
only `getChargeState()` can say whether the issuer authorised the payment. Cancelling the coroutine
that awaits the call (a `withTimeout`, a closed screen's scope) is not `PAYMENT_011`: it closes the
challenge and ends the call with that `CancellationException`. Only one verification runs per
process at a time; a second one throws `PAYMENT_VERIFICATION_IN_PROGRESS`.
The call works after `close()` too, on both platforms: the challenge runs in the WebView and sends
nothing on the session, so only the `getChargeState()` that follows needs a live session.

```kotlin
try {
    session.handle3dsVerification(activity, redirectUrl, returnUrl)   // from your backend
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.PAYMENT_VERIFICATION_UNREACHABLE -> retryCharge()
        // The challenge closed before it returned, but the charge was already sent: the state
        // below decides, so do not offer a new payment before reading it.
        GopayErrorCodes.PAYMENT_CANCELLED -> Unit
        else -> throw e
    }
}
val finalState = session.getChargeState()
```

### Reading the result

A charge comes back `REQUESTED` or `PROCESSING` and is decided later; only `SUCCEEDED` and
`FAILED` are results, `ACTION_REQUIRED` means a 3DS challenge is waiting, and `UNKNOWN` is a state
this SDK does not know, to be treated like `PROCESSING`. The GoPay guide recommends asking once a
second and treating the notification it sends your backend as the authoritative signal
([Payment states and lifecycle](https://api-docs.gopay.com/en/docs/02-payment-lifecycle.md)).
There are two ways to wait for the result; both work after any of the `charge*` methods.

**`awaitChargeState`** does the waiting for you, the way the GoPay browser SDK's method of the
same name does:

```kotlin
try {
    val charge = session.awaitChargeState(
        AwaitChargeOptions(
            intervalMs = 1_000,                  // default: the cadence GoPay recommends
            initialTimeoutMs = 30_000,           // default: how long REQUESTED/PROCESSING/UNKNOWN may last
            threeDSMode = ThreeDSMode.AUTOMATIC, // default: the SDK runs the challenge itself
            activity = activity,                 // the challenge is started from it
            returnUrl = returnUrl,               // the callback.return_url from your backend
            onStateChange = { showProgress(it.state) },
            onActionRequired = { redirectUrl -> /* once per new challenge address */ }
        )
    )
    showPaid(charge)                                       // SUCCEEDED
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.PAYMENT_DECLINED -> showDeclined(e.chargeState)  // FAILED, fail_reason in the message
        GopayErrorCodes.PAYMENT_TIMEOUT -> askBackendForState()         // still undecided after 30 s; e.chargeState is the last state
        GopayErrorCodes.PAYMENT_CANCELLED -> showStateAfterClosedChallenge(session) // the charge was sent; see the quick start
        else -> showError(e)                                            // a read or the challenge failed
    }
}
```

It reads the charge at once and then one `intervalMs` after each answer, hands every state to
`onStateChange`, and ends with the first result: `SUCCEEDED` is returned, `FAILED` throws
`PAYMENT_DECLINED` (`PAYMENT_006`) with the charge on `GopaySDKException.chargeState` and its
`fail_reason` in the message. `REQUESTED`, `PROCESSING`, `UNKNOWN` and an `ACTION_REQUIRED` without
a new address (none, empty, or one already reported) are waited out as undecided; when they
last past `initialTimeoutMs`, counted from the call, the read on the deadline decides, and a charge
still undecided there throws `PAYMENT_TIMEOUT` (`PAYMENT_007`) with that state on `chargeState`.
The gateway may still decide it, so read the state again or wait for your backend's notification
rather than offering a new payment; the SDK does not pass this code to `errorCallback`, as nothing
failed. A 3DS challenge takes as long as the user takes, so it is not timed: for each new, non-empty
`redirect_url` the call reports it through `onActionRequired` and, in `ThreeDSMode.AUTOMATIC`,
runs `handle3dsVerification` with the `activity` and `returnUrl` from the options, then polls on
with the timeout started over from the challenge's return; in `ThreeDSMode.MANUAL` the first
reported address cancels the timeout for good and you run the challenge. The same address is never
run or reported twice. The challenge's own errors end the call unchanged: `PAYMENT_CANCELLED` when
the user closed it, `PAYMENT_VERIFICATION_UNREACHABLE`, `PAYMENT_VERIFICATION_IN_PROGRESS`. So does
a failed read, with its `NETWORK_*` code, and an exception thrown by one of the callbacks.
Cancelling the coroutine stops the polling at once, closes a challenge that is up, and stays a
`CancellationException`. `AUTOMATIC` without an `activity`, or an interval or timeout that is not
positive, is refused with `VALIDATION_INVALID_INPUT` before anything is sent. Two calls on the same
session poll independently; only one of them can open the challenge. The iOS SDK has the same
method with the matching options (milliseconds on Android, seconds on iOS).

The `activity` is held for the duration of the call, as `chargeWithGooglePay` holds its one: the
SDK stores nothing, and the call ends with your scope. Pass the activity on screen when you call,
and read [Rotation and process death](#rotation-and-process-death) for the scope to call from.

**By hand**, ask for the state with `getChargeState()` after a charge, and again after
`handle3dsVerification` returns or throws `PAYMENT_011`. Keep asking while it is `REQUESTED`,
`PROCESSING` or `UNKNOWN`, which can take a while after a 3DS challenge, and stop after a limit of
your own, leaving the payment to your backend then:

```kotlin
suspend fun settle(session: PaymentSession): ChargePaymentResponse {
    repeat(60) {
        val charge = session.getChargeState()
        when (charge.state) {
            ChargeState.SUCCEEDED, ChargeState.FAILED, ChargeState.ACTION_REQUIRED -> return charge
            ChargeState.REQUESTED, ChargeState.PROCESSING, ChargeState.UNKNOWN -> delay(1_000)
        }
    }
    throw PaymentStillPending()   // your own exception: ask your backend later
}
```

A charge that comes back in `ACTION_REQUIRED` with `action?.redirectUrl` needs
`handle3dsVerification`; call `settle` again once it returns. When no charge went out, for
instance after a Google Pay sheet closed without paying, `getChargeState()` throws `NETWORK_002`
with HTTP 404. Your backend, not the app, decides whether the order is paid (see
[Your backend](#your-backend)).

### Rotation and process death

The 3DS challenge and the Google Pay sheet are activities of their own, shown on top of yours, and
the SDK keeps nothing on disk. Two things follow:

- **Call the SDK from a scope that survives a configuration change**, such as a ViewModel's
  `viewModelScope`, not your activity's `lifecycleScope`. A rotation, a switch to dark mode or a
  font size change recreates your activity behind the SDK's screen, and a call running in the old
  activity's scope is cancelled with it; for `handle3dsVerification` that also closes the
  challenge. The `activity` you pass starts the SDK's screen and gives the device's browser data.
  `handle3dsVerification` and the card charges use it only when the call starts;
  `chargeWithGooglePay` keeps it until the sheet returns, because it reads the browser data then,
  and `awaitChargeState` keeps the one in its options until the charge is decided, because it
  starts the challenge from it whenever the gateway asks.
  So pass the one on screen at that moment and do not keep one captured in the coroutine, as the
  [quick start](#3-start-a-session-and-charge) shows. The SDK's own screens keep their state
  across a rotation.
- **Process death ends the call.** It happens above all while the user is away in a banking app
  during 3DS and Android reclaims your app. When the user comes back, the restored 3DS screen
  closes itself and nothing returns to your code. Keep the payment `id` in saved state
  (`SavedStateHandle`, `onSaveInstanceState`), never `payment_secret`, and when your screen comes
  back with an `id` but no result, ask your backend for the payment's state before offering to pay
  again. Your backend learns it from the notification and `GET /payments/{id}`.

### `PaymentCardForm` composable

The form validates input, sets `FLAG_SECURE` unless your app is debuggable, and never exposes
raw card data to the host. Theming is controlled by `PaymentCardFormTheme`. Submitting goes
through `GopaySDK.submitCardForm(formId)` as shown under [The card form](#the-card-form); the
callbacks below are the older, Android-only way of doing the same, and keep working.

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

`onValidationError { … }` reports the fields that failed validation for inline error display
of your own, when `validation` is left at `HIDDEN`. To submit from a button of your own, take
the submit function from `onFormReady`. Its type is
`(suspend () -> CardEncryptionResult) -> Unit`: the form hands you a `suspend` function once,
and each call validates the fields, encrypts them and returns the result (it also goes to
`onEncryptionComplete`); it is `submitCardForm` for this form, with the exception returned inside
`CardEncryptionResult.Error` instead of thrown.

```kotlin
var submit by remember { mutableStateOf<(suspend () -> CardEncryptionResult)?>(null) }
val scope = rememberCoroutineScope()

PaymentCardForm(onFormReady = { submit = it })
Button(onClick = {
    scope.launch {
        when (val result = submit?.invoke()) {
            is CardEncryptionResult.Success -> viewModel.payWithJwe(result.jwe)
            is CardEncryptionResult.Error -> showError(result.message)
            null -> Unit
        }
    }
}) { Text("Pay") }
```

### Theming the form

`PaymentCardFormTheme` is a flat set of parameters named 1:1 after the theme keys of the GoPay
hosted card form (cc-v4), so the same design tokens describe the form on the web, on iOS and on
Android. Web pixels map 1:1 to `dp` (sizes) and `sp` (font sizes and spacing); font weights are CSS
numbers in the 100..900 range.

Two things are worth knowing before reading the table.

**The theme is a subset of the hosted form's set.** The field is a `BasicTextField` in a box the
SDK builds from framework primitives: a background, a `Modifier.border()` and padding. Nothing is
painted onto a canvas, so the theme carries what those primitives can be told to do and nothing
else. Sixteen of the hosted form's keys are therefore absent; they are listed below.

**Nothing is styled by default.** An unthemed form looks like any other form on the screen it sits
in — the platform's type and colors, an ordinary field, labels in the case they were written in,
and the host's own padding around it. The hosted card form is a page of its own and can afford a
look; here the form is one part of the merchant's screen. Theming is fully available, it is just a
choice rather than the starting point, and a theme that wants the hosted form's look states it
parameter by parameter.

```kotlin
PaymentCardForm(
    onEncryptionComplete = { … },
    theme = PaymentCardFormTheme(
        labelColor = Color(0xFF4B5E68),
        labelFontSize = 11.sp,
        labelFontWeight = 600,
        labelUppercase = true,
        inputBorderColor = Color(0xFF698492),
        errorMinHeight = 14.dp
    )
)
```

#### Parity with the hosted card form

All 44 keys of the hosted form, and what this SDK does with them.

| Key | Android | Notes |
|---|---|---|
| `fontFamily` | `fontFamily: FontFamily?` | Resolved by the host; the theme carries no font files |
| `labelColor` | `labelColor` | |
| `labelFontSize` | `labelFontSize` | |
| `labelFontWeight` | `labelFontWeight: Int` | CSS number, clamped into 100..900. Android keeps the exact value, so variable fonts resolve weights such as 450; the iOS SDK quantizes to the nearest hundred |
| `labelLineHeight` | `labelLineHeight` | `null` uses the font metrics |
| `labelUppercase` | `labelUppercase` | Uppercased with the device locale |
| `labelLetterSpacing` | `labelLetterSpacing` | `null` means none; the web's `em` fallback is not computed |
| `labelHidden` | `labelHidden` | The label becomes the field's content description |
| `inputTextColor` | `inputTextColor` | |
| `inputFontSize` | `inputFontSize` | |
| `inputFontWeight` | `inputFontWeight: Int?` | Exact value as above |
| `inputLineHeight` | not supported | A single-line field takes its height from the font, the padding and `inputHeight` |
| `inputLetterSpacing` | not supported | Dropped for parity: the iOS field would have to be measured by hand for it |
| `inputHeight` | `inputHeight` | A minimum height, with the vertical padding inside it; iOS reads it the same way |
| `placeholderColor` | `placeholderColor` | `null` takes the host theme's `textColorHint`, the muted color the host gives its own fields |
| `inputBorderStyle` | not supported | Neither platform offers a bottom line on its own, so `underline` would have to be painted; every field is a box |
| `inputBorderColor` | `inputBorderColor` | |
| `inputBorderWidth` | `inputBorderWidth` | The border thickness. There is only one: the field does not change when it takes focus |
| `inputBackgroundColor` | `inputBackgroundColor` | |
| `inputPaddingVertical` | `inputPaddingVertical` | |
| `inputPaddingHorizontal` | `inputPaddingHorizontal` | |
| `inputBorderRadius` | `inputBorderRadius: Dp` | Replaces the arbitrary `Shape` of 1.x |
| `inputBorderCollapse` | not supported | Merging the borders of neighbouring fields means painting them |
| `focusRingWidth` | not supported | A ring outside the field has no native equivalent |
| `focusRingColor` | not supported | Paired with `focusRingWidth` |
| `focusGradientStart` | not supported | Marking the focused field would mean painting it, see below |
| `focusGradientEnd` | not supported | Paired with `focusGradientStart` |
| `inputErrorBorderColor` | `inputErrorBorderColor` | Shown while the field is invalid |
| `errorTextColor` | `errorTextColor` | |
| `errorFontSize` | `errorFontSize` | |
| `errorMinHeight` | `errorMinHeight` | Reserves the shared error / helper slot |
| `errorSpacing` | `errorSpacing` | `null` falls back to `fieldSpacing` |
| `errorHidden` | web-only | The form only ever draws errors the host passes in as `errorText`, which is what `errorHidden: true` means on the web |
| `groupSpacing` | `groupSpacing` | Between the rows, and between expiry and CVV |
| `fieldSpacing` | `fieldSpacing` | Between a label and its input |
| `formPadding` | `formPadding` | |
| `formBackgroundColor` | `formBackgroundColor` | |
| `submitBackgroundColor` | web-only | |
| `submitHoverBackgroundColor` | web-only | |
| `submitDisabledBackgroundColor` | web-only | |
| `submitTextColor` | web-only | |
| `submitDisabledTextColor` | web-only | |
| `submitBorderRadius` | web-only | |
| `submitFontSize` | web-only | |

Sixteen keys are not supported. The seven `submit*` keys, because the mobile form never renders a
submit button — it is the permanent equivalent of the web's `submitMode: 'external'`, where the
iframe hides its button and the host submits, so there is nothing for them to style. `errorHidden`,
which the mobile form already does by default. And eight the browser can only honour by painting
the field: the border style, the collapsed borders, the focus ring, the focus gradient, and the
letter spacing and line height of the input. The theme is a typed Kotlin object, so an unsupported key is not
something a call site can write: the parameter simply is not there.

Two parameters have no counterpart on the web and are documented as Android-only extensions:
`helperTextColor` and `helperFontSize`, which style the optional helper line under a field. The
iOS SDK has no helper line, so these two apply only here.

#### Default values

The defaults are the platform's, not the hosted form's: an unthemed form is meant to disappear into
the merchant's screen rather than announce itself. In practice that means the Android type sizes,
an ordinary bordered field, no uppercasing, and no padding around the form, since the host lays it
out.

**Every color but the two backgrounds is unset by default and comes from the host's own theme
attributes** — `textColorPrimary` for the label and the entered text, `textColorHint` for the
placeholder, `colorControlNormal` for the border, `textColorSecondary` for the helper line. These
are the platform's own theming attributes, the ones an ordinary Android widget reads, so the form
follows the host's palette the same way the rest of the host's screen does. The iOS SDK resolves
the same parameters to the equivalent system colors. Errors are the exception: an unset
`inputErrorBorderColor` and `errorTextColor` take a fixed red, the same one iOS uses, because an
error has to read as an error whatever the palette says.

**This ties the form's light and dark appearance to the host's theme, not to the system setting.**
A host whose theme has no `values-night` variant keeps its light colors when the system turns dark,
and so does the form inside it — consistently with everything else on that screen. A host that
wants the form to follow dark mode gives its theme a night variant, as it would for its own views.
Where the attribute is missing entirely the SDK falls back to a neutral pair chosen by the system
setting, so the form is never unreadable.

A debug build says so in Logcat under the `GopaySDK` tag when the system is in dark mode and the
host theme still answers with dark text, and names both ways out: the night variant, or the colors
stated in the theme. It is a warning rather than a check — it compares the colors the form resolved
against the system setting, not against what the host's screen actually looks like — but a form
nobody can read is easy to mistake for a bug in the SDK, so it is worth saying out loud.

`inputBackgroundColor` and `formBackgroundColor` are transparent instead of unset, so the host's own
surface shows through.

| Parameter | Android default | Hosted form default |
|---|---|---|
| `labelColor` | unset, the host theme's `textColorPrimary` | `#4b5e68` |
| `labelFontSize` | `12.sp`, the size the iOS SDK uses | `11` |
| `labelFontWeight` | `400` | `600` |
| `labelUppercase` | `false` | `true` |
| `labelLetterSpacing` | `null` (none) | unset, historically `0.06em` |
| `inputTextColor` | unset, the host theme's `textColorPrimary` | `#4b5e68` |
| `inputFontSize` | `16.sp` | `14` |
| `inputBorderColor` | unset, the host theme's `colorControlNormal` | `#698492` |
| `inputBackgroundColor` | `Color.Transparent` | transparent |
| `inputBorderRadius` | `4.dp` | `0` |
| `inputPaddingVertical` | `12.dp` | `6` |
| `inputPaddingHorizontal` | `12.dp` | `0` |
| `inputErrorBorderColor` | unset, a fixed red shared with iOS | `#ea3c55` |
| `errorTextColor` | unset, that same red | `#cc0000` |
| `errorFontSize` | `12.sp` | `11` |
| `errorMinHeight` | `0.dp` (the form grows) | `14` |
| `formPadding` | `0.dp` (the host pads) | `16` |

`groupSpacing`, `fieldSpacing`, `inputBackgroundColor` and `formBackgroundColor` match the hosted
form's defaults as well.

**Neither platform marks the field the keyboard is on.** Nothing about the field changes when it
takes focus, and `inputBorderWidth` is the only thickness there is. That is the same rule as
everywhere else here: a focus ring, a focus gradient or a thickened line would all have to be
painted by the SDK, and a payment form embedded in someone else's screen is not the place to invent
a look the platform does not offer. The theme carries no focus color for the same reason.

One layout note: the expiry and CVV fields sit side by side, each with its own label above it. The
labels are the ones GoPay translates, not ones picked to fit, so in the longer languages, or at a
large system font scale, the expiry label can wrap where the CVV one does not. The row measures
both labels first and hands every field the height of the tallest one as a floor, so the two inputs
stay on one line whichever label wraps. The label itself is never clipped or shortened for it, and
the iOS SDK reserves the row's label height the same way.

A 1.x theme stops compiling in 2.0; what to write instead is under
[Card form theme](#card-form-theme) in [Migrating from 1.x](#migrating-from-1x).

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

Validation error strings are localized too. With `validation = LIVE` or `ON_SUBMIT` the form
shows them itself; with the default `HIDDEN` read them from the resolved locale in your
`onValidationError` handler:

```kotlin
val strings = GopaySDK.getInstance().currentLocaleStrings(preferred = "cs")
// strings.panErrorPattern, strings.expErrorPattern, strings.cvvErrorPattern, …
```

## Google Pay flow

```kotlin
val info = session.getWalletInfo()
if (GopaySDK.getInstance().isWalletAvailable(activity, info)) {
    session.chargeWithWallet(activity)
    // returnUrl: the callback.return_url your backend created the payment with.
    val finalState = session.awaitChargeState(AwaitChargeOptions(activity = activity, returnUrl = returnUrl))
}
```

`isWalletAvailable`, `getWalletInfo` and `chargeWithWallet` are the wallet's names shared with
the iOS SDK, where they ask Apple Pay; `isGooglePayAvailable`, `getGooglePayInfo` and
`chargeWithGooglePay` are the same calls under their Android names. `isWalletAvailable(activity)`
without the info, before a payment exists, asks Google Pay for `PAN_ONLY` and `CRYPTOGRAM_3DS`
with Visa and Mastercard, which is what the gateway allows, in the environment
`GopayConfig.environment` implies (`PRODUCTION` for the production gateway, `TEST` otherwise).

`chargeWithWallet` launches the Google Pay sheet inside an SDK-managed activity, parses the
returned token, and posts the charge in one step. It takes an optional `browserData`, derived
from the activity when left out, and an optional `challengePreference`. The user closing the sheet without paying
throws `PAYMENT_CANCELLED`. Google Pay unable to show its sheet throws
`PAYMENT_METHOD_UNAVAILABLE`, any other failure of the sheet or its token
`PAYMENT_PROCESSING_FAILED`, and `PAYMENT_GOOGLE_PAY_IN_PROGRESS` only means another sheet is
already open.

The SDK brings `play-services-wallet` with it. Add it to your app yourself only when your own code
calls the Google Pay API.

### Setting up Google Pay

According to the GoPay guide
([Google Pay, Sandbox testing](https://api-docs.gopay.com/en/docs/07-google-pay.md)), nothing has
to be set up on Google's side in the sandbox: the gateway answers with the `TEST` environment, no
merchant identifier or release signing is needed, and Google Pay shows dummy payment methods under
an unverified-merchant banner. Production works only once your app and GoPay are set up for it. The
steps are in the GoPay guide,
[Google Pay, App setup](https://api-docs.gopay.com/en/docs/07-google-pay.md); in short, you
register the app in the [Google Pay & Wallet Console](https://pay.google.com/business/console),
submit it for approval with screenshots from a build signed with your release key, and send your
Google merchant identifier to GoPay through `integration@gopay.com`. Before you submit, go through
Google's [Integration checklist](https://developers.google.com/pay/api/android/guides/test-and-deploy/integration-checklist)
and [Request production access](https://developers.google.com/pay/api/android/guides/test-and-deploy/request-prod-access).

The SDK has no Google Pay settings of its own: the environment (`TEST` or `PRODUCTION`), the card
networks, the amount and the tokenization parameters come from the gateway for each payment
(`GET /payments/{id}/google-pay/info`). `gatewayMerchantId` in those parameters is your merchant at
GoPay, filled in by the gateway; you never set it. Which environment the sheet runs in is therefore
the gateway's decision, not the app's.

## Environments

| Environment | Base URL | Status |
| --- | --- | --- |
| `Environment.SANDBOX` | `https://gw.sandbox.gopay.com/gp-gw/api/4.0/` | Testing and integration |
| `Environment.PRODUCTION` | `https://gate.gopay.com/gp-gw/api/4.0/` | Live transactions |
| `Environment.DEVELOPMENT.create(url)` | Custom — must start with `http://` or `https://`, else `CONFIG_INVALID_BASE_URL` | A gateway of your own |

Both built-in hosts come from the Payments 4.0 spec's `servers` block. Use `DEVELOPMENT` only
when you have been given a gateway that is neither — see
[`ENVIRONMENT_USAGE.md`](ENVIRONMENT_USAGE.md).

## Error handling

Every failure throws `GopaySDKException` with a structured error code (`AUTH_*`, `NETWORK_*`,
`PAYMENT_*`, `CONFIG_*`, `VALIDATION_*`, `INTERNAL_*`). No transport failure reaches you bare: the
original exception is the `cause`; nor does an invalid argument, a bad base URL or a card
encryption that fails, which are `VALIDATION_INVALID_INPUT`, `CONFIG_INVALID_BASE_URL` and
`INTERNAL_UNEXPECTED_ERROR` since 2.2 rather than platform exceptions. A coroutine cancellation
stays a `CancellationException` and is never mapped. Codes most likely to surface in the
per-payment flow:

| Code | When |
| --- | --- |
| `NETWORK_TIMEOUT` | The request timed out |
| `NETWORK_NO_CONNECTION` | The gateway could not be reached: offline, DNS failure, connection refused |
| `NETWORK_SSL_ERROR` | The gateway's certificate failed the check or does not match its host name; a TLS connection that drops is `NETWORK_IO_ERROR` |
| `NETWORK_IO_ERROR` | Any other transport failure, e.g. the connection dropped mid-response |
| `NETWORK_CLIENT_ERROR` / `NETWORK_SERVER_ERROR` | The gateway answered with a 4xx / 5xx; the status and the gateway's error are in `httpContext` |
| `AUTH_PAYMENT_CREDENTIALS_INVALID` | `/oauth2/token` turned the `payment_id`/`payment_secret` pair down (HTTP 400, 401 or 403). Any other failure there keeps its `NETWORK_*` code, or `INTERNAL_*` when the answer cannot be read, so an offline device is not told the payment is bad |
| `AUTH_PAYMENT_TOKEN_EXPIRED` | JWT still rejected after a single re-auth retry — fetch fresh creds from backend |
| `AUTH_PAYMENT_SESSION_ALREADY_EXISTS` | `startPaymentSession` called for a `paymentId` that already has a live session |
| `AUTH_PAYMENT_SESSION_CLOSED` | API call on a session after `close()`, except `handle3dsVerification`, which sends nothing on the session; a charge is refused this way before the browser data is fetched |
| `AUTH_SHAREABLE_KEY_MISSING` | `charge(...)`, `encryptCardData` or `PaymentCardForm` called without `clientId`+`shareableKey` |
| `PAYMENT_GOOGLE_PAY_IN_PROGRESS` | A second Google Pay sheet attempted while one is visible, and nothing else |
| `PAYMENT_METHOD_UNAVAILABLE` | Google Pay cannot show its sheet on this device, e.g. Google Play services are missing, disabled or outdated |
| `PAYMENT_PROCESSING_FAILED` | Google Pay failed in the sheet, or returned no data or a token the SDK cannot read; the original is the `cause` |
| `PAYMENT_VERIFICATION_IN_PROGRESS` | A second 3DS WebView attempted while one is open |
| `PAYMENT_VERIFICATION_UNREACHABLE` | The 3DS challenge never reached the user: the page would not load, e.g. the redirect URL is dead or is not a web address, or no app took the hand-off |
| `PAYMENT_CANCELLED` | The Google Pay sheet or the 3DS screen closed before it finished, or the 3DS page failed after it had drawn; read the charge before paying again |
| `PAYMENT_DECLINED` | `awaitChargeState` saw the charge end `FAILED`; the charge is on `chargeState`, its `fail_reason` in the message. A retry needs a new payment |
| `PAYMENT_TIMEOUT` | `awaitChargeState` saw the charge stay undecided (`REQUESTED`/`PROCESSING`/`UNKNOWN`, or `ACTION_REQUIRED` without a new address) for the whole `initialTimeoutMs`; the last state is on `chargeState`. Not a failure and not passed to `errorCallback`: read the state again or wait for the notification |
| `VALIDATION_INVALID_INPUT` | Invalid input: an empty `paymentId` / `paymentSecret`, a card that fails the PAN / month / year / CVV rules in `encryptCardData`, a `submitCardForm` with no card or an invalid one, or `awaitChargeState` with a non-positive interval or timeout, or in `ThreeDSMode.AUTOMATIC` without an `activity` |
| `CONFIG_SDK_NOT_INITIALIZED` | `getInstance()`, and so any call, before `initialize` |
| `CONFIG_INVALID_BASE_URL` | A `DEVELOPMENT` URL that is empty, not `http(s)`, or one the HTTP client refuses at `initialize` |
| `INTERNAL_UNEXPECTED_ERROR` | The card encryption failed after the key was fetched; the platform's exception is the `cause` |

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

When the gateway answered with an error, `httpContext` carries the status and `gatewayError` its
error body (`error`, `message`, `detail`, each nullable); the gateway's `message` is also appended
to the exception's, e.g. `Failed to get Google Pay info: HTTP 403: <the gateway's message>`. The
raw body stays in `httpContext.responseBody`.

`chargeWithGooglePay` adds its own cases:

```kotlin
try {
    session.chargeWithGooglePay(activity)
} catch (e: GopaySDKException) {
    when (e.errorCode) {
        GopayErrorCodes.PAYMENT_GOOGLE_PAY_IN_PROGRESS -> Unit // a sheet is already open
        GopayErrorCodes.PAYMENT_METHOD_UNAVAILABLE -> hideGooglePayButton()
        GopayErrorCodes.PAYMENT_CANCELLED -> Unit // the user closed the sheet; not a failure
        else -> showGenericError(e) // PAYMENT_PROCESSING_FAILED and the charge's own errors
    }
}
```

Closing the Google Pay sheet without paying or the 3DS challenge before it returned throws
`PAYMENT_CANCELLED` (`PAYMENT_011`), and so does a challenge page that fails after it has started
rendering. The payment did not fail. From the Google Pay sheet nothing was charged; from the 3DS
challenge the charge was already sent, so read `getChargeState()` before offering to pay again. It
is not passed to `errorCallback`. A `CancellationException` comes only from cancelling the coroutine
that awaits the call: that closes the 3DS challenge and ends the call with it. A Google Pay sheet
already up stays until the user closes it, but the call ends at once; what the user does in the
sheet is discarded and nothing is charged. Do not catch `CancellationException` to detect a closed
sheet; rethrow it.

`GopayConfig.errorCallback` hears every `GopaySDKException` a public call of the SDK or of a
session throws, once per error, for central logging, on whatever thread the call ended on; the
two outcomes of a payment window, `PAYMENT_TIMEOUT` and `PAYMENT_CANCELLED`, and a
`CancellationException` stay out, and nothing that is not a `GopaySDKException` goes in, not an
exception you construct yourself. The error inside a `CardEncryptionResult.Error` is reported
the same way. The rule is the same on iOS and spelled out at the top of `ERROR_CODES.md`. It is
not a substitute for catching the exception at the call.

Every code, with its causes and what to do about it, is in
[`sdk/docs/ERROR_CODES.md`](sdk/docs/ERROR_CODES.md). The codes are shared with the iOS SDK. Codes
neither SDK throws stay in `GopayErrorCodes` for 2.0, deprecated, so code written against 1.x that
names them still compiles; 3.0 removes them. ERROR_CODES marks them.

## Shared API with iOS

Since 2.2 the two SDKs offer the same calls under the same names, so an integration, or the React
Native SDK built on both, reads the same on either platform. The main path of this README is that
shared API; the platform names stay as aliases, without deprecation, and what 3.0 removes is
listed in [`sdk/docs/API_3_0.md`](sdk/docs/API_3_0.md).

| Shared name | Android alias or variant | What it is |
| --- | --- | --- |
| `session.chargeWithCardToken(cardToken, browserData, challengePreference?)` | variant: `chargeWithCardToken(activity, …)` derives `browserData` from the activity | a card token charge in one call; without an `activity` Android requires `browserData`, iOS derives it from the device default |
| `session.chargeWithEncryptedCard(payload, browserData, challengePreference?)` | variant: `chargeWithEncryptedCard(activity, …)` derives `browserData` from the activity | a JWE charge in one call; without an `activity` Android requires `browserData`, iOS derives it from the device default |
| `GopaySDK.isWalletAvailable(activity, info?)` | alias: `isGooglePayAvailable(activity, info)` | can the device pay with the platform wallet |
| `session.getWalletInfo()` | alias: `getGooglePayInfo()` | the wallet configuration for the payment |
| `session.chargeWithWallet(activity, browserData?, challengePreference?)` | alias: `chargeWithGooglePay(...)` | the managed wallet sheet and charge |
| `PaymentCardForm(formId)` + `submitCardForm(formId)` + `clearCardFormData(formId)` | older path: `onFormReady` / `onEncryptionComplete` / `onValidationError`, the same submit reported as a result | the card form model, now on Android too |
| `PaymentCardForm(validation)` with `HIDDEN`, `LIVE`, `ON_SUBMIT` | — | the validation display modes, now on Android too |

An alias is one body under a second name: it does exactly what the shared name does, with the
same arguments and the same codes. A variant takes an `activity` instead of `browserData` and
derives the latter from it; the older form path runs the same submit and hands the result to a
callback instead of returning it. The response types stay platform-specific
(`GooglePayInfoResponse` here, `GopayApplePayAppInfoResponse` on iOS), and so do the UI
arguments: Android takes an `activity` where the wallet, the 3DS screen and the automatic
polling need one, iOS finds the presenting view controller itself. The errors are the shared
code contract above, with one rule for `errorCallback` on both.

## What changed in 2.2

Besides the shared API, 2.2 changes what an integration sees on a few error paths:

- `IllegalArgumentException` is gone from the SDK's contract: an empty `paymentId` /
  `paymentSecret` in `startPaymentSession`, a card that fails validation in `encryptCardData`,
  a bad `Environment.DEVELOPMENT` URL and a Google Pay token `GooglePayHelper.parseGooglePayToken`
  cannot read are `GopaySDKException`s with `VALIDATION_INVALID_INPUT`, `CONFIG_INVALID_BASE_URL`
  and `PAYMENT_PROCESSING_FAILED`; a card encryption that fails is `INTERNAL_UNEXPECTED_ERROR`
  instead of an `IllegalStateException`.
- `CardEncryptionResult.Error.exception` is a `GopaySDKException` for a form that fails
  validation, and its message for an invalid form is
  `CardEncryptionResult.INVALID_CARD_FORM_DATA_MESSAGE` (`NO_CARD_DATA_MESSAGE` for an empty one
  is unchanged); that `VALIDATION_INVALID_INPUT` now reaches `errorCallback`, from every submit
  of the form.
- `errorCallback` hears every error a public call throws, once, where it leaves the SDK. It no
  longer hears an exception at construction: the inner cause of a wrapped error, an exception you
  build yourself, or `CONFIG_001` before `initialize`. Counts in your analytics change
  accordingly. It runs on the thread the failing call ended on, not necessarily the main thread.
- A failed `initialize` (`CONFIG_INVALID_BASE_URL`) leaves the SDK as an earlier `initialize` set
  it up, callback included.

## Migrating from 1.x

What 2.0 changes for code written against 1.x, in four parts: the error contract, card encryption,
the response enums and the card form theme.

### Error contract

| 1.x | 2.0 |
| --- | --- |
| A transport failure (offline, timeout, TLS) reached you bare (`IOException`) | `GopaySDKException` with `NETWORK_001`, `NETWORK_004`, `NETWORK_005` or `NETWORK_007`; the original error is the `cause` |
| An HTTP 5xx was `NETWORK_002` | `NETWORK_003` |
| Any failure of the token request in `startPaymentSession` was `AUTH_010` | `AUTH_010` only when the gateway turns the pair down (HTTP 400, 401 or 403); anything else keeps its `NETWORK_*` code |
| An unexpected failure while exchanging the payment credentials was `AUTH_010` | `INTERNAL_001` |
| A 2xx without a body was `AUTH_005` | `INTERNAL_003`, as a body the SDK cannot read |
| A Google Pay error or missing data was `PAYMENT_009` | Wallet unavailable: `PAYMENT_004`; any other failure: `PAYMENT_003`; `PAYMENT_009` only for a sheet already open |
| Closing the Google Pay sheet or the 3DS challenge threw `CancellationException` | `PAYMENT_CANCELLED` (`PAYMENT_011`); a `CancellationException` now comes only from cancelling your own coroutine, which also closes the 3DS challenge |
| `HttpErrorContext` had four properties | It gains `gatewayError`, the gateway's error body, as a fifth with a default; source that uses it compiles as before, only code compiled against 1.x has to be rebuilt |
| Codes in `GopayErrorCodes` that the SDK never throws | Deprecated: neither SDK throws them, and 3.0 removes them; ERROR_CODES marks each |
| `charge.returnUrl` was the address to pass `handle3dsVerification` | Deprecated: GoPay is removing `return_url` from the charge response, so do not rely on it. Pass the `callback.return_url` your backend created the payment with |

### Card encryption

| 1.x | 2.0 |
| --- | --- |
| `getPublicEncryptionKey()`, `EncryptionService`, `Jwk`, `JweHeader` | Gone or internal: a key fetched ahead of time, or a JWE built outside the SDK, would carry the device's clock. Call `encryptCardData` or use `PaymentCardForm`. |

### Response enums

| 1.x | 2.0 |
| --- | --- |
| An exhaustive `when` over `ChargeState`, `ChargeActionType`, `Emv3dsState`, `CardScheme`, `PaymentState` or `Currency` | Each has an `UNKNOWN` constant for a value the gateway added later; add a branch; for a state, treat it as not final. |

### Card form theme

`PaymentCardFormTheme` in 2.0 is a new set of parameters. The composed `TextStyle` values are gone,
so every call site is a compile error rather than a silent change of appearance — deliberately, as
two parameters kept their names and changed their meaning.

| 1.x | 2.0 |
| --- | --- |
| `labelTextStyle` | `labelColor` + `labelFontSize` + `labelFontWeight` (+ `fontFamily`) |
| `inputTextStyle` | `inputTextColor` + `inputFontSize` + `inputFontWeight` |
| `errorTextStyle` | `errorTextColor` + `errorFontSize` |
| `helperTextStyle` | `helperTextColor` + `helperFontSize` |
| `placeholderTextStyle` | `placeholderColor` — only the color survives, and unset now means the host theme's `textColorHint`; the placeholder inherits the rest of the input typography |
| `loadingTextStyle` | removed without replacement; it was never rendered |
| `inputShape` | `inputBorderRadius: Dp` |
| `inputPadding` | `inputPaddingVertical` + `inputPaddingHorizontal` |
| `inputBorderColor`, `inputErrorBorderColor`, `inputBorderWidth` | same name, same default |
| `inputBackgroundColor` | same name; the default is `Color.Transparent` instead of `Color.White` |
| `fieldSpacing` (gap between the rows) | `groupSpacing` — **the meaning moved** |
| `groupSpacing` (gap between expiry and CVV) | `groupSpacing` — one value now covers both gaps |
| the hard-coded `4.dp` under a label | `fieldSpacing` — **the name is reused for a new meaning** |

Eight parameters that 2.0 carried in an earlier preview are gone again, because rendering them
would mean painting the field rather than asking the platform for it. Nothing replaces them:

| Removed | What it did |
| --- | --- |
| `inputBorderStyle` | Chose a bottom line instead of a border; every field is a box now |
| `inputBorderCollapse` | Merged the borders of neighbouring fields into one block |
| `focusRingWidth`, `focusRingColor` | Drew a ring outside the focused field |
| `focusGradientStart`, `focusGradientEnd` | Colored the focused field; nothing marks it now |
| `inputLetterSpacing` | Tracking of the entered text, dropped for parity with iOS |
| `inputLineHeight` | Never did anything on a single-line field |

The default form is close to the 1.x one, because both are simply the platform look: the same type
sizes, the same border and the same `12.dp` padding. Two things differ:

```kotlin
PaymentCardFormTheme(
    // 1.x painted the field white, which broke on a dark background
    inputBackgroundColor = Color.White
)
```

The second is not a value: `groupSpacing` is one parameter for both gaps, so it cannot hold the old
pair (`2.dp` between the rows, `16.dp` between expiry and CVV). It keeps `16.dp`, so the gap between
the rows grows from `2.dp` to `16.dp`.

One behaviour changes as well: the label of an invalid field stays in `labelColor` instead of
turning red, which is what the hosted form does too.

## Example app

A demo app that fakes the merchant backend and exercises every session operation lives in
[`app/`](app/) — see [`app/README.md`](app/README.md) for setup and a walkthrough.

Its gateway and merchant values are not in the repository: put them in `local.properties` in the
repo root, then run the app from Android Studio or with `./gradlew :app:installDebug`.

## Security notes

- Neither the `payment_secret` nor the JWT is ever written to disk. Both live in memory inside
  `PaymentSession` and are wiped on `close()`. The SDK no longer reads or writes
  `SharedPreferences`.
- The merchant public key is not cached: it is fetched for every encryption and never stored.
- With `GopayConfig.debug` on, the SDK logs its HTTP traffic, bodies included, to Logcat under
  the `okhttp.OkHttpClient` tag. The `Authorization` header, `payment_secret`, the JWT, card
  tokens, the card JWE and the Google Pay payment data are masked. Keep `debug` off in release
  builds.
- `PaymentCardForm` sets `FLAG_SECURE` on the window it is drawn in, a dialog or a Material3
  `ModalBottomSheet` included, to block screenshots and screen recording of card data. It does so
  unless your app is debuggable (`android:debuggable`, as in a debug build), independent of
  `GopayConfig.debug`, and takes the flag off when the form leaves. A window that already had the
  flag keeps it. One nesting is not covered: a sheet or dialog with the form opened from another
  dialog inherits the flag from that dialog, so set `SecureFlagPolicy.SecureOn` on the sheet, or
  `FLAG_SECURE` on the dialog under it, yourself.
- Card data lives in memory only while the form needs it: the form state is cleared after a
  successful encryption and when the form leaves the composition (PCI DSS 4.0.1, req. 3.3.1).
  It is deliberately kept after a *failed* encryption so the user can retry without retyping.
  `CardData`'s `toString()` is masked, so a stray log never prints the PAN or CVV. JVM strings
  cannot be securely overwritten, so clearing releases the references rather than zeroing bytes.
- Network: both built-in environments are HTTPS. The SDK sets no cleartext policy of its own, so
  your manifest decides it; a `DEVELOPMENT` gateway on `http://` needs cleartext allowed there.
  The SDK declares the `INTERNET` permission itself.
- Certificate pinning is plumbed all the way through `NetworkManager`/`NetworkModule`, but is
  **not reachable from the public API today**: the `CertificatePinner` is a parameter of
  `GopaySDK`'s private constructor and `GopaySDK.initialize(config)` never passes one.
  `GopayConfig` has no pinning field. Exposing it needs a small API change.

### Google Play and PCI DSS

- The card form and Google Pay take payment details in your app. What you declare in the Play
  Console's Data safety form is yours to decide, see Google's
  [Declare your app's data use](https://developer.android.com/guide/topics/data/collect-share),
  User payment info under Financial info in particular.
- Google Play's [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738)
  says when Google Play's billing must and must not be used; physical goods and services are paid
  outside it, which is what the SDK is for.
- According to the GoPay guide
  ([PCI DSS scope](https://api-docs.gopay.com/en/docs/01-security.md)), an integration where the
  GoPay SDK collects and encrypts the card and your code handles only the resulting JWE or card
  token qualifies for SAQ-A; if your code collects the card itself and builds the JWE, it is in
  SAQ-D scope. Confirm your own scope with GoPay at `integration@gopay.com`.

## Testing

```bash
./gradlew :sdk:testDebugUnitTest
```

Requires `local.properties` (see [Requirements](#requirements)). `:sdk:jacocoTestReport` is wired
as a `finalizedBy` of the test task, so a coverage report is generated automatically afterwards:
`sdk/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml` plus a browsable
`.../jacocoTestReport/html/index.html`.

To check the whole project compiles, including the demo app:

```bash
./gradlew :app:assembleDebug
```

## Releasing

A release is started by hand on `master`; semantic-release picks the version from the commit
messages and tags it. Every release tag that reaches the GitHub mirror is published to Maven
Central by a GitHub Actions workflow. See [`sdk/docs/PUBLISHING.md`](sdk/docs/PUBLISHING.md).

## License

MIT, see [`LICENSE`](LICENSE). The published POM declares the same.
