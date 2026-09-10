package app.livosphere.buildlogic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

final class SetPluginSupport {
    private static final String MANIFESTS_PROPERTY = "livosphere.setManifests";

    private SetPluginSupport() {}

    static List<String> manifestPaths(Project project) {
        String configured = project.getProviders().gradleProperty(MANIFESTS_PROPERTY).getOrNull();
        if (configured == null) {
            Object projectProperty = project.findProperty(MANIFESTS_PROPERTY);
            configured = projectProperty == null ? null : projectProperty.toString();
        }
        if (configured == null || configured.isBlank()) {
            throw new GradleException("Gradle property '" + MANIFESTS_PROPERTY + "' должна перечислять manifests");
        }
        List<String> paths = Arrays.stream(configured.split(",", -1)).map(String::trim).toList();
        if (paths.stream().anyMatch(String::isBlank) || paths.stream().distinct().count() != paths.size()) {
            throw new GradleException("Gradle property '" + MANIFESTS_PROPERTY
                    + "' содержит пустой или duplicate path: " + configured);
        }
        Path root;
        try {
            root = project.getRootProject().getProjectDir().toPath().toRealPath();
        } catch (IOException error) {
            throw new GradleException("Не удалось разрешить canonical Android root", error);
        }
        return paths.stream().map(value -> normalizeManifestPath(root, value)).toList();
    }

    static String taskSuffix(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static void configureVariantInputs(Project project, AbstractSetTask task, List<String> manifests, String buildType) {
        configureInputs(project, task, manifests);
        task.getBuildType().set(buildType);
    }

    static void configureInputs(Project project, AbstractSetTask task, List<String> manifestPaths) {
        task.getBuildProfile().set(project.getProviders().gradleProperty(BuildProfile.PROPERTY).orElse("phone"));
        task.getAndroidRoot().set(project.getRootProject().getLayout().getProjectDirectory());
        task.getManifestPaths().set(manifestPaths);
        for (String relative : manifestPaths) {
            File manifest = project.getRootProject().file(relative);
            task.getContractInputs().from(manifest);
            task.getContractInputs().from(project.provider(() -> SetManifestReader.approvalInputFiles(manifest.toPath())
                    .stream().map(Path::toFile).toList()));
            File setRoot = manifest.getParentFile() == null ? null : manifest.getParentFile().getParentFile();
            if (setRoot != null) task.getContractInputs().from(new File(setRoot, "source-assets"));
        }
    }

    private static String normalizeManifestPath(Path androidRoot, String value) {
        final Path relative;
        try {
            relative = Path.of(value);
        } catch (InvalidPathException error) {
            throw new GradleException("Gradle property '" + MANIFESTS_PROPERTY
                    + "' содержит некорректный manifest path: " + value, error);
        }
        if (relative.isAbsolute() || value.contains("\\") || !relative.normalize().equals(relative)
                || value.equals("..") || value.startsWith("../")) {
            throw new GradleException("Gradle property '" + MANIFESTS_PROPERTY
                    + "' требует нормализованный relative path внутри Android root: " + value);
        }
        Path resolved = androidRoot.resolve(relative).normalize();
        if (!resolved.startsWith(androidRoot)) {
            throw new GradleException("Manifest path выходит за Android root: " + value);
        }
        if (Files.exists(resolved)) {
            try {
                if (!resolved.toRealPath().startsWith(androidRoot)) {
                    throw new GradleException("Manifest symlink выходит за Android root: " + value);
                }
            } catch (IOException error) {
                throw new GradleException("Не удалось разрешить manifest path: " + value, error);
            }
        }
        return relative.toString().replace('\\', '/');
    }
}
