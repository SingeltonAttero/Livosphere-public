package app.livosphere.buildlogic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

record SetManifest(
        Path manifestPath,
        Path sourceAssetsRoot,
        int schemaVersion,
        String setId,
        int setRevision,
        int sourceAssetsRevision,
        String contentStatus,
        String distribution,
        Map<String, Approval> approvals,
        List<Contribution> contributions) {

    boolean releaseEligible() {
        return schemaVersion == 2 && distribution.equals("public") && contentStatus.equals("html-approved");
    }

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
            List<Asset> assets,
            String serviceClassName,
            Map<String, String> phaseRefs,
            List<String> effectsRefs,
            String wallpaperRef,
            Map<String, String> widgetRefs,
            String clockStyle,
            String clockDisplayName,
            Map<String, String> layouts,
            String layoutStatus,
            Map<String, Map<String, String>> viewRoles,
            String sceneRef,
            String previewRef) {}

    record Approval(String record, int revision, int sourceAssetsRevision, String sha256) {}

    record Asset(
            String id,
            String relativePath,
            String resourcePath,
            String sha256,
            int revision,
            String provenance) {}
}
