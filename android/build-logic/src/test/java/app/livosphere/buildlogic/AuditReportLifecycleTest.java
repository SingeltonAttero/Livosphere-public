package app.livosphere.buildlogic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.Test;

/** The mutable verdict must be withdrawn when any audit prerequisite fails. */
public class AuditReportLifecycleTest {
    @Test public void passIsWithdrawnAfterEligibilityFailure() throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            assertPass(root);
            Path manifest = root.resolve("sets/public-sentinel/manifest/set.properties");
            Files.writeString(manifest, Files.readString(manifest)
                    .replace("distribution=public", "distribution=debug-only"));
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, "eligibility-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("empty public content closure"));
            assertNoPass(root);
        } finally {
            fixture.temporary.delete();
        }
    }

    @Test public void passIsWithdrawnWhenApprovalInputBecomesInvalidDuringConfiguration() throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            assertPass(root);
            Path manifest = root.resolve("sets/public-sentinel/manifest/set.properties");
            Files.writeString(manifest, Files.readString(manifest).replaceFirst(
                    "approval\\.image\\.sha256=[0-9a-f]+", "approval.image.sha256=" + "0".repeat(64)));
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, "invalid-approval-configuration-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("approval.image.sha256"));
            assertTrue(failed.getOutput(), failed.getOutput().contains("checksum mismatch"));
            assertNoPass(root);
        } finally {
            fixture.temporary.delete();
        }
    }

    @Test public void passIsWithdrawnAfterInvalidXmlInventoryFailure() throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            assertPass(root);
            Path invalid = root.resolve("app/src/release/res/layout/invalid.xml");
            Files.createDirectories(invalid.getParent());
            Files.writeString(invalid, "<FrameLayout>");
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, "invalid-xml-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("invalid.xml"));
            assertTrue(failed.getOutput(), failed.getOutput().contains("Failed to parse XML file"));
            assertNoPass(root);
        } finally {
            fixture.temporary.delete();
        }
    }

    @Test public void passIsWithdrawnAfterGraphFailure() throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            assertPass(root);
            Files.writeString(root.resolve("bridge/build.gradle"),
                    "\ndependencies { api project(':sets:debug-sentinel:preview') }\n",
                    java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(root.resolve("sets/public-sentinel/wallpaper/build.gradle"),
                    "\ndependencies { implementation project(':bridge') }\n",
                    java.nio.file.StandardOpenOption.APPEND);
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, "graph-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("Excluded set debug-sentinel for variant release"));
            assertNoPass(root);
        } finally {
            fixture.temporary.delete();
        }
    }

    private static void assertPass(Path root) throws Exception {
        BuildResult result = VariantContentPackagingTest.run(root, ":app:assembleRelease").build();
        assertTrue(result.getOutput(), result.getOutput().contains("Set APK content audit PASS"));
        Path report = report(root);
        assertTrue(Files.isRegularFile(report));
        assertTrue(Files.readString(report).startsWith("PASS content closure\nsha256="));
    }

    private static void assertNoPass(Path root) {
        assertFalse(Files.exists(report(root)));
    }

    private static Path report(Path root) {
        return root.resolve("app/build/reports/set-content/release/app-release-unsigned.apk-audit.txt");
    }

    private static void save(BuildResult result, String name) throws Exception {
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.writeString(evidence.resolve(name), result.getOutput());
    }
}
