package app.livosphere.buildlogic;

import com.android.build.api.variant.AndroidComponentsExtension;
import java.util.List;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

public final class SetConsumerPlugin implements Plugin<Project> {
    private static final Set<String> SURFACES = Set.of("preview", "wallpaper", "clock-widget", "watchface");

    @Override
    public void apply(Project project) {
        SetContractExtension extension = project.getExtensions().create("setContract", SetContractExtension.class, project.getObjects());
        List<String> manifests = SetPluginSupport.manifestPaths(project);
        TaskProvider<Task> validateAll = project.getTasks().register("validateSetContract");
        TaskProvider<Task> generateAll = project.getTasks().register("generateSetResources");
        project.afterEvaluate(ignored -> {
            String setId = extension.getSetId().getOrNull();
            String surface = extension.getSurface().getOrNull();
            if (setId == null || setId.isBlank()) throw new GradleException(project.getPath() + ": setContract.setId обязателен");
            if (surface == null || !SURFACES.contains(surface)) throw new GradleException(project.getPath() + ": invalid setContract.surface");
            SetManifest manifest = SetManifestReader.readAll(manifests.stream().map(project.getRootProject()::file)
                    .map(java.io.File::toPath).toList()).stream().filter(m -> m.setId().equals(setId)).findFirst()
                    .orElseThrow(() -> new GradleException(project.getPath() + ": setId отсутствует: " + setId));
            if (!manifest.contributionFor(surface).artifactProject().equals(project.getPath()))
                throw new GradleException(project.getPath() + ": manifest связывает surface с другим project");
        });
        project.getPluginManager().withPlugin("com.android.application", ignored -> wireAndroid(project, extension, manifests, validateAll, generateAll));
        project.getPluginManager().withPlugin("com.android.library", ignored -> wireAndroid(project, extension, manifests, validateAll, generateAll));
    }

    private static void wireAndroid(Project project, SetContractExtension extension, List<String> manifests,
            TaskProvider<Task> validateAll, TaskProvider<Task> generateAll) {
        AndroidComponentsExtension<?, ?, ?> components = project.getExtensions().getByType(AndroidComponentsExtension.class);
        components.onVariants(components.selector().all(), variant -> {
            String name = variant.getName();
            String suffix = SetPluginSupport.taskSuffix(name);
            TaskProvider<ValidateSetContractsTask> validate = project.getTasks().register("validate" + suffix + "SetContract", ValidateSetContractsTask.class, task -> {
                SetPluginSupport.configureVariantInputs(project, task, manifests, variant.getBuildType());
                task.doLast(t -> {
                    if (task.selection().selected().stream().noneMatch(m -> m.setId().equals(extension.getSetId().get())))
                        throw new GradleException("Excluded set " + extension.getSetId().get() + " for variant " + name);
                });
            });
            TaskProvider<GenerateSetResourcesTask> generate = project.getTasks().register("generate" + suffix + "SetResources", GenerateSetResourcesTask.class, task -> {
                SetPluginSupport.configureVariantInputs(project, task, manifests, variant.getBuildType());
                task.getSetId().set(extension.getSetId());
                task.getSurface().set(extension.getSurface());
                task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/set-resources/" + name));
                task.dependsOn(validate);
            });
            if (variant.getSources().getRes() == null) throw new GradleException(project.getPath() + ": Android resources отключены");
            variant.getSources().getRes().addGeneratedSourceDirectory(generate, GenerateSetResourcesTask::getOutputDirectory);
            if (variant.getBuildType().equals("debug")) {
                validateAll.configure(t -> t.dependsOn(validate));
                generateAll.configure(t -> t.dependsOn(generate));
            }
        });
    }
}
