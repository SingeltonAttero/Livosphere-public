package app.livosphere.buildlogic;

import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public final class SetRegistryPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        List<String> manifests = SetPluginSupport.manifestPaths(project);

        TaskProvider<ValidateSetContractsTask> validate = project.getTasks().register(
                "validateSetRegistry", ValidateSetContractsTask.class,
                task -> {
                    task.setGroup("verification");
                    task.setDescription("Проверяет все явно подключённые set manifests и глобальную уникальность IDs.");
                    SetPluginSupport.configureInputs(project, task, manifests);
                });
        TaskProvider<GenerateSetRegistryTask> generate = project.getTasks().register(
                "generateSetRegistry", GenerateSetRegistryTask.class,
                task -> {
                    task.setGroup("build");
                    task.setDescription("Генерирует типизированный Kotlin SetRegistry из manifest authority.");
                    SetPluginSupport.configureInputs(project, task, manifests);
                    task.dependsOn(validate);
                });

        project.afterEvaluate(ignored -> {
            List<SetManifest> loaded = SetManifestReader.readAll(manifests.stream()
                    .map(project.getRootProject()::file).map(java.io.File::toPath).toList());
            for (SetManifest manifest : loaded) {
                manifest.contributions().stream()
                        .filter(contribution -> !contribution.surface().equals("watchface"))
                        .map(SetManifest.Contribution::artifactProject)
                        .distinct()
                        .sorted()
                        .forEach(path -> {
                            Project dependency = project.getRootProject().findProject(path);
                            if (dependency == null) {
                                throw new org.gradle.api.GradleException(
                                        manifest.manifestPath() + ": artifactProject отсутствует: " + path);
                            }
                            project.getDependencies().add("implementation", dependency);
                        });
            }
        });

        project.getPluginManager().withPlugin("com.android.application", ignored -> wireAndroid(project, generate));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void wireAndroid(Project project, TaskProvider<GenerateSetRegistryTask> generate) {
        AndroidComponentsExtension components = project.getExtensions().getByType(AndroidComponentsExtension.class);
        components.onVariants(components.selector().all(), rawVariant -> {
            Variant variant = (Variant) rawVariant;
            if (variant.getSources().getKotlin() == null) {
                throw new GradleException(project.getPath() + ": Kotlin sources недоступны для generated registry");
            }
            variant.getSources().getKotlin().addGeneratedSourceDirectory(
                    generate, GenerateSetRegistryTask::getOutputDirectory);
        });
    }
}
