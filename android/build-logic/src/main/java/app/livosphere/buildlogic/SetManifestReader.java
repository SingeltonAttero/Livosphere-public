package app.livosphere.buildlogic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;

final class SetManifestReader {
    private static final Pattern LOWER_KEBAB = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final List<String> LEGACY_SURFACES = List.of("preview", "wallpaper", "watchface");
    private static final List<String> PHONE_SURFACES = List.of("preview", "wallpaper", "clock-widget");
    private static final List<String> WALLPAPER_ONLY_SURFACES = List.of("preview", "wallpaper");
    private static final List<String> PHASES = List.of("morning", "day", "evening", "night");
    private static final List<String> SIZES = List.of("s", "m", "l");
    private static final Set<String> CLOCK_VIEW_ROLES = Set.of(
            "root", "time", "hours", "minutes", "period", "analog", "date", "day", "month", "weekday");
    private static final Pattern RESOURCE_ID = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern CLASS_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)+$");
    private static final Set<String> PLATFORMS = Set.of("android-phone", "wear-os");
    private static final Set<String> INSTALL_ROUTES = Set.of(
            "embedded-preview", "system-wallpaper-preview", "system-widget-pin", "separate-watchface-package");
    private static final Set<String> SETTINGS = Set.of(
            "none", "time-of-day", "battery-level", "charging", "tap", "tilt", "swipe", "reduced-motion", "effect-level");
    private static final Map<String, String> CONTENT_STATUS_ENUMS = Map.of(
            "approved-for-start", "APPROVED_FOR_START",
            "release-ready", "RELEASE_READY", "draft", "DRAFT", "image-approved", "IMAGE_APPROVED", "html-approved", "HTML_APPROVED");
    private static final Pattern RESOURCE_DIRECTORY = Pattern.compile(
            "^(?:drawable|mipmap|raw|values|xml|font|color|layout)(?:-[a-z0-9]+)*$");
    private static final Pattern RESOURCE_FILE = Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z0-9]+$");
    private static final String PREVIEW_WALLPAPER_ROLE = "preview-wallpaper-";
    private static final String PREVIEW_WATCHFACE_ROLE = "preview-watchface-";
    private static final String WFF_ENTRYPOINT = "watchface/raw/watchface.xml";
    private static final String WFF_METADATA = "watchface/xml/watch_face_info.xml";

    private SetManifestReader() {}

    static SetManifest read(Path manifestPath) {
        return read(manifestPath, BuildProfile.LEGACY);
    }

    static SetManifest read(Path manifestPath, BuildProfile profile) {
        Path normalizedManifest = manifestPath.toAbsolutePath().normalize();
        Map<String, String> values = parseStrictProperties(normalizedManifest);
        Set<String> consumed = new HashSet<>();

        int schemaVersion = positiveInt(required(values, consumed, normalizedManifest, "schemaVersion"), normalizedManifest, "schemaVersion");
        require(schemaVersion >= 1 && schemaVersion <= 4, normalizedManifest, "schemaVersion", "поддерживаются только schemaVersion=1,2,3,4");
        List<String> surfaces = switch (schemaVersion) {
            case 1 -> LEGACY_SURFACES;
            case 2 -> PHONE_SURFACES;
            case 3 -> WALLPAPER_ONLY_SURFACES;
            case 4 -> PHONE_SURFACES;
            default -> throw new IllegalStateException();
        };
        String distribution = values.containsKey("distribution") || schemaVersion >= 2
                ? required(values, consumed, normalizedManifest, "distribution") : "debug-only";
        require(Set.of("debug-only", "public").contains(distribution), normalizedManifest, "distribution", "неизвестный distribution");
        require(schemaVersion != 1 || distribution.equals("debug-only"), normalizedManifest, "distribution", "schema1 разрешена только как legacy debug-only");
        require(schemaVersion != 3 || distribution.equals("debug-only"), normalizedManifest, "distribution",
                "schema3 wallpaper-only пока разрешена только как debug-only");
        String setId = lowerKebab(required(values, consumed, normalizedManifest, "setId"), normalizedManifest, "setId");
        int setRevision = positiveInt(required(values, consumed, normalizedManifest, "setRevision"), normalizedManifest, "setRevision");
        int sourceAssetsRevision = positiveInt(
                required(values, consumed, normalizedManifest, "sourceAssetsRevision"), normalizedManifest, "sourceAssetsRevision");
        String contentStatus = required(values, consumed, normalizedManifest, "contentStatus");
        Set<String> contentStatuses = schemaVersion == 1
                ? Set.of("approved-for-start", "release-ready")
                : schemaVersion == 2
                        ? Set.of("draft", "image-approved", "html-approved")
                        : schemaVersion == 3 ? Set.of("draft", "image-approved") : Set.of("html-approved");
        require(contentStatuses.contains(contentStatus), normalizedManifest, "contentStatus",
                "status не поддерживается schemaVersion=" + schemaVersion + ": " + contentStatus
                        + "; допустимые: " + String.join(", ", contentStatuses));
        require(!distribution.equals("public") || contentStatus.equals("html-approved"), normalizedManifest, "distribution",
                "distribution=public требует contentStatus=html-approved");
        Map<String, SetManifest.Approval> approvals = new LinkedHashMap<>();
        List<String> approvalStages = contentStatus.equals("html-approved") ? List.of("image", "html")
                : contentStatus.equals("image-approved") ? List.of("image") : List.of();
        for (String stage : approvalStages) {
            String prefix = "approval." + stage + ".";
            String record = required(values, consumed, normalizedManifest, prefix + "record");
            Path recordPath = approvalFile(normalizedManifest, record, prefix + "record");
            int revision = positiveInt(required(values, consumed, normalizedManifest, prefix + "revision"), normalizedManifest, prefix + "revision");
            int sourceRevision = positiveInt(required(values, consumed, normalizedManifest, prefix + "sourceAssetsRevision"), normalizedManifest, prefix + "sourceAssetsRevision");
            require(sourceRevision <= sourceAssetsRevision, normalizedManifest, prefix + "sourceAssetsRevision", "approval не может ссылаться на future sourceAssetsRevision");
            String hash = required(values, consumed, normalizedManifest, prefix + "sha256");
            require(SHA_256.matcher(hash).matches() && sha256(recordPath).equals(hash), normalizedManifest, prefix + "sha256", "checksum mismatch для " + record);
            approvals.put(stage, new SetManifest.Approval(record, revision, sourceRevision, hash));
        }
        String provenanceFile = safePath(required(values, consumed, normalizedManifest, "provenanceFile"), normalizedManifest, "provenanceFile");
        String checksumsFile = safePath(required(values, consumed, normalizedManifest, "checksumsFile"), normalizedManifest, "checksumsFile");

        List<String> contributionKeys = csv(required(values, consumed, normalizedManifest, "contributions"), normalizedManifest, "contributions");
        require(new HashSet<>(contributionKeys).size() == contributionKeys.size(), normalizedManifest,
                "contributions", "список содержит duplicate contribution key");

        Path sourceAssetsRoot = normalizedManifest.getParent().getParent().resolve("source-assets").normalize();
        require(Files.isDirectory(sourceAssetsRoot), normalizedManifest, "source-assets", "каталог отсутствует");
        sourceAssetsRoot = realPath(sourceAssetsRoot, normalizedManifest, "source-assets");
        Path provenancePath = sourceFile(sourceAssetsRoot, provenanceFile, normalizedManifest, "provenanceFile");
        Path checksumsPath = sourceFile(sourceAssetsRoot, checksumsFile, normalizedManifest, "checksumsFile");
        require(Files.isRegularFile(provenancePath), normalizedManifest, "provenanceFile", "файл отсутствует: " + provenanceFile);
        require(Files.isRegularFile(checksumsPath), normalizedManifest, "checksumsFile", "файл отсутствует: " + checksumsFile);

        List<SetManifest.Contribution> contributions = new ArrayList<>();
        Set<String> componentIds = new HashSet<>();
        Set<String> assetIds = new HashSet<>();
        Map<String, String> relativePathOwners = new LinkedHashMap<>();
        Map<String, String> resourcePathOwners = new LinkedHashMap<>();
        for (String key : contributionKeys) {
            lowerKebab(key, normalizedManifest, "contributions");
            String prefix = "contribution." + key + ".";
            String componentId = lowerKebab(required(values, consumed, normalizedManifest, prefix + "componentId"), normalizedManifest,
                    prefix + "componentId");
            require(componentIds.add(componentId), normalizedManifest, prefix + "componentId",
                    "duplicate component ID: " + componentId);
            int componentRevision = positiveInt(required(values, consumed, normalizedManifest, prefix + "componentRevision"),
                    normalizedManifest, prefix + "componentRevision");
            int resourceRevision = positiveInt(required(values, consumed, normalizedManifest, prefix + "resourceRevision"),
                    normalizedManifest, prefix + "resourceRevision");
            String surface = required(values, consumed, normalizedManifest, prefix + "surface");
            require(surfaces.contains(surface), normalizedManifest, prefix + "surface", "неизвестная surface: " + surface);
            String platform = required(values, consumed, normalizedManifest, prefix + "platform");
            require(PLATFORMS.contains(platform), normalizedManifest, prefix + "platform", "неизвестная platform: " + platform);
            int minimumApi = positiveInt(required(values, consumed, normalizedManifest, prefix + "minimumApi"), normalizedManifest,
                    prefix + "minimumApi");
            String installRoute = required(values, consumed, normalizedManifest, prefix + "installRoute");
            require(INSTALL_ROUTES.contains(installRoute), normalizedManifest, prefix + "installRoute",
                    "неизвестный install route: " + installRoute);
            List<String> supportedSettings = csv(required(values, consumed, normalizedManifest, prefix + "supportedSettings"),
                    normalizedManifest, prefix + "supportedSettings");
            supportedSettings.forEach(setting -> require(SETTINGS.contains(setting), normalizedManifest,
                    prefix + "supportedSettings", "неизвестная setting: " + setting));
            require(new HashSet<>(supportedSettings).size() == supportedSettings.size(), normalizedManifest,
                    prefix + "supportedSettings", "список содержит duplicate setting");
            require(!(supportedSettings.size() > 1 && supportedSettings.contains("none")), normalizedManifest,
                    prefix + "supportedSettings", "none нельзя сочетать с другими settings");
            validateSurfaceContract(normalizedManifest, prefix, surface, platform, installRoute);
            String artifactId = lowerKebab(required(values, consumed, normalizedManifest, prefix + "artifactId"),
                    normalizedManifest, prefix + "artifactId");
            String artifactProject = required(values, consumed, normalizedManifest, prefix + "artifactProject");
            require(artifactProject.matches("^:[a-z0-9:-]+$"), normalizedManifest, prefix + "artifactProject",
                    "ожидается Gradle project path");
            List<String> assetRefs = csv(required(values, consumed, normalizedManifest, prefix + "assetRefs"), normalizedManifest,
                    prefix + "assetRefs");
            require(new HashSet<>(assetRefs).size() == assetRefs.size(), normalizedManifest, prefix + "assetRefs",
                    "список содержит duplicate resource ID");

            String serviceClassName = null;
            Map<String, String> phaseRefs = Map.of();
            List<String> effectsRefs = List.of();
            String wallpaperRef = null;
            String sceneRef = null;
            String previewRef = null;
            Map<String, String> widgetRefs = Map.of();
            String clockStyle = null;
            String clockDisplayName = null;
            Map<String, String> layouts = Map.of();
            String layoutStatus = null;
            Map<String, Map<String, String>> viewRoles = Map.of();
            if (surface.equals("wallpaper")) {
                serviceClassName = required(values, consumed, normalizedManifest, prefix + "serviceClassName");
                require(CLASS_NAME.matcher(serviceClassName).matches(), normalizedManifest, prefix + "serviceClassName", "ожидается fully qualified class name");
                if (schemaVersion >= 2) {
                    phaseRefs = readRefs(values, consumed, normalizedManifest, prefix + "phaseRefs.", PHASES);
                    if (schemaVersion == 2) {
                        effectsRefs = csv(required(values, consumed, normalizedManifest, prefix + "effectsRefs"), normalizedManifest, prefix + "effectsRefs");
                        require(new HashSet<>(effectsRefs).size() == effectsRefs.size(), normalizedManifest, prefix + "effectsRefs", "duplicate effect ref");
                        sceneRef = required(values, consumed, normalizedManifest, prefix + "sceneRef");
                    }
                    previewRef = required(values, consumed, normalizedManifest, prefix + "previewRef");
                }
            } else if (schemaVersion >= 2 && surface.equals("preview")) {
                wallpaperRef = required(values, consumed, normalizedManifest, prefix + "wallpaperRef");
                if (schemaVersion == 2 || schemaVersion == 4) {
                    widgetRefs = readRefs(values, consumed, normalizedManifest, prefix + "widgetRefs.", SIZES);
                }
            } else if (surface.equals("clock-widget")) {
                clockStyle = required(values, consumed, normalizedManifest, prefix + "style");
                require(Set.of("analog", "digital").contains(clockStyle), normalizedManifest, prefix + "style", "ожидается analog/digital");
                if (schemaVersion == 4) {
                    clockDisplayName = required(values, consumed, normalizedManifest, prefix + "displayName");
                    require(!clockDisplayName.isBlank(), normalizedManifest, prefix + "displayName", "название часов пусто");
                }
                layoutStatus = required(values, consumed, normalizedManifest, prefix + "layoutStatus");
                require(Set.of("test-declaration", "native").contains(layoutStatus), normalizedManifest, prefix + "layoutStatus", "ожидается test-declaration/native");
                require(!layoutStatus.equals("test-declaration") || distribution.equals("debug-only"), normalizedManifest,
                        prefix + "layoutStatus", "test-declaration допускается только в debug-only");
                require(schemaVersion != 4 || layoutStatus.equals("native"), normalizedManifest,
                        prefix + "layoutStatus", "schema4 требует native layout");
                layouts = readRefs(values, consumed, normalizedManifest, prefix + "layouts.", SIZES);
                require(new HashSet<>(layouts.values()).size() == 3, normalizedManifest, prefix + "layouts", "S/M/L требуют отдельные layouts");
                if (schemaVersion == 4) {
                    Map<String, Map<String, String>> rolesBySize = new LinkedHashMap<>();
                    for (String size : SIZES) {
                        String rolesPrefix = prefix + "viewRoles." + size + ".";
                        List<String> roles = csv(required(values, consumed, normalizedManifest, rolesPrefix + "keys"),
                                normalizedManifest, rolesPrefix + "keys");
                        require(new HashSet<>(roles).size() == roles.size(), normalizedManifest, rolesPrefix + "keys",
                                "список содержит duplicate role");
                        require(CLOCK_VIEW_ROLES.containsAll(roles), normalizedManifest, rolesPrefix + "keys",
                                "неизвестная clock view role");
                        Map<String, String> refs = new LinkedHashMap<>();
                        for (String role : roles) {
                            String resourceId = required(values, consumed, normalizedManifest, rolesPrefix + role);
                            require(RESOURCE_ID.matcher(resourceId).matches(), normalizedManifest, rolesPrefix + role,
                                    "ожидается Android resource id");
                            refs.put(role, resourceId);
                        }
                        require(new HashSet<>(refs.values()).size() == refs.size(), normalizedManifest, rolesPrefix,
                                "каждая role требует отдельный view id");
                        validateClockViewRoles(normalizedManifest, rolesPrefix, size, clockStyle, refs.keySet());
                        rolesBySize.put(size, Map.copyOf(refs));
                    }
                    viewRoles = Map.copyOf(rolesBySize);
                }
            }

            List<SetManifest.Asset> assets = new ArrayList<>();
            for (String assetId : assetRefs) {
                lowerKebab(assetId, normalizedManifest, prefix + "assetRefs");
                require(assetIds.add(assetId), normalizedManifest, prefix + "assetRefs",
                        "duplicate resource ID: " + assetId);
                String assetPrefix = "asset." + assetId + ".";
                String relativePath = safePath(required(values, consumed, normalizedManifest, assetPrefix + "path"), normalizedManifest,
                        assetPrefix + "path");
                String resourcePath = safePath(required(values, consumed, normalizedManifest, assetPrefix + "resourcePath"), normalizedManifest,
                        assetPrefix + "resourcePath");
                require(relativePathOwners.putIfAbsent(relativePath, assetId) == null, normalizedManifest,
                        assetPrefix + "path", "duplicate asset path '" + relativePath + "', first owner="
                                + relativePathOwners.get(relativePath));
                require(resourcePathOwners.putIfAbsent(resourcePath, assetId) == null, normalizedManifest,
                        assetPrefix + "resourcePath", "duplicate resource path '" + resourcePath + "', first owner="
                                + resourcePathOwners.get(resourcePath));
                String sha256 = required(values, consumed, normalizedManifest, assetPrefix + "sha256");
                require(SHA_256.matcher(sha256).matches(), normalizedManifest, assetPrefix + "sha256",
                        "ожидается lowercase SHA-256");
                int revision = positiveInt(required(values, consumed, normalizedManifest, assetPrefix + "revision"), normalizedManifest,
                        assetPrefix + "revision");
                require(revision == resourceRevision, normalizedManifest, assetPrefix + "revision",
                        "asset revision " + revision + " не совпадает с resourceRevision " + resourceRevision);
                String provenance = required(values, consumed, normalizedManifest, assetPrefix + "provenance");
                require(!provenance.isBlank(), normalizedManifest, assetPrefix + "provenance", "provenance пуст");
                validateResourceName(normalizedManifest, setId, surface, resourcePath, assetPrefix + "resourcePath");
                rejectGeneratedCollision(normalizedManifest, setId, surface, resourcePath);

                if (profile.requiresPhysicalAssets(surface)) {
                    Path assetFile = sourceFile(sourceAssetsRoot, relativePath, normalizedManifest, assetPrefix + "path");
                    require(Files.isRegularFile(assetFile), normalizedManifest, assetPrefix + "path",
                            "asset отсутствует: " + relativePath);
                    require(sha256(assetFile).equals(sha256), normalizedManifest, assetPrefix + "sha256",
                            "checksum mismatch для " + relativePath);
                }
                assets.add(new SetManifest.Asset(assetId, relativePath, resourcePath, sha256, revision, provenance));
            }

            contributions.add(new SetManifest.Contribution(key, componentId, componentRevision, resourceRevision,
                    surface, platform, minimumApi, installRoute, List.copyOf(supportedSettings), artifactId, artifactProject,
                    List.copyOf(assets), serviceClassName, phaseRefs, List.copyOf(effectsRefs), wallpaperRef, widgetRefs,
                    clockStyle, clockDisplayName, layouts, layoutStatus, viewRoles, sceneRef, previewRef));
            if (schemaVersion >= 2) {
                for (var ref : phaseRefs.entrySet()) requireRef(normalizedManifest, prefix + "phaseRefs." + ref.getKey(), ref.getValue(), assets);
                for (String ref : effectsRefs) requireRef(normalizedManifest, prefix + "effectsRefs", ref, assets);
                if (sceneRef != null) requireRef(normalizedManifest, prefix + "sceneRef", sceneRef, assets);
                if (wallpaperRef != null) requirePreviewRef(normalizedManifest, prefix + "wallpaperRef", wallpaperRef, assets);
                for (var ref : widgetRefs.entrySet()) requirePreviewRef(normalizedManifest, prefix + "widgetRefs." + ref.getKey(), ref.getValue(), assets);
                for (var ref : layouts.entrySet()) {
                    SetManifest.Asset asset = requireRef(normalizedManifest, prefix + "layouts." + ref.getKey(), ref.getValue(), assets);
                    String directory = layoutStatus.equals("test-declaration") ? "raw/" : "layout/";
                    require(asset.resourcePath().startsWith(directory) && asset.resourcePath().endsWith(".xml"), normalizedManifest,
                            prefix + "layouts." + ref.getKey(), "layoutStatus требует " + directory + " XML: " + asset.resourcePath());
                }
            }
        }

        for (String surface : surfaces) {
            long count = contributions.stream().filter(contribution -> contribution.surface().equals(surface)).count();
            require(count == 1, normalizedManifest, "contributions",
                    "ожидалась ровно одна contribution surface=" + surface + ", найдено " + count);
        }
        if (schemaVersion == 1) requirePreviewRoles(normalizedManifest, contributions);
        requireEntryPoints(normalizedManifest, setId, contributions, schemaVersion);
        if (schemaVersion >= 2) {
            SetManifest.Contribution wallpaper = contributions.stream().filter(c -> c.surface().equals("wallpaper")).findFirst().orElseThrow();
            SetManifest.Contribution preview = contributions.stream().filter(c -> c.surface().equals("preview")).findFirst().orElseThrow();
            requirePreviewRef(normalizedManifest, "contribution." + wallpaper.key() + ".previewRef", wallpaper.previewRef(), preview.assets());
            require(wallpaper.previewRef().equals(preview.wallpaperRef()), normalizedManifest,
                    "contribution." + wallpaper.key() + ".previewRef", "должен совпадать с preview.wallpaperRef");
        }
        require(consumed.equals(values.keySet()), normalizedManifest, "schema",
                "неизвестные или необъявленные поля: " + difference(values.keySet(), consumed));

        verifySourceAssets(normalizedManifest, sourceAssetsRoot, provenanceFile, checksumsFile, contributions, profile);
        return new SetManifest(normalizedManifest, sourceAssetsRoot, schemaVersion, setId, setRevision,
                sourceAssetsRevision, contentStatus, distribution, Map.copyOf(approvals), List.copyOf(contributions));
    }

    static List<SetManifest> readAll(List<Path> manifestPaths) {
        return readAll(manifestPaths, BuildProfile.LEGACY);
    }

    static List<SetManifest> readAll(List<Path> manifestPaths, BuildProfile profile) {
        require(!manifestPaths.isEmpty(), Path.of("."), "livosphere.setManifests",
                "не указан ни один manifest");
        List<SetManifest> manifests = manifestPaths.stream().map(path -> read(path, profile))
                .sorted((left, right) -> left.setId().compareTo(right.setId()))
                .toList();
        assertUnique(manifests, SetManifest::setId, "set ID");
        assertUnique(manifests,
                manifest -> manifest.contributions().stream().map(SetManifest.Contribution::componentId).toList(),
                "component ID");
        assertUnique(manifests,
                manifest -> manifest.contributions().stream()
                        .flatMap(contribution -> contribution.assets().stream())
                        .map(SetManifest.Asset::id).toList(),
                "resource ID");
        assertUnique(manifests,
                manifest -> manifest.contributions().stream().map(SetManifest.Contribution::serviceClassName)
                        .filter(java.util.Objects::nonNull).toList(), "serviceClassName");
        return manifests;
    }

    static String kotlinContentStatus(String contentStatus) {
        String enumName = CONTENT_STATUS_ENUMS.get(contentStatus);
        if (enumName == null) {
            throw new IllegalArgumentException("Unvalidated contentStatus: " + contentStatus);
        }
        return enumName;
    }

    static List<Path> approvalInputFiles(Path manifest) {
        Path normalized = manifest.toAbsolutePath().normalize();
        Map<String, String> values = parseStrictProperties(normalized);
        return values.entrySet().stream().filter(entry -> entry.getKey().matches("approval\\.(image|html)\\.record"))
                .map(entry -> approvalFile(normalized, entry.getValue(), entry.getKey())).toList();
    }

    private static Path approvalFile(Path manifest, String record, String field) {
        safePath(record, manifest, field);
        require(record.matches("^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.md$"), manifest, field, "ожидается relative Markdown path");
        Path root = realPath(manifest.getParent().getParent(), manifest, field);
        Path file = sourceFile(root, record, manifest, field);
        require(Files.isRegularFile(file), manifest, field, "approval record отсутствует: " + record);
        require(!file.startsWith(realPath(root.resolve("source-assets"), manifest, field)), manifest, field,
                "approval record должен быть вне packaged resources");
        return file;
    }

    private static Map<String, String> readRefs(Map<String, String> values, Set<String> consumed, Path manifest,
            String prefix, List<String> keys) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : keys) result.put(key, lowerKebab(required(values, consumed, manifest, prefix + key), manifest, prefix + key));
        return Map.copyOf(result);
    }

    private static SetManifest.Asset requireRef(Path manifest, String field, String ref, List<SetManifest.Asset> assets) {
        return assets.stream().filter(asset -> asset.id().equals(ref)).findFirst()
                .orElseThrow(() -> failure(manifest, field, "необъявленный resource ref: " + ref, null));
    }

    private static void requirePreviewRef(Path manifest, String field, String ref, List<SetManifest.Asset> assets) {
        SetManifest.Asset asset = requireRef(manifest, field, ref, assets);
        require(asset.resourcePath().startsWith("drawable-nodpi/") && asset.resourcePath().endsWith(".png"), manifest,
                field, "preview требует drawable-nodpi PNG: " + asset.resourcePath());
    }

    private static void verifySourceAssets(
            Path manifest,
            Path sourceRoot,
            String provenanceFile,
            String checksumsFile,
            List<SetManifest.Contribution> contributions,
            BuildProfile profile) {
        Set<String> ignored = contributions.stream()
                .filter(contribution -> !profile.requiresPhysicalAssets(contribution.surface()))
                .flatMap(contribution -> contribution.assets().stream())
                .map(SetManifest.Asset::relativePath)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declared = contributions.stream()
                .filter(contribution -> profile.requiresPhysicalAssets(contribution.surface()))
                .flatMap(contribution -> contribution.assets().stream())
                .map(SetManifest.Asset::relativePath)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual;
        try (var paths = Files.walk(sourceRoot)) {
            actual = paths.filter(Files::isRegularFile)
                    .map(sourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.equals(provenanceFile) && !path.equals(checksumsFile) && !ignored.contains(path))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException error) {
            throw failure(manifest, "source-assets", "не удалось перечислить assets", error);
        }
        require(actual.equals(declared), manifest, "source-assets",
                "файлы должны быть объявлены ровно один раз; declared=" + declared + ", actual=" + actual);

        Map<String, String> checksumIndex = parseChecksums(sourceRoot.resolve(checksumsFile), manifest);
        Map<String, String> expected = contributions.stream()
                .flatMap(contribution -> contribution.assets().stream())
                .collect(Collectors.toMap(SetManifest.Asset::relativePath, SetManifest.Asset::sha256));
        require(checksumIndex.equals(expected), manifest, "checksumsFile",
                "индекс checksums не совпадает с manifest assets");
    }

    private static Map<String, String> parseChecksums(Path file, Path manifest) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+", 2);
                require(parts.length == 2 && SHA_256.matcher(parts[0]).matches(), manifest, "checksumsFile",
                        "некорректная строка: " + rawLine);
                String path = safePath(parts[1].trim(), manifest, "checksumsFile");
                require(result.putIfAbsent(path, parts[0]) == null, manifest, "checksumsFile",
                        "duplicate path: " + path);
            }
        } catch (IOException error) {
            throw failure(manifest, "checksumsFile", "не удалось прочитать " + file.getFileName(), error);
        }
        return result;
    }

    private static void validateResourceName(Path manifest, String setId, String surface, String resourcePath, String field) {
        String[] parts = resourcePath.split("/", -1);
        require(parts.length == 2 && RESOURCE_DIRECTORY.matcher(parts[0]).matches()
                        && (!parts[0].startsWith("layout") || surface.equals("clock-widget"))
                        && (!parts[0].startsWith("font") || surface.equals("clock-widget")), manifest, field,
                "недопустимый Android resource directory: " + resourcePath);
        require(RESOURCE_FILE.matcher(parts[1]).matches(), manifest, field,
                "resource filename и extension должны быть lowercase Android-compatible: " + resourcePath);
        if (surface.equals("watchface")
                && (resourcePath.equals("raw/watchface.xml") || resourcePath.equals("xml/watch_face_info.xml"))) {
            return;
        }
        String fileName = parts[1];
        String prefix = "ls_" + setId.replace('-', '_') + "_" + surface.replace('-', '_') + "_";
        require(fileName.startsWith(prefix), manifest, field,
                "resource должен иметь prefix " + prefix + ": " + resourcePath);
    }

    private static void validateClockViewRoles(
            Path manifest, String prefix, String size, String style, Set<String> roles) {
        require(roles.contains("root"), manifest, prefix, "обязательна role root");
        Set<String> presentDigitalTimeRoles = roles.stream()
                .filter(Set.of("time", "hours", "minutes")::contains).collect(Collectors.toSet());
        boolean combinedTime = presentDigitalTimeRoles.equals(Set.of("time"));
        boolean splitTime = presentDigitalTimeRoles.equals(Set.of("hours", "minutes"));
        if (style.equals("digital")) {
            require(combinedTime != splitTime, manifest, prefix,
                    "digital требует ровно time или пару hours+minutes");
            require(!roles.contains("analog"), manifest, prefix, "digital запрещает analog role");
        } else {
            require(roles.contains("analog"), manifest, prefix, "analog требует analog role");
            require(presentDigitalTimeRoles.isEmpty() && !roles.contains("period"), manifest, prefix,
                    "analog запрещает digital time roles");
        }
        Set<String> dateRoles = Set.of("date", "day", "month", "weekday");
        if (size.equals("s")) {
            require(roles.stream().noneMatch(dateRoles::contains), manifest, prefix, "S не содержит дату");
        } else {
            boolean combinedDate = roles.contains("date") && !roles.contains("weekday")
                    && !roles.contains("day") && !roles.contains("month");
            boolean separateDate = roles.containsAll(Set.of("date", "weekday"))
                    && !roles.contains("day") && !roles.contains("month");
            boolean splitDate = roles.containsAll(Set.of("day", "month", "weekday")) && !roles.contains("date");
            require(List.of(combinedDate, separateDate, splitDate).stream().filter(Boolean::booleanValue).count() == 1,
                    manifest, prefix, "M/L требуют date, date+weekday или day+month+weekday");
        }
    }

    private static Map<String, String> parseStrictProperties(Path path) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf('=');
                require(separator > 0, path, "line " + lineNumber, "ожидается key=value");
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                require(!value.isEmpty(), path, key, "значение пусто");
                require(result.putIfAbsent(key, value) == null, path, key, "duplicate key");
            }
        } catch (IOException error) {
            throw failure(path, "manifest", "не удалось прочитать manifest", error);
        }
        return result;
    }

    private static String required(Map<String, String> values, Set<String> consumed, Path manifest, String key) {
        String value = values.get(key);
        if (value == null) throw failure(manifest, key, "обязательное поле отсутствует", null);
        consumed.add(key);
        return value;
    }

    private static int positiveInt(String value, Path manifest, String field) {
        try {
            int parsed = Integer.parseInt(value);
            require(parsed > 0, manifest, field, "ожидается положительное целое число");
            return parsed;
        } catch (NumberFormatException error) {
            throw failure(manifest, field, "ожидается положительное целое число: " + value, error);
        }
    }

    private static String lowerKebab(String value, Path manifest, String field) {
        require(LOWER_KEBAB.matcher(value).matches(), manifest, field, "ожидается lower-kebab ID: " + value);
        return value;
    }

    private static List<String> csv(String value, Path manifest, String field) {
        List<String> entries = List.of(value.split(",", -1)).stream().map(String::trim).toList();
        require(!entries.isEmpty() && entries.stream().noneMatch(String::isEmpty), manifest, field,
                "список не должен быть пустым");
        return entries;
    }

    private static String safePath(String value, Path manifest, String field) {
        final Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException error) {
            throw failure(manifest, field, "некорректный path: " + value, error);
        }
        require(!path.isAbsolute() && !value.contains("\\") && path.normalize().equals(path)
                        && !value.startsWith("../") && !value.equals(".."),
                manifest, field, "ожидается нормализованный относительный path: " + value);
        return value;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw failure(file, "sha256", "не удалось вычислить checksum", error);
        }
    }

    private static void validateSurfaceContract(
            Path manifest, String prefix, String surface, String platform, String installRoute) {
        String expectedPlatform;
        String expectedRoute;
        switch (surface) {
            case "preview" -> {
                expectedPlatform = "android-phone";
                expectedRoute = "embedded-preview";
            }
            case "wallpaper" -> {
                expectedPlatform = "android-phone";
                expectedRoute = "system-wallpaper-preview";
            }
            case "clock-widget" -> {
                expectedPlatform = "android-phone";
                expectedRoute = "system-widget-pin";
            }
            case "watchface" -> {
                expectedPlatform = "wear-os";
                expectedRoute = "separate-watchface-package";
            }
            default -> throw new IllegalStateException(surface);
        }
        require(platform.equals(expectedPlatform), manifest, prefix + "platform",
                surface + " требует platform=" + expectedPlatform);
        require(installRoute.equals(expectedRoute), manifest, prefix + "installRoute",
                surface + " требует installRoute=" + expectedRoute);
    }

    private static void requireEntryPoints(Path manifest, String setId, List<SetManifest.Contribution> contributions, int schemaVersion) {
        String wallpaperSource = "wallpaper/xml/ls_" + setId.replace('-', '_') + "_wallpaper_entrypoint.xml";
        SetManifest.Contribution wallpaper = contributions.stream()
                .filter(value -> value.surface().equals("wallpaper")).findFirst().orElseThrow();
        require(wallpaper.assets().stream().anyMatch(asset -> asset.relativePath().equals(wallpaperSource)
                        && asset.resourcePath().equals("xml/ls_" + setId.replace('-', '_') + "_wallpaper_entrypoint.xml")),
                manifest, "contribution." + wallpaper.key() + ".assetRefs",
                "обязателен entrypoint " + wallpaperSource);
        if (schemaVersion >= 2) return;
        SetManifest.Contribution watchface = contributions.stream()
                .filter(value -> value.surface().equals("watchface")).findFirst().orElseThrow();
        require(watchface.assets().stream().anyMatch(asset -> asset.relativePath().equals(WFF_ENTRYPOINT)
                        && asset.resourcePath().equals("raw/watchface.xml")),
                manifest, "contribution." + watchface.key() + ".assetRefs",
                "обязателен entrypoint " + WFF_ENTRYPOINT);
        require(watchface.assets().stream().anyMatch(asset -> asset.relativePath().equals(WFF_METADATA)
                        && asset.resourcePath().equals("xml/watch_face_info.xml")),
                manifest, "contribution." + watchface.key() + ".assetRefs",
                "обязателен metadata " + WFF_METADATA);
    }

    private static void requirePreviewRoles(Path manifest, List<SetManifest.Contribution> contributions) {
        SetManifest.Contribution preview = contributions.stream()
                .filter(value -> value.surface().equals("preview")).findFirst().orElseThrow();
        require(preview.assets().size() == 2, manifest, "contribution." + preview.key() + ".assetRefs",
                "preview schema v1 требует ровно два role refs");
        requireSinglePreviewRole(manifest, preview, PREVIEW_WALLPAPER_ROLE);
        requireSinglePreviewRole(manifest, preview, PREVIEW_WATCHFACE_ROLE);
    }

    private static void requireSinglePreviewRole(
            Path manifest, SetManifest.Contribution preview, String rolePrefix) {
        List<SetManifest.Asset> matches = preview.assets().stream()
                .filter(asset -> asset.id().startsWith(rolePrefix)).toList();
        require(matches.size() == 1, manifest, "contribution." + preview.key() + ".assetRefs",
                "ожидался ровно один schema-v1 role " + rolePrefix + "*, найдено " + matches.size());
        String resourcePath = matches.get(0).resourcePath();
        require(resourcePath.startsWith("drawable-nodpi/") && resourcePath.endsWith(".png"),
                manifest, "asset." + matches.get(0).id() + ".resourcePath",
                "preview role обязан ссылаться на drawable-nodpi PNG");
    }

    private static void rejectGeneratedCollision(Path manifest, String setId, String surface, String resourcePath) {
        String prefix = "ls_" + setId.replace('-', '_') + "_" + surface.replace('-', '_') + "_";
        require(!resourcePath.equals("values/" + prefix + "contract.xml"), manifest, "resourcePath",
                "asset конфликтует с generated contract path: " + resourcePath);
        require(!resourcePath.equals("raw/" + prefix + "provenance.txt"), manifest, "resourcePath",
                "asset конфликтует с generated provenance path: " + resourcePath);
    }

    private static Path sourceFile(Path sourceRoot, String relative, Path manifest, String field) {
        Path candidate = sourceRoot.resolve(relative).normalize();
        require(candidate.startsWith(sourceRoot), manifest, field, "asset выходит за source-assets");
        if (!Files.exists(candidate)) return candidate;
        Path real = realPath(candidate, manifest, field);
        require(real.startsWith(sourceRoot), manifest, field,
                "symlink выводит asset за canonical source-assets: " + relative);
        return real;
    }

    private static Path realPath(Path path, Path manifest, String field) {
        try {
            return path.toRealPath();
        } catch (IOException error) {
            throw failure(manifest, field, "не удалось разрешить canonical path: " + path, error);
        }
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static <T> void assertUnique(
            List<SetManifest> manifests,
            java.util.function.Function<SetManifest, T> extractor,
            String label) {
        Map<String, List<Path>> owners = new TreeMap<>();
        for (SetManifest manifest : manifests) {
            Object extracted = extractor.apply(manifest);
            List<?> values = extracted instanceof List<?> list ? list : List.of(extracted);
            for (Object value : values) {
                owners.computeIfAbsent(String.valueOf(value), ignored -> new ArrayList<>()).add(manifest.manifestPath());
            }
        }
        owners.forEach((id, paths) -> {
            if (paths.size() > 1) {
                throw new GradleException("Конфликт " + label + " '" + id + "' в manifests: "
                        + paths.stream().map(Path::toString).sorted().collect(Collectors.joining(", ")));
            }
        });
    }

    private static void require(boolean condition, Path manifest, String field, String message) {
        if (!condition) throw failure(manifest, field, message, null);
    }

    private static GradleException failure(Path manifest, String field, String message, Throwable cause) {
        String fullMessage = manifest + ": поле '" + field + "': " + message;
        return cause == null ? new GradleException(fullMessage) : new GradleException(fullMessage, cause);
    }
}
