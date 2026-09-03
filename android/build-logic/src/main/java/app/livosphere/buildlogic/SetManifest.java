package app.livosphere.buildlogic;

import java.nio.file.Path;
import java.util.List;

record SetManifest(
        Path manifestPath,
        Path sourceAssetsRoot,
        int schemaVersion,
        String setId,
        int setRevision,
        int sourceAssetsRevision,
        String contentStatus,
        List<Contribution> contributions) {

    Contribution contributionFor(String surface) {
        return contributions.stream()
                .filter(contribution -> contribution.surface().equals(surface))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        manifestPath + ": contribution for surface " + surface + " is missing"));
    }

    record Contribution(
            String key,
            String componentId,
            int componentRevision,
            int resourceRevision,
            String surface,
            String platform,
            int minimumApi,
            String installRoute,
            List<String> supportedSettings,
            String artifactId,
            String artifactProject,
            List<Asset> assets) {}

    record Asset(
            String id,
            String relativePath,
            String resourcePath,
            String sha256,
            int revision,
            String provenance) {}
}
