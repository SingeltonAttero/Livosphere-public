package app.livosphere.buildlogic;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Explicit test declarations only. No native widget, art acceptance or public content evidence. */
final class PhoneSetFixture {
    private PhoneSetFixture() {}

    static Path create(Path root, String setId) throws Exception {
        Path setRoot = root.resolve("sets/" + setId);
        Path source = Files.createDirectories(setRoot.resolve("source-assets"));
        Path manifest = Files.createDirectories(setRoot.resolve("manifest")).resolve("set.properties");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("schemaVersion", "2");
        values.put("setId", setId);
        values.put("setRevision", "1");
        values.put("sourceAssetsRevision", "1");
        values.put("contentStatus", "draft");
        values.put("distribution", "debug-only");
        values.put("provenanceFile", "PROVENANCE.md");
        values.put("checksumsFile", "checksums.sha256");
        values.put("contributions", "preview-main,wallpaper-main,clock-main");
        List<String> checksums = new ArrayList<>();
        for (String surface : List.of("preview", "wallpaper", "clock-widget")) {
            String key = surface.equals("clock-widget") ? "clock-main" : surface + "-main";
            String prefix = "contribution." + key + ".";
            values.put(prefix + "componentId", setId + "-" + surface);
            values.put(prefix + "componentRevision", "1");
            values.put(prefix + "resourceRevision", "1");
            values.put(prefix + "surface", surface);
            values.put(prefix + "platform", "android-phone");
            values.put(prefix + "minimumApi", "29");
            values.put(prefix + "installRoute", switch (surface) {
                case "preview" -> "embedded-preview";
                case "wallpaper" -> "system-wallpaper-preview";
                default -> "system-widget-pin";
            });
            values.put(prefix + "supportedSettings", "none");
            values.put(prefix + "artifactId", setId + "-phone");
            values.put(prefix + "artifactProject", ":sets:" + setId + ":" + surface);
            Map<String, String> resources = new LinkedHashMap<>();
            String resourcePrefix = "ls_" + setId.replace('-', '_') + "_" + surface.replace('-', '_') + "_";
            if (surface.equals("preview")) {
                for (String role : List.of("wallpaper", "s", "m", "l")) {
                    String id = setId + "-preview-" + role;
                    resources.put(id, "drawable-nodpi/" + resourcePrefix + role + ".png");
                    values.put(prefix + (role.equals("wallpaper") ? "wallpaperRef" : "widgetRefs." + role), id);
                }
            } else if (surface.equals("wallpaper")) {
                values.put(prefix + "serviceClassName", "test." + setId.replace('-', '_') + ".WallpaperService");
                resources.put(setId + "-entrypoint", "xml/" + resourcePrefix + "entrypoint.xml");
                resources.put(setId + "-scene", "raw/" + resourcePrefix + "scene.xml");
                values.put(prefix + "sceneRef", setId + "-scene");
                values.put(prefix + "previewRef", setId + "-preview-wallpaper");
                for (String phase : List.of("morning", "day", "evening", "night")) {
                    resources.put(setId + "-phase-" + phase, "raw/" + resourcePrefix + phase + ".xml");
                    values.put(prefix + "phaseRefs." + phase, setId + "-phase-" + phase);
                }
                resources.put(setId + "-effects", "raw/" + resourcePrefix + "effects.xml");
                values.put(prefix + "effectsRefs", setId + "-effects");
            } else {
                values.put(prefix + "style", "digital");
                values.put(prefix + "layoutStatus", "test-declaration");
                for (String size : List.of("s", "m", "l")) {
                    resources.put(setId + "-clock-" + size, "raw/" + resourcePrefix + size + ".xml");
                    values.put(prefix + "layouts." + size, setId + "-clock-" + size);
                }
            }
            values.put(prefix + "assetRefs", String.join(",", resources.keySet()));
            for (var resource : resources.entrySet()) {
                String relative = surface + "/" + resource.getValue();
                Path asset = source.resolve(relative);
                Files.createDirectories(asset.getParent());
                if (relative.endsWith(".png")) {
                    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
                    for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) image.setRGB(x, y, Color.ORANGE.getRGB());
                    ImageIO.write(image, "png", asset.toFile());
                } else Files.writeString(asset, "<test-declaration native-widget-pass=\"false\"/>\n");
                String hash = hash(asset);
                String assetPrefix = "asset." + resource.getKey() + ".";
                values.put(assetPrefix + "path", relative);
                values.put(assetPrefix + "resourcePath", resource.getValue());
                values.put(assetPrefix + "sha256", hash);
                values.put(assetPrefix + "revision", "1");
                values.put(assetPrefix + "provenance", "project-authored test declaration; not native widget PASS or accepted art");
                checksums.add(hash + "  " + relative);
            }
        }
        Files.writeString(source.resolve("PROVENANCE.md"), "Debug schema fixture only; NOT native widget PASS; NOT public content.\n");
        Files.write(source.resolve("checksums.sha256"), checksums);
        Files.write(manifest, values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
        return manifest;
    }

    static Path createPublic(Path root, String setId) throws Exception {
        Path manifest = create(root, setId);
        Map<String, String> values = properties(manifest);
        values.put("distribution", "public");
        values.put("contentStatus", "html-approved");
        values.put("contribution.clock-main.layoutStatus", "native");
        Path source = manifest.getParent().getParent().resolve("source-assets");
        List<String> checksums = new ArrayList<>();
        for (String key : new ArrayList<>(values.keySet())) {
            if (!key.startsWith("asset.") || !key.endsWith(".path")) continue;
            String prefix = key.substring(0, key.length() - 4);
            String relative = values.get(key);
            if (relative.startsWith("clock-widget/raw/")) {
                Files.delete(source.resolve(relative));
                relative = relative.replace("clock-widget/raw/", "clock-widget/layout/");
                values.put(key, relative);
                values.put(prefix + "resourcePath", values.get(prefix + "resourcePath").replace("raw/", "layout/"));
                Path layout = source.resolve(relative);
                Files.createDirectories(layout.getParent());
                Files.writeString(layout, "<TextClock xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                        + "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" "
                        + "android:format24Hour=\"HH:mm\"/>\n");
            }
            if (relative.startsWith("wallpaper/xml/")) {
                Files.writeString(source.resolve(relative),
                        "<wallpaper xmlns:android=\"http://schemas.android.com/apk/res/android\"/>\n");
            }
            String hash = hash(source.resolve(relative));
            values.put(prefix + "sha256", hash);
            checksums.add(hash + "  " + relative);
        }
        Files.write(source.resolve("checksums.sha256"), checksums);
        Files.write(manifest, values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList());
        approval(manifest, "image");
        approval(manifest, "html");
        return manifest;
    }

    /**
     * Schema4 static phone set built on the public fixture: no scene/effects, native clock with view roles.
     * Explicit test declaration only; not a content pack and not owner's approval evidence.
     */
    static Path createStatic(Path root, String setId, boolean publicDistribution) throws Exception {
        Path manifest = createPublic(root, setId);
        String wallpaperRefs = setId + "-entrypoint," + setId + "-scene," + setId + "-phase-morning," + setId + "-phase-day,"
                + setId + "-phase-evening," + setId + "-phase-night," + setId + "-effects";
        String staticRefs = setId + "-entrypoint," + setId + "-phase-morning," + setId + "-phase-day,"
                + setId + "-phase-evening," + setId + "-phase-night";
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.startsWith("contribution.wallpaper-main.effectsRefs="))
                .filter(line -> !line.startsWith("contribution.wallpaper-main.sceneRef="))
                .filter(line -> !line.startsWith("asset." + setId + "-scene."))
                .filter(line -> !line.startsWith("asset." + setId + "-effects."))
                .map(line -> line.equals("schemaVersion=2") ? "schemaVersion=4" : line)
                .map(line -> !publicDistribution && line.equals("distribution=public") ? "distribution=debug-only" : line)
                .map(line -> line.equals("contribution.wallpaper-main.assetRefs=" + wallpaperRefs)
                        ? "contribution.wallpaper-main.assetRefs=" + staticRefs : line)
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
                + "contribution.clock-main.viewRoles.l.date=clock_widget_date\n",
                java.nio.file.StandardOpenOption.APPEND);
        Path source = manifest.getParent().getParent().resolve("source-assets");
        String resourcePrefix = "ls_" + setId.replace('-', '_') + "_wallpaper_";
        Files.deleteIfExists(source.resolve("wallpaper/raw/" + resourcePrefix + "scene.xml"));
        Files.deleteIfExists(source.resolve("wallpaper/raw/" + resourcePrefix + "effects.xml"));
        List<String> declared = Files.readAllLines(manifest).stream()
                .filter(line -> line.startsWith("asset.") && line.contains(".path="))
                .map(line -> line.substring(line.indexOf('=') + 1))
                .toList();
        List<String> checksums = new ArrayList<>();
        for (String relative : declared) checksums.add(hash(source.resolve(relative)) + "  " + relative);
        Files.write(source.resolve("checksums.sha256"), checksums);
        return manifest;
    }

    private static Map<String, String> properties(Path manifest) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(manifest)) {
            int equals = line.indexOf('=');
            if (equals > 0) values.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return values;
    }

    static void approval(Path manifest, String stage) throws Exception {
        String record = "approvals/" + stage + ".md";
        Path file = manifest.getParent().getParent().resolve(record);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Test fixture " + stage + " decision reference; not an owner's approval.\n");
        Files.writeString(manifest, "approval." + stage + ".record=" + record + "\n"
                + "approval." + stage + ".revision=1\n"
                + "approval." + stage + ".sourceAssetsRevision=1\n"
                + "approval." + stage + ".sha256=" + hash(file) + "\n", java.nio.file.StandardOpenOption.APPEND);
    }

    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
