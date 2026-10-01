# Publishing the GoPay Android SDK

The SDK is published to Maven Central as `cz.gopay:sdk`. Releases are cut in the upstream
repository and published from its public GitHub mirror,
[`gopaycommunity/gopay-android-sdk`](https://github.com/gopaycommunity/gopay-android-sdk), by the
workflow in `.github/workflows/publish-maven-central.yml`.

## How a release reaches Maven Central

1. Changes land on `master` with conventional commit messages.
2. Someone starts the manual `release` step of the `master` pipeline (`bitbucket-pipelines.yml`),
   from the pipeline of the newest commit. semantic-release picks the next version from the
   commits since the last tag.
3. `scripts/set-version.sh` writes that version into `sdk.version` in `sdk/gradle.properties`.
   The release commit and the tag `X.Y.Z` carry it.
4. The pipeline syncs the release commit and the new tag to the GitHub mirror.
5. The tag starts **Publish to Maven Central** on GitHub. The workflow checks that `sdk.version`
   in the tagged commit equals the tag, runs the unit tests, builds and signs the deployment,
   checks that every artifact has a signature, and uploads it to the Central Portal with publishing
   type `AUTOMATIC`. The Portal validates the deployment and then publishes it.

A published version is permanent: Maven Central never lets the same version be uploaded again.

## GitHub setup

The workflow needs a GitHub environment named `maven-central` on the mirror, with:

- **Deployment branches and tags**: the branch `master` and tags matching `[0-9]*.[0-9]*.[0-9]*`.
  Tags are what releases publish from; `master` is only needed for trial uploads.
- **Environment secrets**:

| Secret | Content | Gradle property it becomes |
|---|---|---|
| `CENTRAL_PORTAL_USERNAME` | username part of a Central Portal user token | `mavenCentralUsername` |
| `CENTRAL_PORTAL_PASSWORD` | password part of that token | `mavenCentralPassword` |
| `SIGNING_KEY` | ASCII-armored secret key, the whole block including the BEGIN and END lines | `signingInMemoryKey` |
| `SIGNING_KEY_ID` | short key id, 8 hex characters; optional, only needed when a subkey signs | `signingInMemoryKeyId` |
| `SIGNING_KEY_PASSWORD` | passphrase of the key | `signingInMemoryKeyPassword` |

The workflow passes them to Gradle as `ORG_GRADLE_PROJECT_<property>` variables, and only to the
steps that sign and upload. How to create the key is in [SIGNING_SETUP.md](SIGNING_SETUP.md).
The Portal user token has an expiry date; replace both token secrets before it runs out.

The workflow file has to be on `master` before the first release tag, otherwise the tag has
nothing to start.

## Running the workflow by hand

**Actions → Publish to Maven Central → Run workflow**, on `master`. Inputs:

- `tag`: an existing release tag, for example after a failed run.
- `publishing-type`: `AUTOMATIC` publishes once the Portal has validated the deployment.
  `USER_MANAGED` stops at `VALIDATED` and waits for someone to press **Publish** or **Drop** in the
  Portal.
- `trial`: see below. With `trial` checked, `tag` and `publishing-type` are ignored.

GitHub sends no event when more than three tags are pushed at once. If a sync ever catches up
several releases in one push, publish the missing tags by hand.

## Trial upload

A trial checks the whole chain (secrets, signing, upload and Portal validation) without
publishing anything and without using a release number. Use it after the secrets are set up and
before the first release.

1. **Run workflow** on `master` with `trial` checked.
2. The run builds `master` as version `0.0.0-trial.<run number>`, signs it, checks the signatures
   and uploads it as `USER_MANAGED`. A green run means the Portal validated the deployment.
3. Sign in to the [Central Portal](https://central.sonatype.com/publishing/deployments) with the
   account that owns the `cz.gopay` namespace and open the deployment named after that version.
   Check that it lists the aar, pom, module, sources and javadoc jars, each with an `.asc`.
4. Press **Drop**. Do not press **Publish**.

A trial skips the version check that releases go through. Existing tags predate this build setup,
so a trial builds master instead. It stays safe because:

- it always uploads as `USER_MANAGED`, whatever `publishing-type` says, so nothing goes live
  without a click in the Portal;
- the version is always `0.0.0-trial.<n>`, which no release uses, so even an accidental
  **Publish** could not take a release number;
- it runs from `master` only, and the `maven-central` environment admits no other branch;
- only people with write access to the mirror can run a workflow by hand.

A dropped deployment leaves nothing behind on Maven Central.

## When a run fails

A failed or cancelled run opens an issue labelled `ci-failure`, titled
`CI: Maven Central publish failed for <version>`, or comments on the open one. Close it by hand
once the version is published. An issue from a failed trial is closed by hand too.

- **Failed before the upload** (version check, tests, signing): nothing was sent. Fix the cause
  and run the workflow by hand with the tag. If the tagged commit itself is wrong, the fix needs
  a new release.
- **Failed in validation**: nothing was published. The Portal shows the reason on the deployment;
  drop it there and run the workflow again.
- **Upload succeeded but the run failed afterwards**: check the deployment in the Portal before
  running again. If it is published, a second upload of the same version is refused, which is
  expected.

## Publishing locally

Local publishing needs no credentials. Without a signing key the Sign tasks are skipped and the
artifacts are unsigned.

```bash
./gradlew :sdk:publishToLocalRepo    # -> sdk/build/repo
./gradlew :sdk:publishToMavenLocal   # -> ~/.m2/repository
./gradlew nmcpZipAggregation         # -> build/nmcp/zip/aggregation.zip, what CI uploads
./gradlew :sdk:showPublishingInfo    # prints group, artifact and version
```

The coordinates come from `sdk.groupId`, `sdk.artifactId` and `sdk.version` in
`sdk/gradle.properties`. Pass `-Psdk.version=<x>` to build another version locally. To sign
locally, see [SIGNING_SETUP.md](SIGNING_SETUP.md).

Never run `publishAggregationToCentralPortal` from a workstation. Uploads belong to the workflow.
