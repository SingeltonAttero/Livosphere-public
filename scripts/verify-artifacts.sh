#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
phone_apk="$repo_root/android/hub/app/build/outputs/apk/debug/app-debug.apk"
phone_manifest="$repo_root/android/hub/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
product_release=$(awk -F= '$1 == "livosphere.productRelease" { print substr($0, index($0, "=") + 1) }' \
    "$repo_root/android/gradle.properties")

if command -v aapt2 >/dev/null 2>&1; then
    aapt2_bin=$(command -v aapt2)
else
    sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
    aapt2_bin=$(find "$sdk_root/build-tools" -type f -name aapt2 2>/dev/null | sort | tail -n 1)
fi
test -x "$aapt2_bin" || { echo "aapt2 не найден" >&2; exit 1; }
test -s "$phone_apk" || { echo "Phone APK не найден: $phone_apk" >&2; exit 1; }
test -f "$phone_manifest" || { echo "Merged manifest телефона не найден: $phone_manifest" >&2; exit 1; }

unzip -tqq "$phone_apk"
"$aapt2_bin" dump badging "$phone_apk" | grep -Fq "versionName='$product_release'"
grep -Fq 'android:name="app.livosphere.LivosphereApplication"' "$phone_manifest"
if grep -Eiq 'com\.google\.[^."]+\.(standalone|watchface)|android\.hardware\.type\.watch|watchface|watch_face' "$phone_manifest"; then
    echo "Phone APK содержит компонент наручных часов" >&2
    exit 1
fi

echo "Phone build artifact verified: ${phone_apk#"$repo_root/"}"
