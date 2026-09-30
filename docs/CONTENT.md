# Content development

A collection links independently usable wallpaper and clock contributions. Current schemas and checks are implemented in `android/core/contract`, `android/build-logic`, and the manifests under `android/sets/`. Read the parser and a current neighbouring set before adding fields; this guide does not substitute a new schema for the code.

## Workflow

1. Define the surface: `hub`, `wallpapers/<id>`, `widgets/<id>` or the relationship `sets/<id>`.
2. Record a brief, source rights and a revision in `docs/content/<surface>/<id>/`. Reuse an existing workspace if present.
3. For wallpaper artwork, obtain approval of the static composition before its dependent motion prototype. For clocks, review S/M/L and legibility. Keep each surface's design and experience separate.
4. Review the applicable HTML behaviour and obtain approval of the exact revision before native integration. HTML is a model, not an installed WallpaperService or AppWidget.
5. Add accepted assets and contributions through the manifest. Preserve source provenance and font licenses. Never mark fixtures public or bypass validation.
6. Run checks chosen for the changed boundary, build the affected modules, review the diff independently and provide a device verification path.

New live wallpaper work describes four local-time environments, object-level motion, triggers, interruption and reduced/off behaviour where applicable. Widget work describes native host geometry, S/M/L, current time and per-instance configuration. Runtime synchronisation between wallpaper and clock is not implied by their artistic relationship.

Shared mechanisms must serve an accepted concrete need; do not expand to an unlimited renderer or network marketplace by default. A missing standalone packaging capability must be implemented and tested, rather than hidden with a dummy companion contribution.

## Where to start

- `android/core/contract/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt`: descriptors.
- `android/sets/<id>/manifest/set.properties`: contributions and revisions.
- `android/sets/<id>/approval.md`, `source-assets/PROVENANCE.md`: acceptance and source records.
- `android/hub/app`: catalogue and system-flow adapters.
- `android/widgets/runtime`: clocks.
- `android/wallpapers/engine`: shared phase and lifecycle policies.
- `.agents/skills/livosphere-content/SKILL.md`: project workflow and templates.

Keep new decisions and source records in these public workspaces so another checkout can continue the work. Historical private design references document origins only; they are not required inputs to the current build. Bundled artwork is covered by [separate terms](../ASSET-LICENSE.md).
