# GoPay Android SDK Publishing Guide

This document explains how to publish the GoPay Android SDK using the Maven Publish Plugin.

## Overview

The SDK is configured to publish to Maven repositories using the `maven-publish` plugin. The publishing configuration is separated into `publish.gradle.kts` for better organization and reusability.

## Configuration

### Version Management

The SDK version is managed through properties in `gradle.properties`:

```properties
sdk.version=1.0.0
sdk.groupId=cz.gopay
sdk.artifactId=sdk
```

### Publishing Metadata

The SDK publishes with the following metadata:

- **Group ID**: `cz.gopay`
- **Artifact ID**: `sdk`
- **Version**: Configurable via `sdk.version` property
- **Description**: Android SDK for GoPay payment integration
- **License**: MIT License
- **Repository**: https://github.com/gopay/gpy-sdk-android

## Available Tasks

### Publishing Tasks

```bash
# Publish to local repository (build/repo)
./gradlew :sdk:publishToLocalRepo

# Publish to Maven local cache (~/.m2/repository)
./gradlew :sdk:publishToMavenLocal

# Publish to all configured repositories
./gradlew :sdk:publish

# Show publishing configuration
./gradlew :sdk:showPublishingInfo
```

### Build Tasks

```bash
# Build the SDK
./gradlew :sdk:assembleRelease

# Run tests
./gradlew :sdk:test

# Generate code coverage report
./gradlew :sdk:jacocoTestReport
```

## Publishing Workflow

### 1. Update Version

Before publishing, update the version in `gradle.properties`:

```properties
sdk.version=1.0.1
```

### 2. Build and Test

```bash
./gradlew :sdk:clean
./gradlew :sdk:test
./gradlew :sdk:assembleRelease
```

### 3. Publish Locally (for testing)

```bash
./gradlew :sdk:publishToLocalRepo
```

This creates a local Maven repository at `sdk/build/repo/`.

### 4. Publish to Maven Local

```bash
./gradlew :sdk:publishToMavenLocal
```

This publishes to your local Maven cache at `~/.m2/repository/`.

### 5. Publish to Remote Repository

To publish to a remote repository, configure the repository in `publish.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "releaseRepo"
        url = uri("https://your-maven-repo.com/releases")
        credentials {
            username = project.findProperty("mavenUsername") as String? ?: ""
            password = project.findProperty("mavenPassword") as String? ?: ""
        }
    }
}
```

Then set your credentials in `gradle.properties`:

```properties
mavenUsername=your-username
mavenPassword=your-password
```

And publish:

```bash
./gradlew :sdk:publish
```

## Consuming the Published SDK

### From Maven Local

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation("cz.gopay:sdk:1.0.0")
}
```

### From Local Repository

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

### From Remote Repository

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

## Generated Artifacts

The publishing process generates the following artifacts:

- **AAR file**: The main Android library archive
- **POM file**: Maven project object model with dependencies
- **Module metadata**: Gradle module metadata for better dependency resolution
- **Checksums**: MD5, SHA1, SHA256, and SHA512 checksums for all artifacts

## Troubleshooting

### Common Issues

1. **Version conflicts**: Ensure the version in `gradle.properties` is updated before publishing
2. **Authentication errors**: Verify your Maven repository credentials
3. **Network issues**: Check your internet connection and repository URLs
4. **Build failures**: Run `./gradlew :sdk:clean` before rebuilding

### Debugging

Use the `showPublishingInfo` task to verify your configuration:

```bash
./gradlew :sdk:showPublishingInfo
```

### Logs

Enable debug logging for more detailed output:

```bash
./gradlew :sdk:publish --debug
```

## Best Practices

1. **Version Management**: Always update the version before publishing
2. **Testing**: Test locally before publishing to remote repositories
3. **Documentation**: Update this guide when changing publishing configuration
4. **Security**: Never commit credentials to version control
5. **Backup**: Keep backups of published artifacts

## Support

For issues related to publishing, check:

1. Gradle documentation: https://docs.gradle.org/current/userguide/publishing_maven.html
2. Android documentation: https://developer.android.com/studio/build/maven-publish-plugin
3. Project issues: https://github.com/gopay/gpy-sdk-android/issues
