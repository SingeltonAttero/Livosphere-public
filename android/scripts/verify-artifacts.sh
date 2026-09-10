#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
profile=${1:-phone}
case "$profile" in
    phone|legacy) ;;
    *) echo "Usage: $0 [phone|legacy]" >&2; exit 64 ;;
esac

phone_apk="$repo_root/android/hub/app/build/outputs/apk/debug/app-debug.apk"
watch_aab="$repo_root/android/watchfaces/contour-wff/build/outputs/bundle/debug/contour-wff-debug.aab"
phone_manifest="$repo_root/android/hub/app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
watch_manifest="$repo_root/android/watchfaces/contour-wff/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
set_manifest="$repo_root/android/sets/contour/manifest/set.properties"
product_release=$(awk -F= '$1 == "livosphere.productRelease" { print substr($0, index($0, "=") + 1) }' \
    "$repo_root/android/gradle.properties")

if command -v aapt2 >/dev/null 2>&1; then
    aapt2_bin=$(command -v aapt2)
else
    sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
    aapt2_bin=$(find "$sdk_root/build-tools" -type f -name aapt2 2>/dev/null | sort | tail -n 1)
fi
test -x "$aapt2_bin" || {
    echo "aapt2 не найден для проверки archive resources" >&2
    exit 1
}

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/livosphere-artifacts.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

test -s "$phone_apk" || {
    echo "Phone APK не найден: $phone_apk" >&2
    exit 1
}

unzip -tqq "$phone_apk"

"$aapt2_bin" dump resources "$phone_apk" > "$tmp_dir/phone-resources.txt"
"$aapt2_bin" dump badging "$phone_apk" > "$tmp_dir/phone-badging.txt"
grep -Fq "versionName='$product_release'" "$tmp_dir/phone-badging.txt"

test -f "$phone_manifest" || {
    echo "Merged manifest телефона не найден: $phone_manifest" >&2
    exit 1
}
service_block=$(sed -n '/ContourWallpaperService/,/<\/service>/p' "$phone_manifest")
printf '%s\n' "$service_block" | grep -Fq 'android:exported="true"'
printf '%s\n' "$service_block" | grep -Fq 'android:permission="android.permission.BIND_WALLPAPER"'
grep -Fq 'android:name="app.livosphere.LivosphereApplication"' "$phone_manifest"

manifest_value() {
    awk -F= -v key="$1" '$1 == key { print substr($0, index($0, "=") + 1) }' "$set_manifest"
}

assert_phone_surface() {
    surface=$1
    contribution=$(manifest_value contributions | tr ',' '\n' | while IFS= read -r key; do
        if test "$(manifest_value "contribution.$key.surface")" = "$surface"; then printf '%s\n' "$key"; fi
    done)
    refs=$(manifest_value "contribution.$contribution.assetRefs")
    old_ifs=$IFS
    IFS=,
    for asset in $refs; do
        resource_path=$(manifest_value "asset.$asset.resourcePath")
        resource_dir=${resource_path%%/*}
        resource_type=${resource_dir%%-*}
        resource_file=${resource_path##*/}
        resource_name=${resource_file%.*}
        if test "$resource_type" = values; then
            source_path=$(manifest_value "asset.$asset.path")
            sed -n 's/.*<\([a-z][a-z0-9-]*\)[^>]* name="\([^"]*\)".*/\1 \2/p' \
                "$repo_root/android/sets/contour/source-assets/$source_path" |
                while IFS=' ' read -r value_type value_name; do
                    grep -Fq " $value_type/$value_name" "$tmp_dir/phone-resources.txt" || {
                        echo "Phone APK не содержит value из manifest-selected $resource_path: $value_type/$value_name" >&2
                        exit 1
                    }
                done
        else
            awk -v expected="$resource_type/$resource_name" \
                '$1 == "resource" && $3 == expected { found = 1 } END { exit !found }' \
                "$tmp_dir/phone-resources.txt" || {
                echo "Phone APK не содержит manifest-selected $surface resource: $resource_path" >&2
                exit 1
            }
        fi
    done
    IFS=$old_ifs
}

assert_phone_surface preview
assert_phone_surface wallpaper

if test "$profile" = phone; then
    echo "Phone build artifact verified:"
    echo "  ${phone_apk#"$repo_root/"}"
    exit 0
fi

test -s "$watch_aab" || {
    echo "WFF AAB не найден: $watch_aab" >&2
    exit 1
}
unzip -tqq "$watch_aab"

watch_contribution=$(manifest_value contributions | tr ',' '\n' | while IFS= read -r key; do
    if test "$(manifest_value "contribution.$key.surface")" = watchface; then printf '%s\n' "$key"; fi
done)
watch_refs=$(manifest_value "contribution.$watch_contribution.assetRefs")
# AAB stores a protobuf resource table. `strings` can join a printable length
# byte to a name (e.g. a 34-byte name gets a leading quote), so parse via AAPT2.
python3 - "$watch_aab" "$tmp_dir/watch-proto.apk" <<'PY_AAB'
import sys
import zipfile
with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], "w") as target:
    for name in source.namelist():
        if name == "base/manifest/AndroidManifest.xml":
            target.writestr("AndroidManifest.xml", source.read(name))
        elif name == "base/resources.pb" or name.startswith("base/res/"):
            target.writestr(name[len("base/"):], source.read(name))
PY_AAB
"$aapt2_bin" convert --output-format binary -o "$tmp_dir/watch-binary.apk" "$tmp_dir/watch-proto.apk"
"$aapt2_bin" dump resources "$tmp_dir/watch-binary.apk" > "$tmp_dir/watch-resources.txt"
old_ifs=$IFS
IFS=,
for asset in $watch_refs; do
    resource_path=$(manifest_value "asset.$asset.resourcePath")
    resource_dir=${resource_path%%/*}
    resource_type=${resource_dir%%-*}
    resource_file=${resource_path##*/}
    resource_name=${resource_file%.*}
    if test "$resource_type" = values; then
        source_path=$(manifest_value "asset.$asset.path")
        sed -n 's/.*<\([a-z][a-z0-9-]*\)[^>]* name="\([^"]*\)".*/\1 \2/p' \
            "$repo_root/android/sets/contour/source-assets/$source_path" |
            while IFS=' ' read -r value_type value_name; do
                awk -v expected="$value_type/$value_name" \
                    '$1 == "resource" && $3 == expected { found = 1 } END { exit !found }' \
                    "$tmp_dir/watch-resources.txt" || {
                    echo "WFF AAB не содержит value из manifest-selected $resource_path: $value_name" >&2
                    exit 1
                }
            done
    else
        awk -v expected="$resource_type/$resource_name" \
            '$1 == "resource" && $3 == expected { found = 1 } END { exit !found }' \
            "$tmp_dir/watch-resources.txt" || {
            echo "WFF AAB не содержит manifest-selected watchface resource: $resource_path" >&2
            exit 1
        }
    fi
done
IFS=$old_ifs

if unzip -Z1 "$watch_aab" | grep -Eq '(^|/)classes([0-9]*)?\.dex$'; then
    echo "WFF AAB содержит DEX и не является resource-only: $watch_aab" >&2
    exit 1
fi

test -f "$watch_manifest" || {
    echo "Merged manifest WFF не найден: $watch_manifest" >&2
    exit 1
}

grep -Fq 'android:hasCode="false"' "$watch_manifest"
grep -Fq 'android:name="com.google.wear.watchface.format.version"' "$watch_manifest"

echo "Build artifacts verified:"
echo "  ${phone_apk#"$repo_root/"}"
echo "  ${watch_aab#"$repo_root/"}"
