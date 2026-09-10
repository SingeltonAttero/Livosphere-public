package app.livosphere.buildlogic;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Variant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

/** Root-owned, early AGP source model for APK closure audit inputs. */
public final class AndroidVariantSourceCollector {
    static final String EXTENSION_NAME = "livosphereAndroidVariantSourceCollector";
    private final Project root;
    private final Map<String, Map<String, VariantInputs>> variants = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<RegisteredGeneratedRoot>>> registeredGeneratedRoots = new ConcurrentHashMap<>();
    private final Set<String> observedProjects = ConcurrentHashMap.newKeySet();

    AndroidVariantSourceCollector(Project root) { this.root = root; }

    void install() {
        root.getAllprojects().forEach(this::observe);
    }

    private void observe(Project project) {
        project.getPluginManager().withPlugin("com.android.application", ignored -> observeAndroid(project));
        project.getPluginManager().withPlugin("com.android.library", ignored -> observeAndroid(project));
    }

    private void observeAndroid(Project project) {
        if (!observedProjects.add(project.getPath())) return;
        // AGP exposes Kotlin and Gradle Action overloads with captured VariantT. Keep the
        // raw bridge contained here; every value is immediately consumed as Variant.
        @SuppressWarnings("rawtypes") AndroidComponentsExtension components = project.getExtensions()
                .getByType(AndroidComponentsExtension.class);
        @SuppressWarnings({"rawtypes", "unchecked"}) Action callback = (Action<Variant>) variant -> {
            variants.computeIfAbsent(project.getPath(), ignored -> new ConcurrentHashMap<>())
                    .put(variant.getName(), VariantInputs.from(variant));
        };
        components.onVariants(components.selector().all(), callback);
    }

    void configureAuditInputs(AuditSetApkTask task) { task.getAndroidSourceCollector().set(this); }

    /**
     * The set-contract generator has a deterministic output beneath this project's configured
     * build directory.  Register it separately from AGP's generated-source Provider: reading
     * that Provider for an excluded contribution asks Gradle to realize its producer.
     */
    void registerCanonicalGeneratedResourceRoot(Project project, String variant, String relativeOutput) {
        registerGeneratedRoot(project, variant, "res", relativeOutput, false);
    }

    /**
     * Opt-in contract for a non-canonical generated root that must be checked even when its
     * contribution is excluded.  The relative path is resolved from the project's actual
     * buildDirectory, so projects that relocate build output remain supported.
     */
    public void registerRequiredGeneratedRoot(Project project, String variant, String sourceType, String relativeOutput) {
        if (!Set.of("java", "kotlin", "resources", "res", "assets").contains(sourceType))
            throw new GradleException("Unsupported generated Android source type: " + sourceType);
        if (relativeOutput.isBlank() || relativeOutput.startsWith("/") || relativeOutput.contains(".."))
            throw new GradleException("Generated Android source output must be a buildDirectory-relative path");
        registerGeneratedRoot(project, variant, sourceType, relativeOutput, true);
    }

    private void registerGeneratedRoot(Project project, String variant, String sourceType, String relativeOutput,
            boolean required) {
        String path = project.getLayout().getBuildDirectory().dir(relativeOutput).get().getAsFile().getAbsolutePath();
        registeredGeneratedRoots.computeIfAbsent(project.getPath(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(variant, ignored -> new ArrayList<>())
                .add(new RegisteredGeneratedRoot(sourceType, path, required));
    }

    SourceMetadata sourceMetadata(String project, String variant, boolean includeGenerated) {
        VariantInputs inputs = variants.getOrDefault(project, Map.of()).get(variant);
        if (inputs == null) throw new GradleException("Android variant source metadata missing for " + project + " " + variant);
        List<RegisteredGeneratedRoot> registered = registeredGeneratedRoots
                .getOrDefault(project, Map.of()).getOrDefault(variant, List.of());
        if (!includeGenerated) {
            for (RegisteredGeneratedRoot root : registered) if (root.required && !new java.io.File(root.path).isDirectory())
                throw new GradleException("Registered generated Android source missing for " + project + " " + variant
                        + "; excluded generators are not executed: " + root.path);
            List<String> unsupported;
            try {
                unsupported = inputs.unregisteredGeneratedRoots(registered);
            } catch (Exception error) {
                throw new GradleException("Unsupported generated Android source input for excluded contribution " + project + " "
                        + variant + "; AGP metadata cannot be inspected without executing the excluded producer");
            }
            if (!unsupported.isEmpty()) throw new GradleException("Unsupported generated Android source input for excluded contribution "
                    + project + " " + variant + "; register the buildDirectory-relative root explicitly: " + unsupported);
        }
        try {
            return new SourceMetadata(inputs.sourceDirectories(includeGenerated, registered), inputs.namespace.get(),
                    Map.copyOf(inputs.manifestPlaceholders.get()));
        } catch (Exception error) {
            throw new GradleException("Registered Android source metadata unavailable for " + project + " " + variant
                    + "; excluded generators are not executed by APK audit", error);
        }
    }

    record SourceMetadata(List<String> sourceDirectories, String namespace, Map<String, String> manifestPlaceholders) {}

    private record RegisteredGeneratedRoot(String sourceType, String path, boolean required) {}

    private record VariantInputs(SourceInput java, SourceInput kotlin, SourceInput resources, SourceInput res,
            SourceInput assets, Provider<List<RegularFile>> manifests,
            Provider<String> namespace, Provider<Map<String, String>> manifestPlaceholders) {
        static VariantInputs from(Variant variant) {
            return new VariantInputs(flat(variant.getSources().getJava()), flat(variant.getSources().getKotlin()),
                    flat(variant.getSources().getResources()), layered(variant.getSources().getRes()),
                    layered(variant.getSources().getAssets()), variant.getSources().getManifests().getAll().map(ArrayList::new),
                    variant.getNamespace(), variant.getManifestPlaceholders());
        }

        List<String> sourceDirectories(boolean includeGenerated, List<RegisteredGeneratedRoot> registered) {
            List<String> result = new ArrayList<>();
            addDirectories(result, "java", java, includeGenerated, registered); addDirectories(result, "kotlin", kotlin, includeGenerated, registered);
            addDirectories(result, "resources", resources, includeGenerated, registered); addDirectories(result, "res", res, includeGenerated, registered);
            addDirectories(result, "assets", assets, includeGenerated, registered);
            if (!includeGenerated) for (RegisteredGeneratedRoot root : registered) if (root.required)
                result.add("required-generated-" + root.sourceType + "|" + root.path);
            for (RegularFile manifest : manifests.get()) result.add("manifest|" + manifest.getAsFile().getAbsolutePath());
            return result;
        }

        List<String> unregisteredGeneratedRoots(List<RegisteredGeneratedRoot> registered) {
            List<String> result = new ArrayList<>();
            collectUnknown(result, "java", java, registered); collectUnknown(result, "kotlin", kotlin, registered);
            collectUnknown(result, "resources", resources, registered); collectUnknown(result, "res", res, registered);
            collectUnknown(result, "assets", assets, registered);
            return result;
        }

        private static void collectUnknown(List<String> result, String type, SourceInput directories,
                List<RegisteredGeneratedRoot> registered) {
            if (directories == null) return;
            Set<String> staticPaths = directories.staticRoots.get().stream()
                    .map(directory -> directory.getAsFile().getAbsolutePath()).collect(Collectors.toSet());
            for (Directory directory : directories.all.get()) {
                String path = directory.getAsFile().getAbsolutePath();
                boolean registeredRoot = registered.stream().anyMatch(root -> root.sourceType.equals(type) && root.path.equals(path));
                if (!staticPaths.contains(path) && !registeredRoot) result.add(type + "|" + path);
            }
        }

        private static void addDirectories(List<String> result, String type, SourceInput directories, boolean includeGenerated,
                List<RegisteredGeneratedRoot> registered) {
            if (directories == null) return;
            Set<String> staticPaths = directories.staticRoots.get().stream().map(directory -> directory.getAsFile().getAbsolutePath())
                    .collect(Collectors.toSet());
            for (Directory directory : (includeGenerated ? directories.all : directories.staticRoots).get()) {
                String path = directory.getAsFile().getAbsolutePath();
                boolean generated = includeGenerated && !staticPaths.contains(path);
                result.add(entryType(type, path, generated, includeGenerated, registered) + "|" + path);
            }
        }

        private static String entryType(String type, String path, boolean generated, boolean includeGenerated,
                List<RegisteredGeneratedRoot> registered) {
            for (RegisteredGeneratedRoot root : registered) if (!root.required && root.sourceType.equals(type) && root.path.equals(path))
                return includeGenerated ? "selected-generated-" + type : "canonical-generated-" + type;
            return generated ? "selected-generated-" + type : type;
        }

        private static SourceInput flat(SourceDirectories.Flat sources) {
            return sources == null ? null : new SourceInput(sources.getAll().map(ArrayList::new), sources.getStatic().map(ArrayList::new));
        }

        private static SourceInput layered(SourceDirectories.Layered sources) {
            return sources == null ? null : new SourceInput(flatten(sources.getAll()), flatten(sources.getStatic()));
        }

        private static Provider<List<Directory>> flatten(Provider<List<Collection<Directory>>> layers) {
            return layers.map(values -> values.stream().flatMap(Collection::stream)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
    }

    private record SourceInput(Provider<List<Directory>> all, Provider<List<Directory>> staticRoots) {}
}
