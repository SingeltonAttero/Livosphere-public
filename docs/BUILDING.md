# Building and testing

## Prerequisites

- JDK 17 (`JAVA_HOME`); the preflight intentionally checks this version.
- Android SDK Platform 37 and Android build tools, platform-tools/adb.
- `ANDROID_SDK_ROOT` or `ANDROID_HOME` pointing at your SDK.
- Python 3 and `make` for repository scripts.

On macOS, Make discovers JDK 17 and the standard SDK location. On Linux, set `JAVA_HOME` and `ANDROID_SDK_ROOT` explicitly. Keep any `android/local.properties` local. The Gradle wrapper and version catalogue pin the project's toolchain; do not silently upgrade them.

```sh
make doctor
make phone
```

Run from the repository root. `make phone` validates assets and builds `android/hub/app/build/outputs/apk/debug/app-debug.apk`. First use needs network access to resolve the wrapper and dependencies. `make offline-smoke` requires those dependencies to be cached.

Install on your selected device without owner signing credentials:

```sh
adb devices
adb -s DEVICE_SERIAL install -r android/hub/app/build/outputs/apk/debug/app-debug.apk
```

If the installed app has a different signing certificate, update installation will fail. Use a separate test device/profile or deliberately remove the old installation after preserving any needed state; uninstalling loses app data.

The current app module uses minSdk 29 (Android 10), compileSdk 37 and targetSdk 36.

## Checks

| Command | Purpose |
| --- | --- |
| `make assets-check` | Validate selected content assets |
| `make check` | Gradle checks for the phone profile |
| `make device-check` | Instrumentation on an attached emulator/device |
| `make offline-smoke` | Cached build and checks |
| `make verify` | Debug build, checks, offline smoke and artifact verification |
| `make phone-v2-pipeline-test` | Unsigned release build and candidate fixtures with a temporary test key, no owner key |
| `python3 android/scripts/test_install_phone.py` | Installer fixtures, no physical device |

For a focused change, run the affected test selectors and module build instead of the full suite. Inspect the concrete commands in `Makefile` before running them. Do not present old test counts as current results.

## Signing and release

Debug builds use the development signing path; they do not use the owner's permanent key. `.env.example` lists the owner inputs for a signed candidate. Copy it to an ignored `.env` only when you own the appropriate signing materials. Never publish that file or a keystore. Candidate creation requires clean Git state and exact owner inputs.

`make release` checks release readiness; it does not publish to RuStore. Candidate evidence lives locally under `.local/evidence/phone-v2/`. Existing private runs under `_bmad-output/implementation-artifacts/evidence/phone-v2/` remain intact; select them by an explicit run path rather than moving or rebuilding them. `LIVOSPHERE_EVIDENCE_DIR` can choose another evidence directory for candidate creation; `LIVOSPHERE_RELEASE_RUN_DIR` selects an exact run for validation; `PHONE_RUN_DIR` or `--run-dir` selects one for installation. Store publication, device checks and artwork permission remain separate from compiling the application.
