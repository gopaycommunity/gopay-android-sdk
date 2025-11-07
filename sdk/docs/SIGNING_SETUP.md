# PGP Signing Setup for GoPay Android SDK

This document explains how to configure PGP signing for the GoPay Android SDK to enable publishing to Maven repositories that require signed artifacts.

## Overview

The SDK is now configured with the Gradle Signing Plugin, which automatically signs your Maven publications when publishing. Signing is only required for release versions (non-SNAPSHOT) and when the `publish` task is executed.

## Prerequisites

1. **GnuPG installed** on your system
2. **PGP key pair** for signing artifacts
3. **Access to your organization's PGP key** (if using a shared key)

## Setup Options

### Option 1: Using GnuPG Agent (Recommended)

This is the most secure approach as it uses the GnuPG agent for passphrase management.

1. **Configure your GnuPG key**:

   ```bash
   # List your keys
   gpg --list-secret-keys

   # If you need to create a new key
   gpg --full-generate-key
   ```

2. **Add to your local `gradle.properties`** (not committed to git):

   ```properties
   signing.gnupg.keyName=YOUR_KEY_NAME_OR_EMAIL
   signing.gnupg.passphrase=YOUR_PASSPHRASE
   ```

3. **Alternative: Use gpg-agent** (no passphrase in properties):
   ```bash
   # Start gpg-agent and cache your passphrase
   gpg-agent --daemon
   echo "YOUR_PASSPHRASE" | gpg --batch --yes --passphrase-fd 0 --sign --local-user YOUR_KEY_NAME_OR_EMAIL /dev/null
   ```

### Option 2: Using Secret Key Ring File

If your organization provides a secret key ring file:

1. **Add to your local `gradle.properties`**:
   ```properties
   signing.keyId=YOUR_KEY_ID
   signing.password=YOUR_PASSPHRASE
   signing.secretKeyRingFile=/path/to/your/secring.gpg
   ```

## Configuration Details

### Key Properties

- **`signing.gnupg.keyName`**: Your PGP key name or email address
- **`signing.gnupg.passphrase`**: Your PGP key passphrase
- **`signing.keyId`**: The last 8 characters of your key ID (use `gpg -K` to find it)
- **`signing.secretKeyRingFile`**: Path to your secret key ring file

### Environment Variables

You can also set these as environment variables:

```bash
export SIGNING_KEY_ID=YOUR_KEY_ID
export SIGNING_PASSWORD=YOUR_PASSPHRASE
export SIGNING_SECRET_KEY_RING_FILE=/path/to/your/secring.gpg
```

## Testing the Setup

1. **Test signing locally**:

   ```bash
   ./gradlew :sdk:signReleasePublication
   ```

2. **Test publishing with signing**:

   ```bash
   ./gradlew :sdk:publishReleasePublicationToLocalRepoRepository
   ```

3. **Check generated signatures**:
   Look in `sdk/build/libs/` for `.asc` signature files alongside your artifacts.

## Troubleshooting

### Common Issues

1. **"No secret key" error**:

   - Ensure your key is available: `gpg --list-secret-keys`
   - Check the key name/ID in your configuration

2. **"Bad passphrase" error**:

   - Verify your passphrase is correct
   - Try using gpg-agent instead of storing passphrase in properties

3. **"gpg: signing failed"**:
   - Check if gpg-agent is running: `gpg-agent --version`
   - Restart gpg-agent if needed: `gpgconf --kill gpg-agent`

### Debugging

Enable debug output:

```bash
./gradlew :sdk:signReleasePublication --debug
```

## Security Best Practices

1. **Never commit signing credentials** to version control
2. **Use gpg-agent** instead of storing passphrases in properties files
3. **Use environment variables** for CI/CD pipelines
4. **Rotate keys regularly** according to your organization's policy
5. **Backup your private key** securely

## CI/CD Integration

For automated builds, configure signing credentials as secrets:

```yaml
# GitHub Actions example
env:
  SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
  SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
  SIGNING_SECRET_KEY_RING_FILE: ${{ secrets.SIGNING_SECRET_KEY_RING_FILE }}
```

## Verification

After publishing, verify signatures:

```bash
# Download and verify your published artifact
gpg --verify artifact-1.0.0.aar.asc artifact-1.0.0.aar
```

## Support

If you encounter issues:

1. Check the [Gradle Signing Plugin documentation](https://docs.gradle.org/current/userguide/signing_plugin.html)
2. Consult your organization's PGP key management policy
3. Contact your DevOps team for key provisioning
