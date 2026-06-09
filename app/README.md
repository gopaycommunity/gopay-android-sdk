# Gopay SDK Test App

Demo app that exercises every public surface of the GoPay Android SDK from a real device.

## What it covers

- Starting a `PaymentSession` from a `payment_id` + `payment_secret` pair (paste them in from
  your merchant backend's response — the app does not create payments itself).
- Reading payment status, charge state, and QR payment info through the session.
- Fetching the merchant public key with `shareable_key` basic auth.
- Encrypting card data into a JWE via `PaymentCardForm` — the resulting JWE is shown on
  screen so you can paste it into a backend `POST /cards/tokens` call.
- Submitting a card-token charge through the session, including 3DS handling.
- Managed Google Pay flow (`session.chargeWithGooglePay(activity)`).

The app intentionally has no merchant-credentials path. Payment creation, refunds, and
`POST /cards/tokens` all belong on the merchant backend.

## Running

```bash
./gradlew :app:installDebug
adb shell am start -n com.gopay.example/.MainActivity
```

`ExampleApplication` initializes the SDK with the dev gateway and the hardcoded sandbox
`clientId` / `shareableKey` baked into source. Change those to your own values when pointing
at sandbox or production.

## Typical flow in the test screen

1. Tap **SDK Test Suite**.
2. Have your backend create a payment and return its `payment_id` + `payment_secret`.
3. Paste them into the **Payment Session** card and tap **Start Payment Session**.
4. With the session live:
   - Tap **Get Payment Status** to confirm.
   - Either:
     - Use the **Card Form** below to encrypt a card → send the printed JWE to your backend →
       paste the returned card token into **Charge with Card Token** → tap charge.
     - Or use **Charge with Google Pay** on a real device with GP configured.
   - If a 3DS redirect URL comes back on the charge response it auto-populates the
     **Handle 3DS Verification** field — tap the button to open the managed WebView.
   - Tap **Get Charge State** to see the final state.
5. Tap **Close** on the Payment Session card when done — the secret and JWT are wiped.

## Code references

- App entry: [`ExampleApplication.kt`](src/main/java/com/gopay/example/ExampleApplication.kt)
- Test screen: [`SDKTestActivity.kt`](src/main/java/com/gopay/example/SDKTestActivity.kt)
- Checkout demo (existing UI sample): [`CheckoutDemoActivity.kt`](src/main/java/com/gopay/example/CheckoutDemoActivity.kt)

For SDK API details see the top-level [README.md](../README.md).
