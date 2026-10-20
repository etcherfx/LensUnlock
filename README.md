<h1 align="center">
  <img src=".github/assets/banner.png" alt="LensUnlock" width="850">
</h1>

<div align="center">

<img alt="kotlin" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/built-with/kotlin_vector.svg">

[![Release](https://img.shields.io/github/v/release/etcherfx/LensUnlock?style=for-the-badge)](https://github.com/etcherfx/LensUnlock/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/etcherfx/LensUnlock/ci.yml?style=for-the-badge)](https://github.com/etcherfx/LensUnlock/actions/workflows/ci.yml)

**An Xposed module that lets apps use the ultrawide lens, video stabilization and a steady frame rate.**

[Getting started](#getting-started) · [Settings](#settings) · [How it works](#how-it-works) · [Compatibility](#compatibility)

</div>

> [!NOTE]
> There is no release yet; build the debug APK from source (see [Development](#development)) to try it.

## What it does

Many camera apps never ask for the ultrawide lens, video stabilization or a fixed frame rate, even though the device supports them. LensUnlock is a modern-API Xposed module (libxposed API 101, compiled against 102) that sets those capture-request values for Camera2 apps you pick. Each setting is opt-in per app and per camera facing, and when it's on it replaces whatever the app sent.

| Setting | Example | Effect |
|---|---|---|
| Zoom ratio | The logical back camera's minimum, `0.508` on a Pixel 10 Pro | Uses the ultrawide lens |
| Video stabilization | `PREVIEW_STABILIZATION` | Stabilizes the preview and the stream |
| AE target fps range | `[30,30]` (optional) | Holds a steady frame rate |

The first tested target is Discord (`com.discord`), whose stock `org.webrtc` `Camera2Session` captures at 1280x720 with zoom `1.0`, video stabilization off and an AE fps range of `[15,30]`.

## Getting started

You need a rooted device with an Xposed framework that implements libxposed API 101 or newer (see [Compatibility](#compatibility)).

1. Install the LensUnlock APK.
2. In your Xposed manager, enable LensUnlock and add the apps you want to change to its scope. Discord is recommended by default.
3. Force-stop each app you added. The framework loads the module when the app's process starts, so no reboot is needed.
4. Open LensUnlock's settings from your Xposed manager and pick values for each app.

Nothing changes until you pick a value: every setting starts at **App default**.

## Settings

Open the settings from the module's entry in your Xposed manager; the module has no launcher icon. The status card says whether the framework is connected and whether any apps are in scope. Below it, each app in scope shows a summary of its settings; tap one to change them.

Each app has a **Back camera** and a **Front camera** section with three settings:

| Setting | Choices | Notes |
|---|---|---|
| Zoom | App default, Camera minimum, Custom | Camera minimum shows the camera's value. A custom ratio is clamped to the camera's zoom range |
| Video stabilization | App default, Off, On, Preview stabilization | Modes the camera doesn't support are disabled |
| Frame rate | App default, or one of the camera's AE target fps ranges | A range like 30–30 holds the frame rate steady; in dim light the image gets darker or noisier instead of slower |

Changes apply the next time the app starts its camera. For a call that means turning the camera off and on, or switching cameras. A setting the camera can't take is skipped and noted once in the module log.

## How it works

Capture requests are built inside the app, so the module is scoped to the apps you pick and runs in their processes. `cameraserver` is native code that Xposed can't hook, and `system_server` sees the requests too late, so neither is the right layer.

In the app's process, the module hooks:

- `CameraDeviceImpl.createCaptureRequest`, to learn which camera each `CaptureRequest.Builder` belongs to, and reads that camera's `CameraCharacteristics` (facing, zoom ratio range, video stabilization modes, AE target fps ranges).
- `CaptureRequest.Builder.build()`, which covers both repeating and single requests. Just before the request is built, the module writes its values into the builder; right after, it puts the app's own values back, so turning a setting off always returns to what the app asked for.

The module never writes `SCALER_CROP_REGION`. Zoom ratio and crop region compound, so if an app sets its own crop region, the zoom override is skipped for that request. WebRTC doesn't set one.

If a hook target is missing or has a different signature on your Android build, the module does nothing and logs why in the framework's module log (tag `LensUnlock`). It never crashes the app.

## Compatibility

| Item | Status |
|---|---|
| Android | 12 or newer; tested on Android 17 (Pixel 10 Pro) |
| Xposed frameworks | Any that implements libxposed API 101 or newer. Tested with Vector 2.2 (the JingMatrix LSPosed fork) |
| Discord | Tested with 349.9: back camera at minimum zoom (`0.508`, the ultrawide) with `PREVIEW_STABILIZATION` and `[30,30]`, front camera at minimum zoom (`0.896`), and stock values again with every setting at App default |
| Frameworks with API 100 only (LSPosed 1.9.x, Vector 2.0) | Not supported: they can't load modern API 101 modules |
| Apps | Camera2 apps. Apps that use the old Camera API or CameraX extensions are not covered |
| Samsung, Xiaomi and other OEM Android builds | Untested |

## Development

Everything Android-related builds inside a Docker container; see [CONTRIBUTING.md](CONTRIBUTING.md) for the commands and [AGENTS.md](AGENTS.md) for the code map.

## License

[GPL-3.0](LICENSE)
