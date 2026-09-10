package app.livosphere.buildlogic;

import java.util.Set;
import java.util.TreeSet;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

/** Resolve the real graph, retaining ownership of private transitive dependencies. */
record VariantProjectClosure(Set<String> runtime, Set<String> excluded) {
    static VariantProjectClosure resolve(Project app, String variant, VariantContentSelection selection) {
        Set<String> runtime = projectPaths(components(app, variant));
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
        // A shell-owned bridge may share core code, but it cannot launder an excluded
        // contribution itself.  Retaining contribution roots makes neutral -> excluded edges
        // fail even when Gradle's component graph also lists the bridge as shell-owned.
        Set<String> excludedContributionRoots = new TreeSet<>(selection.excludedProjects());
        excluded.removeAll(neutral);
        excluded.addAll(excludedContributionRoots);
        Set<String> leaks = new TreeSet<>(runtime); leaks.retainAll(excluded);
        if (!leaks.isEmpty()) throw new GradleException("Excluded contribution in transitive " + variant + " runtime graph: " + leaks);
        Set<Path> runtimeFiles = declaredFiles(app, runtime, variant);
        Set<Path> excludedFiles = declaredFiles(app, projectPaths(excluded), variant);
        excludedFiles.removeAll(declaredFiles(app, Set.of(app.getPath()), variant));
        runtimeFiles.retainAll(excludedFiles);
        if (!runtimeFiles.isEmpty()) throw new GradleException("Excluded local binary in " + variant + " runtime graph: " + runtimeFiles);
        return new VariantProjectClosure(runtime, excluded);
    }

    private static Set<String> projectPaths(Set<String> components) {
        Set<String> result = new TreeSet<>();
        for (String component : components) if (component.startsWith(":")) result.add(component);
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

    private static Set<Path> declaredFiles(Project root, Set<String> paths, String variant) {
        Set<Path> result = new TreeSet<>();
        for (String path : paths) {
            Project project = root.getRootProject().findProject(path);
            if (project == null) continue;
            for (String name : Set.of("implementation", "runtimeOnly", variant + "Implementation", variant + "RuntimeOnly")) {
                Configuration configuration = project.getConfigurations().findByName(name);
                if (configuration == null) continue;
                configuration.getAllDependencies().withType(FileCollectionDependency.class).forEach(dependency ->
                        dependency.getFiles().getFiles().forEach(file -> result.add(canonical(file.toPath()))));
            }
        }
        return result;
    }

    private static Path canonical(Path path) {
        try { return path.toRealPath(); }
        catch (Exception error) { throw new GradleException("Cannot resolve declared local binary " + path, error); }
    }

}
