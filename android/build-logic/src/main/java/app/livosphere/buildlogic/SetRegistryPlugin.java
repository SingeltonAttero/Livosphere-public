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
        // manifestPaths() parses and validates inputs immediately. Invalidate every existing
        // mutable verdict directory first, so a failure in the first variant cannot preserve
        // a PASS from a later variant. Immutable evidence lives outside this directory.
        invalidateCurrentVariantReports(project);
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
                TaskProvider<Task> verifyGenerated = project.getTasks().register(
                        "verify" + suffix + "GeneratedSetRegistry", task -> {
                            task.dependsOn(generate);
                            task.doLast(t -> verifyGeneratedRegistry(project, generate, selection));
                        });
                project.getTasks().matching(t -> t.getName().equals("compile" + suffix + "Kotlin")
                        || t.getName().equals("compile" + suffix + "JavaWithJavac"))
                        .configureEach(t -> t.dependsOn(verifyGenerated));
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
                        p.getPluginManager().withPlugin("java", plugin -> configureJvmSources(p, task));
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
            throw new GradleException("Unable to invalidate set content audit reports", e);
        }
    }

    private static void verifyGeneratedRegistry(Project project, TaskProvider<GenerateSetRegistryTask> generate,
            VariantContentSelection selection) {
        java.nio.file.Path registry = generate.get().getOutputDirectory().get().getAsFile().toPath()
                .resolve("app/livosphere/generated/GeneratedSetRegistry.kt");
        try {
            if (!java.nio.file.Files.isRegularFile(registry)
                    || !java.nio.file.Files.readString(registry).equals(SetContractEngine.registrySource(selection)))
                throw new GradleException("Generated registry descriptor differs from authoritative variant selection");
        } catch (java.io.IOException e) {
            throw new GradleException("Unable to read generated registry descriptor", e);
        }
    }

    private static void configureJvmSources(Project project, AuditSetApkTask task) {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        task.getJvmSourceDirectories().put(project.getPath(), project.provider(() -> {
            SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            return java.util.stream.Stream.concat(main.getAllJava().getSrcDirs().stream(), main.getResources().getSrcDirs().stream())
                    .map(java.io.File::getAbsolutePath).distinct().toList();
        }));
    }
}
