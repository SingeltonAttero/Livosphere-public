package app.livosphere.buildlogic;

import com.android.build.api.attributes.BuildTypeAttr;
import com.android.build.api.dsl.ApplicationExtension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;

/** Resolves actual AGP variant ownership without treating contribution dependencies as shell-owned. */
record VariantProjectClosure(Set<String> runtimeComponents, Set<String> runtimeProjects, Set<String> excludedProjects,
        Map<String, String> projectVariants) {
    static VariantProjectClosure resolve(Project app, String requestedVariant, VariantContentSelection selection) {
        List<String> candidates = configuredVariants(app, requestedVariant);
        VariantGraph runtime = graph(app, candidates);
        Set<String> runtimeProjects = projectPaths(runtime.components());
        Set<String> excludedComponents = new TreeSet<>();
        Set<String> excludedGraphProjects = new TreeSet<>();
        Map<String, String> variants = new TreeMap<>(runtime.projectVariants());
        for (String path : selection.excludedProjects()) {
            // Excluded roots do not participate in the app graph, so only the app's explicit
            // matchingFallbacks may choose their real runtime configuration.
            VariantGraph closure = graph(app.getRootProject().project(path), candidates);
            excludedComponents.addAll(closure.components());
            excludedGraphProjects.addAll(projectPaths(closure.components()));
            mergeVariants(variants, closure.projectVariants());
        }

        Set<String> contributionRoots = new TreeSet<>(selection.projects());
        contributionRoots.addAll(selection.excludedProjects());
        Set<String> neutralComponents = new TreeSet<>();
        Map<String, String> neutralVariants = new TreeMap<>();
        for (DependencyResult edge : runtime.root().getDependencies()) {
            if (!(edge instanceof ResolvedDependencyResult resolved)) continue;
            String root = identity(resolved.getSelected().getId());
            if (!contributionRoots.contains(root)) collect(resolved.getSelected(), neutralComponents, neutralVariants);
        }
        mergeVariants(variants, neutralVariants);
        Set<String> neutralProjects = projectPaths(neutralComponents);

        // A neutral bridge may share its own transitive dependencies, but never an excluded
        // contribution root. This is deliberately applied after neutral subtraction.
        excludedComponents.removeAll(neutralComponents);
        excludedComponents.addAll(selection.excludedProjects());
        Set<String> componentLeaks = new TreeSet<>(runtime.components());
        componentLeaks.retainAll(excludedComponents);
        if (!componentLeaks.isEmpty())
            throw new GradleException("Excluded contribution in transitive " + requestedVariant + " runtime graph: " + componentLeaks);

        Set<Path> runtimeFiles = declaredFiles(app, runtimeProjects, variants);
        Set<Path> excludedFiles = declaredFiles(app, excludedGraphProjects, variants);
        Set<Path> neutralFiles = declaredFiles(app, Set.of(app.getPath()), variants);
        neutralFiles.addAll(declaredFiles(app, neutralProjects, variants));
        excludedFiles.removeAll(neutralFiles);
        runtimeFiles.retainAll(excludedFiles);
        if (!runtimeFiles.isEmpty())
            throw new GradleException("Excluded local binary in " + requestedVariant + " runtime graph: " + runtimeFiles);

        Set<String> privateExcludedProjects = projectPaths(excludedComponents);
        Map<String, String> sourceVariants = new TreeMap<>();
        for (String path : runtimeProjects) sourceVariants.put(path, requireVariant(variants, path));
        for (String path : privateExcludedProjects) sourceVariants.put(path, requireVariant(variants, path));
        return new VariantProjectClosure(runtime.components(), runtimeProjects, privateExcludedProjects, sourceVariants);
    }

    private static List<String> configuredVariants(Project app, String requested) {
        List<String> result = new ArrayList<>(); result.add(requested);
        ApplicationExtension android = app.getExtensions().findByType(ApplicationExtension.class);
        if (android == null) throw new GradleException("Application extension missing for " + app.getPath());
        var buildType = android.getBuildTypes().findByName(requested);
        if (buildType == null) throw new GradleException("Application build type missing for " + requested);
        for (String fallback : buildType.getMatchingFallbacks()) if (!result.contains(fallback)) result.add(fallback);
        return result;
    }

    private static VariantGraph graph(Project project, List<String> candidates) {
        RuntimeConfiguration runtime = runtimeConfiguration(project, candidates);
        Set<String> components = new TreeSet<>();
        Map<String, String> variants = new TreeMap<>();
        components.add(project.getPath());
        variants.put(project.getPath(), runtime.variant());
        collect(runtime.configuration().getIncoming().getResolutionResult().getRoot(), components, variants);
        return new VariantGraph(components, variants, runtime.configuration().getIncoming().getResolutionResult().getRoot());
    }

    private static RuntimeConfiguration runtimeConfiguration(Project project, List<String> candidates) {
        for (String candidate : candidates) {
            Configuration configuration = project.getConfigurations().findByName(candidate + "RuntimeClasspath");
            if (configuration != null && configuration.isCanBeResolved()) return new RuntimeConfiguration(configuration, candidate);
        }
        Configuration plain = project.getConfigurations().findByName("runtimeClasspath");
        if (plain != null && plain.isCanBeResolved()) return new RuntimeConfiguration(plain, "runtime");
        throw new GradleException("No runtime configuration for " + project.getPath() + " among configured variants " + candidates);
    }

    private static void collect(ResolvedComponentResult component, Set<String> components, Map<String, String> variants) {
        components.add(identity(component.getId()));
        for (DependencyResult edge : component.getDependencies()) {
            if (!(edge instanceof ResolvedDependencyResult resolved)) continue;
            ComponentIdentifier selected = resolved.getSelected().getId();
            String identity = identity(selected);
            if (selected instanceof ProjectComponentIdentifier) {
                BuildTypeAttr buildType = resolved.getResolvedVariant().getAttributes().getAttribute(BuildTypeAttr.ATTRIBUTE);
                if (buildType != null) putVariant(variants, identity, buildType.getName());
            }
            if (components.add(identity)) collect(resolved.getSelected(), components, variants);
        }
    }

    private static void mergeVariants(Map<String, String> target, Map<String, String> source) {
        source.forEach((path, variant) -> putVariant(target, path, variant));
    }

    private static void putVariant(Map<String, String> variants, String path, String variant) {
        String current = variants.putIfAbsent(path, variant);
        if (current != null && !current.equals(variant))
            throw new GradleException("Ambiguous selected runtime variants for " + path + ": " + current + ", " + variant);
    }

    private static String requireVariant(Map<String, String> variants, String path) {
        String variant = variants.get(path);
        if (variant == null || variant.equals("runtime"))
            throw new GradleException("Selected Android runtime variant missing for " + path);
        return variant;
    }

    private static String identity(ComponentIdentifier component) {
        return component instanceof ProjectComponentIdentifier project ? project.getProjectPath() : component.getDisplayName();
    }

    private static Set<String> projectPaths(Set<String> components) {
        Set<String> result = new TreeSet<>();
        for (String component : components) if (component.startsWith(":")) result.add(component);
        return result;
    }

    private static Set<Path> declaredFiles(Project root, Set<String> paths, Map<String, String> variants) {
        Set<Path> result = new TreeSet<>();
        for (String path : paths) {
            Project project = root.getRootProject().findProject(path);
            if (project == null) continue;
            String variant = variants.get(path);
            List<String> names = new ArrayList<>(List.of("api", "implementation", "runtimeOnly", "runtimeClasspath"));
            if (variant != null && !variant.equals("runtime")) {
                names.add(variant + "Api"); names.add(variant + "Implementation");
                names.add(variant + "RuntimeOnly"); names.add(variant + "RuntimeClasspath");
            }
            for (String name : names) {
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

    private record RuntimeConfiguration(Configuration configuration, String variant) {}
    private record VariantGraph(Set<String> components, Map<String, String> projectVariants, ResolvedComponentResult root) {}
}
