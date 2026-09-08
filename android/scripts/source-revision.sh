#!/bin/sh
set -eu

[ "$#" -eq 1 ] || { echo "usage: $0 <repository-root>" >&2; exit 64; }
repo_root=$(CDPATH= cd -- "$1" && pwd -P)
head=$(git -C "$repo_root" rev-parse HEAD)
if [ -n "$(git -C "$repo_root" status --porcelain --untracked-files=all)" ]; then
    printf 'WORKTREE_UNCOMMITTED@%s\n' "$head"
else
    printf '%s\n' "$head"
fi
