package app.livosphere.buildlogic;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/** Resolves component ownership without treating a contribution's dependencies as shell-owned. */
record VariantProjectClosure(Set<String> runtimeComponents, Set<String> runtimeProjects, Set<String> excludedProjects) {
    static VariantProjectClosure resolve(Project app, String variant, VariantContentSelection selection) {
        Set<String> runtimeComponents = components(app, variant);
        Set<String> runtimeProjects = projectPaths(runtimeComponents);
        Set<String> excludedComponents = new TreeSet<>();
        Set<String> excludedProjects = new TreeSet<>();
        for (String path : selection.excludedProjects()) {
            Set<String> closure = components(app.getRootProject().project(path), variant);
            excludedComponents.addAll(closure);
            excludedProjects.addAll(projectPaths(closure));
        }

        Set<String> contributionRoots = new TreeSet<>(selection.projects());
        contributionRoots.addAll(selection.excludedProjects());
        Set<String> neutralComponents = new TreeSet<>();
        for (DependencyResult edge : runtimeRoot(app, variant).getDependencies()) {
            if (!(edge instanceof ResolvedDependencyResult resolved)) continue;
            String root = identity(resolved.getSelected().getId());
            if (!contributionRoots.contains(root)) neutralComponents.addAll(reachable(resolved.getSelected()));
        }
        Set<String> neutralProjects = projectPaths(neutralComponents);

        // A neutral bridge may share its own transitive dependencies, but never an excluded
        // contribution root. This is deliberately applied after the neutral subtraction.
        excludedComponents.removeAll(neutralComponents);
        excludedComponents.addAll(selection.excludedProjects());
        Set<String> componentLeaks = new TreeSet<>(runtimeComponents);
        componentLeaks.retainAll(excludedComponents);
        if (!componentLeaks.isEmpty())
            throw new GradleException("Excluded contribution in transitive " + variant + " runtime graph: " + componentLeaks);

        Set<Path> runtimeFiles = declaredFiles(app, runtimeProjects, variant);
        Set<Path> excludedFiles = declaredFiles(app, excludedProjects, variant);
        // A direct shell dependency and complete non-contribution first-level closures are
        // neutral owners. Selected contributions never become neutral seeds.
        Set<Path> neutralFiles = declaredFiles(app, Set.of(app.getPath()), variant);
        neutralFiles.addAll(declaredFiles(app, neutralProjects, variant));
        excludedFiles.removeAll(neutralFiles);
        runtimeFiles.retainAll(excludedFiles);
        if (!runtimeFiles.isEmpty())
            throw new GradleException("Excluded local binary in " + variant + " runtime graph: " + runtimeFiles);
        return new VariantProjectClosure(runtimeComponents, runtimeProjects, excludedProjects);
    }

    private static ResolvedComponentResult runtimeRoot(Project project, String variant) {
        Configuration configuration = runtimeConfiguration(project, variant);
        if (configuration == null || !configuration.isCanBeResolved())
            throw new GradleException("Runtime configuration missing for " + project.getPath() + " variant " + variant);
        return configuration.getIncoming().getResolutionResult().getRoot();
    }

    private static Set<String> components(Project project, String variant) {
        Set<String> result = new TreeSet<>(); result.add(project.getPath());
        Configuration configuration = runtimeConfiguration(project, variant);
        if (configuration != null && configuration.isCanBeResolved())
            configuration.getIncoming().getResolutionResult().getAllComponents()
                    .forEach(component -> result.add(identity(component.getId())));
        return result;
    }

    private static Configuration runtimeConfiguration(Project project, String variant) {
        Configuration variantRuntime = project.getConfigurations().findByName(variant + "RuntimeClasspath");
        return variantRuntime != null ? variantRuntime : project.getConfigurations().findByName("runtimeClasspath");
    }

    private static Set<String> reachable(ResolvedComponentResult root) {
        Set<String> result = new TreeSet<>(); collect(root, result); return result;
    }

    private static void collect(ResolvedComponentResult component, Set<String> result) {
        if (!result.add(identity(component.getId()))) return;
        for (DependencyResult edge : component.getDependencies())
            if (edge instanceof ResolvedDependencyResult resolved) collect(resolved.getSelected(), result);
    }

    private static String identity(ComponentIdentifier component) {
        return component instanceof ProjectComponentIdentifier project ? project.getProjectPath() : component.getDisplayName();
    }

    private static Set<String> projectPaths(Set<String> components) {
        Set<String> result = new TreeSet<>();
        for (String component : components) if (component.startsWith(":")) result.add(component);
        return result;
    }

    private static Set<Path> declaredFiles(Project root, Set<String> paths, String variant) {
        Set<Path> result = new TreeSet<>();
        for (String path : paths) {
            Project project = root.getRootProject().findProject(path);
            if (project == null) continue;
            for (String name : Set.of("api", "implementation", "runtimeOnly", variant + "Api",
                    variant + "Implementation", variant + "RuntimeOnly", variant + "RuntimeClasspath", "runtimeClasspath")) {
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
