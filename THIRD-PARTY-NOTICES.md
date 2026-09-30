# Third-party notices

The root MIT license covers project-authored code and documentation. It does not replace licenses or copyright notices for third-party material.

| Component | Origin | License and notice |
| --- | --- | --- |
| Bundled BMAD skills | [BMAD-METHOD](https://github.com/bmad-code-org/BMAD-METHOD), pinned installer `6.11.0` | [BMAD MIT notice](docs/licenses/BMAD-MIT.txt), BMad Code, LLC and contributors |
| UI/UX Pro Max skill and local data | [UI UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill), bundled repository snapshot | [Upstream MIT notice](docs/licenses/UI-UX-PRO-MAX-MIT.txt), Next Level Builder; local modifications retain the upstream terms |
| Clock fonts | Google Fonts; original source recorded per set | SIL Open Font License 1.1, notices in `android/sets/*/*_OFL.txt` |
| Gradle wrapper | [Gradle](https://github.com/gradle/gradle) | Apache License 2.0; [license](https://github.com/gradle/gradle/blob/master/LICENSE) |

Bundled font families: Anton, Bodoni Moda, Cormorant Garamond, Manrope, Noto Serif, Space Grotesk and Space Mono. Preserve each set's OFL notice alongside its font; the author's artwork restrictions do not apply to those fonts. UI/UX skill data may reference other third-party resources; those references do not grant a license to copy the referenced artwork or fonts.

Android/Maven dependencies are resolved from their published distributions. The version catalogue is `android/gradle/libs.versions.toml`; retain the applicable dependency notices when distributing builds. This inventory is not a claim that every transitive dependency has been audited for a binary release.
