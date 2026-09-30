# Contributing

Start with [building](docs/BUILDING.md), [architecture](docs/architecture/README.md) and [content development](docs/CONTENT.md). Agent tooling is optional; see [setup](docs/AGENTS-SETUP.md).

Before editing, state the user outcome, affected modules and selected checks. Keep the change focused. Run the relevant tests and module build, inspect the final diff and run `git diff --check`. UI changes need a manual path and the applicable accessibility/host checks. Record the device, OS and launcher when reporting native behaviour. Screenshots and emulators do not establish physical battery performance.

Choose the checks for the changed boundary rather than copying an old test total. Explain what passed, what was skipped and what remains unknown in the pull request. Obtain independent review and resolve supported blockers before acceptance.

Art changes require the author's approval of the exact revision before dependent integration. Do not redistribute the bundled artwork under MIT. New visual work must retain sources, license information and approvals; see [artwork terms](ASSET-LICENSE.md).

Do not commit `.env`, signing keys, local SDK paths, credentials, generated builds or raw device logs. Report a suspected credential privately to the maintainer through an available private contact; do not put its value in an issue.

By contributing code or documentation, you agree to license your contribution under the repository's MIT terms unless an existing third-party license applies. Artwork contributions require a separate written license and approval. Submitting a contribution is not permission to publish an app release or push to the owner's remote.
