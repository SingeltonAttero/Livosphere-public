package app.livosphere.buildlogic;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.Test;

/** Real APK regression for class identities absent from the simple source regex. */
public class SourceClassInventoryTest {
    @Test public void excludedEnumRecordAndKotlinFacadesAreRejectedFromRealApk() throws Exception {
        assertExcludedType("PayloadEnum", "test.hidden.PayloadEnum", "java", "public enum PayloadEnum { VALUE }");
        assertExcludedType("PayloadRecord", "test.hidden.PayloadRecord", "java", "public record PayloadRecord(String value) {}");
        assertExcludedType("QualifiedFacade", "test.hidden.QualifiedFacade", "kotlin",
                "@file:kotlin.jvm.JvmName (\"QualifiedFacade\")\npackage test.hidden\nfun qualifiedPayload() = 1");
        assertExcludedType("OrdinaryFacade", "test.hidden.OrdinaryFacadeKt", "kotlin",
                "package test.hidden\nfun ordinaryPayload() = 1");
        assertExcludedType("OrdinarySuspend", "test.hidden.OrdinarySuspendKt", "kotlin",
                "package test.hidden\nsuspend fun hiddenPayload() = 1");
    }

    private static void assertExcludedType(String fileName, String type, String language, String source) throws Exception {
        VariantContentPackagingTest fixture = new VariantContentPackagingTest();
        fixture.temporary.create();
        try {
            Path root = fixture.packagingProject();
            Files.writeString(root.resolve("app/build.gradle"),
                    "\nandroid { compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 } }\n",
                    java.nio.file.StandardOpenOption.APPEND);
            String extension = language.equals("kotlin") ? ".kt" : ".java";
            Path excluded = root.resolve("debug-payload/src/main/" + language + "/test/hidden");
            Path app = root.resolve("app/src/release/" + language + "/test/hidden");
            Files.createDirectories(excluded); Files.createDirectories(app);
            String packagePrefix = language.equals("kotlin") ? "" : "package test.hidden; ";
            Files.writeString(excluded.resolve(fileName + extension), packagePrefix + source + "\n");
            Files.writeString(app.resolve(fileName + extension), packagePrefix + source + "\n");
            BuildResult failed = VariantContentPackagingTest.run(root, ":app:assembleRelease").buildAndFail();
            save(failed, "source-class-" + fileName + "-failure.txt");
            assertTrue(failed.getOutput(), failed.getOutput().contains("Excluded DEX class in APK: " + type));
        } finally {
            fixture.temporary.delete();
        }
    }

    private static void save(BuildResult result, String name) throws Exception {
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.writeString(evidence.resolve(name), result.getOutput());
    }
}
