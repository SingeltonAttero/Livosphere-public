package app.livosphere.contract

import kotlinx.coroutines.flow.Flow

/** Owned preferences only. Platform active/installed/host observations never enter this contract. */
enum class WallpaperMotionMode { NORMAL, REDUCED, OFF }
enum class PreviewSurface { WALLPAPER, CLOCK_WIDGET }

data class BrowsingPreferences(val setId: String, val surface: PreviewSurface, val revision: Long = 0) {
    init { requireSettingsId(setId); require(revision >= 0) }
}
data class WallpaperPreferences(
    val interactionsEnabled: Boolean = true,
    val motionMode: WallpaperMotionMode = WallpaperMotionMode.NORMAL,
    val revision: Long = 0,
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
) { init { requireSettingsId(widgetId); require(configurationRevision >= 0) } }

sealed interface SettingsOwner {
    data object Browsing : SettingsOwner
    data class Wallpaper(val wallpaperId: String) : SettingsOwner { init { requireSettingsId(wallpaperId) } }
    data class Widget(val appWidgetId: Int) : SettingsOwner { init { require(appWidgetId > 0) } }
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
}
interface WidgetSettingsRepository {
    fun observe(appWidgetId: Int): Flow<SettingsOutcome<WidgetPreferences>>
    suspend fun configure(appWidgetId: Int, widgetId: String, size: WidgetSize, clockTarget: ClockTarget?): SettingsOutcome<WidgetPreferences>
    suspend fun delete(appWidgetId: Int): SettingsOutcome<Unit>
}

internal fun requireSettingsId(value: String) { require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(value)) }
