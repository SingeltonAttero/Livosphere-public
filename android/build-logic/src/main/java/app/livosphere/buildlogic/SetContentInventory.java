package app.livosphere.buildlogic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.gradle.api.GradleException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Concrete Android names, not archive filenames or arbitrary migration strings. */
final class SetContentInventory {
    final Set<String> resources = new TreeSet<>();
    final Set<String> classes = new TreeSet<>();
    final Set<String> components = new TreeSet<>();
    final Set<String> assetPaths = new TreeSet<>();
    final Set<String> references = new TreeSet<>();
    private static final Pattern REFERENCE = Pattern.compile("^[@?](?:\\+)?(?:([A-Za-z_][\\w.]*):)?([a-zA-Z_][\\w]*)/([a-zA-Z_][\\w.]*)$");

    void contribution(SetManifest manifest, SetManifest.Contribution contribution) {
        String prefix = "ls_" + manifest.setId().replace('-', '_') + "_" + contribution.surface().replace('-', '_') + "_";
        for (String name : Set.of("schema_version", "set_revision", "resource_revision")) resources.add("integer/" + prefix + name);
        resources.add("string/" + prefix + "component_id");
        resources.add("raw/" + prefix + "provenance");
        for (SetManifest.Asset asset : contribution.assets()) resource(manifest.sourceAssetsRoot().resolve(asset.relativePath()), asset.resourcePath());
        if (contribution.serviceClassName() != null) {
            classes.add(contribution.serviceClassName());
            components.add(contribution.serviceClassName());
        }
    }

    /** Uses the final AGP variant source model, including custom and generated roots. */
    void androidModule(String module, String variant, java.util.List<String> sourceRoots, String namespace,
            java.util.Map<String, String> placeholders) {
        for (String entry : sourceRoots) {
            int separator = entry.indexOf('|');
            if (separator <= 0) throw new GradleException("Unsupported Android source metadata for " + module + " " + variant);
            String type = entry.substring(0, separator);
            Path source = Path.of(entry.substring(separator + 1));
            // AGP's getAll also reports conventional but empty src/<variant> directories.
            // A missing conventional root is not an omitted generated input.
            if (!Files.exists(source)) continue;
            if (type.equals("manifest")) { manifest(source, namespace, placeholders); continue; }
            if (!Files.isDirectory(source)) throw new GradleException("Unsupported Android source input for "
                    + module + " " + variant + ": " + source);
            try (var files = Files.walk(source)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String relative = source.relativize(file).toString().replace('\\', '/');
                    if (type.equals("res")) resource(file, relative);
                    else if (type.equals("assets")) assetPaths.add("assets/" + relative);
                    else if (type.equals("java") || type.equals("kotlin")) {
                        if (relative.endsWith(".kt") || relative.endsWith(".java")) sourceClass(file);
                    }
                });
            } catch (Exception error) { throw new GradleException("Cannot inventory module " + module, error); }
        }
    }

    /** JVM project inputs are supplied from Gradle's actual main SourceSet. */
    void jvmModule(java.util.List<Path> sources) {
        for (Path source : sources) {
            if (!Files.isDirectory(source)) continue;
            try (var files = Files.walk(source)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    if (file.toString().endsWith(".kt") || file.toString().endsWith(".java")) sourceClass(file);
                    else assetPaths.add("jvm/" + source.relativize(file).toString().replace('\\', '/'));
                });
            } catch (Exception error) { throw new GradleException("Cannot inventory JVM source " + source, error); }
        }
    }


    void resource(Path file, String resourcePath) {
        String directory = resourcePath.substring(0, resourcePath.indexOf('/')).split("-", 2)[0];
        String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1).replaceFirst("\\.9\\.png$", ".png").replaceFirst("\\.[^.]+$", "");
        if (!directory.equals("values")) resources.add(directory + "/" + name);
        if (!resourcePath.endsWith(".xml")) return;
        Element root = xml(file);
        if (directory.equals("values")) {
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element e) || !e.hasAttribute("name")) continue;
                String type = e.getTagName().equals("item") ? e.getAttribute("type") : e.getTagName();
                if (type.equals("string-array") || type.equals("integer-array")) type = "array";
                if (!type.equals("declare-styleable") && !type.equals("public")) resources.add(type + "/" + e.getAttribute("name"));
                if (type.equals("declare-styleable")) {
                    for (Node nested = e.getFirstChild(); nested != null; nested = nested.getNextSibling()) {
                        if (nested instanceof Element attr && attr.getTagName().equals("attr") && attr.hasAttribute("name"))
                            resources.add("attr/" + attr.getAttribute("name"));
                    }
                }
            }
        }
        collectReferences(root, false);
    }

    private void collectReferences(Node node, boolean itemValue) {
        short nodeType = node.getNodeType();
        if (nodeType == Node.COMMENT_NODE) return;
        if (nodeType == Node.ATTRIBUTE_NODE
                || (itemValue && (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE)))
            addReference(node.getNodeValue());
        if (node.hasAttributes()) for (int i = 0; i < node.getAttributes().getLength(); i++) collectReferences(node.getAttributes().item(i), false);
        boolean childIsReferenceValue = node instanceof Element element
                && (element.getTagName().equals("item") || element.getTagName().equals("attr"));
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collectReferences(child, childIsReferenceValue);
    }

    private void addReference(String raw) {
        if (raw == null) return;
        var match = REFERENCE.matcher(raw.trim());
        if (!match.matches()) return; // comments and escaped/literal text are not Android references.
        String type = match.group(2);
        String name = match.group(3);
        String packageName = match.group(1);
        String ref = packageName == null ? type + "/" + name : packageName + ":" + type + "/" + name;
        if (raw.trim().startsWith("@+id/")) resources.add("id/" + name);
        references.add(ref);
    }

    private void sourceClass(Path file) {
        try {
            String text = Files.readString(file);
            var pkg = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)").matcher(text);
            if (!pkg.find()) return;
            String namespace = pkg.group(1);
            var names = Pattern.compile("\\b(?:class|interface|object|record|enum\\s+class|enum|annotation\\s+class|data\\s+class|sealed\\s+class|value\\s+class)\\s+([A-Za-z_][\\w]*)").matcher(text);
            while (names.find()) classes.add(namespace + "." + names.group(1));
            if (file.toString().endsWith(".kt")) {
                var jvmName = Pattern.compile("@file:(?:[A-Za-z_][\\w.]*\\.)?JvmName\\s*\\(\\s*\\\"([A-Za-z_][\\w]*)\\\"\\s*\\)").matcher(text);
                if (jvmName.find()) classes.add(namespace + "." + jvmName.group(1));
                else classes.add(namespace + "." + file.getFileName().toString().replace(".kt", "Kt"));
            }
        } catch (Exception error) { throw new GradleException("Cannot inventory classes " + file, error); }
    }

    void manifest(Path file, String defaultPackage, java.util.Map<String, String> placeholders) {
        Element root = xml(file);
        String pkg = root.getAttribute("package");
        if (pkg.isEmpty()) pkg = defaultPackage;
        for (String tag : Set.of("service", "provider", "receiver", "activity", "activity-alias")) {
            var entries = root.getElementsByTagName(tag);
            for (int i = 0; i < entries.getLength(); i++) {
                String name = ((Element) entries.item(i)).getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                name = substitutePlaceholders(name, placeholders);
                if (name.startsWith(".") && !pkg.isEmpty()) name = pkg + name;
                else if (!name.isEmpty() && name.indexOf('.') < 0 && !pkg.isEmpty()) name = pkg + "." + name;
                if (!name.isEmpty()) components.add(name);
            }
        }
        collectReferences(root, false);
    }

    private static String substitutePlaceholders(String value, java.util.Map<String, String> placeholders) {
        var match = Pattern.compile("\\$\\{([A-Za-z_][\\w.]*)}").matcher(value);
        StringBuffer result = new StringBuffer();
        while (match.find()) {
            String replacement = placeholders.get(match.group(1));
            if (replacement == null) throw new GradleException("Unresolved manifest placeholder: " + match.group(1));
            match.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        match.appendTail(result);
        return result.toString();
    }

    static Element xml(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (Exception error) { throw new GradleException("Unsafe or invalid resource XML " + file, error); }
    }
}
