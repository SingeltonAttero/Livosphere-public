package app.livosphere.buildlogic;

import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

/** Resolve the real graph, retaining ownership of private transitive dependencies. */
record VariantProjectClosure(Set<String> runtime, Set<String> excluded) {
    static VariantProjectClosure resolve(Project app, String variant, VariantContentSelection selection) {
        Set<String> runtime = projects(app, variant);
        Set<String> excluded = new TreeSet<>();
        // Resolve the same build type/fallback that AGP selected for the app. Inspecting an
        // excluded debug contribution must never build that contribution for a release audit.
        selection.excludedProjects().forEach(path -> excluded.addAll(components(app.getRootProject().project(path), variant)));
        // Neutral runtime must be explicitly owned by the shell, independently of any contribution.
        // Merely adding an excluded private module to a public contribution cannot make it shared.
        Set<String> contributionProjects = new TreeSet<>(selection.projects());
        contributionProjects.addAll(selection.excludedProjects());
        Set<String> neutral = new TreeSet<>();
        for (String bucket : Set.of("api", "implementation", "runtimeOnly")) {
            var config = app.getConfigurations().findByName(bucket);
            if (config == null) continue;
            config.getDependencies().withType(ProjectDependency.class).forEach(dependency -> {
                if (!contributionProjects.contains(dependency.getPath()))
                    neutral.addAll(components(app.getRootProject().project(dependency.getPath()), variant));
            });
        }
        // Only an explicitly shell-owned dependency can be neutral. An excluded contribution
        // (or any of its private dependencies) is never removed through a neutral bridge.
        excluded.removeAll(neutral);
        Set<String> leaks = new TreeSet<>(runtime); leaks.retainAll(excluded);
        if (!leaks.isEmpty()) throw new GradleException("Excluded contribution in transitive " + variant + " runtime graph: " + leaks);
        return new VariantProjectClosure(runtime, excluded);
    }

    private static Set<String> projects(Project project, String variant) {
        Set<String> result = new TreeSet<>();
        for (String component : components(project, variant)) if (component.startsWith(":")) result.add(component);
        return result;
    }

    private static Set<String> components(Project project, String variant) {
        Set<String> result = new TreeSet<>(); result.add(project.getPath());
        var config = project.getConfigurations().findByName(variant + "RuntimeClasspath");
        if (config == null) config = project.getConfigurations().findByName("runtimeClasspath");
        if (config != null && config.isCanBeResolved()) config.getIncoming().getResolutionResult().getAllComponents().forEach(component -> {
            ComponentIdentifier id = component.getId();
            result.add(id instanceof ProjectComponentIdentifier projectId ? projectId.getProjectPath() : id.getDisplayName());
        });
        return result;
    }
}
