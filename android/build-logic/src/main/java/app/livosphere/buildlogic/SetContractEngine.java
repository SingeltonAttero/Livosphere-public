package app.livosphere.buildlogic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;

final class SetContractEngine {
    private SetContractEngine() {}

    static List<SetManifest> validate(List<Path> manifestPaths) {
        return SetManifestReader.readAll(manifestPaths);
    }

    static VariantContentSelection select(List<Path> manifestPaths, String buildType) {
        return VariantContentSelection.select(validate(manifestPaths), buildType);
    }

    static void generateResources(List<Path> manifestPaths, String setId, String surface, Path outputDirectory) {
        generateResources(select(manifestPaths, "debug"), setId, surface, outputDirectory);
    }

    static void generateResources(VariantContentSelection selection, String setId, String surface, Path outputDirectory) {
        selection.requireNonEmpty();
        List<SetManifest> manifests = selection.selected();
        SetManifest manifest = manifests.stream()
                .filter(candidate -> candidate.setId().equals(setId))
                .findFirst()
                .orElseThrow(() -> new GradleException(
                        "Set '" + setId + "' не найден среди явно подключённых manifests: " + manifests));
        SetManifest.Contribution contribution = manifest.contributionFor(surface);
        recreate(outputDirectory);
        contribution.assets().stream()
                .sorted(Comparator.comparing(SetManifest.Asset::resourcePath))
                .forEach(asset -> copy(manifest.sourceAssetsRoot().resolve(asset.relativePath()),
                        outputDirectory.resolve(asset.resourcePath())));

        String prefix = "ls_" + setId.replace('-', '_') + "_" + surface.replace('-', '_') + "_";
        Path values = outputDirectory.resolve("values/" + prefix + "contract.xml");
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <integer name="%sschema_version">%d</integer>
                    <integer name="%sset_revision">%d</integer>
                    <integer name="%sresource_revision">%d</integer>
                    <string name="%scomponent_id" translatable="false">%s</string>
                </resources>
                """.formatted(prefix, manifest.schemaVersion(), prefix, manifest.setRevision(), prefix, contribution.resourceRevision(), prefix,
                contribution.componentId());
        write(values, xml);

        Path provenance = outputDirectory.resolve("raw/" + prefix + "provenance.txt");
        String provenanceText = contribution.assets().stream()
                .sorted(Comparator.comparing(SetManifest.Asset::id))
                .map(asset -> asset.id() + "=" + asset.provenance() + " sha256=" + asset.sha256())
                .collect(Collectors.joining("\n", "setId=" + setId + "\nsetRevision=" + manifest.setRevision() + "\n", "\n"));
        write(provenance, provenanceText);
    }

    static void generateRegistry(List<Path> manifestPaths, Path outputDirectory) {
        generateRegistry(select(manifestPaths, "debug"), outputDirectory);
    }

    static void generateRegistry(VariantContentSelection selection, Path outputDirectory) {
        selection.requireNonEmpty();
        recreate(outputDirectory);
        Path output = outputDirectory.resolve("app/livosphere/generated/GeneratedSetRegistry.kt");
        write(output, registrySource(selection));
    }

    /**
     * Canonical generated source used by both the generator and the APK audit.  Comparing this
     * file after generation catches descriptor mutations that retain a set ID (for example a
     * changed component, revision, asset or artifact project) before an APK is called PASS.
     */
    static String registrySource(VariantContentSelection selection) {
        String entries = selection.selected().stream().sorted(Comparator.comparing(SetManifest::setId))
                .map(SetContractEngine::descriptorKotlin).collect(Collectors.joining(",\n"));
        String descriptorDigest = registryDescriptorDigest(selection);
        return """
                package app.livosphere.generated

                import app.livosphere.contract.*

                object GeneratedSetRegistry : SetRegistry {
                    // Kept in DEX so the APK audit binds every descriptor field to this exact selection.
                    private const val descriptorDigest = "%s"
                    override val sets: List<SetDescriptor> = listOf(
                %s
                    )
                }
                """.formatted(descriptorDigest, indent(entries, 8));
    }

    /** A canonical digest of every generated descriptor field, not merely set IDs. */
    static String registryDescriptorDigest(VariantContentSelection selection) {
        String canonical = selection.selected().stream().sorted(Comparator.comparing(SetManifest::setId))
                .map(SetContractEngine::descriptorKotlin).collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new GradleException("Cannot hash generated set registry descriptor", error);
        }
    }

    private static String descriptorKotlin(SetManifest manifest) {
        String schemaSpecific = manifest.schemaVersion() == 1
                ? "watchFace = " + contributionKotlin("WatchFaceContribution", manifest.contributionFor("watchface"))
                : "clockWidget = " + contributionKotlin("ClockWidgetContribution", manifest.contributionFor("clock-widget"));
        return """
                SetDescriptor(
                    schemaVersion = %d,
                    setId = SetId(%s),
                    setRevision = Revision(%d),
                    sourceAssetsRevision = Revision(%d),
                    contentStatus = ContentStatus.%s,
                    distribution = Distribution.%s,
                    preview = %s,
                    wallpaper = %s,
                    %s,
                    approvals = %s,
                )
                """.formatted(
                manifest.schemaVersion(), quote(manifest.setId()), manifest.setRevision(), manifest.sourceAssetsRevision(),
                SetManifestReader.kotlinContentStatus(manifest.contentStatus()), enumName(manifest.distribution()),
                contributionKotlin("PreviewContribution", manifest.contributionFor("preview")),
                contributionKotlin("WallpaperContribution", manifest.contributionFor("wallpaper")), schemaSpecific,
                approvalKotlin(manifest.approvals())).stripTrailing();
    }

    private static String approvalKotlin(Map<String, SetManifest.Approval> approvals) {
        return approvals.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            SetManifest.Approval ref = entry.getValue();
            return "ApprovalStage." + enumName(entry.getKey()) + " to ApprovalReference(" + quote(ref.record())
                    + ", Revision(" + ref.revision() + "), Revision(" + ref.sourceAssetsRevision() + "), " + quote(ref.sha256()) + ")";
        }).collect(Collectors.joining(", ", "mapOf(", ")"));
    }

    private static String refMap(String enumType, Map<String, String> references) {
        return references.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> enumType + "." + enumName(entry.getKey()) + " to " + quote(entry.getValue()))
                .collect(Collectors.joining(", ", "mapOf(", ")"));
    }

    private static String contributionKotlin(String type, SetManifest.Contribution contribution) {
        String settings = contribution.supportedSettings().stream()
                .map(value -> "SupportedSetting." + enumName(value))
                .sorted()
                .collect(Collectors.joining(", "));
        String resources = contribution.assets().stream().sorted(Comparator.comparing(SetManifest.Asset::id))
                .map(asset -> "ResourceReference(" + quote(asset.id()) + ", " + quote(asset.resourcePath()) + ", " + quote(asset.sha256())
                        + ", Revision(" + asset.revision() + "), " + quote(asset.provenance()) + ")")
                .collect(Collectors.joining(", "));
        String extra = "";
        if (contribution.serviceClassName() != null) {
            extra += ", serviceClassName = " + quote(contribution.serviceClassName())
                    + ", phaseRefs = " + refMap("DayPhase", contribution.phaseRefs())
                    + ", effectsRefs = " + contribution.effectsRefs().stream().map(SetContractEngine::quote)
                            .collect(Collectors.joining(", ", "listOf(", ")"));
            if (contribution.sceneRef() != null) extra += ", sceneRef = " + quote(contribution.sceneRef()) + ", previewRef = " + quote(contribution.previewRef());
        }
        if (contribution.wallpaperRef() != null) extra += ", wallpaperRef = " + quote(contribution.wallpaperRef())
                + ", widgetRefs = " + refMap("WidgetSize", contribution.widgetRefs());
        if (contribution.clockStyle() != null) extra += ", style = ClockStyle." + enumName(contribution.clockStyle())
                + ", layouts = " + refMap("WidgetSize", contribution.layouts())
                + ", layoutStatus = WidgetLayoutStatus." + enumName(contribution.layoutStatus());
        return type + "(componentId = ComponentId(" + quote(contribution.componentId()) + ")"
                + ", componentRevision = Revision(" + contribution.componentRevision() + ")"
                + ", resourceRevision = Revision(" + contribution.resourceRevision() + ")"
                + ", compatibility = Compatibility(Platform." + enumName(contribution.platform()) + ", "
                + contribution.minimumApi() + ")"
                + ", installRoute = InstallRoute." + installRouteName(contribution.installRoute())
                + ", supportedSettings = setOf(" + settings + ")"
                + ", artifact = ArtifactReference(ArtifactId(" + quote(contribution.artifactId()) + "), "
                + quote(contribution.artifactProject()) + ")"
                + ", resources = listOf(" + resources + ")" + extra + ")";
    }

    private static String installRouteName(String value) {
        return switch (value) {
            case "embedded-preview" -> "EmbeddedPreview";
            case "system-wallpaper-preview" -> "SystemWallpaperPreview";
            case "separate-watchface-package" -> "SeparateWatchFacePackage";
            case "system-widget-pin" -> "SystemWidgetPin";
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static String enumName(String value) {
        return value.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
                .replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private static String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.lines().map(line -> prefix + line).collect(Collectors.joining("\n"));
    }

    private static void recreate(Path directory) {
        try {
            if (Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException error) {
                            throw new GradleException("Не удалось очистить generated output " + path, error);
                        }
                    });
                }
            }
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new GradleException("Не удалось подготовить generated output " + directory, error);
        }
    }

    private static void copy(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException error) {
            throw new GradleException("Не удалось сгенерировать resource " + target.getFileName(), error);
        }
    }

    private static void write(Path target, String contents) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, contents, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new GradleException("Не удалось записать generated output " + target.getFileName(), error);
        }
    }
}
