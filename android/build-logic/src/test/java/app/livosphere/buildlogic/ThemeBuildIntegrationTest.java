package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Two bounded TestKit scenarios for the production theme build path. */
public class ThemeBuildIntegrationTest {
    private static final Pattern SET_ID = Pattern.compile("SetId\\(\\\"([^\\\"]+)\\\"\\)");

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void debugReleaseDebugUsesSelectedRegistryAndDependenciesWithoutLegacyAudit() throws Exception {
        Path root = project(true);

        BuildResult firstDebug = run(root, ":app:assembleDebug", ":app:captureVariantDependencies").build();
        assertEquals(TaskOutcome.SUCCESS, firstDebug.task(":app:assembleDebug").getOutcome());
        assertNull(firstDebug.task(":app:auditDebugSetApk"));
        assertEquals(Set.of("debug-set", "public-set"), registryIds(root, "debug"));
        assertEquals(expectedProjects("debug-set", "public-set"), dependencyProjects(root, "debug"));

        BuildResult release = run(root, ":app:assembleRelease", ":app:captureVariantDependencies").build();
        assertEquals(TaskOutcome.SUCCESS, release.task(":app:assembleRelease").getOutcome());
        assertNull(release.task(":app:auditReleaseSetApk"));
        assertEquals(Set.of("public-set"), registryIds(root, "release"));
        assertEquals(expectedProjects("public-set"), dependencyProjects(root, "release"));

        BuildResult secondDebug = run(root, ":app:assembleDebug").build();
        assertTrue(Set.of(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
                .contains(secondDebug.task(":app:assembleDebug").getOutcome()));
        assertNull(secondDebug.task(":app:auditDebugSetApk"));
        assertEquals(Set.of("debug-set", "public-set"), registryIds(root, "debug"));
        assertFalse(registryIds(root, "release").contains("debug-set"));
    }

    @Test public void emptyPublicContentFailsBeforePackagingWhileDebugStillBuilds() throws Exception {
        Path root = project(false);

        BuildResult debug = run(root, ":app:assembleDebug").build();
        assertEquals(TaskOutcome.SUCCESS, debug.task(":app:assembleDebug").getOutcome());
        assertNull(debug.task(":app:auditDebugSetApk"));
        assertEquals(Set.of("debug-set"), registryIds(root, "debug"));

        BuildResult release = run(root, ":app:assembleRelease").buildAndFail();
        assertTrue(release.getOutput(), release.getOutput().contains("empty public content closure"));
        assertEquals(TaskOutcome.FAILED, release.task(":app:validateReleaseSetRegistry").getOutcome());
        assertNull(release.task(":app:packageRelease"));
        assertNull(release.task(":app:auditReleaseSetApk"));
        assertFalse(Files.exists(root.resolve("app/build/outputs/apk/release/app-release-unsigned.apk")));
    }

    private Path project(boolean includePublic) throws Exception {
        Path root = temporary.newFolder().toPath();
        List<String> setIds = new ArrayList<>(List.of("debug-set"));
        List<Path> manifests = new ArrayList<>(List.of(PhoneSetFixture.create(root, "debug-set")));
        if (includePublic) {
            setIds.add("public-set");
            manifests.add(PhoneSetFixture.createPublic(root, "public-set"));
        }
        List<String> modules = new ArrayList<>(List.of(":app"));
        for (String setId : setIds) {
            for (String surface : List.of("preview", "wallpaper", "clock-widget")) {
                String modulePath = ":sets:" + setId + ":" + surface;
                modules.add(modulePath);
                createContributionModule(root, setId, surface, modulePath);
            }
        }
        write(root.resolve("settings.gradle"),
                "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n"
                        + "dependencyResolutionManagement { repositories { google(); mavenCentral() } }\n"
                        + "rootProject.name='theme-build-integration'\ninclude "
                        + modules.stream().map(module -> "'" + module + "'")
                                .collect(java.util.stream.Collectors.joining(", ")) + "\n");
        write(root.resolve("gradle.properties"), "livosphere.setManifests=" + manifests.stream()
                .map(root::relativize).map(Path::toString).collect(java.util.stream.Collectors.joining(","))
                + "\norg.gradle.jvmargs=-Xmx1g\n");
        write(root.resolve("build.gradle"), "");
        write(root.resolve("local.properties"), "sdk.dir=" + System.getenv("ANDROID_SDK_ROOT") + "\n");
        write(root.resolve("app/build.gradle"), """
                import org.gradle.api.artifacts.ProjectDependency
                plugins { id 'com.android.application'; id 'livosphere.set-registry' }
                android {
                    namespace 'test.livosphere.themebuild'
                    compileSdk 37
                    defaultConfig { applicationId 'test.livosphere.themebuild'; minSdk 29; targetSdk 36; versionCode 1; versionName 'test-only' }
                }
                tasks.register('captureVariantDependencies') {
                    doLast {
                        ['debug', 'release'].each { variant ->
                            def projects = configurations.getByName(variant + 'Implementation').dependencies
                                .withType(ProjectDependency).collect { it.path }.sort()
                            def output = file("$buildDir/reports/selection/${variant}-projects.txt")
                            output.parentFile.mkdirs()
                            output.text = projects.join('\\n') + '\\n'
                        }
                    }
                }
                """);
        write(root.resolve("app/src/main/AndroidManifest.xml"), "<manifest><application/></manifest>\n");
        Path contract = root.resolve("app/src/main/kotlin/app/livosphere/contract/SetDescriptor.kt");
        Files.createDirectories(contract.getParent());
        Files.copy(Path.of(System.getProperty("livosphere.contractSource")), contract);
        return root;
    }

    private static void createContributionModule(Path root, String setId, String surface, String modulePath) throws Exception {
        Path module = root.resolve(modulePath.substring(1).replace(':', '/'));
        write(module.resolve("build.gradle"),
                "plugins { id 'com.android.library'; id 'livosphere.set-consumer' }\n"
                        + "android { namespace 'test." + setId.replace('-', '_') + "." + surface.replace('-', '_')
                        + "'; compileSdk 37; defaultConfig { minSdk 29 } }\n"
                        + "setContract { setId.set('" + setId + "'); surface.set('" + surface + "') }\n");
        String manifest = "<manifest/>\n";
        if (surface.equals("wallpaper")) {
            String namespace = "test." + setId.replace('-', '_');
            manifest = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application>"
                    + "<service android:name=\"" + namespace + ".WallpaperService\" android:exported=\"true\" "
                    + "android:permission=\"android.permission.BIND_WALLPAPER\"/></application></manifest>\n";
            write(module.resolve("src/main/java/" + namespace.replace('.', '/') + "/WallpaperService.java"),
                    "package " + namespace + "; public class WallpaperService extends android.service.wallpaper.WallpaperService "
                            + "{ @Override public Engine onCreateEngine() { return new Engine(); } }\n");
        }
        write(module.resolve("src/main/AndroidManifest.xml"), manifest);
    }

    private static Set<String> registryIds(Path root, String variant) throws Exception {
        String suffix = Character.toUpperCase(variant.charAt(0)) + variant.substring(1);
        String source = Files.readString(root.resolve("app/build/generated/kotlin/generate" + suffix
                + "SetRegistry/app/livosphere/generated/GeneratedSetRegistry.kt"));
        Matcher matcher = SET_ID.matcher(source);
        Set<String> result = new TreeSet<>();
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private static Set<String> dependencyProjects(Path root, String variant) throws Exception {
        return new TreeSet<>(Files.readAllLines(root.resolve("app/build/reports/selection/" + variant + "-projects.txt")));
    }

    private static Set<String> expectedProjects(String... setIds) {
        Set<String> result = new TreeSet<>();
        for (String setId : setIds) for (String surface : List.of("preview", "wallpaper", "clock-widget"))
            result.add(":sets:" + setId + ":" + surface);
        return result;
    }

    private static GradleRunner run(Path root, String... tasks) {
        GradleRunner runner = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withTestKitDir(Path.of(System.getProperty("livosphere.testKitHome")).toFile());
        List<File> classpath = new ArrayList<>(runner.getPluginClasspath());
        Arrays.stream(System.getProperty("livosphere.testKitPluginClasspath").split(Pattern.quote(File.pathSeparator)))
                .map(File::new).forEach(classpath::add);
        List<String> arguments = new ArrayList<>(List.of("--offline", "--console=plain", "--max-workers=2"));
        arguments.addAll(List.of(tasks));
        return runner.withPluginClasspath(classpath).withArguments(arguments);
    }

    private static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
