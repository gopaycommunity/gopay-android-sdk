# Environment Configuration

The Gopay SDK supports multiple environments for different stages of development and deployment.

## Available Environments

### Production Environment

Use for live transactions in production.

```kotlin
val config = GopayConfig(
    environment = Environment.PRODUCTION
)
```

### Sandbox Environment

Use for testing and integration with GoPay's sandbox environment.

```kotlin
val config = GopayConfig(
    environment = Environment.SANDBOX
)
```

### Development Environment

Use for local development with a custom endpoint URL.

```kotlin
// With custom development URL
// — a gateway you run yourself; note that on an emulator "localhost" is the emulator,
//   so use 10.0.2.2 to reach a server on your own machine
val config = GopayConfig(
    environment = Environment.DEVELOPMENT.create("https://localhost:8080")
)

// Or with HTTPS
// — same call, just a remote dev/staging host instead of a local one. Both examples are
//   https:// because that is what you will almost always want; see the cleartext note below
val config = GopayConfig(
    environment = Environment.DEVELOPMENT.create("https://dev-api.mycompany.com")
)
```

`create()` accepts a plain `http://` URL, but Android blocks cleartext traffic by default (and
the bundled demo app sets `usesCleartextTraffic="false"` explicitly). For an `http://` dev
endpoint you also need a network-security config permitting cleartext for that host.

## Environment URLs

| Environment | Base URL                                      | Use for                 |
| ----------- | --------------------------------------------- | ----------------------- |
| Sandbox     | `https://gw.sandbox.gopay.com/gp-gw/api/4.0/` | Testing and integration |
| Production  | `https://gate.gopay.com/gp-gw/api/4.0/`       | Live transactions       |
| Development | Custom (user-defined)                         | A gateway of your own   |

Both built-in hosts come from the Payments 4.0 spec's `servers` block, so `SANDBOX` and
`PRODUCTION` need no URL from you. Reach for
`Environment.DEVELOPMENT.create(<gateway-url>)` only when you have been given a gateway that is
neither — that is what the bundled demo app does.

The SDK's operational data goes to `https://lx.sandbox.gopay.com` from `SANDBOX` and to
`https://lx.gopay.com` from `PRODUCTION`; a `DEVELOPMENT` gateway sends none. See "Operational
data" in the README.

## Development Environment Requirements

When using `Environment.DEVELOPMENT.create(customUrl)`:

1. **URL must not be empty** - Will throw `GopaySDKException` with `CONFIG_INVALID_BASE_URL` (`CONFIG_006`)
2. **URL must start with `http://` or `https://`** - Will throw `GopaySDKException` with `CONFIG_INVALID_BASE_URL`
3. **Trailing slash is automatically added** - If your URL doesn't end with `/`, one will be added automatically

## Example Usage

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = GopayConfig(
            environment = when (BuildConfig.DEBUG) {
                true -> Environment.DEVELOPMENT.create("https://localhost:8080")
                false -> Environment.PRODUCTION
            },
            // `debug` — GopayConfig has no `debugLoggingEnabled` field, that name will not compile
            debug = BuildConfig.DEBUG,
            requestTimeoutMs = 30000
        )

        GopaySDK.initialize(config)
    }
}
```

The full set of things you can pass to `GopayConfig` is `environment` (the only required one),
`clientId`, `shareableKey`, `requestTimeoutMs` (default 30000), `debug`, `errorCallback`,
`locale`, and `customLocales`. `apiBaseUrl` is also exposed but is derived from `environment`,
not set by you — see [`GopayConfig.kt`](sdk/src/main/java/cz/gopay/sdk/config/GopayConfig.kt).

## Error Handling

The development environment validates the provided URL and throws `GopaySDKException` with the
shared code `CONFIG_INVALID_BASE_URL` (`CONFIG_006`), the code the iOS SDK reports for a base URL
it cannot use, for:

- Empty URLs
- URLs that don't start with `http://` or `https://`

```kotlin
// Each of these throws GopaySDKException with errorCode CONFIG_INVALID_BASE_URL
Environment.DEVELOPMENT.create("")
Environment.DEVELOPMENT.create("invalid-url")
Environment.DEVELOPMENT.create("ftp://localhost:8080")
```

The constructor checks the scheme only. A URL that passes it but that the HTTP client refuses,
say one with a space in the host, fails with the same code from `GopaySDK.initialize(config)`,
with the client's `IllegalArgumentException` as the `cause`; the SDK stays as an earlier
`initialize` left it (uninitialized if there was none).
