package app.livosphere.buildlogic;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.*;

/** Verifies actual compiled APK contents. Reports are external and bound to the immutable APK digest. */
public abstract class AuditSetApkTask extends AbstractSetTask {
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getRegistryDirectory();
    @Internal public abstract DirectoryProperty getSdkDirectory();
    @Internal public abstract MapProperty<String, String> getModuleDirectories();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getModuleSources();
    @OutputDirectory public abstract DirectoryProperty getInventoryDirectory();

    @TaskAction
    public void audit() throws Exception {
        VariantContentSelection selection = selection();
        selection.requireNonEmpty();
        String variant = getName().substring("audit".length(), getName().length() - "SetApk".length());
        variant = Character.toLowerCase(variant.charAt(0)) + variant.substring(1);
        VariantProjectClosure graph = VariantProjectClosure.resolve(getProject(), variant, selection);
        Set<String> runtimeProjects = graph.runtimeProjects();
        Set<String> excludedModules = graph.excludedProjects();
        SetContentInventory allowed = new SetContentInventory();
        SetContentInventory excluded = new SetContentInventory();
        selection.selected().forEach(m -> m.contributions().stream().filter(c -> !c.surface().equals("watchface"))
                .forEach(c -> allowed.contribution(m, c)));
        selection.excluded().forEach(m -> m.contributions().forEach(c -> excluded.contribution(m, c)));
        for (String module : runtimeProjects) inventoryModule(allowed, module, variant, getBuildType().get());
        // Excluded contributions can have release/fallback source sets too.  Inventory the
        // variant actually being audited, rather than assuming their debug source tree.
        for (String module : excludedModules) inventoryModule(excluded, module, variant, getBuildType().get());
        Path reports = getInventoryDirectory().get().getAsFile().toPath();
        clearReports(reports);
        verifyGeneratedRegistry(selection);
        verifyShellDoesNotOverrideSelection(selection, runtimeProjects, variant, getBuildType().get());
        Files.write(reports.resolve("inventory.txt"), List.of("variant=" + variant,
                "selected=" + selection.selected().stream().map(SetManifest::setId).sorted().toList(),
                "excluded=" + selection.excluded().stream().map(SetManifest::setId).sorted().toList(),
                "runtimeComponents=" + graph.runtimeComponents(), "runtimeProjects=" + runtimeProjects,
                "excludedProjects=" + excludedModules,
                "allowedResources=" + allowed.resources, "excludedResources=" + excluded.resources,
                "excludedClasses=" + excluded.classes, "excludedComponents=" + excluded.components));
        Path aapt2 = sdkTool("aapt2");
        Path dexdump = sdkTool("dexdump");
        List<Path> apks;
        try (var files = Files.walk(getApkDirectory().get().getAsFile().toPath())) {
            apks = files.filter(p -> p.toString().endsWith(".apk")).toList();
        }
        require(!apks.isEmpty(), "APK missing for content audit");
        for (Path apk : apks) auditApk(apk, aapt2, dexdump, selection, allowed, excluded, reports);
    }

    /** A failed prerequisite must never leave a stale PASS report for this variant. */
    private static void clearReports(Path reports) throws Exception {
        if (Files.exists(reports)) try (var files = Files.list(reports)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) Files.delete(file);
        }
        Files.createDirectories(reports);
    }

    private void verifyGeneratedRegistry(VariantContentSelection selection) throws Exception {
        Path registry = getRegistryDirectory().get().getAsFile().toPath()
                .resolve("app/livosphere/generated/GeneratedSetRegistry.kt");
        require(Files.isRegularFile(registry), "Generated registry source missing for APK audit: " + registry);
        require(Files.readString(registry).equals(SetContractEngine.registrySource(selection)),
                "Generated registry descriptor differs from authoritative variant selection");
    }

    /** A shell/app overlay may not replace an approved set resource under the same identifier. */
    private void verifyShellDoesNotOverrideSelection(VariantContentSelection selection, Set<String> runtime,
            String variant, String buildType) {
        Set<String> contributionProjects = selection.projects();
        for (String module : runtime) {
            if (contributionProjects.contains(module)) continue;
            String directory = getModuleDirectories().get().get(module);
            if (directory == null) continue;
            SetContentInventory inventory = new SetContentInventory();
            inventory.module(Path.of(directory), variant, buildType);
            for (String resource : inventory.resources) {
                if (resource.startsWith("values/")) continue;
                for (SetManifest selected : selection.selected()) {
                    String prefix = "ls_" + selected.setId().replace('-', '_') + "_";
                    if (resource.substring(resource.indexOf('/') + 1).startsWith(prefix)) {
                        throw new GradleException("Selected resource overridden outside contribution closure: " + resource
                                + " from " + module);
                    }
                }
            }
        }
    }

    private void inventoryModule(SetContentInventory inventory, String module, String variant, String buildType) {
        String directory = getModuleDirectories().get().get(module);
        if (directory != null) inventory.module(Path.of(directory), variant, buildType);
    }

    private void auditApk(Path apk, Path aapt2, Path dexdump, VariantContentSelection selection,
            SetContentInventory allowed, SetContentInventory excluded, Path reports) throws Exception {
        String resources = command(aapt2.toString(), "dump", "resources", apk.toString());
        String manifest = command(aapt2.toString(), "dump", "xmltree", apk.toString(), "--file", "AndroidManifest.xml");
        Files.writeString(reports.resolve(apk.getFileName() + "-resources.txt"), resources);
        Files.writeString(reports.resolve(apk.getFileName() + "-manifest.txt"), manifest);
        Set<String> actualResources = new TreeSet<>();
        var resource = Pattern.compile("resource (0x[0-9a-f]+) ([\\w-]+/[\\w.]+)").matcher(resources);
        while (resource.find()) actualResources.add(resource.group(2));
        Set<String> missing = new TreeSet<>(allowed.resources); missing.removeAll(actualResources);
        // Source-only declare-styleable/attrs and dependency resources are not all materialized by AAPT.
        missing.removeIf(name -> !name.substring(name.indexOf('/') + 1).startsWith("ls_"));
        require(missing.isEmpty(), "Missing selected resources in APK: " + missing);
        for (String name : excluded.resources) require(!actualResources.contains(name), "Excluded resource in APK: " + name);
        for (SetManifest forbidden : selection.excluded()) {
            String namespace = "ls_" + forbidden.setId().replace('-', '_') + "_";
            for (String name : actualResources) require(!name.substring(name.indexOf('/') + 1).startsWith(namespace),
                    "Excluded resource namespace in APK: " + name);
        }
        for (String name : actualResources) if (name.substring(name.indexOf('/') + 1).startsWith("ls_"))
            require(allowed.resources.contains(name), "Undeclared set resource in APK: " + name);
        for (String reference : allowed.references) require(reference.startsWith("android:") || actualResources.contains(reference),
                "Resource XML reference outside allowed closure/framework: " + reference);
        Set<String> manifestComponents = manifestComponents(manifest);
        for (String component : excluded.components) require(!manifestComponents.contains(component), "Excluded manifest component in APK: " + component);
        for (SetManifest selected : selection.selected()) {
            String service = selected.contributionFor("wallpaper").serviceClassName();
            require(manifestComponents.contains(service), "Selected wallpaper service missing in APK manifest: " + service);
        }
        Set<String> types = new TreeSet<>();
        Set<String> registryStrings = new TreeSet<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                require(!excluded.assetPaths.contains(entry.getName()), "Excluded asset in APK: " + entry.getName());
                if (!entry.getName().matches("classes(?:[0-9]+)?\\.dex")) continue;
                Path dex = getTemporaryDir().toPath().resolve(entry.getName());
                try (var stream = zip.getInputStream(entry)) { Files.copy(stream, dex, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                readDex(dexdump, dex, types, registryStrings);
            }
        }
        require(types.contains("app.livosphere.generated.GeneratedSetRegistry"), "GeneratedSetRegistry DEX type missing");
        for (String type : types) for (String forbidden : excluded.classes)
            require(!type.equals(forbidden) && !type.startsWith(forbidden + "$"), "Excluded DEX class in APK: " + type);
        for (SetManifest selected : selection.selected()) {
            require(registryStrings.contains(selected.setId()), "Selected entry missing from registry DEX: " + selected.setId());
            require(types.contains(selected.contributionFor("wallpaper").serviceClassName()), "Selected wallpaper DEX type missing");
        }
        for (SetManifest forbidden : selection.excluded())
            require(!registryStrings.contains(forbidden.setId()), "Excluded entry in registry DEX: " + forbidden.setId());
        Files.write(reports.resolve(apk.getFileName() + "-dex-types.txt"), types);
        Files.write(reports.resolve(apk.getFileName() + "-registry-strings.txt"), registryStrings);
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(apk)));
        Files.writeString(reports.resolve(apk.getFileName() + "-audit.txt"), "PASS content closure\nsha256=" + digest
                + "\nNative/quality/publication acceptance: UNKNOWN; external attestations only.\n");
        getLogger().lifecycle("Set APK content audit PASS: {} SHA-256 {}", apk.getFileName(), digest);
    }

    private static void readDex(Path tool, Path dex, Set<String> types, Set<String> registryStrings) throws Exception {
        Process process = new ProcessBuilder(tool.toString(), "-d", dex.toString()).redirectErrorStream(true).start();
        boolean registry = false;
        Pattern descriptor = Pattern.compile("Class descriptor\\s*: 'L([^;]+);'");
        Pattern string = Pattern.compile("const-string(?:/jumbo)? .*?, \"(.*?)\" // string@");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Class #")) registry = false;
                var match = descriptor.matcher(line);
                if (match.find()) {
                    String type = match.group(1).replace('/', '.'); types.add(type);
                    registry = type.equals("app.livosphere.generated.GeneratedSetRegistry");
                }
                if (registry) { var constant = string.matcher(line); if (constant.find()) registryStrings.add(constant.group(1)); }
            }
        }
        require(process.waitFor() == 0, "dexdump failed for " + dex);
    }

    /** Extract only actual Android component nodes; a name in meta-data is not a component. */
    private static Set<String> manifestComponents(String dump) {
        Set<String> result = new TreeSet<>();
        Set<String> componentTags = Set.of("service", "provider", "receiver", "activity", "activity-alias");
        String current = null;
        Pattern element = Pattern.compile("^\\s*E: ([\\w-]+)");
        Pattern name = Pattern.compile("android:name.*?\\\"([^\\\"]+)\\\"");
        for (String line : dump.lines().toList()) {
            var elementMatch = element.matcher(line);
            if (elementMatch.find()) current = componentTags.contains(elementMatch.group(1)) ? elementMatch.group(1) : null;
            if (current == null) continue;
            var nameMatch = name.matcher(line);
            if (nameMatch.find()) result.add(nameMatch.group(1));
        }
        return result;
    }

    private Path sdkTool(String name) throws Exception {
        Path root = getSdkDirectory().get().getAsFile().toPath().resolve("build-tools");
        try (var directories = Files.list(root)) {
            return directories.sorted(java.util.Comparator.reverseOrder()).map(p -> p.resolve(name)).filter(Files::isExecutable)
                    .findFirst().orElseThrow(() -> new GradleException("Android SDK build tool missing: " + name));
        }
    }

    private static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        require(process.waitFor() == 0, "Android build tool failed: " + output);
        return output;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GradleException(message); }
}
