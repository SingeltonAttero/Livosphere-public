# Developer handoff

Baseline: 30 September 2026. Maintainer and application author: Yakov Weber (Вебер Яков).

## Start on another machine

Read [building](BUILDING.md), run `make doctor` and `make phone`, then read [architecture](architecture/README.md). For agent-assisted work, follow [agent setup](AGENTS-SETUP.md). Neither the owner's signing key nor the historical planning archive is needed for a debug build. Accepted planning documents and editable design are linked from [docs index](README.md).

## Current product

Android phone catalogue, independent live wallpapers and S/M/L clocks. The current manifest baseline selects 13 public collections. Runtime scene phases, native widget instances and local asset validation are implemented. The repository contains source, fixtures and tests; presence of a test is not a claim that it passed on your device.

The owner reports that the application has been submitted to RuStore and says publication is in progress. The public listing URL and the exact store-delivered build are not yet recorded. Physical performance, battery, launcher compatibility and final store delivery remain separate acceptance checks. Do not advertise a published release solely from a successful local build.

## Continue development

Choose a concrete outcome and affected surface. Check current code and manifests, record a small plan and selected checks, implement a focused change, run those checks and arrange independent review. Art revisions need the author's explicit approval. Use `docs/content/` for new content workspaces and `docs/decisions/` for accepted decisions.

The optional BMAD installation restores local support and points to checked-in planning and the canonical sprint state. Read [the docs index](README.md) and current implementation before using it; do not create a second tracker or fabricate completed historical stories. If the owner needs historical release proofs on another machine, transfer that private archive separately.

## Next release checkpoint

Record the verified RuStore listing URL and exact delivered package/version. Bind physical functional, performance and battery observations to that artifact, device, OS and launcher. Update the README store link and this handoff only when those facts are available. GitHub source publication and RuStore application release have separate acceptance paths.
