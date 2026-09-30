#!/bin/sh
set -eu
repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
valid_runtime() {
    [ -f "$1/scripts/resolve_config.py" ] &&
    [ -f "$1/scripts/resolve_customization.py" ] &&
    [ -f "$1/scripts/render_skill.py" ] &&
    [ -f "$1/scripts/config_utils.py" ] &&
    [ -f "$1/scripts/memlog.py" ] &&
    [ -f "$1/_config/bmad-help.csv" ] &&
    [ -f "$1/config.toml" ] &&
    [ -f "$1/_config/manifest.yaml" ] &&
    grep -q '^  version: 6\.11\.0$' "$1/_config/manifest.yaml"
}
if [ -e "$repo_root/_bmad" ] || [ -L "$repo_root/_bmad" ]; then
    valid_runtime "$repo_root/_bmad" || { echo "Existing BMAD support is incomplete or differs from 6.11.0. Preserve it and repair explicitly; no files changed." >&2; exit 1; }
    echo "BMAD 6.11.0 support already exists; leaving it and bundled skills untouched."
    exit 0
fi
command -v npx >/dev/null 2>&1 || { echo "Install Node.js 20.12+ and npm/npx first." >&2; exit 1; }
command -v uv >/dev/null 2>&1 || { echo "Install uv first; BMAD skills require it." >&2; exit 1; }
mkdir -p "$repo_root/.local"
lock_dir="$repo_root/.local/agent-setup.lock"
mkdir "$lock_dir" 2>/dev/null || { echo "Another setup is active, or an interrupted setup left $lock_dir. Check before retrying." >&2; exit 1; }
setup_dir=
stage_dir=
cleanup() {
    [ -z "$setup_dir" ] || rm -rf "$setup_dir"
    [ -z "$stage_dir" ] || rm -rf "$stage_dir"
    rmdir "$lock_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
setup_dir=$(mktemp -d "${TMPDIR:-/tmp}/livosphere-bmad-setup.XXXXXX")
stage_dir=$(mktemp -d "$repo_root/.local/bmad-stage.XXXXXX")
npx --yes bmad-method@6.11.0 install --directory "$setup_dir" --modules bmm --tools codex --yes \
    --set core.project_name=Livosphere --set core.communication_language=Russian \
    --set core.document_output_language=Russian --set bmm.project_knowledge=docs
valid_runtime "$setup_dir/_bmad" || { echo "Pinned installer did not produce expected 6.11.0 support files." >&2; exit 1; }
# Stage on the checkout filesystem, then rename. Bundled skills are never overwritten.
cp -R "$setup_dir/_bmad" "$stage_dir/_bmad"
[ ! -e "$repo_root/_bmad" ] && [ ! -L "$repo_root/_bmad" ] || { echo "BMAD directory appeared during setup; leaving it untouched." >&2; exit 1; }
mv "$stage_dir/_bmad" "$repo_root/_bmad"
echo "BMAD 6.11.0 support restored. Read docs/HANDOFF.md before starting work."
