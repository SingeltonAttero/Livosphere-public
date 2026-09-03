package app.livosphere.buildlogic;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateSetResourcesTask extends AbstractSetTask {
    @Input
    public abstract Property<String> getSetId();

    @Input
    public abstract Property<String> getSurface();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public final void generate() {
        SetContractEngine.generateResources(resolvedManifestPaths(), getSetId().get(), getSurface().get(),
                getOutputDirectory().get().getAsFile().toPath());
    }
}
