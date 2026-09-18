package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Pure JVM profile coverage. Fixtures are not build or publication evidence. */
public class PhoneProfileUnitTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void defaultAndExplicitProfilesAreStrict() {
        assertEquals(BuildProfile.PHONE, BuildProfile.parse(null));
        assertEquals(BuildProfile.PHONE, BuildProfile.parse("phone"));
        assertEquals(BuildProfile.LEGACY, BuildProfile.parse("legacy"));
        GradleException error = assertThrows(GradleException.class, () -> BuildProfile.parse("wear"));
        assertTrue(error.getMessage().contains("supports only phone or legacy"));
    }

    @Test public void phoneKeepsManifestGuardsButDoesNotRequirePhysicalWatchfaceAssets() throws Exception {
        Path manifest = contourWithoutWatchfaceAssets();

        SetManifest phone = SetManifestReader.read(manifest, BuildProfile.PHONE);

        assertEquals("contour-draft", phone.setId());
        assertEquals("watchface", phone.contributionFor("watchface").surface());
        GradleException legacy = assertThrows(GradleException.class,
                () -> SetManifestReader.read(manifest, BuildProfile.LEGACY));
        assertTrue(legacy.getMessage().contains("asset отсутствует"));
    }

    @Test public void phoneStillRejectsCorruptedPhoneAssets() throws Exception {
        Path manifest = contourWithoutWatchfaceAssets();
        Path wallpaper = manifest.getParent().getParent().resolve(
                "source-assets/wallpaper/xml/ls_contour_draft_wallpaper_entrypoint.xml");
        Files.writeString(wallpaper, "<corrupted-phone-asset/>\n");

        GradleException error = assertThrows(GradleException.class,
                () -> SetManifestReader.read(manifest, BuildProfile.PHONE));
        assertTrue(error.getMessage().contains("checksum mismatch"));
        assertTrue(error.getMessage().contains("wallpaper-entrypoint"));
    }

    @Test public void phoneStillChecksDeclaredWatchfaceHashMetadata() throws Exception {
        Path manifest = contourWithoutWatchfaceAssets();
        String text = Files.readString(manifest);
        text = text.replaceFirst("asset\\.watchface-entrypoint\\.sha256=[0-9a-f]{64}",
                "asset.watchface-entrypoint.sha256=" + "0".repeat(64));
        Files.writeString(manifest, text);

        GradleException error = assertThrows(GradleException.class,
                () -> SetManifestReader.read(manifest, BuildProfile.PHONE));
        assertTrue(error.getMessage().contains("checksumsFile"));
        assertTrue(error.getMessage().contains("индекс checksums не совпадает"));
    }

    @Test public void acceptsDeclaredEffectLevelAndRejectsUnknownSetting() throws Exception {
        Path manifest = PhoneSetFixture.create(temporary.newFolder().toPath(), "effects");
        String text = Files.readString(manifest).replace("contribution.wallpaper-main.supportedSettings=none",
                "contribution.wallpaper-main.supportedSettings=time-of-day,effect-level");
        Files.writeString(manifest, text);
        assertTrue(SetManifestReader.read(manifest, BuildProfile.PHONE).contributionFor("wallpaper")
                .supportedSettings().contains("effect-level"));
        Files.writeString(manifest, text.replace("effect-level", "unsupported-effect"));
        assertThrows(GradleException.class, () -> SetManifestReader.read(manifest, BuildProfile.PHONE));
    }

    private Path contourWithoutWatchfaceAssets() throws Exception {
        Path sourceManifest = Path.of(System.getProperty("livosphere.contourManifest"));
        Path sourceSet = sourceManifest.getParent().getParent();
        Path targetSet = temporary.newFolder().toPath().resolve("sets/contour");
        copyTree(sourceSet, targetSet);
        deleteTree(targetSet.resolve("source-assets/watchface"));
        return targetSet.resolve("manifest/set.properties");
    }

    static void copyTree(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
