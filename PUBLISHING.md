# Publishing expr4k to Maven Central

The build is configured to publish to **Maven Central via the Sonatype Central Portal** using the
[`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/) plugin.
The configuration lives in [`build.gradle.kts`](build.gradle.kts) (`mavenPublishing { … }`). The
publish itself is a manual, credentialed step — run by the maintainer, not by CI.

## One-time setup

1. **Sonatype Central account** — sign up at <https://central.sonatype.com/>.
2. **Verify the namespace** — register and verify `com.digitalbluebird` as a namespace (a DNS TXT
   record on `digitalbluebird.com` proves ownership). Central Portal will not accept artifacts under
   an unverified namespace.
3. **Generate a user token** — in the Central Portal account settings, generate a token; it gives a
   username and password used below (not the login password).
4. **GPG signing key** — create a key (`gpg --gen-key`), publish the public half to a keyserver
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`), and note the key id and passphrase.
   Central Portal requires every artifact to be signed.

## Credentials

Put these in `~/.gradle/gradle.properties` (never in the repo):

```properties
mavenCentralUsername=<central-portal-token-username>
mavenCentralPassword=<central-portal-token-password>

signing.keyId=<last 8 chars of the GPG key id>
signing.password=<GPG key passphrase>
signing.secretKeyRingFile=/Users/<you>/.gnupg/secring.gpg
# (or use the in-memory form: signingInMemoryKey / signingInMemoryKeyPassword)
```

## Publishing a release

Central Portal accepts **releases only**, so first bump the version off `-SNAPSHOT` in
`build.gradle.kts` (e.g. `0.1.0`), then:

```bash
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

That builds, signs, and uploads all three publications (`kotlinMultiplatform`, `jvm`, `js`) and
releases them. To upload but review in the portal before releasing manually, use
`./gradlew publishToMavenCentral` instead.

After a successful release, tag it (`git tag v0.1.0 && git push --tags`), move the `CHANGELOG.md`
entries from **Unreleased** into a dated version section, and bump the version to the next
`-SNAPSHOT`.

## Sanity checks that need no credentials

```bash
./gradlew publishToMavenLocal              # needs signing keys; publishes to ~/.m2
./gradlew generatePomFileForJvmPublication # writes the POM, no signing, no upload
```
