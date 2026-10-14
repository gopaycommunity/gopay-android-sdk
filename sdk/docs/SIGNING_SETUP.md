# Signing setup

Maven Central requires a PGP signature (`.asc`) next to every published artifact. The SDK signs
with the Gradle Signing Plugin from an in-memory key: the key never lives in the repository or in
a keyring file the build points at.

## How the build signs

`sdk/build.gradle.kts` reads three Gradle properties:

| Property | Content |
|---|---|
| `signingInMemoryKey` | ASCII-armored secret key, the whole block including the BEGIN and END lines |
| `signingInMemoryKeyId` | short key id, 8 hex characters; optional, only needed when a subkey signs |
| `signingInMemoryKeyPassword` | passphrase of the key |

When `signingInMemoryKey` is set, signing is required: a wrong passphrase or key id fails the
build. When it is not set, the Sign tasks are skipped and the artifacts are unsigned, which is
fine for local publishing and never accepted by Maven Central.

In CI the properties come from the `MVN_SIGNING_KEY`, `MVN_SIGNING_KEY_ID` and `MVN_KEY_PASSWORD`
secrets of the `maven-central` environment; see [PUBLISHING.md](PUBLISHING.md).

## Creating the key

Use a key that exists only for the SDK. The name and e-mail on it are visible to anyone who
verifies a signature. The command asks for a passphrase, which becomes `MVN_KEY_PASSWORD`.

```bash
gpg --quick-generate-key "GoPay Android SDK <e-mail>" rsa4096 sign never
```

The key id is the 8 characters after `rsa4096/` on the `sec` line; the commands below use it as
`<KEYID>`. `MVN_SIGNING_KEY_ID` is optional and stays empty for a key made with the command above.
Only when a subkey signs does it hold the 8 characters after `rsa4096/` on that subkey's `ssb`
line. The build does not accept the 16-character long form.

```bash
gpg --list-secret-keys --keyid-format short
```

Maven Central checks signatures against public keyservers, so publish the public key right away;
it can take a while to spread.

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
gpg --keyserver keys.openpgp.org --send-keys <KEYID>
gpg --keyserver pgp.mit.edu --send-keys <KEYID>
```

Export the secret key. The whole output, including the BEGIN and END lines, is `MVN_SIGNING_KEY`;
paste it into the secret as it is.

```bash
out="$(mktemp -d)/signing-key.asc"
gpg --export-secret-keys --armor <KEYID> > "$out"
echo "$out"
```

Delete that file once the secret is stored, and keep the key itself somewhere safe: it is needed
to extend or replace it.

## Signing locally

Pass the key through environment variables for one command, so it never lands in a properties
file:

```bash
ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --export-secret-keys --armor <KEYID>)" \
ORG_GRADLE_PROJECT_signingInMemoryKeyPassword='<passphrase>' \
./gradlew :sdk:publishToLocalRepo
```

Every artifact in `sdk/build/repo/cz/gopay/sdk/<version>/` (aar, pom, module, sources and javadoc
jars) then has an `.asc` next to it. Check one:

```bash
gpg --verify sdk/build/repo/cz/gopay/sdk/<version>/sdk-<version>.aar.asc \
             sdk/build/repo/cz/gopay/sdk/<version>/sdk-<version>.aar
```

## Replacing the key

1. Create and publish a new key as above.
2. Replace `MVN_SIGNING_KEY` and `MVN_KEY_PASSWORD` in the `maven-central` environment, and
   `MVN_SIGNING_KEY_ID` only when a subkey signs.
3. Keep the old public key on the keyservers, so signatures of versions already published still
   verify. Revoke it only if the secret part was exposed.
