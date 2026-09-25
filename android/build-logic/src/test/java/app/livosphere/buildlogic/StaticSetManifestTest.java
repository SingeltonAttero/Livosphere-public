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
        assertInvalid("mixed-digital", "contribution.clock-main.viewRoles.s.keys=root,time\n",
                "contribution.clock-main.viewRoles.s.keys=root,time,hours,minutes\n",
                "contribution.clock-main.viewRoles.s", "digital требует ровно time или пару hours+minutes");
        assertInvalid("analog-with-digital-time", "contribution.clock-main.style=digital",
                "contribution.clock-main.style=analog", "contribution.clock-main.viewRoles.s",
                "analog запрещает digital time roles");
        assertInvalid("public", "distribution=debug-only", "distribution=public",
                "distribution", "schema4 static set");
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
        Path manifest = PhoneSetFixture.createPublic(temporaryFolder.newFolder(directory).toPath(), "static-clock");
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.startsWith("contribution.wallpaper-main.effectsRefs="))
                .filter(line -> !line.startsWith("contribution.wallpaper-main.sceneRef="))
                .filter(line -> !line.startsWith("asset.static-clock-scene."))
                .filter(line -> !line.startsWith("asset.static-clock-effects."))
                .map(line -> line.equals("schemaVersion=2") ? "schemaVersion=4" : line)
                .map(line -> line.equals("distribution=public") ? "distribution=debug-only" : line)
                .map(line -> line.equals("contribution.wallpaper-main.assetRefs=static-clock-entrypoint,static-clock-scene,static-clock-phase-morning,static-clock-phase-day,static-clock-phase-evening,static-clock-phase-night,static-clock-effects")
                        ? "contribution.wallpaper-main.assetRefs=static-clock-entrypoint,static-clock-phase-morning,static-clock-phase-day,static-clock-phase-evening,static-clock-phase-night" : line)
                .toList();
        Files.write(manifest, lines);
        Files.writeString(manifest, "\n"
                + "contribution.clock-main.displayName=Часы\n"
                + "contribution.clock-main.viewRoles.s.keys=root,time\n"
                + "contribution.clock-main.viewRoles.s.root=clock_widget_root\n"
                + "contribution.clock-main.viewRoles.s.time=clock_widget_time\n"
                + "contribution.clock-main.viewRoles.m.keys=root,time,date\n"
                + "contribution.clock-main.viewRoles.m.root=clock_widget_root\n"
                + "contribution.clock-main.viewRoles.m.time=clock_widget_time\n"
                + "contribution.clock-main.viewRoles.m.date=clock_widget_date\n"
                + "contribution.clock-main.viewRoles.l.keys=root,time,date\n"
                + "contribution.clock-main.viewRoles.l.root=clock_widget_root\n"
                + "contribution.clock-main.viewRoles.l.time=clock_widget_time\n"
                + "contribution.clock-main.viewRoles.l.date=clock_widget_date\n");
        return manifest;
    }
}
