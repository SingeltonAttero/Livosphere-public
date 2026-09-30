#!/bin/sh
# Resolve tools for Make, Gradle and Python. No machine paths are written.
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
JAVA_HOME=${LIVOSPHERE_JAVA_HOME:-${JAVA_HOME:-}}
if [ -z "${LIVOSPHERE_JAVA_HOME:-}" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(uname -s)" = Darwin ]; then
    java_major=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java\.version = \([0-9][0-9]*\).*/\1/p' | head -n 1)
    # Android Studio can export its own newer JDK; choose an installed 17 on macOS.
    [ "$java_major" = 17 ] || JAVA_HOME=
fi
if [ -z "$JAVA_HOME" ]; then
    if [ "$(uname -s)" = Darwin ]; then
        if command -v brew >/dev/null 2>&1; then
            java_prefix=$(brew --prefix openjdk@17 2>/dev/null || true)
            [ -z "$java_prefix" ] || JAVA_HOME="$java_prefix/libexec/openjdk.jdk/Contents/Home"
        fi
        if [ ! -x "${JAVA_HOME:-/nonexistent}/bin/java" ]; then
            JAVA_HOME=$(/usr/libexec/java_home -F -v 17 2>/dev/null || true)
        fi
    elif command -v javac >/dev/null 2>&1; then
        javac_path=$(readlink -f "$(command -v javac)")
        JAVA_HOME=$(dirname "$(dirname "$javac_path")")
    fi
fi
sdk_from_properties=
if [ -f "$repo_root/android/local.properties" ]; then
    sdk_from_properties=$(python3 - "$repo_root/android/local.properties" <<'PY'
from pathlib import Path
import sys
for line in Path(sys.argv[1]).read_text().splitlines():
    key, separator, value = line.partition('=')
    if separator and key.strip() == 'sdk.dir':
        print(value.strip().replace('\\:', ':').replace('\\ ', ' ').replace('\\\\', '\\'))
        break
PY
    )
fi
sdk_from_environment=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -n "$sdk_from_properties" ] && [ -n "$sdk_from_environment" ] && [ "$sdk_from_properties" != "$sdk_from_environment" ]; then
    echo 'Android SDK differs between android/local.properties and ANDROID_SDK_ROOT/ANDROID_HOME. Set them to the same directory.' >&2
    exit 1
fi
ANDROID_SDK_ROOT=${sdk_from_properties:-$sdk_from_environment}
if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ "$(uname -s)" = Darwin ]; then
        ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
    else
        ANDROID_SDK_ROOT="$HOME/Android/Sdk"
    fi
fi
ANDROID_HOME=$ANDROID_SDK_ROOT
export JAVA_HOME ANDROID_SDK_ROOT ANDROID_HOME
if [ -n "$JAVA_HOME" ]; then PATH="$JAVA_HOME/bin:$PATH"; fi
PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
export PATH
cd "$repo_root"
[ "$#" -gt 0 ] || { echo 'usage: android-env.sh COMMAND [ARGS...]' >&2; exit 64; }
exec "$@"
