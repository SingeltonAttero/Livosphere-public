package app.livosphere.buildlogic;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.GradleException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** All public sentinels live only in JUnit's temporary test.livosphere.packaging application. */
public class VariantContentPackagingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void releaseSelectionUsesOnePolicyAndEmptyGuardIsDeferred() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path draft = PhoneSetFixture.create(root, "draft-set");
        Path approvedDebug = PhoneSetFixture.create(root, "approved-debug");
        replace(approvedDebug, "contentStatus=draft", "contentStatus=html-approved");
        PhoneSetFixture.approval(approvedDebug, "image"); PhoneSetFixture.approval(approvedDebug, "html");
        Path publicSet = compilablePublic(root, "public-sentinel");
        var all = List.of(draft, approvedDebug, publicSet);
        assertEquals(3, SetContractEngine.select(all, "debug").selected().size());
        assertEquals(List.of("public-sentinel"), SetContractEngine.select(all, "release").selected().stream().map(SetManifest::setId).toList());
        assertEquals(List.of("public-sentinel"), SetContractEngine.select(all, "benchmark").selected().stream().map(SetManifest::setId).toList());
        var empty = SetContractEngine.select(List.of(draft, approvedDebug), "release");
        assertTrue(empty.selected().isEmpty());
        try { empty.requireNonEmpty(); fail(); } catch (GradleException e) { assertTrue(e.getMessage().contains("empty public content closure")); }
    }

    @Test public void debugReleaseDebugPackagesOnlyItsClosureAndDetectsConcreteSentinelLeaks() throws Exception {
        Path root = packagingProject();
        BuildResult debug = run(root, ":app:assembleDebug").build();
        assertEquals(TaskOutcome.SUCCESS, debug.task(":app:auditDebugSetApk").getOutcome());
        String debugInventory = report(root, "debug", "inventory.txt");
        assertTrue(debugInventory.contains("selected=[debug-sentinel, public-sentinel]"));
        Path debugRegistry = root.resolve("app/build/generated/kotlin/generateDebugSetRegistry/app/livosphere/generated/GeneratedSetRegistry.kt");
        String before = Files.readString(debugRegistry);
        assertTrue(before.contains("SetId(\"debug-sentinel\")"));
        BuildResult release = run(root, ":app:bundleRelease").build();
        assertEquals(TaskOutcome.SUCCESS, release.task(":app:auditReleaseSetApk").getOutcome());
        Path candidate = root.resolve("app/build/outputs/apk/release/app-release-unsigned.apk");
        String candidateHash = PhoneSetFixture.hash(candidate);
        Path generatedMetadata = root.resolve("app/build/generated/kotlin/generateReleaseSetRegistry/app/livosphere/generated/GeneratedSetRegistry.kt");
        String metadataHash = PhoneSetFixture.hash(generatedMetadata);
        Path externalAttestation = write(root.resolve("external-attestations/" + candidateHash + ".properties"),
                "artifactSha256=" + candidateHash + "\nnative=UNKNOWN\nquality=UNKNOWN\n");
        Files.writeString(externalAttestation, "artifactSha256=" + candidateHash
                + "\nnative=SYNTHETIC-TEST-RECORD\nquality=SYNTHETIC-TEST-RECORD\n");
        BuildResult attestationChanged = run(root, ":app:assembleRelease").build();
        assertEquals(TaskOutcome.UP_TO_DATE, attestationChanged.task(":app:generateReleaseSetRegistry").getOutcome());
        assertEquals(candidateHash, PhoneSetFixture.hash(candidate));
        assertEquals(metadataHash, PhoneSetFixture.hash(generatedMetadata));
        try (java.util.zip.ZipFile packaged = new java.util.zip.ZipFile(candidate.toFile())) {
            assertTrue(packaged.stream().noneMatch(e -> e.getName().contains("attestation") || e.getName().contains("approvals")));
        }
        String releaseResources = report(root, "release", "app-release-unsigned.apk-resources.txt");
        assertTrue(releaseResources.contains("layout/ls_public_sentinel_clock_widget_s"));
        assertFalse(releaseResources.contains("ls_debug_sentinel"));
        String releaseDex = report(root, "release", "app-release-unsigned.apk-dex-types.txt");
        assertTrue(releaseDex.contains("test.public_sentinel.WallpaperService"));
        assertFalse(releaseDex.contains("test.debug_sentinel"));
        assertFalse(releaseDex.contains("test.hidden.Payload"));
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.createDirectories(evidence);
        Files.deleteIfExists(evidence.resolve("sentinel-rejections.txt"));
        Files.writeString(evidence.resolve("external-attestation.txt"), "PASS external native/quality record changed; APK and generated registry unchanged\n"
                + "applicationId=test.livosphere.packaging\nartifactSha256=" + candidateHash + "\nmetadataSha256=" + metadataHash + "\nSynthetic test records do not provide native or quality acceptance.\n");
        for (String name : List.of("inventory.txt", "app-release-unsigned.apk-audit.txt", "app-release-unsigned.apk-manifest.txt",
                "app-release-unsigned.apk-resources.txt", "app-release-unsigned.apk-dex-types.txt", "app-release-unsigned.apk-registry-strings.txt")) {
            Files.copy(root.resolve("app/build/reports/set-content/release/" + name), evidence.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(evidence.resolve("README.txt"), "Temporary TestKit applicationId=test.livosphere.packaging only.\nSynthetic public/html approvals do not approve production art. No sentinel APK is exported.\n");
        // Migration metadata may retain a logical ID; it must not count as a GeneratedSetRegistry entry.
        assertTrue(releaseDex.contains("test.shell.LegacyMigration"));
        BuildResult again = run(root, ":app:assembleDebug").build();
        assertEquals(TaskOutcome.UP_TO_DATE, again.task(":app:generateDebugSetRegistry").getOutcome());
        assertEquals(before, Files.readString(debugRegistry));
        assertFalse(Files.readString(root.resolve("app/build/generated/kotlin/generateReleaseSetRegistry/app/livosphere/generated/GeneratedSetRegistry.kt")).contains("SetId(\"debug-sentinel\")"));

        // Each mutation changes the actual release artifact; no generator-string-only test can pass these.
        Path releaseSource = root.resolve("app/src/release");
        Path values = write(releaseSource.resolve("res/values/leak.xml"), "<resources><string name=\"ls_debug_sentinel_wallpaper_private_title\">LEAK</string></resources>");
        assertAuditFailure(root, "Excluded resource in APK: string/ls_debug_sentinel_wallpaper_private_title"); Files.delete(values);
        Path layout = write(releaseSource.resolve("res/layout/ls_debug_sentinel_clock_widget_hidden.xml"), "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"/>");
        assertAuditFailure(root, "Excluded resource in APK: layout/ls_debug_sentinel_clock_widget_hidden"); Files.delete(layout);
        Path preview = releaseSource.resolve("res/drawable-nodpi/ls_debug_sentinel_preview_wallpaper.png");
        Files.createDirectories(preview.getParent()); Files.copy(root.resolve("sets/debug-sentinel/source-assets/preview/drawable-nodpi/ls_debug_sentinel_preview_wallpaper.png"), preview);
        assertAuditFailure(root, "Excluded resource in APK: drawable/ls_debug_sentinel_preview_wallpaper"); Files.delete(preview);
        // A shell overlay with an approved identifier must not replace the selected asset.
        Path selectedOverlay = releaseSource.resolve("res/drawable-nodpi/ls_public_sentinel_preview_wallpaper.png");
        Files.copy(root.resolve("sets/debug-sentinel/source-assets/preview/drawable-nodpi/ls_debug_sentinel_preview_wallpaper.png"), selectedOverlay);
        assertAuditFailure(root, "Selected resource overridden outside contribution closure: drawable/ls_public_sentinel_preview_wallpaper"); Files.delete(selectedOverlay);
        // The whole excluded namespace is rejected, even when the name never occurred in its source inventory.
        Path unknownExcluded = write(releaseSource.resolve("res/values/unknown-excluded.xml"), "<resources><string name=\"ls_debug_sentinel_unlisted\">LEAK</string></resources>");
        assertAuditFailure(root, "Excluded resource namespace in APK: string/ls_debug_sentinel_unlisted"); Files.delete(unknownExcluded);
        Path provider = write(releaseSource.resolve("AndroidManifest.xml"), "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><provider android:name=\"test.debug_sentinel.HiddenProvider\" android:authorities=\"test.sentinel.leak\" android:exported=\"false\"/></application></manifest>");
        assertAuditFailure(root, "Excluded manifest component in APK: test.debug_sentinel.HiddenProvider"); Files.delete(provider);
        Path payload = write(releaseSource.resolve("java/test/hidden/Payload.java"), "package test.hidden; public class Payload { public static String data() { return \"LEAK\"; } }");
        assertAuditFailure(root, "Excluded DEX class in APK: test.hidden.Payload"); Files.delete(payload);
        Path transit = write(releaseSource.resolve("res/values/transitive.xml"), "<resources><string name=\"hidden_transitive_title\">LEAK</string></resources>");
        assertAuditFailure(root, "Excluded resource in APK: string/hidden_transitive_title"); Files.delete(transit);
        Path assets = write(releaseSource.resolve("assets/private-payload.txt"), "LEAK");
        assertAuditFailure(root, "Excluded asset in APK: assets/private-payload.txt"); Files.delete(assets);

        Path appBuild = root.resolve("app/build.gradle");
        String normalBuild = Files.readString(appBuild);
        Files.writeString(appBuild, normalBuild + """
                tasks.matching { it.name == 'generateReleaseSetRegistry' }.configureEach {
                    outputs.upToDateWhen { false }
                    doLast {
                        def registry = outputDirectory.file('app/livosphere/generated/GeneratedSetRegistry.kt').get().asFile
                        registry.text = registry.text.replace('SetId("public-sentinel")', 'SetId("debug-sentinel")')
                    }
                }
                """);
        assertGeneratedRegistryFailure(root, "Generated registry descriptor differs from authoritative variant selection");
        Files.writeString(appBuild, normalBuild);

        // A saved component name in meta-data cannot impersonate the required service node.
        Path publicManifest = root.resolve("sets/public-sentinel/wallpaper/src/main/AndroidManifest.xml");
        write(publicManifest, "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><meta-data android:name=\"test.public_sentinel.WallpaperService\" android:value=\"history only\"/></application></manifest>");
        assertAuditFailure(root, "Selected wallpaper service missing in APK manifest: test.public_sentinel.WallpaperService");
        write(publicManifest, "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><service android:name=\"test.public_sentinel.WallpaperService\" android:exported=\"true\" android:permission=\"android.permission.BIND_WALLPAPER\"/></application></manifest>");

        // Public -> bridge -> excluded contribution fails the real resolved runtime graph before packaging.
        Files.writeString(root.resolve("bridge/build.gradle"), "\ndependencies { api project(':sets:debug-sentinel:preview') }\n", java.nio.file.StandardOpenOption.APPEND);
        Files.writeString(root.resolve("sets/public-sentinel/wallpaper/build.gradle"), "\ndependencies { implementation project(':bridge') }\n", java.nio.file.StandardOpenOption.APPEND);
        BuildResult transitive = run(root, ":app:validateReleaseSetRegistry").buildAndFail();
        assertTrue(transitive.getOutput(), transitive.getOutput().contains("Excluded contribution in transitive release runtime graph"));
        assertEquals(TaskOutcome.FAILED, transitive.task(":app:validateReleaseSetRegistry").getOutcome());
        assertNull(transitive.task(":app:packageRelease"));
        // A private transitive module cannot be laundered as shared simply by adding a public edge.
        replace(root.resolve("bridge/build.gradle"), "api project(':sets:debug-sentinel:preview')", "api project(':debug-payload')");
        BuildResult privateLeak = run(root, ":app:assembleRelease").buildAndFail();
        assertTrue(privateLeak.getOutput(), privateLeak.getOutput().contains("Excluded contribution in transitive release runtime graph: [:debug-payload]"));
        assertNull(privateLeak.task(":app:packageRelease"));
        Files.writeString(evidence.resolve("transitive-rejections.txt"), transitive.getOutput() + "\n" + privateLeak.getOutput());
    }

    @Test public void realAppWithOnlyDebugContentRejectsReleaseWithoutBlockingDebugConfiguration() throws Exception {
        Path root = packagingProject();
        Files.writeString(root.resolve("gradle.properties"), "livosphere.setManifests=sets/debug-sentinel/manifest/set.properties\norg.gradle.jvmargs=-Xmx1g\n");
        // Unlisted contribution projects intentionally do not apply consumer conventions in this fixture.
        for (String surface : List.of("preview", "wallpaper", "clock-widget")) {
            Path build = root.resolve("sets/public-sentinel/" + surface + "/build.gradle");
            Files.writeString(build, "plugins { id 'com.android.library' }; android { namespace 'test.unselected." + surface.replace('-', '_') + "'; compileSdk 37; defaultConfig { minSdk 29 } }\n");
        }
        assertEquals(TaskOutcome.SUCCESS, run(root, ":app:assembleDebug").build().task(":app:auditDebugSetApk").getOutcome());
        BuildResult failed = run(root, ":app:assembleRelease").buildAndFail();
        assertTrue(failed.getOutput(), failed.getOutput().contains("empty public content closure"));
        assertNull(failed.task(":app:packageRelease"));
        assertFalse(Files.exists(root.resolve("app/build/outputs/apk/release/app-release-unsigned.apk")));
    }

    @Test public void customBenchmarkVariantUsesTheSamePublicClosureAndEmptyGuard() throws Exception {
        Path root = packagingProject();
        Path build = root.resolve("app/build.gradle");
        Files.writeString(build, Files.readString(build) + "\nandroid { buildTypes { benchmark { initWith release; matchingFallbacks = ['release'] } } }\n");
        BuildResult benchmark = run(root, ":app:assembleBenchmark").build();
        assertEquals(TaskOutcome.SUCCESS, benchmark.task(":app:validateBenchmarkSetRegistry").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, benchmark.task(":app:generateBenchmarkSetRegistry").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, benchmark.task(":app:auditBenchmarkSetApk").getOutcome());
        String registry = Files.readString(root.resolve("app/build/generated/kotlin/generateBenchmarkSetRegistry/app/livosphere/generated/GeneratedSetRegistry.kt"));
        assertTrue(registry.contains("SetId(\"public-sentinel\")"));
        assertFalse(registry.contains("debug-sentinel"));

        Path empty = packagingProject();
        Files.writeString(empty.resolve("gradle.properties"), "livosphere.setManifests=sets/debug-sentinel/manifest/set.properties\norg.gradle.jvmargs=-Xmx1g\n");
        for (String surface : List.of("preview", "wallpaper", "clock-widget")) {
            Path module = empty.resolve("sets/public-sentinel/" + surface + "/build.gradle");
            Files.writeString(module, "plugins { id 'com.android.library' }; android { namespace 'test.unselected." + surface.replace('-', '_') + "'; compileSdk 37; defaultConfig { minSdk 29 } }\n");
        }
        Path emptyBuild = empty.resolve("app/build.gradle");
        Files.writeString(emptyBuild, Files.readString(emptyBuild) + "\nandroid { buildTypes { benchmark { initWith release; matchingFallbacks = ['release'] } } }\n");
        BuildResult rejected = run(empty, ":app:assembleBenchmark").buildAndFail();
        assertTrue(rejected.getOutput(), rejected.getOutput().contains("empty public content closure"));
        assertNull(rejected.task(":app:packageBenchmark"));
    }

    @Test public void benchmarkUsesConfiguredReleaseFallbackForExcludedPrivateProjectAndBinary() throws Exception {
        Path projectLeak = packagingProject();
        addBenchmark(projectLeak);
        addReleasePayload(projectLeak);
        for (String module : List.of("sets/public-sentinel/wallpaper", "sets/debug-sentinel/wallpaper"))
            append(projectLeak.resolve(module + "/build.gradle"), "dependencies { releaseImplementation project(':release-payload') }\n");
        BuildResult projectRejected = run(projectLeak, ":app:validateBenchmarkSetRegistry").buildAndFail();
        assertEquals(projectRejected.getOutput(), TaskOutcome.FAILED,
                projectRejected.task(":app:validateBenchmarkSetRegistry").getOutcome());
        assertTrue(projectRejected.getOutput(), projectRejected.getOutput()
                .contains("Excluded contribution in transitive benchmark runtime graph: [:release-payload]"));

        Path binaryLeak = packagingProject();
        addBenchmark(binaryLeak); archive(binaryLeak.resolve("local/release-private.jar"));
        for (String module : List.of("sets/public-sentinel/wallpaper", "sets/debug-sentinel/wallpaper"))
            append(binaryLeak.resolve(module + "/build.gradle"),
                    "dependencies { releaseImplementation files('../../../local/release-private.jar') }\n");
        BuildResult binaryRejected = run(binaryLeak, ":app:validateBenchmarkSetRegistry").buildAndFail();
        assertEquals(binaryRejected.getOutput(), TaskOutcome.FAILED,
                binaryRejected.task(":app:validateBenchmarkSetRegistry").getOutcome());
        assertTrue(binaryRejected.getOutput(), binaryRejected.getOutput()
                .contains("Excluded local binary in benchmark runtime graph") && binaryRejected.getOutput().contains("release-private.jar"));
    }

    @Test public void jvmRuntimeProjectHasExplicitMainMetadataAndDoesNotNeedAndroidVariant() throws Exception {
        Path root = packagingProject();
        addJvmShared(root);
        for (String module : List.of("app", "sets/public-sentinel/wallpaper", "sets/debug-sentinel/wallpaper"))
            append(root.resolve(module + "/build.gradle"), "dependencies { implementation project(':shared-jvm') }\n");
        BuildResult release = run(root, ":app:assembleRelease").build();
        assertEquals(release.getOutput(), TaskOutcome.SUCCESS, release.task(":app:auditReleaseSetApk").getOutcome());
        assertTrue(report(root, "release", "app-release-unsigned.apk-dex-types.txt").contains("test.shared_jvm.SharedJvmPayload"));
    }

    @Test public void rootCollectorUsesActualCustomGeneratedRootsAndRejectsMissingExcludedRoots() throws Exception {
        Path selected = packagingProject();
        addGeneratedAuditSource(selected, "sets/public-sentinel/wallpaper", "generateSelectedAuditSource",
                "test.public_sentinel.GeneratedSelectedAuditSource");
        BuildResult selectedBuild = run(selected, ":app:assembleRelease").build();
        assertEquals(selectedBuild.getOutput(), TaskOutcome.SUCCESS, selectedBuild.task(":app:auditReleaseSetApk").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, selectedBuild.task(":sets:public-sentinel:wallpaper:generateSelectedAuditSource").getOutcome());
        assertTrue(report(selected, "release", "inventory.txt").contains("test.public_sentinel.GeneratedSelectedAuditSource"));

        Path excluded = packagingProject();
        addGeneratedResource(excluded, "app", "generateExcludedAuditSource", "ls_debug_sentinel_generated_payload");
        BuildResult rejected = run(excluded, ":app:assembleRelease").buildAndFail();
        assertEquals(rejected.getOutput(), TaskOutcome.FAILED, rejected.task(":app:auditReleaseSetApk").getOutcome());
        assertTrue(rejected.getOutput(), rejected.getOutput().contains("Excluded resource namespace in APK: string/ls_debug_sentinel_generated_payload"));
        assertEquals(TaskOutcome.SUCCESS, rejected.task(":app:generateExcludedAuditSource").getOutcome());
    }

    @Test public void collectorNormalizesPlaceholderRelativeComponentsWithFinalVariantNamespace() throws Exception {
        Path root = packagingProject();
        Path publicWallpaper = root.resolve("sets/public-sentinel/wallpaper");
        append(publicWallpaper.resolve("build.gradle"), """
                def selectedNamespace = providers.gradleProperty('selectedNamespace').getOrElse('test.public_sentinel')
                android { namespace selectedNamespace; defaultConfig { manifestPlaceholders = [wallpaperService: '.WallpaperService'] } }
                """);
        write(publicWallpaper.resolve("src/main/AndroidManifest.xml"), "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><service android:name=\"${wallpaperService}\" android:exported=\"true\" android:permission=\"android.permission.BIND_WALLPAPER\"/></application></manifest>");
        Path debugWallpaper = root.resolve("sets/debug-sentinel/wallpaper");
        append(debugWallpaper.resolve("build.gradle"), """
                def excludedNamespace = providers.gradleProperty('excludedNamespace').getOrElse('test.debug_sentinel')
                android { namespace excludedNamespace; defaultConfig { manifestPlaceholders = [hiddenProvider: '.HiddenProvider'] } }
                """);
        write(debugWallpaper.resolve("src/main/AndroidManifest.xml"), "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><provider android:name=\"${hiddenProvider}\" android:authorities=\"test.sentinel.private\" android:exported=\"false\"/></application></manifest>");
        write(root.resolve("app/src/release/AndroidManifest.xml"), "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><provider android:name=\"test.debug_sentinel.HiddenProvider\" android:authorities=\"test.sentinel.leak\" android:exported=\"false\"/></application></manifest>");
        BuildResult rejected = run(root, ":app:assembleRelease", "-PselectedNamespace=test.public_sentinel", "-PexcludedNamespace=test.debug_sentinel").buildAndFail();
        assertEquals(rejected.getOutput(), TaskOutcome.FAILED, rejected.task(":app:auditReleaseSetApk").getOutcome());
        assertTrue(rejected.getOutput(), rejected.getOutput().contains("Excluded manifest component in APK: test.debug_sentinel.HiddenProvider"));
    }

    @Test public void benchmarkAuditUsesExcludedReleaseSourcesWithoutAProjectDependencyLeak() throws Exception {
        Path root = packagingProject();
        addBenchmark(root);
        Path excluded = root.resolve("sets/debug-sentinel/wallpaper/src/release");
        write(excluded.resolve("res/values/release-private.xml"),
                "<resources><string name=\"release_private_payload\">PRIVATE</string></resources>");
        write(excluded.resolve("java/test/debug_sentinel/ReleasePrivatePayload.java"),
                "package test.debug_sentinel; public final class ReleasePrivatePayload {}\n");
        Path app = root.resolve("app/src/benchmark");
        write(app.resolve("res/values/release-private.xml"),
                "<resources><string name=\"release_private_payload\">PRIVATE</string></resources>");
        write(app.resolve("java/test/debug_sentinel/ReleasePrivatePayload.java"),
                "package test.debug_sentinel; public final class ReleasePrivatePayload {}\n");
        BuildResult rejected = run(root, ":app:assembleBenchmark").buildAndFail();
        assertEquals(rejected.getOutput(), TaskOutcome.FAILED, rejected.task(":app:auditBenchmarkSetApk").getOutcome());
        assertTrue(rejected.getOutput(), rejected.getOutput().contains("Excluded resource in APK: string/release_private_payload"));
    }

    @Test public void rejectsPrivateLocalBinariesFromContributionsButAllowsNeutralShellOwnership() throws Exception {
        Path direct = packagingProject();
        archive(direct.resolve("local/private.jar"));
        append(direct.resolve("sets/public-sentinel/wallpaper/build.gradle"),
                "dependencies { api fileTree(dir: '../../../local', include: ['private.jar']) }\n");
        append(direct.resolve("sets/debug-sentinel/wallpaper/build.gradle"),
                "dependencies { api files('../../../local/private.jar') }\n");
        assertLocalBinaryRejected(direct, "private.jar");

        Path transitive = packagingProject();
        archive(transitive.resolve("local/private.aar"));
        append(transitive.resolve("bridge/build.gradle"), "dependencies { api files('../local/private.aar') }\n");
        append(transitive.resolve("sets/public-sentinel/wallpaper/build.gradle"), "dependencies { api project(':bridge') }\n");
        append(transitive.resolve("sets/debug-sentinel/wallpaper/build.gradle"),
                "dependencies { api files('../../../local/private.aar') }\n");
        assertLocalBinaryRejected(transitive, "private.aar");

        Path shellOwned = packagingProject();
        archive(shellOwned.resolve("local/shared.jar"));
        append(shellOwned.resolve("app/build.gradle"), "dependencies { api files('../local/shared.jar') }\n");
        append(shellOwned.resolve("sets/debug-sentinel/wallpaper/build.gradle"),
                "dependencies { api files('../../../local/shared.jar') }\n");
        BuildResult allowed = run(shellOwned, ":app:validateReleaseSetRegistry").build();
        assertEquals(allowed.getOutput(), TaskOutcome.SUCCESS, allowed.task(":app:validateReleaseSetRegistry").getOutcome());

        Path kotlinShared = packagingProject();
        append(kotlinShared.resolve("app/build.gradle"),
                "dependencies { implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.3.21' }\n");
        append(kotlinShared.resolve("sets/debug-sentinel/wallpaper/build.gradle"),
                "dependencies { implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.3.21' }\n");
        BuildResult shared = run(kotlinShared, ":app:validateReleaseSetRegistry").build();
        assertEquals(shared.getOutput(), TaskOutcome.SUCCESS, shared.task(":app:validateReleaseSetRegistry").getOutcome());
    }

    @Test public void shellOwnedSharedProjectRemainsAllowedInRealReleasePackaging() throws Exception {
        Path root = packagingProject();
        addSharedProject(root);
        for (String module : List.of("app", "sets/public-sentinel/wallpaper", "sets/debug-sentinel/wallpaper"))
            append(root.resolve(module + "/build.gradle"), "dependencies { implementation project(':shared') }\n");
        BuildResult release = run(root, ":app:assembleRelease").build();
        assertEquals(release.getOutput(), TaskOutcome.SUCCESS, release.task(":app:auditReleaseSetApk").getOutcome());
        assertTrue(report(root, "release", "app-release-unsigned.apk-dex-types.txt").contains("test.shared.SharedPayload"));
        String excluded = report(root, "release", "inventory.txt").lines()
                .filter(line -> line.startsWith("excludedResources=")).findFirst().orElseThrow();
        assertFalse(excluded, excluded.contains("shell_shared_marker"));
    }

    @Test public void shellOwnedBridgeCannotLaunderExcludedContributionRoot() throws Exception {
        Path root = packagingProject();
        append(root.resolve("app/build.gradle"), "dependencies { implementation project(':bridge') }\n");
        append(root.resolve("bridge/build.gradle"), "dependencies { api project(':sets:debug-sentinel:preview') }\n");
        BuildResult rejected = run(root, ":app:validateReleaseSetRegistry").buildAndFail();
        assertEquals(rejected.getOutput(), TaskOutcome.FAILED, rejected.task(":app:validateReleaseSetRegistry").getOutcome());
        assertTrue(rejected.getOutput(), rejected.getOutput().contains("Excluded contribution in transitive release runtime graph")
                && rejected.getOutput().contains(":sets:debug-sentinel:preview"));
    }


    @Test public void xmlInventoryRejectsExternalEntitiesAndUnknownReference() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path xml = write(root.resolve("unsafe.xml"), "<!DOCTYPE resources [<!ENTITY external SYSTEM 'file:///unread-secret'>]><resources><string name=\"leak\">&external;</string></resources>");
        try { new SetContentInventory().resource(xml, "values/unsafe.xml"); fail(); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("Unsafe or invalid resource XML")); }
        Path layout = write(root.resolve("safe.xml"), "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:background=\"@drawable/excluded\" android:id=\"@+id/local\"/>");
        SetContentInventory inventory = new SetContentInventory(); inventory.resource(layout, "layout/safe.xml");
        assertTrue(inventory.references.contains("drawable/excluded")); assertTrue(inventory.resources.contains("id/local"));
        SetManifest legacy = SetContractEngine.validate(List.of(Path.of(System.getProperty("livosphere.contourManifest")))).get(0);
        SetContentInventory excluded = new SetContentInventory();
        legacy.contributions().forEach(c -> excluded.contribution(legacy, c));
        assertTrue(excluded.resources.contains("raw/watchface"));
        assertTrue(excluded.resources.contains("xml/watch_face_info"));
    }

    Path packagingProject() throws Exception {
        Path root = temporary.newFolder().toPath();
        compilablePublic(root, "public-sentinel"); PhoneSetFixture.create(root, "debug-sentinel");
        List<String> modules = new ArrayList<>(List.of(":app", ":debug-payload", ":bridge"));
        for (String id : List.of("public-sentinel", "debug-sentinel")) for (String surface : List.of("preview", "wallpaper", "clock-widget")) {
            String modulePath = ":sets:" + id + ":" + surface; modules.add(modulePath);
            Path module = root.resolve(modulePath.substring(1).replace(':', '/'));
            write(module.resolve("build.gradle"), "plugins { id 'com.android.library'; id 'livosphere.set-consumer' }\n"
                    + "android { namespace 'test." + id.replace('-', '_') + "." + surface.replace('-', '_') + "'; compileSdk 37; defaultConfig { minSdk 29 } }\n"
                    + "setContract { setId.set('" + id + "'); surface.set('" + surface + "') }\n");
            String manifest = "<manifest/>";
            if (surface.equals("wallpaper")) {
                String namespace = "test." + id.replace('-', '_');
                manifest = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application><service android:name=\"" + namespace + ".WallpaperService\" android:exported=\"true\" android:permission=\"android.permission.BIND_WALLPAPER\"/>"
                        + (id.startsWith("debug") ? "<provider android:name=\"test.debug_sentinel.HiddenProvider\" android:authorities=\"test.sentinel.private\" android:exported=\"false\"/>" : "") + "</application></manifest>";
                write(module.resolve("src/main/java/" + namespace.replace('.', '/') + "/WallpaperService.java"), "package " + namespace + "; public class WallpaperService extends android.service.wallpaper.WallpaperService { @Override public Engine onCreateEngine() { return new Engine(); } }");
                if (id.startsWith("debug")) {
                    Files.writeString(module.resolve("build.gradle"), "dependencies { implementation project(':debug-payload') }\n", java.nio.file.StandardOpenOption.APPEND);
                    write(module.resolve("src/main/res/values/private.xml"), "<resources><string name=\"ls_debug_sentinel_wallpaper_private_title\">PRIVATE</string></resources>");
                    write(module.resolve("src/main/res/layout/ls_debug_sentinel_clock_widget_hidden.xml"), "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"/>");
                }
            }
            write(module.resolve("src/main/AndroidManifest.xml"), manifest);
        }
        for (String module : List.of("debug-payload", "bridge")) {
            write(root.resolve(module + "/build.gradle"), "plugins { id 'com.android.library' }; android { namespace 'test." + module.replace('-', '_') + "'; compileSdk 37; defaultConfig { minSdk 29 } }\n");
            write(root.resolve(module + "/src/main/AndroidManifest.xml"), "<manifest/>");
        }
        write(root.resolve("debug-payload/src/main/java/test/hidden/Payload.java"), "package test.hidden; public class Payload {} ");
        write(root.resolve("debug-payload/src/main/res/values/hidden.xml"), "<resources><string name=\"hidden_transitive_title\">PRIVATE</string></resources>");
        write(root.resolve("debug-payload/src/main/assets/private-payload.txt"), "PRIVATE");
        write(root.resolve("settings.gradle"), "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
                + "dependencyResolutionManagement { repositories { google(); mavenCentral() } }\nrootProject.name='temporary-packaging-sentinel'\n"
                + "include " + modules.stream().map(m -> "'" + m + "'").collect(java.util.stream.Collectors.joining(", ")) + "\n");
        write(root.resolve("gradle.properties"), "livosphere.setManifests=sets/public-sentinel/manifest/set.properties,sets/debug-sentinel/manifest/set.properties\norg.gradle.jvmargs=-Xmx1g\n");
        write(root.resolve("build.gradle"), "plugins { id 'livosphere.variant-source-collector' }\n");
        write(root.resolve("local.properties"), "sdk.dir=" + System.getenv("ANDROID_SDK_ROOT") + "\n");
        write(root.resolve("app/build.gradle"), "plugins { id 'com.android.application'; id 'livosphere.set-registry' }\n"
                + "android { namespace 'test.livosphere.packaging'; compileSdk 37; defaultConfig { applicationId 'test.livosphere.packaging'; minSdk 29; targetSdk 36; versionCode 1; versionName 'test-only' } }\n");
        write(root.resolve("app/src/main/AndroidManifest.xml"), "<manifest><application/></manifest>");
        write(root.resolve("app/src/main/java/test/shell/LegacyMigration.java"), "package test.shell; public class LegacyMigration { public static String id() { return \"debug-sentinel\"; } }");
        Path contract = root.resolve("app/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt"); Files.createDirectories(contract.getParent());
        Files.copy(Path.of(System.getProperty("livosphere.contractSource")), contract);
        return root;
    }

    private static Path compilablePublic(Path root, String id) throws Exception {
        Path manifest = PhoneSetFixture.create(root, id);
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifest)) { int equals = line.indexOf('='); if (equals > 0) values.put(line.substring(0, equals), line.substring(equals + 1)); }
        values.put("distribution", "public"); values.put("contentStatus", "html-approved"); values.put("contribution.clock-main.layoutStatus", "native");
        Path source = manifest.getParent().getParent().resolve("source-assets");
        List<String> checksums = new ArrayList<>();
        for (String key : new ArrayList<>(values.keySet())) if (key.startsWith("asset.") && key.endsWith(".path")) {
            String prefix = key.substring(0, key.length() - 4); String relative = values.get(key);
            if (relative.startsWith("clock-widget/raw/")) {
                Files.delete(source.resolve(relative)); relative = relative.replace("clock-widget/raw/", "clock-widget/layout/");
                values.put(key, relative); values.put(prefix + "resourcePath", values.get(prefix + "resourcePath").replace("raw/", "layout/"));
                write(source.resolve(relative), "<TextClock xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" android:format24Hour=\"HH:mm\"/>");
            }
            if (relative.startsWith("wallpaper/xml/")) write(source.resolve(relative), "<wallpaper xmlns:android=\"http://schemas.android.com/apk/res/android\"/>");
            String hash = PhoneSetFixture.hash(source.resolve(relative)); values.put(prefix + "sha256", hash); checksums.add(hash + "  " + relative);
        }
        Files.write(source.resolve("checksums.sha256"), checksums);
        Files.write(manifest, values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
        PhoneSetFixture.approval(manifest, "image"); PhoneSetFixture.approval(manifest, "html");
        return manifest;
    }

    private static void assertAuditFailure(Path root, String expected) throws Exception {
        BuildResult result = run(root, ":app:assembleRelease").buildAndFail();
        assertEquals(result.getOutput(), TaskOutcome.FAILED, result.task(":app:auditReleaseSetApk").getOutcome());
        assertTrue(result.getOutput(), result.getOutput().contains(expected));
        assertFalse(Files.exists(root.resolve("app/build/reports/set-content/release/app-release-unsigned.apk-audit.txt")));
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.writeString(evidence.resolve("sentinel-rejections.txt"), expected + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    private static void assertGeneratedRegistryFailure(Path root, String expected) throws Exception {
        BuildResult result = run(root, ":app:assembleRelease").buildAndFail();
        Path evidence = Path.of(System.getProperty("livosphere.packagingEvidence"));
        Files.writeString(evidence.resolve("generated-registry-failure.txt"), result.getOutput());
        assertEquals(result.getOutput(), TaskOutcome.FAILED, result.task(":app:verifyReleaseGeneratedSetRegistry").getOutcome());
        assertTrue(result.getOutput(), result.getOutput().contains(expected));
        assertFalse(Files.exists(root.resolve("app/build/reports/set-content/release/app-release-unsigned.apk-audit.txt")));
    }
    private static void assertLocalBinaryRejected(Path root, String filename) {
        BuildResult result = run(root, ":app:validateReleaseSetRegistry").buildAndFail();
        assertEquals(result.getOutput(), TaskOutcome.FAILED, result.task(":app:validateReleaseSetRegistry").getOutcome());
        assertTrue(result.getOutput(), result.getOutput().contains("Excluded local binary in release runtime graph")
                && result.getOutput().contains(filename));
    }
    private static void append(Path file, String content) throws Exception {
        Files.writeString(file, content, java.nio.file.StandardOpenOption.APPEND);
    }
    private static void addSharedProject(Path root) throws Exception {
        append(root.resolve("settings.gradle"), "include ':shared'\n");
        write(root.resolve("shared/build.gradle"), "plugins { id 'com.android.library' }\n"
                + "android { namespace 'test.shared'; compileSdk 37; defaultConfig { minSdk 29 } }\n");
        write(root.resolve("shared/src/main/AndroidManifest.xml"), "<manifest/>\n");
        write(root.resolve("shared/src/main/java/test/shared/SharedPayload.java"),
                "package test.shared; public final class SharedPayload { public static String value() { return \"shared\"; } }\n");
        write(root.resolve("shared/src/main/res/values/shared.xml"),
                "<resources><string name=\"shell_shared_marker\">shared</string></resources>\n");
    }
    private static void addBenchmark(Path root) throws Exception {
        append(root.resolve("app/build.gradle"),
                "android { buildTypes { benchmark { initWith release; matchingFallbacks = ['release'] } } }\n");
    }
    private static void addReleasePayload(Path root) throws Exception {
        append(root.resolve("settings.gradle"), "include ':release-payload'\n");
        write(root.resolve("release-payload/build.gradle"), "plugins { id 'com.android.library' }\n"
                + "android { namespace 'test.release_payload'; compileSdk 37; defaultConfig { minSdk 29 } }\n");
        write(root.resolve("release-payload/src/main/AndroidManifest.xml"), "<manifest/>\n");
        write(root.resolve("release-payload/src/release/java/test/release_payload/PrivateReleasePayload.java"),
                "package test.release_payload; public final class PrivateReleasePayload {}\n");
    }
    private static void addJvmShared(Path root) throws Exception {
        append(root.resolve("settings.gradle"), "include ':shared-jvm'\n");
        write(root.resolve("shared-jvm/build.gradle"), "plugins { id 'java-library' }\n");
        write(root.resolve("shared-jvm/src/main/java/test/shared_jvm/SharedJvmPayload.java"),
                "package test.shared_jvm; public final class SharedJvmPayload { public static String value() { return \"shared\"; } }\n");
        write(root.resolve("shared-jvm/src/main/resources/shared-jvm.txt"), "shared\n");
    }
    private static void addGeneratedAuditSource(Path root, String module, String taskName, String type) throws Exception {
        Path build = root.resolve(module + "/build.gradle");
        int lastDot = type.lastIndexOf('.');
        String packageName = type.substring(0, lastDot); String className = type.substring(lastDot + 1);
        append(build, """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.OutputDirectory
                import org.gradle.api.tasks.TaskAction
                abstract class GeneratedAuditSource extends DefaultTask {
                    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
                    @TaskAction void writeSource() {
                        def file = outputDirectory.file('%s.java').get().asFile
                        file.parentFile.mkdirs(); file.text = 'package %s; public final class %s {}\\n'
                    }
                }
                def %s = tasks.register('%s', GeneratedAuditSource) { outputDirectory.set(layout.buildDirectory.dir('generated/%s')) }
                androidComponents { onVariants(selector().withName('release')) { variant ->
                    variant.sources.java.addGeneratedSourceDirectory(%s, { it.outputDirectory })
                } }
                """.formatted(type.replace('.', '/'), packageName, className, taskName, taskName, taskName, taskName));
    }
    private static void addGeneratedResource(Path root, String module, String taskName, String resourceName) throws Exception {
        Path build = root.resolve(module + "/build.gradle");
        append(build, """
                import org.gradle.api.DefaultTask
                import org.gradle.api.file.DirectoryProperty
                import org.gradle.api.tasks.OutputDirectory
                import org.gradle.api.tasks.TaskAction
                abstract class GeneratedAuditResource extends DefaultTask {
                    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
                    @TaskAction void writeResource() {
                        def file = outputDirectory.file('values/generated.xml').get().asFile
                        file.parentFile.mkdirs(); file.text = '<resources><string name=\"%s\">LEAK</string></resources>'
                    }
                }
                def %s = tasks.register('%s', GeneratedAuditResource) { outputDirectory.set(layout.buildDirectory.dir('generated/%s')) }
                androidComponents { onVariants(selector().withName('release')) { variant ->
                    variant.sources.res.addGeneratedSourceDirectory(%s, { it.outputDirectory })
                } }
                """.formatted(resourceName, taskName, taskName, taskName, taskName));
    }
    private static void archive(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry("placeholder.txt")); output.write(new byte[] { 0 }); output.closeEntry();
        }
    }
    private static String report(Path root, String variant, String file) throws Exception { return Files.readString(root.resolve("app/build/reports/set-content/" + variant + "/" + file)); }
    private static Path write(Path file, String content) throws Exception { Files.createDirectories(file.getParent()); Files.writeString(file, content); return file; }
    private static void replace(Path file, String from, String to) throws Exception { Files.writeString(file, Files.readString(file).replace(from, to)); }
    static GradleRunner run(Path root, String... tasks) {
        GradleRunner runner = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath().withTestKitDir(Path.of(System.getProperty("livosphere.testKitHome")).toFile());
        List<File> classpath = new ArrayList<>(runner.getPluginClasspath());
        Arrays.stream(System.getProperty("livosphere.testKitPluginClasspath").split(Pattern.quote(File.pathSeparator))).map(File::new).forEach(classpath::add);
        List<String> arguments = new ArrayList<>(List.of("--offline", "--console=plain", "--max-workers=2"));
        arguments.addAll(List.of(tasks));
        return runner.withPluginClasspath(classpath).withArguments(arguments);
    }
}
