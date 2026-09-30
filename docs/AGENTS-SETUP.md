# Agent setup on another machine

The repository includes `.agents/skills/` with project content templates, UI/UX search data and scripts, and BMAD skill instructions. Codex and tools supporting this directory can discover them from the checkout. The app builds without an agent or BMAD runtime.

## Restore BMAD support

Prerequisites: Node.js 20.12+ with npm/npx, Python 3, and `uv` for BMAD's Python helpers. From the repository root:

```sh
./scripts/setup-agents.sh
```

The script installs **bmad-method 6.11.0** in a temporary directory, then copies only `_bmad/` support into the checkout. It validates an existing runtime before returning, refuses an incomplete or different-version installation, and stages new support before an atomic rename. It refreshes local `_bmad/custom/config.toml` from tracked `.agents/bmad/config.toml`: planning points to `docs/planning`, implementation to `docs/development`, and project knowledge to `docs`. Personal user overrides remain separate and are preserved. It preserves tracked `.agents/skills/`, `AGENTS.md`, project documentation and existing BMAD workspaces. The installer may advertise a newer version; do not accept an upgrade implicitly. Network access is required on first installation. Review the pinned installer if you need an offline package cache.

Generated runtime and historical `_bmad-output/` files are ignored. Do not commit them to reintroduce the private archive. New shareable decisions and content workspaces belong in `docs/decisions/` and `docs/content/`. Preserve relevant context in a checked-in handoff, not only in chat or personal memory.

## Continue a change

Read `AGENTS.md`, `docs/HANDOFF.md`, the relevant public guide and current implementation. Ask the agent to use `bmad-help` for orientation, then `bmad-build` for a concrete change or `livosphere-content` for a content cycle. Provide the user outcome, affected area and selected checks. The accepted documents and canonical sprint state are already under `docs/`. Read `docs/README.md`; do not bootstrap a greenfield product or invent historical stories.

`ui-ux-pro-max` is self-contained and uses Python 3 with its bundled data. Example:

```sh
python3 .agents/skills/ui-ux-pro-max/scripts/search.py "clock readability" --stack jetpack-compose
```

Content artwork approvals are explicit and bound to a revision. Image generation and Pencil integration depend on tools available in your own agent environment; downloading this repository does not install those services or credentials. Work without them on code and documentation, or obtain the relevant tool when the task requires it.
