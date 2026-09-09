package app.livosphere.buildlogic;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;

/** The only selection policy used by dependencies, generators, validation and APK inventory. */
record VariantContentSelection(String buildType, List<SetManifest> selected, List<SetManifest> excluded) {
    static VariantContentSelection select(List<SetManifest> manifests, String buildType) {
        // Every non-debug variant is a candidate. A custom build type cannot accidentally publish debug art.
        boolean debug = buildType.equals("debug");
        return new VariantContentSelection(buildType,
                manifests.stream().filter(m -> debug || m.releaseEligible()).toList(),
                manifests.stream().filter(m -> !debug && !m.releaseEligible()).toList());
    }

    void requireNonEmpty() {
        if (selected.isEmpty()) throw new GradleException("Variant " + buildType
                + ": empty public content closure; requires schema2 public + html-approved. No candidate may be packaged.");
    }

    Set<String> projects() { return projects(selected); }
    Set<String> excludedProjects() { return projects(excluded); }
    private static Set<String> projects(List<SetManifest> manifests) {
        return manifests.stream().flatMap(m -> m.contributions().stream())
                .filter(c -> !c.surface().equals("watchface"))
                .map(SetManifest.Contribution::artifactProject).collect(Collectors.toSet());
    }
}
