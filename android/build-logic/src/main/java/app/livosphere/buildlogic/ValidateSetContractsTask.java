package app.livosphere.buildlogic;

import org.gradle.api.tasks.TaskAction;

public abstract class ValidateSetContractsTask extends AbstractSetTask {
    @TaskAction
    public final void validateContracts() {
        SetContractEngine.validate(resolvedManifestPaths());
    }
}
