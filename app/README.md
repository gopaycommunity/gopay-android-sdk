# Gopay SDK Test App

Demo app that exercises every public surface of the GoPay Android SDK from a real device or
emulator.

## What it covers

- Simulating the **merchant backend** in-app (`MerchantBackendSimulator`): creating a payment with
  merchant `client_credentials` to get the `payment_id` + `payment_secret` pair, and tokenizing a
  JWE via `POST /cards/tokens`. In a real integration both belong on your server.
- Starting a `PaymentSession` from that pair, or from a pair you paste in.
- Reading payment status, charge state, Google Pay info and QR payment info through the session.
- Encrypting card data into a JWE via `PaymentCardForm`, including live form-locale switching.
- Charging via a card token (`chargeWithCardToken`), the encrypted card directly
  (`chargeWithEncryptedCard`), or managed Google Pay (`chargeWithGooglePay`), each with 3DS
  handling.

## Prerequisites

- JDK 17 or newer (Gradle 8.7, AGP 8.6)
- Android SDK Platform 35 (`compileSdk = 35`)
- A device or emulator, `minSdk = 24`
- `local.properties` in the repo root with `sdk.dir` and the demo keys below

## Running

Open the repo in Android Studio, fill in `local.properties`, and run the `app` configuration.

From the CLI:

```bash
./gradlew :app:installDebug
```

## Configuration

The demo reads these from `local.properties` in the repo root (gitignored). A `-P` project
property of the same name takes precedence. Missing keys are empty and the build still succeeds.

```properties
gopay.demo.baseUrl=
gopay.demo.clientId=
gopay.demo.shareableKey=
gopay.demo.clientSecret=
gopay.demo.goid=
```

Leave `gopay.demo.baseUrl` empty to use the SDK's sandbox host. The sandbox or production gateway
URL starts the badge on **Sandbox** or **Production**; any other URL makes **Development** the
initial selection and adds it to the badge. A value runs to the end of the line, so no trailing
`# comment`.

Gateway hosts: sandbox `https://gw.sandbox.gopay.com/gp-gw/api/4.0/`, production
`https://gate.gopay.com/gp-gw/api/4.0/`.

Switching environments from the badge closes any live session and re-initializes the SDK; the
choice isn't persisted.

`gopay.demo.clientSecret` is a merchant secret, used only by the simulated backend. A real app
never carries one.

## Typical flow in the test screen

The screen is split into four numbered sections. Sections 3 and 4 appear once a session is live.

1. Tap **Developer sandbox** on the main screen.
2. **1. Merchant backend (simulated)** — tap **Create payment on "server"**. Creates a 1 CZK
   payment (amount `100`, in minor units) and fills in the `payment_id` / `payment_secret` fields.
3. **2. Payment session** — tap **Start session**. Auth happens eagerly, so bad credentials fail
   here. A "Session live: …" row with a **Close** button appears.
4. **3. Operations** —
   - **Get status** to confirm the payment.
   - Then charge it one of three ways:
     - **Get test card token (server)** tokenizes the built-in test card `4444…4448` (12/28),
       fills the `card_token` field and copies it to the clipboard, then **Charge a payment**.
     - **Get Google Pay info**, then **Charge with Google Pay** (real device with Google Pay
       configured).
     - Or section 4 below, to charge an encrypted card with no tokenization round-trip.
   - A 3DS redirect URL in the charge response enables **Handle 3DS verification**, which opens
     the managed WebView.
   - **Get charge state** to read the final state.
   - **Get QR payment info** for the bank-transfer/QR variant.
5. **4. Card form → JWE** — pick a form language from the **Locale** dropdown, fill in the card,
   and tap **Encrypt card → JWE**. Then **Charge with encrypted card (JWE)**.
6. Tap **Close** in section 2 when done.

Every step logs its result, or a structured `GopaySDKException`, into the **Response** card.

## Code references

- App entry: [`ExampleApplication.kt`](src/main/java/com/gopay/example/ExampleApplication.kt)
- Demo configuration: [`DemoConfig.kt`](src/main/java/com/gopay/example/DemoConfig.kt)
- Simulated backend: [`MerchantBackendSimulator.kt`](src/main/java/com/gopay/example/MerchantBackendSimulator.kt)
- Main menu: [`MainActivity.kt`](src/main/java/com/gopay/example/MainActivity.kt)
- Test screen: [`SDKTestActivity.kt`](src/main/java/com/gopay/example/SDKTestActivity.kt)
- Checkout demo: [`CheckoutDemoActivity.kt`](src/main/java/com/gopay/example/CheckoutDemoActivity.kt)

For SDK API details see the top-level [README.md](../README.md).
