package app.livosphere.contract

private val LOWER_KEBAB = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")
private val SHA_256 = Regex("^[0-9a-f]{64}$")
private val RESOURCE_PATH = Regex("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*/[a-z][a-z0-9_]*\\.[a-z0-9]+$")

@JvmInline
value class SetId(val value: String) {
    init {
        require(LOWER_KEBAB.matches(value)) { "SetId must be lower-kebab: $value" }
    }
}

@JvmInline
value class ComponentId(val value: String) {
    init {
        require(LOWER_KEBAB.matches(value)) { "ComponentId must be lower-kebab: $value" }
    }
}

@JvmInline
value class ArtifactId(val value: String) {
    init {
        require(LOWER_KEBAB.matches(value)) { "ArtifactId must be lower-kebab: $value" }
    }
}

@JvmInline
value class Revision(val value: Int) {
    init {
        require(value > 0) { "Revision must be positive: $value" }
    }
}

data class ResourceReference(
    val symbolicName: String,
    val resourcePath: String,
    val sha256: String,
    val revision: Revision,
    val provenance: String,
) {
    init {
        require(LOWER_KEBAB.matches(symbolicName)) {
            "Resource symbolicName must be lower-kebab: $symbolicName"
        }
        require(RESOURCE_PATH.matches(resourcePath) && !resourcePath.contains("..")) {
            "Resource path must be a normalized platform-neutral Android resource path: $resourcePath"
        }
        require(SHA_256.matches(sha256)) { "Resource SHA-256 must be lowercase hexadecimal" }
        require(provenance.isNotBlank()) { "Resource provenance must not be blank" }
    }
}

data class Compatibility(
    val platform: Platform,
    val minimumApi: Int,
) {
    init {
        require(minimumApi > 0) { "minimumApi must be positive" }
    }
}

data class ArtifactReference(
    val artifactId: ArtifactId,
    val projectPath: String,
) {
    init {
        require(Regex("^:[a-z0-9:-]+$").matches(projectPath)) {
            "Artifact projectPath must be a Gradle project path: $projectPath"
        }
    }
}

enum class Platform {
    ANDROID_PHONE,
    WEAR_OS,
}

enum class SupportedSetting {
    NONE,
    TIME_OF_DAY,
    BATTERY_LEVEL,
    CHARGING,
    TAP,
    SWIPE,
    REDUCED_MOTION,
}

sealed interface InstallRoute {
    data object EmbeddedPreview : InstallRoute
    data object SystemWallpaperPreview : InstallRoute
    data object SeparateWatchFacePackage : InstallRoute
}

sealed interface SurfaceContribution {
    val componentId: ComponentId
    val componentRevision: Revision
    val resourceRevision: Revision
    val compatibility: Compatibility
    val installRoute: InstallRoute
    val supportedSettings: Set<SupportedSetting>
    val artifact: ArtifactReference
    val resources: List<ResourceReference>
}

data class PreviewContribution(
    override val componentId: ComponentId,
    override val componentRevision: Revision,
    override val resourceRevision: Revision,
    override val compatibility: Compatibility,
    override val installRoute: InstallRoute,
    override val supportedSettings: Set<SupportedSetting>,
    override val artifact: ArtifactReference,
    override val resources: List<ResourceReference>,
) : SurfaceContribution {
    init {
        validateContribution("preview", supportedSettings, resources)
        require(compatibility.platform == Platform.ANDROID_PHONE && installRoute == InstallRoute.EmbeddedPreview) {
            "Preview requires ANDROID_PHONE with EmbeddedPreview"
        }
    }
}

data class WallpaperContribution(
    override val componentId: ComponentId,
    override val componentRevision: Revision,
    override val resourceRevision: Revision,
    override val compatibility: Compatibility,
    override val installRoute: InstallRoute,
    override val supportedSettings: Set<SupportedSetting>,
    override val artifact: ArtifactReference,
    override val resources: List<ResourceReference>,
) : SurfaceContribution {
    init {
        validateContribution("wallpaper", supportedSettings, resources)
        require(compatibility.platform == Platform.ANDROID_PHONE && installRoute == InstallRoute.SystemWallpaperPreview) {
            "Wallpaper requires ANDROID_PHONE with SystemWallpaperPreview"
        }
    }
}

data class WatchFaceContribution(
    override val componentId: ComponentId,
    override val componentRevision: Revision,
    override val resourceRevision: Revision,
    override val compatibility: Compatibility,
    override val installRoute: InstallRoute,
    override val supportedSettings: Set<SupportedSetting>,
    override val artifact: ArtifactReference,
    override val resources: List<ResourceReference>,
) : SurfaceContribution {
    init {
        validateContribution("watchface", supportedSettings, resources)
        require(compatibility.platform == Platform.WEAR_OS && installRoute == InstallRoute.SeparateWatchFacePackage) {
            "Watch face requires WEAR_OS with SeparateWatchFacePackage"
        }
    }
}

private fun validateContribution(
    surface: String,
    supportedSettings: Set<SupportedSetting>,
    resources: List<ResourceReference>,
) {
    require(resources.isNotEmpty()) { "$surface resources must not be empty" }
    require(supportedSettings.isNotEmpty()) { "$surface supportedSettings must not be empty; use NONE explicitly" }
    require(!(SupportedSetting.NONE in supportedSettings && supportedSettings.size > 1)) {
        "$surface NONE setting cannot be combined with other settings"
    }
}

enum class ContentStatus {
    APPROVED_FOR_START,
    RELEASE_READY,
}

data class SetDescriptor(
    val schemaVersion: Int,
    val setId: SetId,
    val setRevision: Revision,
    val sourceAssetsRevision: Revision,
    val contentStatus: ContentStatus,
    val preview: PreviewContribution,
    val wallpaper: WallpaperContribution,
    val watchFace: WatchFaceContribution,
) {
    init {
        require(schemaVersion == 1) { "Unsupported set schema version: $schemaVersion" }
        require(listOf(preview, wallpaper, watchFace).flatMap { it.resources }.isNotEmpty()) {
            "A set must reference at least one resource"
        }
    }
}

interface SetRegistry {
    val sets: List<SetDescriptor>
}
