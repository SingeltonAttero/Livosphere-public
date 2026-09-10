package app.livosphere.buildlogic;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Test;

/** Mutating a generated descriptor must fail before Kotlin/Java compilation. */
public class RegistryDescriptorIntegrityTest {
    @Test public void componentIdMutationIsRejectedBeforeCompilation() throws Exception {
        assertMutation("ComponentId(\"public-sentinel-preview\")", "ComponentId(\"mutated-preview\")", "component-id");
    }

    @Test public void artifactProjectMutationIsRejectedBeforeCompilation() throws Exception {
        assertMutation("\":sets:public-sentinel:preview\"", "\":mutated:preview\"", "artifact-project");
    }

    @Test public void resourceReferenceMutationIsRejectedBeforeCompilation() throws Exception {
        assertMutation("public-sentinel-preview-wallpaper", "mutated-resource", "resource-reference");
    }

    @Test public void revisionMutationIsRejectedBeforeCompilation() throws Exception {
        assertMutation("setRevision = Revision(1)", "setRevision = Revision(2)", "revision");
    }

    private static void assertMutation(String from, String to, String label) throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            BuildResult pass = VariantContentPackagingTest.run(root, ":app:assembleRelease").build();
            assertTrue(pass.getOutput(), pass.getOutput().contains("Set APK content audit PASS"));
            Path report = root.resolve("app/build/reports/set-content/release/app-release-unsigned.apk-audit.txt");
            assertTrue(Files.isRegularFile(report));
            Files.writeString(root.resolve("app/build.gradle"), """
                    tasks.matching { it.name == 'generateReleaseSetRegistry' }.configureEach {
                        outputs.upToDateWhen { false }
                        doLast {
                            def registry = outputDirectory.file('app/livosphere/generated/GeneratedSetRegistry.kt').get().asFile
                            def canonical = registry.text
                            registry.text = registry.text.replace('%s', '%s')
                            tasks.matching { it.name == 'compileReleaseKotlin' || it.name == 'compileReleaseJavaWithJavac' }.configureEach {
                                doLast { registry.text = canonical }
                            }
                        }
                    }
                    """.formatted(from, to), java.nio.file.StandardOpenOption.APPEND);
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, label + "-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("Generated registry descriptor differs from authoritative variant selection"));
            assertTrue(failed.getOutput(), failed.getOutput().contains(":app:verifyReleaseGeneratedSetRegistry"));
            assertTrue(failed.getOutput(), failed.task(":app:verifyReleaseGeneratedSetRegistry") != null);
            assertTrue(failed.task(":app:verifyReleaseGeneratedSetRegistry").getOutcome() == TaskOutcome.FAILED);
            assertTrue(failed.task(":app:compileReleaseKotlin") == null);
            assertTrue(failed.task(":app:compileReleaseJavaWithJavac") == null);
            assertTrue(failed.task(":app:packageRelease") == null);
            assertTrue(!Files.exists(report));
        } finally {
            fixture.temporary.delete();
        }
    }

    private static void save(BuildResult result, String name) throws Exception {
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.writeString(evidence.resolve(name), result.getOutput());
    }
}
