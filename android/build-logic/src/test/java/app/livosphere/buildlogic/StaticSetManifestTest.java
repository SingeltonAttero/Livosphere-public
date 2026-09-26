package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** One schema4 fixture covers future static sets; it is not a content-pack test. */
public class StaticSetManifestTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void schema4GeneratesTypedStaticClockContract() throws Exception {
        Path manifest = schema4Fixture("static-clock");
        SetManifest descriptor = SetContractEngine.validate(List.of(manifest)).get(0);

        assertEquals(4, descriptor.schemaVersion());
        assertEquals(List.of("clock-widget", "preview", "wallpaper"), descriptor.contributions().stream()
                .map(SetManifest.Contribution::surface).sorted().toList());
        assertEquals(3, descriptor.contributionFor("preview").widgetRefs().size());
        assertEquals(3, descriptor.contributionFor("clock-widget").viewRoles().size());
        assertEquals("native", descriptor.contributionFor("clock-widget").layoutStatus());
        assertTrue(descriptor.contributionFor("wallpaper").effectsRefs().isEmpty());
        assertEquals(null, descriptor.contributionFor("wallpaper").sceneRef());

        Path output = temporaryFolder.newFolder("generated").toPath();
        SetContractEngine.generateRegistry(List.of(manifest), output);
        String generated = Files.readString(output.resolve("app/livosphere/generated/GeneratedSetRegistry.kt"));
        assertTrue(generated.contains("schemaVersion = 4"));
        assertTrue(generated.contains("ClockViewRole.TIME"));
        assertTrue(generated.contains("ClockViewRole.DATE"));
    }

    @Test
    public void schema4RejectsMissingStaticSurfaceReferencesOrInvalidRoles() throws Exception {
        assertInvalid("missing-preview", "contribution.preview-main.widgetRefs.m=static-clock-preview-m\n", "",
                "contribution.preview-main.widgetRefs.m", "обязательное поле отсутствует");
        assertInvalid("missing-role", "contribution.clock-main.viewRoles.l.keys=root,time,date\n", "",
                "contribution.clock-main.viewRoles.l.keys", "обязательное поле отсутствует");
        assertInvalid("mixed-digital",
                "contribution.clock-main.viewRoles.s.keys=root,time\n"
                        + "contribution.clock-main.viewRoles.s.root=clock_widget_root\n"
                        + "contribution.clock-main.viewRoles.s.time=clock_widget_time\n",
                "contribution.clock-main.viewRoles.s.keys=root,time,hours,minutes\n"
                        + "contribution.clock-main.viewRoles.s.root=clock_widget_root\n"
                        + "contribution.clock-main.viewRoles.s.time=clock_widget_time\n"
                        + "contribution.clock-main.viewRoles.s.hours=clock_widget_hours\n"
                        + "contribution.clock-main.viewRoles.s.minutes=clock_widget_minutes\n",
                "contribution.clock-main.viewRoles.s", "digital требует ровно time или пару hours+minutes");
        assertInvalid("analog-with-digital-time", "contribution.clock-main.style=digital",
                "contribution.clock-main.style=analog", "contribution.clock-main.viewRoles.s",
                "analog требует analog role");
    }

    @Test
    public void schema4PublicHtmlApprovedSetIsReleaseEligible() throws Exception {
        Path manifest = PhoneSetFixture.createStatic(temporaryFolder.newFolder("static-public").toPath(), "static-clock", true);

        SetManifest descriptor = SetContractEngine.validate(List.of(manifest)).get(0);

        assertEquals(4, descriptor.schemaVersion());
        assertEquals("public", descriptor.distribution());
        assertTrue(descriptor.releaseEligible());
        VariantContentSelection release = SetContractEngine.select(List.of(manifest), "release");
        assertEquals(List.of("static-clock"), release.selected().stream().map(SetManifest::setId).toList());
        Path debugOnly = schema4Fixture("static-debug");
        assertTrue(!SetContractEngine.validate(List.of(debugOnly)).get(0).releaseEligible());
        assertTrue(SetContractEngine.select(List.of(debugOnly), "release").selected().isEmpty());
    }

    @Test
    public void schema4DoesNotReinterpretExistingPhoneSchemaOrProfiles() throws Exception {
        Path legacyCompatiblePhone = PhoneSetFixture.create(temporaryFolder.newFolder("schema2").toPath(), "existing-phone");
        SetManifest descriptor = SetContractEngine.validate(List.of(legacyCompatiblePhone)).get(0);

        assertEquals(2, descriptor.schemaVersion());
        assertEquals("test-declaration", descriptor.contributionFor("clock-widget").layoutStatus());
        assertEquals(BuildProfile.PHONE, BuildProfile.parse("phone"));
        assertEquals(BuildProfile.LEGACY, BuildProfile.parse("legacy"));
    }

    private void assertInvalid(String directory, String target, String replacement, String field, String reason) throws Exception {
        Path manifest = schema4Fixture(directory);
        String contents = Files.readString(manifest);
        assertTrue("Fixture mutation target is missing: " + directory, contents.contains(target));
        Files.writeString(manifest, contents.replace(target, replacement));
        try {
            SetContractEngine.validate(List.of(manifest));
            fail("Expected schema4 contract rejection: " + directory);
        } catch (GradleException expected) {
            assertTrue(expected.getMessage().contains("set.properties"));
            assertTrue(expected.getMessage().contains(field));
            assertTrue(expected.getMessage().contains(reason));
        }
    }

    private Path schema4Fixture(String directory) throws Exception {
        return PhoneSetFixture.createStatic(temporaryFolder.newFolder(directory).toPath(), "static-clock", false);
    }
}
