package app.livosphere.buildlogic;

import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public abstract class AbstractSetTask extends DefaultTask {
    public AbstractSetTask() { getBuildType().convention("debug"); }

    @Input
    public abstract Property<String> getBuildType();

    protected final VariantContentSelection selection() {
        return SetContractEngine.select(resolvedManifestPaths(), getBuildType().get());
    }

    @Input
    public abstract ListProperty<String> getManifestPaths();

    @Internal
    public abstract DirectoryProperty getAndroidRoot();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getContractInputs();

    protected final List<Path> resolvedManifestPaths() {
        Path root = getAndroidRoot().get().getAsFile().toPath();
        return getManifestPaths().get().stream().map(root::resolve).toList();
    }
}
