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
val config = GopayConfig(
    environment = Environment.DEVELOPMENT.create("https://localhost:8080")
)

// Or with HTTPS
val config = GopayConfig(
    environment = Environment.DEVELOPMENT.create("https://dev-api.mycompany.com")
)
```

## Environment URLs

| Environment | Base URL                            |
| ----------- | ----------------------------------- |
| Production  | `https://api.gopay.com/v1/`         |
| Sandbox     | `https://api.sandbox.gopay.com/v1/` |
| Development | Custom (user-defined)               |

## Development Environment Requirements

When using `Environment.DEVELOPMENT.create(customUrl)`:

1. **URL must not be empty** - Will throw `IllegalArgumentException`
2. **URL must start with `http://` or `https://`** - Will throw `IllegalArgumentException`
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
            debugLoggingEnabled = BuildConfig.DEBUG,
            requestTimeoutMs = 30000
        )

        GopaySDK.initialize(config)
    }
}
```

## Error Handling

The development environment will validate the provided URL and throw `IllegalArgumentException` for:

- Empty URLs
- URLs that don't start with `http://` or `https://`
- Invalid URL formats

```kotlin
// This will throw IllegalArgumentException
Environment.DEVELOPMENT.create("")

// This will throw IllegalArgumentException
Environment.DEVELOPMENT.create("invalid-url")

// This will throw IllegalArgumentException
Environment.DEVELOPMENT.create("ftp://localhost:8080")
```
