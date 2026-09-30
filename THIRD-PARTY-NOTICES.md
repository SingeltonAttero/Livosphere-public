# Third-party notices

The root MIT license covers project-authored code and documentation. It does not replace licenses or copyright notices for third-party material.

| Component | Origin | License and notice |
| --- | --- | --- |
| Clock fonts | Google Fonts; original source recorded per set | SIL Open Font License 1.1, notices in `android/sets/*/*_OFL.txt` |
| Design font Inter | [Inter](https://github.com/rsms/inter), preserved WOFF2 | [SIL OFL 1.1](docs/licenses/Inter-OFL.txt), The Inter Project Authors |
| Gradle wrapper | [Gradle](https://github.com/gradle/gradle) | Apache License 2.0; [license](https://github.com/gradle/gradle/blob/master/LICENSE) |


Font families: Anton, Bodoni Moda, Cormorant Garamond, Manrope, Noto Serif, Space Grotesk and Space Mono. Preserve the OFL notices alongside each bundled font.

Android/Maven dependencies are resolved from their published distributions. The version catalogue is `android/gradle/libs.versions.toml`; retain the applicable dependency notices when distributing builds. This inventory is not a claim that every transitive dependency has been audited for a binary release.
