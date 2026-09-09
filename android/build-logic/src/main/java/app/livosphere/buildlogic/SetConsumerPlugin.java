package app.livosphere.buildlogic;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public final class SetConsumerPlugin implements Plugin<Project> {
    private static final Set<String> SURFACES = Set.of("preview", "wallpaper", "clock-widget", "watchface");

    @Override
    public void apply(Project project) {
        SetContractExtension extension = project.getExtensions().create(
                "setContract", SetContractExtension.class, project.getObjects());
        List<String> manifests = SetPluginSupport.manifestPaths(project);

        TaskProvider<ValidateSetContractsTask> validate = project.getTasks().register(
                "validateSetContract", ValidateSetContractsTask.class,
                task -> {
                    task.setGroup("verification");
                    task.setDescription("Проверяет manifest, assets, revisions и SHA-256 выбранного набора.");
                    SetPluginSupport.configureInputs(project, task, manifests);
                });
        TaskProvider<GenerateSetResourcesTask> generate = project.getTasks().register(
                "generateSetResources", GenerateSetResourcesTask.class,
                task -> {
                    task.setGroup("build");
                    task.setDescription("Детерминированно восстанавливает Android resources из set manifest.");
                    SetPluginSupport.configureInputs(project, task, manifests);
                    task.getSetId().set(extension.getSetId());
                    task.getSurface().set(extension.getSurface());
                    task.dependsOn(validate);
                });

        project.afterEvaluate(ignored -> {
            String setId = extension.getSetId().getOrNull();
            String surface = extension.getSurface().getOrNull();
            if (setId == null || setId.isBlank()) {
                throw new GradleException(project.getPath() + ": setContract.setId обязателен");
            }
            if (surface == null || !SURFACES.contains(surface)) {
                throw new GradleException(project.getPath() + ": setContract.surface должна быть одной из " + SURFACES);
            }
            List<SetManifest> loaded = SetManifestReader.readAll(manifests.stream()
                    .map(project.getRootProject()::file).map(java.io.File::toPath).toList());
            SetManifest manifest = loaded.stream().filter(candidate -> candidate.setId().equals(setId)).findFirst()
                    .orElseThrow(() -> new GradleException(project.getPath() + ": setId '" + setId
                            + "' отсутствует в явно подключённых manifests"));
            String expectedProject = manifest.contributionFor(surface).artifactProject();
            if (!expectedProject.equals(project.getPath())) {
                throw new GradleException(project.getPath() + ": manifest связывает surface " + surface
                        + " с " + expectedProject);
            }
        });

        project.getPluginManager().withPlugin("com.android.application", ignored -> wireAndroid(project, generate));
        project.getPluginManager().withPlugin("com.android.library", ignored -> wireAndroid(project, generate));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void wireAndroid(Project project, TaskProvider<GenerateSetResourcesTask> generate) {
        AndroidComponentsExtension components = project.getExtensions().getByType(AndroidComponentsExtension.class);
        components.onVariants(components.selector().all(), rawVariant -> {
            Variant variant = (Variant) rawVariant;
            if (variant.getSources().getRes() == null) {
                throw new GradleException(project.getPath() + ": Android resources отключены");
            }
            variant.getSources().getRes().addGeneratedSourceDirectory(
                    generate, GenerateSetResourcesTask::getOutputDirectory);
        });
    }
}
