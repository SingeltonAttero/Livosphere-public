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
    TILT,
    SWIPE,
    REDUCED_MOTION,
    EFFECT_LEVEL,
}

sealed interface InstallRoute {
    data object EmbeddedPreview : InstallRoute
    data object SystemWallpaperPreview : InstallRoute
    data object SystemWidgetPin : InstallRoute
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
    val wallpaperRef: String? = null,
    val widgetRefs: Map<WidgetSize, String> = emptyMap(),
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
    val serviceClassName: String,
    val phaseRefs: Map<DayPhase, String> = emptyMap(),
    val effectsRefs: List<String> = emptyList(),
    val sceneRef: String? = null,
    val previewRef: String? = null,
) : SurfaceContribution {
    init {
        require(Regex("^[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)+$").matches(serviceClassName)) {
            "wallpaper serviceClassName must be a fully qualified class name"
        }
        validateReferences("wallpaper phaseRefs", phaseRefs.values, resources)
        validateReferences("wallpaper effectsRefs", effectsRefs, resources)
        require(effectsRefs.distinct().size == effectsRefs.size) { "wallpaper duplicate effect ref" }
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
    require(resources.map { it.symbolicName }.distinct().size == resources.size) { "$surface duplicate resource ID" }
    require(resources.map { it.resourcePath }.distinct().size == resources.size) { "$surface duplicate resource path" }
    require(resources.isNotEmpty()) { "$surface resources must not be empty" }
    require(supportedSettings.isNotEmpty()) { "$surface supportedSettings must not be empty; use NONE explicitly" }
    require(!(SupportedSetting.NONE in supportedSettings && supportedSettings.size > 1)) {
        "$surface NONE setting cannot be combined with other settings"
    }
}

enum class DayPhase { MORNING, DAY, EVENING, NIGHT }
enum class WidgetSize { S, M, L }
enum class ClockStyle { ANALOG, DIGITAL }
/** A declaration is schema evidence only; NATIVE is not a quality or device attestation. */
enum class WidgetLayoutStatus { TEST_DECLARATION, NATIVE }
enum class Distribution { DEBUG_ONLY, PUBLIC }
enum class ContentStatus {
    APPROVED_FOR_START,
    RELEASE_READY,
    DRAFT,
    IMAGE_APPROVED,
    HTML_APPROVED,
}

data class ClockWidgetContribution(
    override val componentId: ComponentId,
    override val componentRevision: Revision,
    override val resourceRevision: Revision,
    override val compatibility: Compatibility,
    override val installRoute: InstallRoute,
    override val supportedSettings: Set<SupportedSetting>,
    override val artifact: ArtifactReference,
    override val resources: List<ResourceReference>,
    val style: ClockStyle,
    val layouts: Map<WidgetSize, String>,
    val layoutStatus: WidgetLayoutStatus,
) : SurfaceContribution {
    init {
        validateContribution("clock-widget", supportedSettings, resources)
        require(compatibility.platform == Platform.ANDROID_PHONE && installRoute == InstallRoute.SystemWidgetPin) {
            "Clock widget requires ANDROID_PHONE with SystemWidgetPin"
        }
        require(layouts.keys == WidgetSize.entries.toSet()) { "clock-widget layouts require S/M/L" }
        validateReferences("clock-widget layouts", layouts.values, resources)
        require(layouts.values.distinct().size == 3) { "clock-widget requires a separate layout for S/M/L" }
        val directory = if (layoutStatus == WidgetLayoutStatus.TEST_DECLARATION) "raw/" else "layout/"
        require(layouts.values.all { ref -> resources.single { it.symbolicName == ref }.resourcePath.let { it.startsWith(directory) && it.endsWith(".xml") } }) {
            "clock-widget layoutStatus requires $directory resources"
        }
    }
}

private fun validateReferences(field: String, references: Collection<String>, resources: List<ResourceReference>) {
    require(references.all { reference -> resources.any { it.symbolicName == reference } }) {
        "$field contains an undeclared resource reference"
    }
}

enum class ApprovalStage { IMAGE, HTML }

/** Reference integrity only; does not infer that a human accepted an artifact. */
data class ApprovalReference(
    val record: String,
    val revision: Revision,
    val sourceAssetsRevision: Revision,
    val sha256: String,
) {
    init {
        require(Regex("^[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.md$").matches(record)) { "approval record must be a relative Markdown path" }
        require(!record.startsWith("source-assets/")) { "approval record must stay outside packaged resources" }
        require(SHA_256.matches(sha256)) { "approval SHA-256 must be lowercase hexadecimal" }
    }
}

data class SetDescriptor(
    val schemaVersion: Int,
    val setId: SetId,
    val setRevision: Revision,
    val sourceAssetsRevision: Revision,
    val contentStatus: ContentStatus,
    val preview: PreviewContribution,
    val wallpaper: WallpaperContribution,
    val watchFace: WatchFaceContribution? = null,
    val clockWidget: ClockWidgetContribution? = null,
    val distribution: Distribution = Distribution.DEBUG_ONLY,
    val approvals: Map<ApprovalStage, ApprovalReference> = emptyMap(),
) {
    init {
        require(schemaVersion in 1..2) { "Unsupported set schema version: $schemaVersion" }
        if (schemaVersion == 1) {
            require(distribution == Distribution.DEBUG_ONLY && watchFace != null && clockWidget == null && approvals.isEmpty()) {
                "schema1 is legacy debug only and requires watchFace without clockWidget"
            }
            require(contentStatus in setOf(ContentStatus.APPROVED_FOR_START, ContentStatus.RELEASE_READY)) {
                "schema1 requires a legacy content status"
            }
        } else {
            require(watchFace == null && clockWidget != null) { "schema2 requires clockWidget without watchFace" }
            val requiredApprovals = when (contentStatus) {
                ContentStatus.IMAGE_APPROVED -> setOf(ApprovalStage.IMAGE)
                ContentStatus.HTML_APPROVED -> ApprovalStage.entries.toSet()
                else -> emptySet()
            }
            require(approvals.keys == requiredApprovals) { "approval references must match artistic status" }
            require(approvals.values.all { it.sourceAssetsRevision.value <= sourceAssetsRevision.value }) { "approval references a future sourceAssetsRevision" }
            require(contentStatus in setOf(ContentStatus.DRAFT, ContentStatus.IMAGE_APPROVED, ContentStatus.HTML_APPROVED)) {
                "schema2 requires an artistic content status"
            }
            require(wallpaper.phaseRefs.keys == DayPhase.entries.toSet()) { "schema2 requires four wallpaper phase refs" }
            require(wallpaper.sceneRef != null && wallpaper.previewRef == preview.wallpaperRef) { "schema2 requires wallpaper sceneRef and matching previewRef" }
            validateReferences("wallpaper sceneRef", listOfNotNull(wallpaper.sceneRef), wallpaper.resources)
            require(wallpaper.effectsRefs.isNotEmpty()) { "schema2 requires wallpaper effects refs" }
            require(preview.wallpaperRef != null && preview.widgetRefs.keys == WidgetSize.entries.toSet()) {
                "schema2 requires wallpaper and S/M/L widget previews"
            }
            val previewRefs = listOfNotNull(preview.wallpaperRef) + preview.widgetRefs.values
            validateReferences("preview", previewRefs, preview.resources)
            require(previewRefs.all { ref -> preview.resources.single { it.symbolicName == ref }.resourcePath.let { it.startsWith("drawable-nodpi/") && it.endsWith(".png") } }) {
                "schema2 previews require drawable-nodpi PNG"
            }
            require(distribution == Distribution.DEBUG_ONLY || clockWidget.layoutStatus != WidgetLayoutStatus.TEST_DECLARATION) {
                "test layout declarations are debug only"
            }
        }
        val contributions = listOfNotNull(preview, wallpaper, watchFace, clockWidget)
        require(contributions.map { it.componentId }.distinct().size == contributions.size) { "duplicate component ID" }
        val resources = contributions.flatMap { it.resources }
        require(resources.map { it.symbolicName }.distinct().size == resources.size) { "duplicate resource ID" }
        require(resources.map { it.resourcePath }.distinct().size == resources.size) { "duplicate resource path" }
        for (contribution in contributions) {
            require(contribution.resources.all { it.revision == contribution.resourceRevision }) { "stale resource revision" }
        }
    }

    /** Candidate composition only; physical, native-quality and publication gates remain external. */
    val releaseEligible: Boolean
        get() = schemaVersion == 2 && distribution == Distribution.PUBLIC && contentStatus == ContentStatus.HTML_APPROVED
}

interface SetRegistry {
    val sets: List<SetDescriptor>
}
