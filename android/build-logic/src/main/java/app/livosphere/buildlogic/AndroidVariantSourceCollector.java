package app.livosphere.buildlogic;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.SourceDirectories;
import com.android.build.api.variant.Variant;
import java.io.File;
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

    SourceMetadata sourceMetadata(String project, String variant, boolean includeGenerated) {
        VariantInputs inputs = variants.getOrDefault(project, Map.of()).get(variant);
        if (inputs == null) throw new GradleException("Android variant source metadata missing for " + project + " " + variant);
        try {
            return new SourceMetadata(inputs.sourceDirectories(includeGenerated), inputs.namespace.get(), Map.copyOf(inputs.manifestPlaceholders.get()));
        } catch (Exception error) {
            throw new GradleException("Registered Android source metadata unavailable for " + project + " " + variant
                    + "; excluded generators are not executed by APK audit", error);
        }
    }

    record SourceMetadata(List<String> sourceDirectories, String namespace, Map<String, String> manifestPlaceholders) {}

    private record VariantInputs(SourceInput java, SourceInput kotlin, SourceInput resources, SourceInput res,
            SourceInput assets, Provider<List<RegularFile>> manifests,
            Provider<String> namespace, Provider<Map<String, String>> manifestPlaceholders) {
        static VariantInputs from(Variant variant) {
            return new VariantInputs(flat(variant.getSources().getJava()), flat(variant.getSources().getKotlin()),
                    flat(variant.getSources().getResources()), layered(variant.getSources().getRes()),
                    layered(variant.getSources().getAssets()), variant.getSources().getManifests().getAll().map(ArrayList::new),
                    variant.getNamespace(), variant.getManifestPlaceholders());
        }

        List<String> sourceDirectories(boolean includeGenerated) {
            List<String> result = new ArrayList<>();
            addDirectories(result, "java", java, includeGenerated); addDirectories(result, "kotlin", kotlin, includeGenerated);
            addDirectories(result, "resources", resources, includeGenerated); addDirectories(result, "res", res, includeGenerated);
            addDirectories(result, "assets", assets, includeGenerated);
            for (RegularFile manifest : manifests.get()) result.add("manifest|" + manifest.getAsFile().getAbsolutePath());
            return result;
        }

        private static void addDirectories(List<String> result, String type, SourceInput directories, boolean includeGenerated) {
            if (directories != null) for (Directory directory : (includeGenerated ? directories.all : directories.staticRoots).get())
                result.add(type + "|" + directory.getAsFile().getAbsolutePath());
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
