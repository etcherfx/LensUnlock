# LensUnlock

A modern-API Xposed module (libxposed API 101, compiled against 102) scoped to user-selected apps (the app process, not `system_server`). For Camera2 apps it overrides capture-request settings the app never sets: zoom ratio, video stabilization mode and an optional fixed AE target fps range, configured per app. First tested target: Discord (`com.discord`).

The repo is scaffolding only so far: no Gradle project or code yet. Update this file when the implementation lands.

## Setup and verification

Android builds will run inside a Docker container that carries the Android SDK and JDK, as in [CameraRefreshFix](https://github.com/etcherfx/CameraRefreshFix). Don't install anything Android-related on the host, and don't assume the SDK exists outside the container. If a JS tool is ever needed, use Bun; there is no Node or npm.

Hooks can only be verified on a device with an Xposed framework that implements libxposed API 101 or newer (tested with Vector 2.2, the JingMatrix LSPosed fork).

## Design facts

- Capture requests are built inside the app, so the module hooks the app process. `cameraserver` is native and not hookable, and `system_server` is the wrong layer.
- Each setting is opt-in per app; when enabled it replaces the app's value. Apps do set some of these explicitly (stock WebRTC sends video stabilization OFF and AE fps range `[15,30]`), so "fill only unset keys" would do nothing. Settings that are off leave the app's request untouched.
- Example values: zoom ratio at the logical back camera's minimum (`0.508` on a Pixel 10 Pro, which selects the ultrawide), video stabilization `PREVIEW_STABILIZATION`, optional AE fps range `[30,30]`.
- Discord uses stock `org.webrtc` `Camera2Session` at 1280x720 with zoom `1.0`, stabilization off and AE fps range `[15,30]`.

## Constraints and conventions

- American English in code, comments, docs and user-facing text.
- Conventional Commits with a DCO sign-off (`git commit -s`).
- Never commit machine paths, personal paths, hostnames, device serials or network names. Keystores and passwords never enter the repo.
- A hooked app must never crash because of this module: resolve targets reflectively, catch every Throwable around decisions, and keep `chain.proceed()` outside those try blocks.
- Target API 101: don't use members added in API 102.
- No R8 in release builds, so the release APK has the same bytecode as the device-tested debug build.
