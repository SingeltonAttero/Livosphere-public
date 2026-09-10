package app.livosphere.buildlogic;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/** Installs the collector from the root build before child Android projects configure variants. */
public final class AndroidVariantSourceCollectorPlugin implements Plugin<Project> {
    @Override public void apply(Project project) {
        if (project != project.getRootProject())
            throw new GradleException("livosphere.variant-source-collector must be applied to the root project");
        if (project.getExtensions().findByName(AndroidVariantSourceCollector.EXTENSION_NAME) != null) return;
        AndroidVariantSourceCollector collector = new AndroidVariantSourceCollector(project);
        project.getExtensions().add(AndroidVariantSourceCollector.EXTENSION_NAME, collector);
        collector.install();
    }
}
