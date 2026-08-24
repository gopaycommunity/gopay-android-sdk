# Gopay SDK Test App

Demo app that exercises every public surface of the GoPay Android SDK from a real device or
emulator.

## What it covers

- Simulating the **merchant backend** in-app (`MerchantBackendSimulator`) — creating a payment
  with merchant `client_credentials` to obtain the `payment_id` + `payment_secret` pair, and
  tokenizing a JWE via `POST /cards/tokens`. In a real integration both belong on your server;
  they are in-app here only so the demo is self-contained.
- Starting a `PaymentSession` from that `payment_id` + `payment_secret` pair (you can also paste
  a pair in by hand).
- Reading payment status, charge state, Google Pay info, and QR payment info through the session.
- Encrypting card data into a JWE via `PaymentCardForm`, including live switching of the form
  locale. The resulting JWE is shown on screen.
- Charging via a card token (`chargeWithCardToken`), via the encrypted card directly
  (`chargeWithEncryptedCard`), or via managed Google Pay (`chargeWithGooglePay`) — each with 3DS
  handling.

## Prerequisites

None of these are checked by the build — if one is missing you get a confusing failure, so
verify them first.

| Requirement | Notes |
| --- | --- |
| JDK 17 or newer | Gradle 8.7 + AGP 8.6. JDK 21 works (verified). `java -version` |
| Android SDK Platform 35 | `compileSdk = 35`. Install via Android Studio → SDK Manager. |
| `local.properties` with `sdk.dir` | **Not in git** — you must create it, see below. |
| A device or emulator | `minSdk = 24`. Needed for `installDebug`. |
| **GoPay VPN / internal network** | The demo targets an internal dev gateway, see below. |
| `adb` on `PATH` (optional) | Lives in `$ANDROID_HOME/platform-tools`. |

### 1. Point Gradle at your Android SDK

`local.properties` is gitignored, so a fresh clone has no SDK location and **every Gradle
command fails** with:

```
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment
variable or by setting the sdk.dir path in your project's local properties file
```

Opening the project in Android Studio creates the file for you. From the CLI, create it once:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

On Linux the default path is `$HOME/Android/Sdk`; on Windows
`C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`. Exporting `ANDROID_HOME` works instead.

### 2. Network access to the gateway

`DemoConfig.BASE_URL` points at `https://gw.alpha8.dev.gopay.com/…`, which resolves to a
private `10.26.x.x` address — it is only reachable from inside GoPay's network. **The demo needs
the GoPay VPN and there is no supported way around that**; the dev gateway is deliberately the
target environment, so don't repoint the demo at sandbox to get it running.

Until you have VPN access you can still build, install, launch the app and browse the UI — every
screen renders. What you cannot do is any network call: **the request hangs instead of failing.**
`MerchantBackendSimulator` sets no connect/read timeout on its `HttpURLConnection`, so tapping
e.g. **Create payment on "server"** spins forever with no error (verified: still spinning after
five minutes). If a button seems to hang, check your VPN before debugging anything else.

Note that `AndroidManifest.xml` sets `usesCleartextTraffic="false"`, so a plain `http://` dev
URL is blocked even though `Environment.DEVELOPMENT.create()` accepts one.

## Running

Creating `local.properties` does **not** set `ANDROID_HOME`, and neither `adb` nor `emulator` are
on `PATH` by default. Either export it once (`export ANDROID_HOME=$HOME/Library/Android/sdk`,
plus `$ANDROID_HOME/platform-tools` and `$ANDROID_HOME/emulator` on `PATH`) or use full paths as
below.

```bash
# once per clone — see "Point Gradle at your Android SDK" above
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# start an emulator (or plug in a device) — installDebug otherwise fails with
# "com.android.builder.testing.api.DeviceException: No connected devices!"
~/Library/Android/sdk/emulator/emulator -list-avds
~/Library/Android/sdk/emulator/emulator -avd <avd-name> &
~/Library/Android/sdk/platform-tools/adb wait-for-device

./gradlew :app:installDebug
~/Library/Android/sdk/platform-tools/adb shell am start -n com.gopay.example/.MainActivity
```

The first build downloads the Gradle 8.7 distribution and all dependencies — budget about five
minutes. Subsequent builds take seconds.

Then tap **Open SDK Test Suite** on the main screen.

## Configuration

All demo credentials live in [`DemoConfig.kt`](src/main/java/com/gopay/example/DemoConfig.kt):
base URL, `clientId`, `shareableKey`, `goid`, the 3DS return URL, and `clientSecret`. Replace
them with your own when pointing at sandbox or production.

`CLIENT_SECRET` is a **merchant** secret and exists only to let `MerchantBackendSimulator` fake
your server. Never ship it in a real app.

> **The committed alpha8 credentials go stale.** The dev environment is periodically reset, which
> rotates the client secret and the shareable key. Every call then fails with `401 UNAUTHORIZED —
> Invalid client_id or client_secret` on `/oauth2/token`, which looks like a code bug but isn't.
> Verify with a token request before debugging anything else:
>
> ```bash
> curl -s -X POST "https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/oauth2/token" \
>   -u "<client_id>:<client_secret>" \
>   -d "grant_type=client_credentials&scope=payment:write payment:read card:write card:read"
> ```
>
> A token back means the credentials are current; a `401` means they were rotated. Current values
> are posted in the `#shared-gpy-mobile-sdk` Slack channel.

[`ExampleApplication`](src/main/java/com/gopay/example/ExampleApplication.kt) initializes the
SDK from those values with `Environment.DEVELOPMENT`, `debug = true`, a 5 s request timeout, a
global `errorCallback` that prints to stdout, and a custom demo locale registered under the code
`xx`.

## Typical flow in the test screen

The screen is split into four numbered sections. Sections 3 and 4 only appear once a session is
live.

1. Tap **Open SDK Test Suite** on the main screen.
2. **1. Merchant backend (simulated)** — tap **Create payment on "server"**. This creates a
   1000 CZK payment and fills in the `payment_id` / `payment_secret` fields. (Or paste in a pair
   your own backend produced.)
3. **2. Payment session** — tap **Start session**. Auth happens eagerly, so bad credentials fail
   here. A "Session live: …" row with a **Close** button appears.
4. **3. Operations** —
   - **Get status** to confirm the payment.
   - Then charge it one of three ways:
     - **Get test card token (server)** tokenizes the built-in test card `4444…4448` (12/28) on
       the simulated server, fills the `card_token` field and copies it to the clipboard — then
       tap **Charge a payment**.
     - **Get Google Pay info** to check availability, then **Charge with Google Pay** (real
       device with Google Pay configured).
     - Or use section 4 below to charge an encrypted card with no tokenization round-trip.
   - If the charge response carries a 3DS redirect URL, it is stored automatically and
     **Handle 3DS verification** becomes enabled — tap it to open the managed WebView.
   - **Get charge state** to read the final state.
   - **Get QR payment info** for the bank-transfer/QR variant.
5. **4. Card form → JWE** — pick a form language from the **Locale** dropdown (or "System
   default"), fill in the card, and tap **Encrypt card → JWE**. The JWE lands in the field
   below; tap **Charge with encrypted card (JWE)** to charge it directly.
6. Tap **Close** in section 2 when done — the secret and JWT are wiped.

Every step logs its result (or a structured `GopaySDKException`) into the **Response** card at
the bottom.

## Code references

- App entry: [`ExampleApplication.kt`](src/main/java/com/gopay/example/ExampleApplication.kt)
- Demo credentials: [`DemoConfig.kt`](src/main/java/com/gopay/example/DemoConfig.kt)
- Simulated backend: [`MerchantBackendSimulator.kt`](src/main/java/com/gopay/example/MerchantBackendSimulator.kt)
- Main menu: [`MainActivity.kt`](src/main/java/com/gopay/example/MainActivity.kt)
- Test screen: [`SDKTestActivity.kt`](src/main/java/com/gopay/example/SDKTestActivity.kt)
- Checkout demo (UI sample, not wired into the menu): [`CheckoutDemoActivity.kt`](src/main/java/com/gopay/example/CheckoutDemoActivity.kt)

For SDK API details see the top-level [README.md](../README.md).
