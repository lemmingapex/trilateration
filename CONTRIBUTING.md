# Contributing

Bug reports and pull requests are welcome. For anything larger than a small fix, please open an
issue first so the approach can be discussed.

## Development

You need JDK 17 or newer. The Gradle wrapper downloads the right Gradle version.

```sh
./gradlew build
```

This compiles with all warnings treated as errors, runs the tests, builds the Javadoc (strict
doclint), and writes a coverage report to `build/reports/jacoco/test/html`.

Guidelines:

- The library targets Java 8. Do not use newer language features or APIs in `src/main`. The
  compiler's `--release 8` flag enforces this. Tests may use Java 17.
- Keep Hipparchus out of the public API, so the math library can change without breaking users.
- Every public type and member needs Javadoc.
- Add a test for every bug fix and behaviour change, and an entry under "Unreleased" in
  [CHANGELOG.md](CHANGELOG.md).
- Formatting follows [.editorconfig](.editorconfig): 4 spaces, LF line endings.

## Releasing

Releases are published to Maven Central by the [Release workflow](.github/workflows/release.yml)
when a version tag is pushed.

1. Set `version` in [build.gradle.kts](build.gradle.kts) and the versions in the README.
2. Move the "Unreleased" changelog entries under the new version and date.
3. Commit, then tag and push: `git tag v2.0.0 && git push origin v2.0.0`.
4. The workflow checks that the tag matches the project version, builds, signs and uploads to the
   [Central Portal](https://central.sonatype.com/). Then publish the deployment there.

The workflow needs these repository secrets:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` | A user token generated on the Central Portal |
| `SIGNING_KEY` | ASCII-armored GPG private key (`gpg --armor --export-secret-keys <id>`) |
| `SIGNING_KEY_PASSWORD` | The key's passphrase |

To check what will be published, without credentials:

```sh
./gradlew publishToMavenLocal
```
