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
    private static final List<String> SURFACES = List.of("preview", "wallpaper", "watchface");
    private static final Set<String> PLATFORMS = Set.of("android-phone", "wear-os");
    private static final Set<String> INSTALL_ROUTES = Set.of(
            "embedded-preview", "system-wallpaper-preview", "separate-watchface-package");
    private static final Set<String> SETTINGS = Set.of(
            "none", "time-of-day", "battery-level", "charging", "tap", "swipe", "reduced-motion");
    private static final Pattern RESOURCE_DIRECTORY = Pattern.compile(
            "^(?:drawable|mipmap|raw|values|xml|font|color)(?:-[a-z0-9]+)*$");
    private static final Pattern RESOURCE_FILE = Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z0-9]+$");

    private SetManifestReader() {}

    static SetManifest read(Path manifestPath) {
        Path normalizedManifest = manifestPath.toAbsolutePath().normalize();
        Map<String, String> values = parseStrictProperties(normalizedManifest);
        Set<String> consumed = new HashSet<>();

        int schemaVersion = positiveInt(required(values, consumed, normalizedManifest, "schemaVersion"), normalizedManifest, "schemaVersion");
        require(schemaVersion == 1, normalizedManifest, "schemaVersion", "поддерживается только schemaVersion=1");
        String setId = lowerKebab(required(values, consumed, normalizedManifest, "setId"), normalizedManifest, "setId");
        int setRevision = positiveInt(required(values, consumed, normalizedManifest, "setRevision"), normalizedManifest, "setRevision");
        int sourceAssetsRevision = positiveInt(
                required(values, consumed, normalizedManifest, "sourceAssetsRevision"), normalizedManifest, "sourceAssetsRevision");
        String contentStatus = required(values, consumed, normalizedManifest, "contentStatus");
        require(contentStatus.equals("approved-for-start"), normalizedManifest, "contentStatus",
                "technical art должен иметь status approved-for-start");
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
            require(SURFACES.contains(surface), normalizedManifest, prefix + "surface", "неизвестная surface: " + surface);
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
                validateResourceName(normalizedManifest, setId, surface, resourcePath);
                rejectGeneratedCollision(normalizedManifest, setId, surface, resourcePath);

                Path assetFile = sourceFile(sourceAssetsRoot, relativePath, normalizedManifest, assetPrefix + "path");
                require(Files.isRegularFile(assetFile), normalizedManifest, assetPrefix + "path",
                        "asset отсутствует: " + relativePath);
                require(sha256(assetFile).equals(sha256), normalizedManifest, assetPrefix + "sha256",
                        "checksum mismatch для " + relativePath);
                assets.add(new SetManifest.Asset(assetId, relativePath, resourcePath, sha256, revision, provenance));
            }

            contributions.add(new SetManifest.Contribution(key, componentId, componentRevision, resourceRevision,
                    surface, platform, minimumApi, installRoute, List.copyOf(supportedSettings), artifactId, artifactProject,
                    List.copyOf(assets)));
        }

        for (String surface : SURFACES) {
            long count = contributions.stream().filter(contribution -> contribution.surface().equals(surface)).count();
            require(count == 1, normalizedManifest, "contributions",
                    "ожидалась ровно одна contribution surface=" + surface + ", найдено " + count);
        }
        requireEntryPoints(normalizedManifest, setId, contributions);
        require(consumed.equals(values.keySet()), normalizedManifest, "schema",
                "неизвестные или необъявленные поля: " + difference(values.keySet(), consumed));

        verifySourceAssets(normalizedManifest, sourceAssetsRoot, provenanceFile, checksumsFile, contributions);
        return new SetManifest(normalizedManifest, sourceAssetsRoot, schemaVersion, setId, setRevision,
                sourceAssetsRevision, contentStatus, List.copyOf(contributions));
    }

    static List<SetManifest> readAll(List<Path> manifestPaths) {
        require(!manifestPaths.isEmpty(), Path.of("."), "livosphere.setManifests",
                "не указан ни один manifest");
        List<SetManifest> manifests = manifestPaths.stream().map(SetManifestReader::read)
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
        return manifests;
    }

    private static void verifySourceAssets(
            Path manifest,
            Path sourceRoot,
            String provenanceFile,
            String checksumsFile,
            List<SetManifest.Contribution> contributions) {
        Set<String> declared = contributions.stream()
                .flatMap(contribution -> contribution.assets().stream())
                .map(SetManifest.Asset::relativePath)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actual;
        try (var paths = Files.walk(sourceRoot)) {
            actual = paths.filter(Files::isRegularFile)
                    .map(sourceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.equals(provenanceFile) && !path.equals(checksumsFile))
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

    private static void validateResourceName(Path manifest, String setId, String surface, String resourcePath) {
        String[] parts = resourcePath.split("/", -1);
        require(parts.length == 2 && RESOURCE_DIRECTORY.matcher(parts[0]).matches(), manifest, "resourcePath",
                "недопустимый Android resource directory: " + resourcePath);
        require(RESOURCE_FILE.matcher(parts[1]).matches(), manifest, "resourcePath",
                "resource filename и extension должны быть lowercase Android-compatible: " + resourcePath);
        if (surface.equals("watchface") && resourcePath.equals("raw/watchface.xml")) return;
        String fileName = parts[1];
        String prefix = "ls_" + setId.replace('-', '_') + "_" + surface + "_";
        require(fileName.startsWith(prefix), manifest, "resourcePath",
                "resource должен иметь prefix " + prefix + ": " + resourcePath);
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

    private static void requireEntryPoints(Path manifest, String setId, List<SetManifest.Contribution> contributions) {
        String wallpaperSource = "wallpaper/xml/ls_" + setId.replace('-', '_') + "_wallpaper_entrypoint.xml";
        SetManifest.Contribution wallpaper = contributions.stream()
                .filter(value -> value.surface().equals("wallpaper")).findFirst().orElseThrow();
        require(wallpaper.assets().stream().anyMatch(asset -> asset.relativePath().equals(wallpaperSource)
                        && asset.resourcePath().equals("xml/ls_" + setId.replace('-', '_') + "_wallpaper_entrypoint.xml")),
                manifest, "contribution." + wallpaper.key() + ".assetRefs",
                "обязателен entrypoint " + wallpaperSource);
        SetManifest.Contribution watchface = contributions.stream()
                .filter(value -> value.surface().equals("watchface")).findFirst().orElseThrow();
        require(watchface.assets().stream().anyMatch(asset -> asset.relativePath().equals("watchface/raw/watchface.xml")
                        && asset.resourcePath().equals("raw/watchface.xml")),
                manifest, "contribution." + watchface.key() + ".assetRefs",
                "обязателен entrypoint watchface/raw/watchface.xml");
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
