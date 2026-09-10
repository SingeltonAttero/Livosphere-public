package app.livosphere.buildlogic;

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public final class SetRegistryPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        List<String> manifests = SetPluginSupport.manifestPaths(project);
        BuildProfile profile = BuildProfile.from(project);
        boolean legacyAuditEnabled = project.getProviders()
                .gradleProperty("livosphere.enableLegacySetApkAudit")
                .map(Boolean::parseBoolean)
                .getOrElse(false);
        if (legacyAuditEnabled) invalidateCurrentVariantReports(project);
        TaskProvider<Task> validateAll = project.getTasks().register("validateSetRegistry");
        TaskProvider<Task> generateAll = project.getTasks().register("generateSetRegistry");
        project.getPluginManager().withPlugin("com.android.application", ignored -> {
            AndroidComponentsExtension<?, ?, ?> components = project.getExtensions().getByType(AndroidComponentsExtension.class);
            components.onVariants(components.selector().all(), variant -> {
                String name = variant.getName();
                String suffix = SetPluginSupport.taskSuffix(name);
                String buildType = variant.getBuildType();
                VariantContentSelection selection = SetContractEngine.select(manifests.stream()
                        .map(project.getRootProject()::file).map(java.io.File::toPath).toList(), buildType, profile);
                selection.projects().stream().sorted().forEach(path -> {
                    Project dependency = project.getRootProject().findProject(path);
                    if (dependency == null) throw new GradleException("artifactProject отсутствует: " + path);
                    project.getDependencies().add(name + "Implementation", dependency);
                });
                TaskProvider<ValidateSetContractsTask> validate = project.getTasks().register(
                        "validate" + suffix + "SetRegistry", ValidateSetContractsTask.class, task -> {
                            SetPluginSupport.configureVariantInputs(project, task, manifests, buildType);
                            if (legacyAuditEnabled) task.doFirst(t -> clearVariantReport(project, name));
                            task.doLast(t -> VariantProjectClosure.resolve(project, name, task.selection()));
                        });
                TaskProvider<GenerateSetRegistryTask> generate = project.getTasks().register(
                        "generate" + suffix + "SetRegistry", GenerateSetRegistryTask.class, task -> {
                            SetPluginSupport.configureVariantInputs(project, task, manifests, buildType);
                            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory()
                                    .dir("generated/set-registry/" + name));
                            task.dependsOn(validate);
                        });
                if (variant.getSources().getKotlin() == null) throw new GradleException("Kotlin sources unavailable");
                variant.getSources().getKotlin().addGeneratedSourceDirectory(generate, GenerateSetRegistryTask::getOutputDirectory);
                project.getTasks().matching(t -> t.getName().equals("pre" + suffix + "Build"))
                        .configureEach(t -> t.dependsOn(validate));
                if (buildType.equals("debug")) {
                    validateAll.configure(t -> t.dependsOn(validate));
                    generateAll.configure(t -> t.dependsOn(generate));
                }
                if (legacyAuditEnabled) {
                    project.getTasks().register("audit" + suffix + "SetApk", AuditSetApkTask.class, task -> {
                        task.setGroup("legacy verification");
                        task.setDescription("Явный исторический APK content audit; не входит в обычную сборку");
                        SetPluginSupport.configureVariantInputs(project, task, manifests, buildType);
                        task.getOutputs().upToDateWhen(t -> false);
                        task.getApkDirectory().set(variant.getArtifacts().get(SingleArtifact.APK.INSTANCE));
                        task.getSdkDirectory().set(components.getSdkComponents().getSdkDirectory());
                        task.getRegistryDirectory().set(generate.flatMap(GenerateSetRegistryTask::getOutputDirectory));
                        task.getInventoryDirectory().set(project.getLayout().getBuildDirectory().dir("reports/set-content/" + name));
                        AndroidVariantSourceCollector collector = project.getRootProject().getExtensions()
                                .findByType(AndroidVariantSourceCollector.class);
                        if (collector == null) throw new GradleException(
                                "Root plugin livosphere.variant-source-collector is required for legacy APK audit");
                        collector.configureAuditInputs(task);
                        project.getRootProject().getAllprojects().stream().filter(p -> p.getBuildFile().isFile()).forEach(p -> {
                            task.getModuleDirectories().put(p.getPath(), p.getProjectDir().getAbsolutePath());
                            p.getPluginManager().withPlugin("java", plugin -> configureJvmSources(p, task));
                        });
                        task.dependsOn(validate);
                    });
                }
            });
        });
    }

    private static void clearVariantReport(Project project, String variant) {
        try {
            AuditSetApkTask.clearReports(project.getLayout().getBuildDirectory()
                    .dir("reports/set-content/" + variant).get().getAsFile().toPath());
        } catch (Exception e) {
            throw new GradleException("Unable to invalidate legacy set content audit reports", e);
        }
    }

    private static void invalidateCurrentVariantReports(Project project) {
        try {
            java.nio.file.Path reports = project.getLayout().getBuildDirectory()
                    .dir("reports/set-content").get().getAsFile().toPath();
            if (!java.nio.file.Files.isDirectory(reports)) return;
            try (var variants = java.nio.file.Files.list(reports)) {
                for (java.nio.file.Path variant : variants.filter(java.nio.file.Files::isDirectory).toList()) {
                    AuditSetApkTask.clearReports(variant);
                }
            }
        } catch (Exception e) {
            throw new GradleException("Unable to invalidate legacy set content audit reports", e);
        }
    }

    private static void configureJvmSources(Project project, AuditSetApkTask task) {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        task.getJvmSourceDirectories().put(project.getPath(), project.provider(() -> {
            SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            return java.util.stream.Stream.concat(main.getAllSource().getSrcDirs().stream(), main.getResources().getSrcDirs().stream())
                    .map(java.io.File::getAbsolutePath).distinct().toList();
        }));
    }
}
