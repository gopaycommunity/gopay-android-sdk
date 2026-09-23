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

### Theming the form

`PaymentCardFormTheme` is a flat set of parameters named 1:1 after the theme keys of the GoPay
hosted card form (cc-v4), so the same design tokens describe the form on the web, on iOS and on
Android. Web pixels map 1:1 to `dp` (sizes) and `sp` (font sizes and spacing); font weights are CSS
numbers in the 100..900 range.

Two things are worth knowing before reading the table.

**The theme is a subset of the hosted form's set.** The field is the platform's own text field,
decorated by the platform, and the SDK paints no part of it, so the theme carries what a native
input can be told to do and nothing else. Fifteen of the hosted form's keys are therefore absent;
they are listed below.

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
| `placeholderColor` | `placeholderColor` | `null` uses the platform's own placeholder color, which follows the system appearance rather than the theme |
| `inputBorderStyle` | `inputBorderStyle: InputBorderStyle` | `BOXED` or `UNDERLINE`, both from Material. **Android only**: the iOS SDK has no native underline and renders both as a box |
| `inputBorderColor` | `inputBorderColor` | |
| `inputBorderWidth` | `inputBorderWidth` | The resting thickness; the focused field takes the thicker of this and Material's own |
| `inputBackgroundColor` | `inputBackgroundColor` | |
| `inputPaddingVertical` | `inputPaddingVertical` | |
| `inputPaddingHorizontal` | `inputPaddingHorizontal` | |
| `inputBorderRadius` | `inputBorderRadius: Dp` | Replaces the arbitrary `Shape` of 1.x |
| `inputBorderCollapse` | not supported | Merging the borders of neighbouring fields means painting them |
| `focusRingWidth` | not supported | A ring outside the field has no native equivalent |
| `focusRingColor` | not supported | Paired with `focusRingWidth` |
| `focusGradientStart` | not supported | Marking the focused field is left to the platform, see below |
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

Fifteen keys are not supported. The seven `submit*` keys, because the mobile form never renders a
submit button — it is the permanent equivalent of the web's `submitMode: 'external'`, where the
iframe hides its button and the host submits, so there is nothing for them to style. `errorHidden`,
which the mobile form already does by default. And seven the browser can only honour by painting
the field: the collapsed borders, the focus ring, the focus gradient, and the letter spacing and
line height of the input. The theme is a typed Kotlin object, so an unsupported key is not
something a call site can write: the parameter simply is not there.

Two parameters have no counterpart on the web and are documented as Android-only extensions:
`helperTextColor` and `helperFontSize`, which style the optional helper line under a field. The
iOS SDK has no helper line, so these two apply only here.

#### Default values

The defaults are the platform's, not the hosted form's: an unthemed form is meant to disappear into
the merchant's screen rather than announce itself. In practice that means the Android type sizes,
an ordinary Material field, no uppercasing, and no padding around the form, since the host lays it
out.

**Every color but the two backgrounds is unset by default and comes from the ambient
`MaterialTheme.colorScheme`**, so the form follows the host's palette and its light or dark mode
without being told. `inputBackgroundColor` and `formBackgroundColor` are transparent instead, so
the host's own surface shows through, and `placeholderColor` is unset in the sense of leaving the
field's placeholder to Material. A host that sets up no Material theme at all still gets a scheme,
Material's own light one, so the form is readable either way.

One thing a dark theme has to say out loud: a theme that paints the field dark should set
`placeholderColor` too. The platform's placeholder color follows the *system's* light or dark
setting, not the theme's, so a dark field under a light system leaves the placeholder dark on dark.

| Parameter | Android default | Hosted form default |
|---|---|---|
| `labelColor` | unset, the scheme's `onSurfaceVariant` | `#4b5e68` |
| `labelFontSize` | `14.sp` | `11` |
| `labelFontWeight` | `400` | `600` |
| `labelUppercase` | `false` | `true` |
| `labelLetterSpacing` | `null` (none) | unset, historically `0.06em` |
| `inputTextColor` | unset, the field's own text color | `#4b5e68` |
| `inputFontSize` | `16.sp` | `14` |
| `inputBorderColor` | unset, the color the platform gives a field | `#698492` |
| `inputBackgroundColor` | `Color.Transparent` | transparent |
| `inputBorderRadius` | `4.dp` | `0` |
| `inputPaddingVertical` | `12.dp` | `6` |
| `inputPaddingHorizontal` | `12.dp` | `0` |
| `inputErrorBorderColor` | unset, the scheme's `error` | `#ea3c55` |
| `errorTextColor` | unset, the scheme's `error` | `#cc0000` |
| `errorFontSize` | `12.sp` | `11` |
| `errorMinHeight` | `0.dp` (the form grows) | `14` |
| `formPadding` | `0.dp` (the host pads) | `16` |

`inputBorderStyle` is the one place the two agree by accident: both default to the underline, which
is also the ordinary Android field. `groupSpacing`, `fieldSpacing`, `inputBackgroundColor` and
`formBackgroundColor` match the hosted form as well.

Whatever the theme leaves unset, the platform fills in. **Marking the focused field is one of those
things, and it is native on each platform, so it differs between them**: on Android Material colors
the border of the field the keyboard is on and thickens it, while on iOS the native field marks
nothing, so there the form shows no focus state at all. The theme carries no focus color to even it
out; that was deliberate, because any such color would have to be painted by the SDK.
`inputBorderWidth` sets the resting thickness only. The focused field keeps Material's own
thickness, or the resting one where that is already thicker, so a heavy border never reads as
thinner once the field is focused.

One layout note: the expiry and CVV fields sit side by side, each with its own label above it, so
the two line up as long as both labels take the same number of lines. The labels are the ones GoPay
translates, not ones picked to fit, so in the longer languages, or at a large system font scale,
the expiry label can wrap where the CVV one does not and leave the pair a line out of step. That is
left as it is — the label is not clipped to avoid it, and the field is not measured into place, and
the hosted form and the iOS SDK behave the same way.

#### Migrating from 1.x

`PaymentCardFormTheme` in 2.0 is a new set of parameters. The composed `TextStyle` values are gone,
so every call site is a compile error rather than a silent change of appearance — deliberately, as
two parameters kept their names and changed their meaning.

| 1.x | 2.0 |
|---|---|
| `labelTextStyle` | `labelColor` + `labelFontSize` + `labelFontWeight` (+ `fontFamily`) |
| `inputTextStyle` | `inputTextColor` + `inputFontSize` + `inputFontWeight` |
| `errorTextStyle` | `errorTextColor` + `errorFontSize` |
| `helperTextStyle` | `helperTextColor` + `helperFontSize` |
| `placeholderTextStyle` | `placeholderColor` — only the color survives, and unset now means the platform's; the placeholder inherits the rest of the input typography |
| `loadingTextStyle` | removed without replacement; it was never rendered |
| `inputShape` | `inputBorderRadius: Dp` |
| `inputPadding` | `inputPaddingVertical` + `inputPaddingHorizontal` |
| `inputBorderColor`, `inputErrorBorderColor`, `inputBorderWidth` | same name, same default |
| `inputBackgroundColor` | same name; the default is `Color.Transparent` instead of `Color.White` |
| `fieldSpacing` (gap between the rows) | `groupSpacing` — **the meaning moved** |
| `groupSpacing` (gap between expiry and CVV) | `groupSpacing` — one value now covers both gaps |
| the hard-coded `4.dp` under a label | `fieldSpacing` — **the name is reused for a new meaning** |

Seven parameters that 2.0 carried in an earlier preview are gone again, because rendering them
would mean painting the field rather than asking the platform for it. Nothing replaces them:

| Removed | What it did |
|---|---|
| `inputBorderCollapse` | Merged the borders of neighbouring fields into one block |
| `focusRingWidth`, `focusRingColor` | Drew a ring outside the focused field |
| `focusGradientStart`, `focusGradientEnd` | Colored the focused field; Material now marks it |
| `inputLetterSpacing` | Tracking of the entered text, dropped for parity with iOS |
| `inputLineHeight` | Never did anything on a single-line field |

The default form is close to the 1.x one, because both are simply the platform look: the same type
sizes and the same `12.dp` padding. Three things differ:

```kotlin
PaymentCardFormTheme(
    // 1.x drew a box; the field is now an ordinary Material one, underlined
    inputBorderStyle = InputBorderStyle.BOXED,
    // 1.x painted the field white, which broke on a dark background
    inputBackgroundColor = Color.White
)
```

The third is not a value: `groupSpacing` is one parameter for both gaps, so it cannot hold the old
pair (`2.dp` between the rows, `16.dp` between expiry and CVV). It keeps `16.dp`, so the gap between
the rows grows from `2.dp` to `16.dp`.

Two behaviours change as well. The label of an invalid field stays in `labelColor` instead of
turning red, which is what the hosted form does too. And the focused field is now marked, by
Material rather than by the theme — see the note on focus above.

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
| `Environment.SANDBOX` | `https://gw.sandbox.gopay.com/gp-gw/api/4.0/` | Testing and integration |
| `Environment.PRODUCTION` | `https://gate.gopay.com/gp-gw/api/4.0/` | Live transactions |
| `Environment.DEVELOPMENT.create(url)` | Custom — must start with `http://` or `https://` | A gateway of your own |

Both built-in hosts come from the Payments 4.0 spec's `servers` block. Use `DEVELOPMENT` only
when you have been given a gateway that is neither — see
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

Its gateway and merchant values are not in the repository: put them in `local.properties` in the
repo root, then run the app from Android Studio or with `./gradlew :app:installDebug`.

## Security notes

- Neither the `payment_secret` nor the JWT is ever written to disk. Both live in memory inside
  `PaymentSession` and are wiped on `close()`. The SDK no longer reads or writes
  `SharedPreferences`.
- The merchant public key is cached only in memory; clears on process death.
- `PaymentCardForm` enables `FLAG_SECURE` on the host window in non-debug builds to block
  screenshots of card data.
- Card data lives in memory only while the form needs it: the form state is cleared after a
  successful encryption and when the form leaves the composition (PCI DSS 4.0.1, req. 3.3.1).
  It is deliberately kept after a *failed* encryption so the user can retry without retyping.
  `CardData`'s `toString()` is masked, so a stray log never prints the PAN or CVV. JVM strings
  cannot be securely overwritten, so clearing releases the references rather than zeroing bytes.
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
