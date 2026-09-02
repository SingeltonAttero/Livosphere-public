#!/bin/sh
set -eu

java_home=${JAVA_HOME:-}
sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}

if [ -z "$java_home" ] || [ ! -x "$java_home/bin/java" ]; then
    echo "JDK не найден: укажите JAVA_HOME на JDK 17." >&2
    exit 1
fi

java_version=$(
    "$java_home/bin/java" -XshowSettings:properties -version 2>&1 |
        sed -n 's/^[[:space:]]*java\.version = \([0-9][0-9]*\).*/\1/p' |
        head -n 1
)

if [ "$java_version" != "17" ]; then
    echo "Требуется JDK 17, обнаружена версия ${java_version:-unknown}." >&2
    exit 1
fi

if [ -z "$sdk_root" ] || [ ! -d "$sdk_root" ]; then
    echo "Android SDK не найден: укажите ANDROID_SDK_ROOT или ANDROID_HOME." >&2
    exit 1
fi

if [ ! -f "$sdk_root/platforms/android-37/android.jar" ] &&
    [ ! -f "$sdk_root/platforms/android-37.0/android.jar" ]; then
    echo "Android SDK Platform 37 не установлена или повреждена в $sdk_root." >&2
    exit 1
fi

echo "Livosphere doctor (preflight): JDK 17, Android SDK 37 — OK"
