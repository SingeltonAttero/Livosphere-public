package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Generic schema3 contract tests. The fixture is authored locally and represents no content pack. */
public class WallpaperOnlyManifestTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void wallpaperOnlySchemaGeneratesTypedContractWithoutClockSurface() throws Exception {
        Path manifest = schema3Fixture("static-fixture");
        SetManifest descriptor = SetContractEngine.validate(List.of(manifest)).get(0);

        assertEquals(3, descriptor.schemaVersion());
        assertEquals(List.of("preview", "wallpaper"), descriptor.contributions().stream()
                .map(SetManifest.Contribution::surface).sorted().toList());
        assertEquals(4, descriptor.contributionFor("wallpaper").phaseRefs().size());
        assertTrue(descriptor.contributionFor("wallpaper").effectsRefs().isEmpty());
        assertEquals(null, descriptor.contributionFor("wallpaper").sceneRef());

        Path output = temporaryFolder.newFolder("generated").toPath();
        SetContractEngine.generateRegistry(List.of(manifest), output);
        String generated = Files.readString(output.resolve("app/livosphere/generated/GeneratedSetRegistry.kt"));
        assertTrue(generated.contains("schemaVersion = 3"));
        assertTrue(generated.contains("clockWidget = null"));
        assertTrue(generated.contains("DayPhase.MORNING"));
        assertTrue(!generated.contains("ClockWidgetContribution"));
    }

    @Test
    public void wallpaperOnlySchemaRejectsMissingOrMismatchedRequiredReferences() throws Exception {
        assertInvalid("missing-phase", "contribution.wallpaper-main.phaseRefs.night=static-fixture-phase-night\n", "",
                "contribution.wallpaper-main.phaseRefs.night", "обязательное поле отсутствует");
        assertInvalid("mismatched-preview", "contribution.wallpaper-main.previewRef=static-fixture-preview-wallpaper",
                "contribution.wallpaper-main.previewRef=static-fixture-phase-day",
                "contribution.wallpaper-main.previewRef", "необъявленный resource ref");
        assertInvalid("forbidden-widget", "contribution.preview-main.assetRefs=static-fixture-preview-wallpaper",
                "contribution.preview-main.assetRefs=static-fixture-preview-wallpaper\n"
                        + "contribution.preview-main.widgetRefs.s=static-fixture-preview-wallpaper",
                "schema", "widgetRefs.s");
        assertInvalid("forbidden-clock", "contributions=preview-main,wallpaper-main",
                "contributions=preview-main,wallpaper-main,clock-main",
                "contribution.clock-main.componentId", "обязательное поле отсутствует");
        assertInvalid("public", "distribution=debug-only", "distribution=public",
                "distribution", "schema3 wallpaper-only");
    }

    private void assertInvalid(String name, String target, String replacement, String field, String reason) throws Exception {
        Path manifest = schema3Fixture(name);
        String contents = Files.readString(manifest);
        assertTrue("Fixture mutation target is missing: " + name, contents.contains(target));
        Files.writeString(manifest, contents.replace(target, replacement));
        try {
            SetContractEngine.validate(List.of(manifest));
            fail("Expected schema3 contract rejection: " + name);
        } catch (GradleException expected) {
            assertTrue(expected.getMessage().contains("set.properties"));
            assertTrue(expected.getMessage().contains(field));
            assertTrue(expected.getMessage().contains(reason));
        }
    }

    private Path schema3Fixture(String directory) throws Exception {
        Path manifest = PhoneSetFixture.create(temporaryFolder.newFolder(directory).toPath(), "static-fixture");
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.startsWith("contribution.clock-main."))
                .filter(line -> !line.startsWith("asset.static-fixture-clock-"))
                .filter(line -> !line.startsWith("asset.static-fixture-scene."))
                .filter(line -> !line.startsWith("asset.static-fixture-effects."))
                .filter(line -> !line.startsWith("contribution.wallpaper-main.effectsRefs="))
                .filter(line -> !line.startsWith("contribution.wallpaper-main.sceneRef="))
                .filter(line -> !line.startsWith("contribution.preview-main.widgetRefs."))
                .filter(line -> !line.startsWith("asset.static-fixture-preview-s."))
                .filter(line -> !line.startsWith("asset.static-fixture-preview-m."))
                .filter(line -> !line.startsWith("asset.static-fixture-preview-l."))
                .map(line -> line.equals("schemaVersion=2") ? "schemaVersion=3" : line)
                .map(line -> line.equals("contentStatus=draft") ? "contentStatus=image-approved" : line)
                .map(line -> line.equals("contributions=preview-main,wallpaper-main,clock-main") ? "contributions=preview-main,wallpaper-main" : line)
                .map(line -> line.equals("contribution.preview-main.assetRefs=static-fixture-preview-wallpaper,static-fixture-preview-s,static-fixture-preview-m,static-fixture-preview-l")
                        ? "contribution.preview-main.assetRefs=static-fixture-preview-wallpaper" : line)
                .map(line -> line.equals("contribution.wallpaper-main.assetRefs=static-fixture-entrypoint,static-fixture-scene,static-fixture-phase-morning,static-fixture-phase-day,static-fixture-phase-evening,static-fixture-phase-night,static-fixture-effects")
                        ? "contribution.wallpaper-main.assetRefs=static-fixture-entrypoint,static-fixture-phase-morning,static-fixture-phase-day,static-fixture-phase-evening,static-fixture-phase-night" : line)
                .toList();
        Files.write(manifest, lines);
        Path source = manifest.getParent().getParent().resolve("source-assets");
        deleteTree(source.resolve("clock-widget"));
        Files.deleteIfExists(source.resolve("wallpaper/raw/ls_static_fixture_wallpaper_scene.xml"));
        Files.deleteIfExists(source.resolve("wallpaper/raw/ls_static_fixture_wallpaper_effects.xml"));
        Files.deleteIfExists(source.resolve("preview/drawable-nodpi/ls_static_fixture_preview_s.png"));
        Files.deleteIfExists(source.resolve("preview/drawable-nodpi/ls_static_fixture_preview_m.png"));
        Files.deleteIfExists(source.resolve("preview/drawable-nodpi/ls_static_fixture_preview_l.png"));
        rewriteChecksums(manifest, source);
        PhoneSetFixture.approval(manifest, "image");
        return manifest;
    }

    private static void rewriteChecksums(Path manifest, Path source) throws Exception {
        List<String> paths = Files.readAllLines(manifest).stream()
                .filter(line -> line.startsWith("asset.") && line.contains(".path="))
                .map(line -> line.substring(line.indexOf('=') + 1))
                .toList();
        Files.write(source.resolve("checksums.sha256"), paths.stream()
                .map(path -> {
                    try { return PhoneSetFixture.hash(source.resolve(path)) + "  " + path; }
                    catch (Exception error) { throw new IllegalStateException(error); }
                }).collect(Collectors.toList()));
    }

    private static void deleteTree(Path root) throws Exception {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
