package app.livosphere.buildlogic;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateSetRegistryTask extends AbstractSetTask {
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public final void generate() {
        SetContractEngine.generateRegistry(selection(), getOutputDirectory().get().getAsFile().toPath());
    }
}
