package app.livosphere.buildlogic;

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;

public final class SetRegistryPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        List<String> manifests = SetPluginSupport.manifestPaths(project);
        TaskProvider<Task> validateAll = project.getTasks().register("validateSetRegistry");
        TaskProvider<Task> generateAll = project.getTasks().register("generateSetRegistry");
        project.getPluginManager().withPlugin("com.android.application", ignored -> {
            AndroidComponentsExtension<?, ?, ?> components = project.getExtensions().getByType(AndroidComponentsExtension.class);
            components.onVariants(components.selector().all(), variant -> {
                String name = variant.getName();
                String suffix = SetPluginSupport.taskSuffix(name);
                String buildType = variant.getBuildType();
                // Selection can fail during configuration, before the audit task is created.
                try {
                    AuditSetApkTask.clearReports(project.getLayout().getBuildDirectory()
                            .dir("reports/set-content/" + name).get().getAsFile().toPath());
                } catch (Exception e) {
                    throw new GradleException("Unable to invalidate set content audit reports", e);
                }
                VariantContentSelection selection = SetContractEngine.select(manifests.stream()
                        .map(project.getRootProject()::file).map(java.io.File::toPath).toList(), buildType);
                selection.projects().stream().sorted().forEach(path -> {
                    Project dependency = project.getRootProject().findProject(path);
                    if (dependency == null) throw new GradleException("artifactProject отсутствует: " + path);
                    project.getDependencies().add(name + "Implementation", dependency);
                });
                TaskProvider<ValidateSetContractsTask> validate = project.getTasks().register(
                        "validate" + suffix + "SetRegistry", ValidateSetContractsTask.class, task -> {
                            SetPluginSupport.configureVariantInputs(project, task, manifests, buildType);
                            task.doFirst(t -> {
                                try {
                                    AuditSetApkTask.clearReports(project.getLayout().getBuildDirectory()
                                            .dir("reports/set-content/" + name).get().getAsFile().toPath());
                                } catch (Exception e) {
                                    throw new GradleException("Unable to invalidate set content audit reports", e);
                                }
                            });
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
                TaskProvider<AuditSetApkTask> audit = project.getTasks().register("audit" + suffix + "SetApk", AuditSetApkTask.class, task -> {
                    SetPluginSupport.configureVariantInputs(project, task, manifests, buildType);
                    task.getOutputs().upToDateWhen(t -> false);
                    task.getApkDirectory().set(variant.getArtifacts().get(SingleArtifact.APK.INSTANCE));
                    task.getSdkDirectory().set(components.getSdkComponents().getSdkDirectory());
                    task.getRegistryDirectory().set(generate.flatMap(GenerateSetRegistryTask::getOutputDirectory));
                    task.getInventoryDirectory().set(project.getLayout().getBuildDirectory().dir("reports/set-content/" + name));
                    project.getRootProject().getAllprojects().stream().filter(p -> p.getBuildFile().isFile()).forEach(p -> {
                        task.getModuleDirectories().put(p.getPath(), p.getProjectDir().getAbsolutePath());
                        task.getModuleSources().from(p.fileTree("src"));
                    });
                    task.dependsOn(validate);
                });
                project.getTasks().matching(t -> t.getName().equals("assemble" + suffix) || t.getName().equals("bundle" + suffix))
                        .configureEach(t -> t.dependsOn(audit));
                project.getTasks().matching(t -> t.getName().equals("package" + suffix))
                        .configureEach(t -> t.finalizedBy(audit));
            });
        });
    }
}
