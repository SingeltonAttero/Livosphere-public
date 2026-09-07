#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
validator="$script_dir/validate-epic-3-evidence.sh"
tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/livosphere-evidence-fixtures.XXXXXX")
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

write_common() {
  target=$1
  protocol=$2
  printf '%s\n' \
    'verdict: PASS' \
    "protocol: $protocol" \
    'raw_trace: capture.txt' \
    'artifact_path: measured.apk' \
    "artifact_sha256: $(shasum -a 256 "$target/measured.apk" | awk '{print $1}')" \
    'device: fixture-device' \
    'os_build: fixture-build' \
    'launcher: fixture-launcher' \
    'brightness: 50%' \
    'network: offline' \
    'scenario: fixture scenario' \
    'duration: 60s' > "$target/index.md"
}

unknown="$tmp_dir/unknown"
mkdir "$unknown"
printf '%s\n' 'verdict: UNKNOWN' 'protocol: SP-06' 'raw_trace: none' 'artifact_path: none' 'artifact_sha256:' > "$unknown/index.md"
"$validator" "$unknown"

sp06="$tmp_dir/sp06-pass"
mkdir "$sp06"
printf '%s\n' 'raw timing trace' > "$sp06/capture.txt"
printf '%s\n' 'measured package bytes' > "$sp06/measured.apk"
write_common "$sp06" SP-06
printf '%s\n' 'p95_frame_ms: 34' 'p99_frame_ms: 50' 'max_warm_stall_ms: 100' 'response_ms: 100' >> "$sp06/index.md"
"$validator" "$sp06"

sp07="$tmp_dir/sp07-pass"
mkdir "$sp07"
printf '%s\n' 'raw energy trace' > "$sp07/capture.txt"
printf '%s\n' 'measured package bytes' > "$sp07/measured.apk"
write_common "$sp07" SP-07
printf '%s\n' 'static_energy_mah: 100' 'normal_energy_mah: 110' 'reduced_energy_mah: 105' >> "$sp07/index.md"
"$validator" "$sp07"

escape="$tmp_dir/path-escape"
mkdir "$escape"
printf '%s\n' 'outside trace' > "$tmp_dir/outside.txt"
printf '%s\n' 'measured package bytes' > "$escape/measured.apk"
write_common "$escape" SP-06
sed -i.bak 's|^raw_trace: .*|raw_trace: ../outside.txt|' "$escape/index.md"
rm "$escape/index.md.bak"
printf '%s\n' 'p95_frame_ms: 1' 'p99_frame_ms: 1' 'max_warm_stall_ms: 1' 'response_ms: 1' >> "$escape/index.md"
if "$validator" "$escape"; then
  echo "Traversal fixture unexpectedly passed" >&2
  exit 1
fi

over_budget="$tmp_dir/over-budget"
mkdir "$over_budget"
printf '%s\n' 'raw energy trace' > "$over_budget/capture.txt"
printf '%s\n' 'measured package bytes' > "$over_budget/measured.apk"
write_common "$over_budget" SP-07
printf '%s\n' 'static_energy_mah: 100' 'normal_energy_mah: 111' 'reduced_energy_mah: 106' >> "$over_budget/index.md"
if "$validator" "$over_budget"; then
  echo "Over-budget SP-07 fixture unexpectedly passed" >&2
  exit 1
fi

echo "Epic 3 evidence validator fixtures: PASS"
