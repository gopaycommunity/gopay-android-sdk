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

`DemoConfig.DEVELOPMENT_BASE_URL` points at `https://gw.alpha8.dev.gopay.com/…`, which resolves to a
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

That last `am start` runs against the compiled-in placeholder credentials, which reach no
gateway. To run against a real one, use `./scripts/run-demo.sh --install` instead —
see [Credentials: `.env`](#credentials-env) below.

Then tap **Open SDK Test Suite** on the main screen.

## Configuration

### Credentials: `.env`

Credentials are **not** in the repository. Copy `.env.example` in the repo root to `.env`, fill in
your GoPay values, and launch through the runner:

```bash
cp .env.example .env
./scripts/run-demo.sh --install
```

`.env` holds one gateway at a time — five keys, no profiles; point the demo somewhere else by
editing the values. It is gitignored, and the iOS demo app reads the same key names, so one set
of values drives both platforms. `run-demo.sh` hands them to the app as intent extras, the one
channel that does not bake a secret into the APK. `--install` runs `:app:installDebug` first, and
`ANDROID_SERIAL` picks a device when more than one is attached.

Every key is optional; one you leave empty is reported and the app keeps its compiled-in default
for it. A value runs to the end of its line, so `KEY=value # note` puts ` # note` *inside* the
credential and you get an opaque 401 — put comments on their own line. The base URL must be
`https://`.

Because the values travel as launch overrides they land in the **Development** slot, so the
environment badge reads "Development" whatever gateway you set. The base URL the runner prints,
and the host on the badge, is what the app is actually talking to.

### The compiled-in defaults

[`DemoConfig.kt`](src/main/java/com/gopay/example/DemoConfig.kt) holds the fallbacks a plain
launch uses — the dev base URL, placeholder `clientId` / `shareableKey` / `clientSecret` /
`goid`, and the 3DS return URL. They are placeholders on purpose; use `.env` rather than editing
them.

`CLIENT_SECRET` is a **merchant** secret and exists only to let `MerchantBackendSimulator` fake
your server. Never ship it in a real app.

> **The alpha8 credentials go stale.** The dev environment is periodically reset, which rotates
> the client secret and the shareable key. Every call then fails with `401 UNAUTHORIZED —
> Invalid client_id or client_secret` on `/oauth2/token`, which looks like a code bug but isn't.
> Verify with a token request before debugging anything else:
>
> ```bash
> curl -s -X POST "https://gw.alpha8.dev.gopay.com/gp-gw/api/4.0/oauth2/token" \
>   -u "<client_id>:<client_secret>" \
>   -d "grant_type=client_credentials&scope=payment:write payment:read card:write card:read"
> ```
>
> A token back means the credentials are current; a `401` means they were rotated — put the new
> ones in your `.env`.

[`ExampleApplication`](src/main/java/com/gopay/example/ExampleApplication.kt) initializes the
SDK from those values with `Environment.DEVELOPMENT`, `debug = true`, a 5 s request timeout, a
global `errorCallback` that prints to stdout, and a custom demo locale registered under the code
`xx`.

### Pointing the demo at another gateway

You do not have to edit `DemoConfig.kt` to run against a different environment. Two channels feed
the same setting; a launch-time extra wins over a build-time property, which wins over the
compiled-in constant.

**At launch, no rebuild** — pass extras to `MainActivity`. `scripts/run-demo.sh` above is a
wrapper around exactly this; reach for the raw command when you have a one-off gateway URL and
credentials in hand:

```bash
adb shell am start -S -n com.gopay.example/.MainActivity \
  -e GOPAY_DEMO_BASE_URL https://gw.example.dev.gopay.com/gp-gw/api/4.0/ \
  -e GOPAY_DEMO_CLIENT_ID SDK \
  -e GOPAY_DEMO_SHAREABLE_KEY sk_… \
  -e GOPAY_DEMO_CLIENT_SECRET cs_… \
  -e GOPAY_DEMO_GOID 8761908826
```

**Keep the `-S`.** It force-stops the app first so the extras land on a cold start. Without it,
`am start` on an app that is already running just brings its task to the front, the extras are
never delivered, and you silently keep the previous gateway. A cold start is what you want here
regardless — switching gateways with a payment session still open would be worse.

Every extra is optional — omit the ones you want left at their defaults, and a plain
`am start -n com.gopay.example/.MainActivity` behaves exactly as before. Supplying any of them
also selects the **Development** environment, since that is the only one a custom host describes.
The environment badge on the main screen shows the host actually in use, so glance at it to
confirm the override took — and check logcat if it did not.

Overrides apply on launch only. Rotating the device does not re-apply them, so an environment you
picked by hand from the badge afterwards survives a rotation. A restore after Android kills the
backgrounded process *does* re-apply them — the app is starting from scratch there, so the
alternative would be silently landing back on the default gateway. That also means a hand-picked
Sandbox selection is replaced by the launch override after a background kill.

**At build time** — for a repeatable build (CI, a shared install) set the base URL as a Gradle
property:

```bash
./gradlew :app:installDebug -Pgopay.demo.baseUrl=https://gw.example.dev.gopay.com/gp-gw/api/4.0/
```

Credentials deliberately have no Gradle property: it would compile them into the APK, which is
the thing you are trying to avoid. Pass those as intent extras.

Both channels validate the URL twice: the SDK requires an `http://` / `https://` scheme and adds
the trailing slash, then OkHttp's own parser — the one Retrofit will be handed — has to accept
the result. That second pass is what catches `https://` on its own, a stray space, a port like
`:99999`, or a query string, all of which would otherwise take the app down inside
`GopaySDK.initialize`. Hosts with an underscore or non-ASCII characters are fine.

It also rejects `http://`, which the SDK itself allows. This app's manifest sets
`usesCleartextTraffic="false"`, so a cleartext gateway dies much later with a
`CleartextNotPermittedException` that mentions neither the override nor the manifest.

**A refused URL is refused in full.** The gateway is left alone *and* every credential from the
same launch is dropped, because applying them would send real secrets to whichever gateway the
build was compiled against — one typo away from exactly the environment mixing the rest of this
setup avoids. The rejection is logged as `⛔️ LAUNCH OVERRIDES IGNORED IN FULL …` for an intent
extra, `⚠️ Ignoring gopay.demo.baseUrl …` for the Gradle property. An extra you leave out, or
pass empty, simply keeps its default; that is not an error, and a launch with no extras at all
logs nothing.

`MainActivity` is an exported activity, so any app on the device could start it with these
extras, and a `GOPAY_DEMO_CLIENT_SECRET` on an `am start` command line is visible in logcat. Both are fine
for a demo app aimed at a test gateway and neither is a pattern to copy into a real one.

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
