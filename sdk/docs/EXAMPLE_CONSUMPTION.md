# GoPay SDK Consumption Examples

This document shows how to consume the published GoPay Android SDK in different scenarios.

## From Maven Local

If you've published the SDK to your local Maven cache:

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation("cz.gopay:sdk:1.0.0")
}
```

## From Local Repository

If you want to consume from the local repository created during publishing:

```kotlin
// In your app's build.gradle.kts
repositories {
    maven {
        url = uri("file:///path/to/gpy-sdk-android/sdk/build/repo")
    }
}

dependencies {
    implementation("cz.gopay:sdk:1.0.0")
}
```

## From Remote Repository

Once you've configured and published to a remote repository:

```kotlin
// In your app's build.gradle.kts
repositories {
    maven {
        url = uri("https://your-maven-repo.com/releases")
    }
}

dependencies {
    implementation("cz.gopay:sdk:1.0.0")
}
```

## Usage in Kotlin/Java Code

```kotlin
// Initialize the SDK
GopaySDK.initialize(
    context = applicationContext,
    config = GopayConfig(
        environment = Environment.SANDBOX,
        merchantId = "your-merchant-id",
        apiKey = "your-api-key"
    )
)

// Use the SDK for payment processing
GopaySDK.processPayment(
    amount = 1000.0,
    currency = "USD",
    orderId = "order-123"
) { result ->
    when (result) {
        is PaymentResult.Success -> {
            // Handle successful payment
            println("Payment successful: ${result.transactionId}")
        }
        is PaymentResult.Error -> {
            // Handle payment error
            println("Payment failed: ${result.error}")
        }
    }
}
```

## Version Management

To use a specific version:

```kotlin
dependencies {
    // Use exact version
    implementation("cz.gopay:sdk:1.0.0")

    // Use version range
    implementation("cz.gopay:sdk:[1.0.0,2.0.0)")

    // Use latest version (not recommended for production)
    implementation("cz.gopay:sdk:1.+")
}
```

## Troubleshooting

### Version Conflicts

If you encounter version conflicts, you can force a specific version:

```kotlin
configurations.all {
    resolutionStrategy {
        force("cz.gopay:sdk:1.0.0")
    }
}
```

### Repository Issues

If you can't resolve the dependency, check:

1. Repository URL is correct
2. Network connectivity
3. Repository credentials (if required)
4. Version exists in the repository

### Build Issues

If the build fails, try:

```bash
# Clean and rebuild
./gradlew clean build

# Refresh dependencies
./gradlew --refresh-dependencies build

# Check dependency tree
./gradlew app:dependencies
```
