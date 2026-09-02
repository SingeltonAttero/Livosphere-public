#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)

phone_apk="$repo_root/android/hub/app/build/outputs/apk/debug/app-debug.apk"
watch_aab="$repo_root/android/watchfaces/contour-wff/build/outputs/bundle/debug/contour-wff-debug.aab"
phone_manifest="$repo_root/android/hub/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
watch_manifest="$repo_root/android/watchfaces/contour-wff/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"

test -s "$phone_apk" || {
    echo "Phone APK не найден: $phone_apk" >&2
    exit 1
}

test -s "$watch_aab" || {
    echo "WFF AAB не найден: $watch_aab" >&2
    exit 1
}

unzip -tqq "$phone_apk"
unzip -tqq "$watch_aab"

if unzip -Z1 "$watch_aab" | grep -Eq '(^|/)classes([0-9]*)?\.dex$'; then
    echo "WFF AAB содержит DEX и не является resource-only: $watch_aab" >&2
    exit 1
fi

test -f "$phone_manifest" || {
    echo "Merged manifest телефона не найден: $phone_manifest" >&2
    exit 1
}
test -f "$watch_manifest" || {
    echo "Merged manifest WFF не найден: $watch_manifest" >&2
    exit 1
}

service_block=$(sed -n '/ContourWallpaperService/,/<\/service>/p' "$phone_manifest")
printf '%s\n' "$service_block" | grep -Fq 'android:exported="true"'
printf '%s\n' "$service_block" | grep -Fq 'android:permission="android.permission.BIND_WALLPAPER"'
grep -Fq 'android:name="app.livosphere.LivosphereApplication"' "$phone_manifest"
grep -Fq 'android:hasCode="false"' "$watch_manifest"
grep -Fq 'android:name="com.google.wear.watchface.format.version"' "$watch_manifest"

echo "Build artifacts verified:"
echo "  ${phone_apk#"$repo_root/"}"
echo "  ${watch_aab#"$repo_root/"}"
