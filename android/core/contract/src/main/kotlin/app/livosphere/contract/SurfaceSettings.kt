package app.livosphere.contract

import kotlinx.coroutines.flow.Flow

/** Owned preferences only. Platform active/installed/host observations never enter this contract. */
enum class WallpaperMotionMode { NORMAL, REDUCED, OFF }
enum class WallpaperEffectLevel { SUBTLE, BALANCED, FULL }
enum class PreviewSurface { WALLPAPER, CLOCK_WIDGET }

data class BrowsingPreferences(val setId: String, val surface: PreviewSurface, val revision: Long = 0) {
    init { requireSettingsId(setId); require(revision >= 0) }
}
data class WallpaperPreferences(
    val interactionsEnabled: Boolean = true,
    val motionMode: WallpaperMotionMode = WallpaperMotionMode.NORMAL,
    val revision: Long = 0,
    val effectLevel: WallpaperEffectLevel = WallpaperEffectLevel.FULL,
) { init { require(revision >= 0) } }

/** Explicit clock application destination; timezone always remains the phone's. */
data class ClockTarget(val packageName: String, val className: String, val action: String) {
    init { require(packageName.isNotBlank() && className.isNotBlank() && action.isNotBlank()) }
}
data class WidgetPreferences(
    val widgetId: String,
    val size: WidgetSize,
    val clockTarget: ClockTarget?,
    val configurationRevision: Long = 0,
    val generation: Long = 0,
) { init { requireSettingsId(widgetId); require(configurationRevision >= 0); require(generation >= 0) } }

enum class PendingPinStatus { PENDING, CONSUMED, EXPIRED }
data class PendingWidgetPin(
    val token: String,
    val widgetId: String,
    val size: WidgetSize,
    val clockTarget: ClockTarget?,
    val providerClassName: String,
    val createdAtEpochMillis: Long,
    val status: PendingPinStatus = PendingPinStatus.PENDING,
    val boundAppWidgetId: Int? = null,
    val resolvedAtEpochMillis: Long? = null,
) {
    init {
        require(Regex("[A-Za-z0-9_-]{16,128}").matches(token))
        requireSettingsId(widgetId)
        require(providerClassName.startsWith("app.livosphere.") && providerClassName.none(Char::isWhitespace))
        require(createdAtEpochMillis >= 0)
        require(boundAppWidgetId == null || boundAppWidgetId > 0)
        require(resolvedAtEpochMillis == null || resolvedAtEpochMillis >= createdAtEpochMillis)
        require((status == PendingPinStatus.PENDING) == (boundAppWidgetId == null && resolvedAtEpochMillis == null))
    }
}

sealed interface PendingPinConsumeResult {
    data class Consumed(val preferences: WidgetPreferences) : PendingPinConsumeResult
    data class Replay(val appWidgetId: Int) : PendingPinConsumeResult
}

sealed interface SettingsOwner {
    data object Browsing : SettingsOwner
    data class Wallpaper(val wallpaperId: String) : SettingsOwner { init { requireSettingsId(wallpaperId) } }
    data class Widget(val appWidgetId: Int) : SettingsOwner { init { require(appWidgetId > 0) } }
    data class PendingPin(val token: String) : SettingsOwner { init { require(Regex("[A-Za-z0-9_-]{16,128}").matches(token)) } }
}

sealed interface SurfaceSettingsFailure {
    data object Read : SurfaceSettingsFailure
    data object Write : SurfaceSettingsFailure
    data object CorruptFile : SurfaceSettingsFailure
    data class UnsupportedVersion(val version: Int) : SurfaceSettingsFailure
    data class CorruptRecord(val owner: SettingsOwner) : SurfaceSettingsFailure
    data class NeedsConfiguration(val owner: SettingsOwner, val missingReference: String?) : SurfaceSettingsFailure
}
sealed interface SettingsOutcome<out T> {
    data class Success<T>(val value: T) : SettingsOutcome<T>
    data class Failure(val reason: SurfaceSettingsFailure) : SettingsOutcome<Nothing>
}

interface WallpaperSettingsRepository {
    fun observe(wallpaperId: String): Flow<SettingsOutcome<WallpaperPreferences>>
    suspend fun setInteractions(wallpaperId: String, enabled: Boolean): SettingsOutcome<WallpaperPreferences>
    suspend fun setMotion(wallpaperId: String, mode: WallpaperMotionMode): SettingsOutcome<WallpaperPreferences>
    suspend fun setEffectLevel(wallpaperId: String, level: WallpaperEffectLevel): SettingsOutcome<WallpaperPreferences>
}
interface WidgetSettingsRepository {
    fun observe(appWidgetId: Int): Flow<SettingsOutcome<WidgetPreferences>>
    suspend fun configure(appWidgetId: Int, widgetId: String, size: WidgetSize, clockTarget: ClockTarget?): SettingsOutcome<WidgetPreferences>
    suspend fun delete(appWidgetId: Int): SettingsOutcome<Unit>
    suspend fun remap(mapping: Map<Int, Int>): SettingsOutcome<Map<Int, WidgetPreferences>>
    suspend fun restoreAfterFailedUpdate(
        appWidgetId: Int,
        failedRevision: Long,
        previous: WidgetPreferences?,
    ): SettingsOutcome<Unit>
}
interface PendingPinRepository {
    fun observe(token: String): Flow<SettingsOutcome<PendingWidgetPin?>>
    suspend fun create(pin: PendingWidgetPin): SettingsOutcome<PendingWidgetPin>
    suspend fun consume(
        token: String,
        providerClassName: String,
        appWidgetId: Int,
        nowEpochMillis: Long,
    ): SettingsOutcome<PendingPinConsumeResult>
    suspend fun cleanup(nowEpochMillis: Long): SettingsOutcome<Unit>
}

internal fun requireSettingsId(value: String) { require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(value)) }
