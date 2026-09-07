#!/bin/sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: $0 <evidence-directory>" >&2
  exit 64
fi

directory=$1
index="$directory/index.md"
if [ ! -d "$directory" ] || [ -L "$directory" ] || [ ! -f "$index" ] || [ -L "$index" ]; then
  echo "Missing regular evidence directory/index: $directory" >&2
  exit 1
fi
directory=$(cd "$directory" && pwd -P)
index="$directory/index.md"

field() {
  sed -n "s/^$1: \(.*\)$/\1/p" "$index" | head -n 1
}
require_field() {
  value=$(field "$1")
  if [ -z "$value" ] || [ "$value" = "none" ]; then
    echo "PASS requires $1 metadata in $index" >&2
    exit 1
  fi
  printf '%s' "$value"
}
safe_relative_file() {
  value=$1
  case "$value" in ''|none|/*|../*|*/../*|..)
    echo "Evidence path must be a non-empty relative file below its index: $value" >&2
    exit 1
  esac
  path="$directory/$value"
  if [ ! -f "$path" ] || [ -L "$path" ]; then
    echo "Evidence file must exist and not be a symlink: $value" >&2
    exit 1
  fi
  parent=$(cd "$(dirname "$path")" && pwd -P)
  case "$parent" in "$directory"|"$directory"/*) ;; *)
    echo "Evidence path escapes its index directory: $value" >&2
    exit 1
  esac
  printf '%s' "$path"
}
number() {
  value=$(require_field "$1")
  if ! printf '%s' "$value" | grep -Eq '^[0-9]+([.][0-9]+)?$'; then
    echo "$1 must be a non-negative decimal, got: $value" >&2
    exit 1
  fi
  printf '%s' "$value"
}
at_most() {
  actual=$(number "$1")
  limit=$2
  if ! awk -v actual="$actual" -v limit="$limit" 'BEGIN { exit !(actual <= limit) }'; then
    echo "$1=$actual exceeds approved limit $limit" >&2
    exit 1
  fi
}

verdict_count=$(grep -Ec '^verdict: (UNKNOWN|FAIL|PASS)$' "$index" || true)
if [ "$verdict_count" -ne 1 ]; then
  echo "Index must contain exactly one verdict: UNKNOWN, FAIL, or PASS: $index" >&2
  exit 1
fi
verdict=$(field verdict)
protocol=$(field protocol)
case "$protocol" in SP-06|SP-07) ;; *)
  echo "Index must declare protocol: SP-06 or SP-07" >&2
  exit 1
esac

case "$verdict" in
  UNKNOWN|FAIL)
    echo "$directory: $verdict (not physical PASS)"
    exit 0
    ;;
  PASS) ;;
esac

raw_trace=$(safe_relative_file "$(require_field raw_trace)")
artifact_path=$(safe_relative_file "$(require_field artifact_path)")
artifact=$(require_field artifact_sha256)
if ! printf '%s' "$artifact" | grep -Eq '^[0-9a-f]{64}$'; then
  echo "PASS requires the 64-character SHA-256 of the measured artifact" >&2
  exit 1
fi
actual_digest=$(shasum -a 256 "$artifact_path" | awk '{print $1}')
if [ "$actual_digest" != "$artifact" ]; then
  echo "artifact_sha256 does not match existing artifact_path" >&2
  exit 1
fi
for metadata in device os_build launcher brightness network scenario duration; do
  require_field "$metadata" >/dev/null
done

case "$protocol" in
  SP-06)
    at_most p95_frame_ms 34
    at_most p99_frame_ms 50
    at_most max_warm_stall_ms 100
    at_most response_ms 100
    ;;
  SP-07)
    static_energy_mah=$(number static_energy_mah)
    normal_energy_mah=$(number normal_energy_mah)
    reduced_energy_mah=$(number reduced_energy_mah)
    if ! awk -v value="$static_energy_mah" 'BEGIN { exit !(value > 0) }'; then
      echo "static_energy_mah must be greater than zero" >&2
      exit 1
    fi
    normal_delta=$(awk -v value="$normal_energy_mah" -v base="$static_energy_mah" 'BEGIN { printf "%.8f", (value - base) / base * 100 }')
    reduced_delta=$(awk -v value="$reduced_energy_mah" -v base="$static_energy_mah" 'BEGIN { printf "%.8f", (value - base) / base * 100 }')
    if ! awk -v value="$normal_delta" 'BEGIN { exit !(value <= 10) }'; then
      echo "normal energy delta $normal_delta% exceeds approved +10%" >&2
      exit 1
    fi
    if ! awk -v value="$reduced_delta" 'BEGIN { exit !(value <= 5) }'; then
      echo "reduced energy delta $reduced_delta% exceeds approved +5%" >&2
      exit 1
    fi
    ;;
esac

echo "$directory: PASS ($protocol) with raw trace ${raw_trace#"$directory/"}"
