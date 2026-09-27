package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Phone-only build configuration and content guard. */
public class PhoneProfileUnitTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void onlyPhoneProfileIsAccepted() {
        assertEquals(BuildProfile.PHONE, BuildProfile.parse(null));
        assertEquals(BuildProfile.PHONE, BuildProfile.parse("phone"));
        GradleException error = assertThrows(GradleException.class, () -> BuildProfile.parse("legacy"));
        assertTrue(error.getMessage().contains("supports only phone"));
    }

    @Test public void phoneStillRejectsCorruptedAssets() throws Exception {
        Path manifest = PhoneSetFixture.create(temporary.newFolder().toPath(), "phone-assets");
        SetManifest valid = SetManifestReader.read(manifest, BuildProfile.PHONE);
        assertEquals("phone-assets", valid.setId());
        SetManifest.Asset asset = valid.contributionFor("preview").assets().get(0);
        Files.writeString(valid.sourceAssetsRoot().resolve(asset.relativePath()), "corrupted");
        GradleException error = assertThrows(GradleException.class,
                () -> SetManifestReader.read(manifest, BuildProfile.PHONE));
        assertTrue(error.getMessage().contains("checksum mismatch"));
    }
}
