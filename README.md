# Livosphere

Live wallpapers and clock widgets for Android, created by **Yakov Weber (Вебер Яков)**.

Browse a local collection of illustrated worlds, apply a wallpaper, and add clocks in S, M or L. Wallpapers and widgets work independently: mix them across collections or use either on its own.

[Русский](README.ru.md) · [Build](docs/BUILDING.md) · [Documentation](docs/README.md) · [Design](pen-design/README.md) · [Contribute](CONTRIBUTING.md)

[Privacy policy (Russian)](https://singeltonattero.github.io/Livosphere-public/privacy/)

## A look inside

<p align="center">
  <img src="docs/images/01-orbital-window.jpg" width="240" alt="Orbital Window wallpaper in the Livosphere hub">
  <img src="docs/images/02-rainforest.jpg" width="240" alt="Rainforest wallpaper and the wallpaper installation action">
</p>
<p align="center">
  <img src="docs/images/07-wallpaper-catalog.jpg" width="200" alt="Local wallpaper catalogue">
  <img src="docs/images/08-widget-catalog.jpg" width="200" alt="Clock widget catalogue">
  <img src="docs/images/09-widget-detail.jpg" width="200" alt="Clock configuration with S, M and L sizes and tap action">
</p>

These are existing RuStore promotional compositions made from app screenshots and artwork, rather than raw screen captures. Their Russian captions are retained from the store materials. [Image sources](docs/images/README.md).

## What it does

- A bundled catalogue: no account or online theme download.
- Four local-time phases: morning, day, evening and night. Three neon collections have animation and supported interactions; the other ten change between phase images without continuous animation.
- Independent clock widgets with per-instance configuration.
- System wallpaper preview and widget pin/picker flows.
- Reduced/off effect policies and lifecycle-aware rendering.

The current manifests select 13 public collections. The product targets Android phones. Launcher behaviour, lock-screen support and battery results depend on the device and need separate validation.

Browsing a wallpaper does not apply it. The hub shows image previews; Android's system wallpaper preview runs the selected wallpaper component. Clock previews and installed widgets are also checked separately. See [wallpaper behaviour](docs/runtime/wallpapers.md) and [clock behaviour](docs/runtime/widgets.md).

## Get the app

Verified RuStore or Google Play listing links have not yet been recorded in this repository. A store submission does not establish public availability. Until a verified listing link is added, the documented installation route is a local development build.

Signed candidate tooling is available to the owner in [release instructions](docs/RELEASE.md). A local APK or successful build does not establish a store-distributed release or completed device validation.

## Build locally

The app requires Android 10 or later (API 29). To build, install JDK 17, Python 3.9+, Make, Android SDK Platform 37, Build-Tools 36.0.0 and Platform-Tools. Run from the repository root:

```sh
make doctor
make phone
./scripts/android-env.sh adb devices
make phone-install PHONE_SERIAL=DEVICE_SERIAL
make check
make scripts-check
```

Enable USB debugging and authorize the selected device before installation. The installer uses the existing debug APK and launches the app after installation. If exactly one authorized device is connected, `PHONE_SERIAL` can be omitted. It does not automatically uninstall an app with an incompatible signature.

Debug APK: `android/hub/app/build/outputs/apk/debug/app-debug.apk`.
No owner signing key is required for this build. The first build downloads Gradle and Maven dependencies. See [building and testing](docs/BUILDING.md) for prerequisites, checks and device installation.

## For developers

Kotlin, Jetpack Compose, Hilt, DataStore, Android WallpaperService and AppWidget/RemoteViews. Versions are pinned in `android/gradle/libs.versions.toml` and the Gradle wrapper.

[Screen specifications](docs/README.md) describe what each screen does and where to change it. [Architecture](docs/architecture/README.md) explains the modules and system boundaries. [Content development](docs/CONTENT.md) covers manifests, assets and approvals. [Editable design](pen-design/README.md) contains the design source and its dependencies. [Device testing](docs/TESTING.md) gives a manual check route.

`make verify` combines the debug build, unit tests, lint, module and script checks, and APK inspection. `make device-check` runs instrumentation tests on a connected device or emulator. `make benchmark-build` prepares a profileable APK; it does not run performance measurements.

## License and artwork

Code and project-authored documentation: [MIT](LICENSE), copyright © 2026 Yakov Weber. Keep the copyright and license notice when reusing the code.

**Artwork is separately licensed.** Wallpapers, illustrations, app icons, widget artwork and promotional images are excluded from MIT. Local build/testing is permitted; reuse or redistribution of that artwork requires the author's written permission. See [artwork terms](ASSET-LICENSE.md). Third-party fonts and bundled tools retain their own [licenses](THIRD-PARTY-NOTICES.md).
