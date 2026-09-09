package app.livosphere.buildlogic;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Pattern REFERENCE = Pattern.compile("[@?](?:\\+)?(?:([A-Za-z_][\\w.]*):)?([a-zA-Z_][\\w]*)/([a-zA-Z_][\\w.]*)");

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

    void module(Path root, String variant, String buildType) {
        for (String sourceSet : new java.util.LinkedHashSet<>(java.util.List.of("main", buildType, variant))) {
            Path source = root.resolve("src/" + sourceSet);
            if (!Files.isDirectory(source)) continue;
            try (var files = Files.walk(source)) {
                files.filter(Files::isRegularFile).forEach(file -> {
                    String relative = source.relativize(file).toString().replace('\\', '/');
                    if (relative.startsWith("res/")) resource(file, relative.substring(4));
                    else if (relative.startsWith("assets/")) assetPaths.add(relative);
                    else if (relative.endsWith(".kt") || relative.endsWith(".java")) sourceClass(file);
                    else if (relative.equals("AndroidManifest.xml")) manifest(file);
                });
            } catch (Exception error) { throw new GradleException("Cannot inventory module " + root, error); }
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
            }
        }
        collectReferences(root);
    }

    private void collectReferences(Node node) {
        if (node.getNodeValue() != null) {
            var match = REFERENCE.matcher(node.getNodeValue());
            while (match.find()) {
                String ref = (match.group(1) == null ? "" : match.group(1) + ":") + match.group(2) + "/" + match.group(3);
                if (match.group().startsWith("@+id/")) resources.add("id/" + match.group(3));
                references.add(ref);
            }
        }
        if (node.hasAttributes()) for (int i = 0; i < node.getAttributes().getLength(); i++) collectReferences(node.getAttributes().item(i));
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collectReferences(child);
    }

    private void sourceClass(Path file) {
        try {
            String text = Files.readString(file);
            var pkg = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)").matcher(text);
            if (!pkg.find()) return;
            String namespace = pkg.group(1);
            var names = Pattern.compile("\\b(?:class|interface|object)\\s+([A-Za-z_][\\w]*)").matcher(text);
            while (names.find()) classes.add(namespace + "." + names.group(1));
            if (file.toString().endsWith(".kt")) classes.add(namespace + "." + file.getFileName().toString().replace(".kt", "Kt"));
        } catch (Exception error) { throw new GradleException("Cannot inventory classes " + file, error); }
    }

    private void manifest(Path file) {
        Element root = xml(file);
        String pkg = root.getAttribute("package");
        for (String tag : Set.of("service", "provider", "receiver", "activity", "activity-alias")) {
            var entries = root.getElementsByTagName(tag);
            for (int i = 0; i < entries.getLength(); i++) {
                String name = ((Element) entries.item(i)).getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name.startsWith(".") && !pkg.isEmpty()) name = pkg + name;
                if (!name.isEmpty()) components.add(name);
            }
        }
        collectReferences(root);
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
