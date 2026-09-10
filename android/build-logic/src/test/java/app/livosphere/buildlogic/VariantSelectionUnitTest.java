package app.livosphere.buildlogic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Pure JVM coverage for the policy shared by dependency and generated-registry wiring. */
public class VariantSelectionUnitTest {
    private static final Pattern SET_ID = Pattern.compile("SetId\\(\\\"([^\\\"]+)\\\"\\)");

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void debugSelectsEveryDeclaredSetAndMapsEveryContributionProject() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path draft = PhoneSetFixture.create(root, "draft-set");
        Path publicSet = PhoneSetFixture.createPublic(root, "public-set");

        VariantContentSelection selection = SetContractEngine.select(List.of(draft, publicSet), "debug");

        assertEquals(Set.of("draft-set", "public-set"), ids(selection));
        assertEquals(Set.of(
                ":sets:draft-set:preview", ":sets:draft-set:wallpaper", ":sets:draft-set:clock-widget",
                ":sets:public-set:preview", ":sets:public-set:wallpaper", ":sets:public-set:clock-widget"),
                selection.projects());
    }

    @Test public void nonDebugSelectsOnlyPublicHtmlApprovedSet() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path debug = PhoneSetFixture.create(root, "debug-set");
        Path imageOnly = imageApprovedPublic(root, "image-only");
        Path publicSet = PhoneSetFixture.createPublic(root, "public-set");

        VariantContentSelection release = SetContractEngine.select(List.of(debug, imageOnly, publicSet), "release");
        VariantContentSelection benchmark = SetContractEngine.select(List.of(debug, imageOnly, publicSet), "benchmark");

        assertEquals(Set.of("public-set"), ids(release));
        assertEquals(Set.of("public-set"), ids(benchmark));
        assertEquals(Set.of("debug-set", "image-only"),
                release.excluded().stream().map(SetManifest::setId).collect(Collectors.toSet()));
    }

    @Test public void emptyPublicSelectionRefusesCandidate() throws Exception {
        Path root = temporary.newFolder().toPath();
        VariantContentSelection selection = SetContractEngine.select(
                List.of(PhoneSetFixture.create(root, "debug-set"), imageApprovedPublic(root, "image-only")),
                "release");

        GradleException error = assertThrows(GradleException.class, selection::requireNonEmpty);
        assertTrue(error.getMessage().contains("empty public content closure"));
    }

    @Test public void generatedRegistryContainsSelectedModelIdsOnly() throws Exception {
        Path root = temporary.newFolder().toPath();
        Path debug = PhoneSetFixture.create(root, "debug-set");
        Path publicSet = PhoneSetFixture.createPublic(root, "public-set");
        VariantContentSelection selection = SetContractEngine.select(List.of(debug, publicSet), "release");
        Path output = root.resolve("generated");

        SetContractEngine.generateRegistry(selection, output);

        String source = Files.readString(output.resolve("app/livosphere/generated/GeneratedSetRegistry.kt"));
        assertEquals(Set.of("public-set"), generatedSetIds(source));
        assertTrue(source.contains("ComponentId(\"public-set-preview\")"));
        assertTrue(source.contains("ComponentId(\"public-set-wallpaper\")"));
        assertTrue(source.contains("ComponentId(\"public-set-clock-widget\")"));
        assertFalse(source.contains("debug-set"));
    }

    private static Path imageApprovedPublic(Path root, String setId) throws Exception {
        Path manifest = PhoneSetFixture.createPublic(root, setId);
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.startsWith("approval.html."))
                .map(line -> line.equals("contentStatus=html-approved") ? "contentStatus=image-approved" : line)
                .toList();
        Files.write(manifest, lines);
        return manifest;
    }

    private static Set<String> ids(VariantContentSelection selection) {
        return selection.selected().stream().map(SetManifest::setId).collect(Collectors.toSet());
    }

    private static Set<String> generatedSetIds(String source) {
        Matcher matcher = SET_ID.matcher(source);
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }
}
