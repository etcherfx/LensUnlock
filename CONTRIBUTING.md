# Contributing to LensUnlock

Bug reports, compatibility reports for other devices, frameworks and apps, and focused fixes are welcome. Open an issue before a large change.

## Reporting issues

Search existing issues first. Include reproducible steps, expected and actual behavior, and the module version, device, Android version, Xposed framework and target app. Review logs and screenshots before sharing; remove secrets and personal information.

Use the issue forms for bugs and feature requests. For a security problem, see [SECURITY.md](SECURITY.md).

## Development

Everything Android-related runs inside a Docker container that carries the Android SDK and JDK 21, so the host needs only Docker:

```sh
docker build -t lensunlock-build docker
docker run --rm -v "$PWD:/work" -v lensunlock-gradle:/root/.gradle lensunlock-build ./gradlew check assembleDebug assembleRelease
```

`check` runs the unit tests and lint; both must pass before a pull request. The debug APK lands in `app/build/outputs/apk/debug/`. A hook change also needs a test on a device or emulator with an Xposed framework that implements libxposed API 101 or newer. [AGENTS.md](AGENTS.md) has the code map and the rules hooks must follow.

## Pull requests

- Keep each pull request to one focused change, and fill in the pull request template.
- Include relevant tests and update affected docs when behavior changes.
- Write in American English: code, comments, docs and user-facing text.
- Use Conventional Commits and sign off every commit (`git commit -s`).

## Releasing

Maintainers only. Write `.github/release-notes/vX.Y.Z.md`, commit it as `chore(release): vX.Y.Z`, then tag and push:

```sh
git tag -m vX.Y.Z vX.Y.Z
git push origin vX.Y.Z
```

The version lives in `gradle.properties` (`version=X.Y.Z`); bump it in the release commit. The release workflow checks that the tag matches it, runs the same checks as CI, signs the APK, appends the Full Changelog link to the notes, attaches `LensUnlock-X.Y.Z.apk` to a draft and publishes it. Run it from the Actions tab (`workflow_dispatch`) for a dry run that builds without publishing.

Signing uses three repository secrets: `RELEASE_KEYSTORE_BASE64` (the PKCS12 keystore, base64-encoded), `RELEASE_KEYSTORE_PASSWORD` (store and key password) and `RELEASE_KEY_ALIAS`. The workflow fails early if any is missing. The key must never change: Android refuses an update signed with a different key.

## Licensing

Contributions are licensed under the project's [GPL-3.0](LICENSE) license.
