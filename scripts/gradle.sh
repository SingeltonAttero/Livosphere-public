#!/bin/sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir/../android"
exec ./gradlew --console=plain --no-daemon --max-workers=2 -Plivosphere.buildProfile=phone "$@"
