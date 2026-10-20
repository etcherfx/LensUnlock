# LensUnlock

A modern-API Xposed module (libxposed API 101, compiled against 102) scoped to user-selected apps (the app process, not `system_server`). For Camera2 apps it overrides capture-request settings the app gets wrong: zoom ratio, video stabilization mode and an optional fixed AE target fps range, configured per app and per camera facing. Tested target: Discord 349.9 (`com.discord`) on a Pixel 10 Pro with Android 17 and Vector 2.2.

## Setup and verification

Builds and tests run inside a Docker container that carries the Android SDK and JDK 21, as in [CameraRefreshFix](https://github.com/etcherfx/CameraRefreshFix). Don't install anything Android-related on the host, and don't assume the SDK exists outside the container.

```sh
docker build -t lensunlock-build docker
docker run --rm -v "$PWD:/work" -v lensunlock-gradle:/root/.gradle lensunlock-build ./gradlew check assembleDebug assembleRelease
```

- `check` runs the JVM unit tests and lint (warnings are errors, including "a newer Gradle or AGP is available").
- Outputs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk` (signed only when the `RELEASE_KEYSTORE_*` variables are set; see CONTRIBUTING).
- Hooks can only be verified on a device with an Xposed framework that implements libxposed API 101 or newer (Vector 2.2, the JingMatrix LSPosed fork): install the debug APK, enable the module with the app in scope, force-stop the app, and read the framework's module log (tag `LensUnlock`). `dumpsys media.camera` shows the values the camera actually received.
- If a JS tool is ever needed, use Bun; there is no Node or npm.

## Code map

- `app/src/main/kotlin/dev/etcherfx/lensunlock/core/`: pure logic, no Android or Xposed types. `Settings` (`AppSettings`, `FacingSettings`, pref keys, `fromLookup`/`write`), `CameraCaps` (camera capabilities and request values), `OverrideDecision` (what to write, or why a setting is skipped), `OnceLogger`.
- `.../camera/Camera2Caps.kt`: reads `CameraCharacteristics` into `CameraCaps`; shared by the hooks and the settings screen.
- `.../xposed/`: runs in the scoped app's process. `ModuleEntry` (entry point, `onPackageReady`), `FrameworkTargets` (plain reflection with injectable names, so it's unit-testable), `HookInstaller` (the hooks and WebRTC deoptimization), `CameraRegistry` (builder → camera, weakly held; characteristics cached per camera ID), `RequestAccess` (builder keys behind an interface), `RequestPatch` (apply, then restore), `PrefsBridge` (remote preferences into a volatile `AppSettings`), `ModuleLog`.
- `.../ModuleApp.kt`, `.../SettingsActivity.kt`, `.../SettingsScreens.kt`: the settings screen, a Compose Material 3 Expressive screen (Material3 1.5 beta; move to stable when released), opened from the Xposed manager (`MODULE_SETTINGS` category, no launcher icon).
- `app/src/main/resources/META-INF/xposed/`: `java_init.list`, `scope.list` (recommended scope: `com.discord`), `module.prop` (`staticScope=false`).
- `app/src/test/`: JVM unit tests; `xposed/fakes/` holds stand-ins for the framework classes and a recording `XposedInterface`.
- `docker/Dockerfile`: the build environment.
- `.github/workflows/ci.yml`: builds and checks on push and pull request.
- `.github/workflows/release.yml`: tag-driven release; checks the tag against `version` in `gradle.properties`, signs the APK, attaches it to a draft, then publishes the notes from `.github/release-notes/vX.Y.Z.md`.

## Design facts

- Capture requests are built inside the app, so the module hooks the app process. `cameraserver` is native and not hookable, and `system_server` is the wrong layer.
- Each setting is opt-in per app; when enabled it replaces the app's value. Apps do set some of these explicitly (stock WebRTC sends video stabilization OFF and AE fps range `[15,30]`), so "fill only unset keys" would do nothing. Settings that are off leave the app's request untouched.
- Hooks: `CameraDeviceImpl.createCaptureRequest(int, ...)` (both overloads) maps each returned builder to its camera; `CaptureRequest.Builder.build()` applies the overrides just before the original runs and restores the app's values right after (a key the app hadn't set is erased with `set(key, null)`). `build()` copies the metadata, so the built request keeps the overrides while the app's builder never does. Builders from reprocess requests or unknown cameras are untouched.
- Values are checked against the camera's characteristics: zoom is clamped to `CONTROL_ZOOM_RATIO_RANGE`, and a stabilization mode or fps range the camera doesn't list is skipped and logged once. Cameras facing neither back nor front are untouched.
- The module never writes `SCALER_CROP_REGION`. If the app's crop region isn't the full active array, the zoom override is skipped, since zoom ratio and crop region compound.
- No live apply: settings are read at every `build()`, so a change reaches the next request the app builds. WebRTC builds its repeating request once per camera start, so for Discord a change applies on the next camera start or flip. Re-issuing a running repeating request would need hidden internals and replay the app's callbacks.
- Example values: zoom ratio at the logical back camera's minimum (`0.508` on a Pixel 10 Pro, which selects the ultrawide), video stabilization `PREVIEW_STABILIZATION`, optional AE fps range `[30,30]`.
- Discord uses stock `org.webrtc` `Camera2Session` at 1280x720 with zoom `1.0`, stabilization off and AE fps range `[15,30]`.

## Constraints and conventions

- American English in code, comments, docs and user-facing text.
- Conventional Commits with a DCO sign-off (`git commit -s`).
- Never commit machine paths, personal paths, hostnames, device serials or network names. Keystores and passwords never enter the repo.
- A hooked app must never crash because of this module: resolve targets reflectively, catch every Throwable around decisions, and keep `chain.proceed()` outside those try blocks.
- Target API 101: don't use members added in API 102 (`HookHandle.getId`/`replaceHook`, `HookBuilder.setId`, `onHotReloading`/`onHotReloaded`, `detach`, service `getRunningTargets`/`hotReloadModule`).
- No R8 in release builds, so the release APK has the same bytecode as the device-tested debug build, and `java_init.list` stays valid.
- The package name `dev.etcherfx.lensunlock` and the signing key are permanent; the framework stores the enabled flag and scope under the package.
- Preferences: one remote-preferences group per app, named after its package. Keys are prefixed `back_` or `front_`: `zoom_mode` (`app` | `minimum` | `custom`), `zoom_ratio` (Float, used by `custom`), `stabilization` (`app` | `off` | `on` | `preview`), `fps_range` (`app` | `min-max`). A missing or unparseable value means `app`.

## Gotchas

- `CameraDeviceImpl` is hidden API. The module relies on the framework letting module code reflect on it; if resolution fails, the module logs why and hooks nothing.
- ART may inline the tiny `build()` into app code, which would bypass the hook. `HookInstaller.deoptimize` deoptimizes `org.webrtc.Camera2Session$CaptureSessionCallback.onConfigured`, where WebRTC builds its repeating request, when the app has that class.
- Stock WebRTC turns EIS off whenever the logical camera advertises OIS. At 0.5x the ultrawide is active and has no OIS, so the minimum zoom without forced stabilization is unstabilized.
- Characteristics come from `ActivityThread.currentApplication()`'s `CameraManager`; a failed lookup isn't cached, so the next capture request retries.
- Changing the scope or updating the module takes effect after the app is force-stopped. Releases are immutable on this repo: the release workflow creates a draft, attaches the APK, then publishes.
