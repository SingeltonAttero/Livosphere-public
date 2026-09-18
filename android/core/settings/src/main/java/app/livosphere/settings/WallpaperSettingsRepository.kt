package app.livosphere.settings

import android.content.Context
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.WallpaperPreferences
import app.livosphere.contract.WallpaperEffectLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Compatibility projection for existing Engine and Hub consumers, backed only by typed destination. */
typealias WallpaperMotionMode = app.livosphere.contract.WallpaperMotionMode

open class WallpaperSettingsRepository(
    private val repository: SurfaceSettingsRepository,
    val wallpaperId: String,
    private val available: (String) -> Boolean = { true },
) {
    init { require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(wallpaperId)) }
    constructor(context: Context, wallpaperId: String) : this(ApplicationSurfaceSettings.get(context), wallpaperId)
    private val port = repository.wallpapers(available)

    open fun forWallpaper(wallpaperId: String): WallpaperSettingsRepository =
        if (this.wallpaperId == wallpaperId) this else WallpaperSettingsRepository(repository, wallpaperId, available)

    open val settings: Flow<SettingsOutcome<WallpaperPreferences>> = port.observe(wallpaperId)
    open val touchReactionsEnabled: Flow<Boolean?> = settings.map { (it as? SettingsOutcome.Success)?.value?.interactionsEnabled }
        .distinctUntilChanged()
    open val motionMode: Flow<WallpaperMotionMode?> = settings.map { (it as? SettingsOutcome.Success)?.value?.motionMode }
        .distinctUntilChanged()

    open suspend fun setTouchReactionsEnabled(enabled: Boolean) { port.setInteractions(wallpaperId, enabled).orThrow() }
    open suspend fun setEffectLevel(level: WallpaperEffectLevel) { port.setEffectLevel(wallpaperId, level).orThrow() }
    open suspend fun setMotionMode(mode: WallpaperMotionMode) { port.setMotion(wallpaperId, mode).orThrow() }
}
private fun SettingsOutcome<*>.orThrow() { if (this is SettingsOutcome.Failure) throw SurfaceSettingsException(reason) }
