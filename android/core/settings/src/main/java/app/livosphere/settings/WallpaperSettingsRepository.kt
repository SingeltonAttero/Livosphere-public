package app.livosphere.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException

/** A user-owned preference. It is intentionally independent from touch reactions and Hub motion. */
enum class WallpaperMotionMode { NORMAL, REDUCED, OFF }

private const val WALLPAPER_SETTINGS_FILE = "contour-wallpaper-settings"
private val Context.contourWallpaperSettings by preferencesDataStore(name = WALLPAPER_SETTINGS_FILE)
const val LEGACY_CONTOUR_WALLPAPER_ID = "contour-wallpaper"
private fun keyName(wallpaperId: String, legacy: String) =
    if (wallpaperId == LEGACY_CONTOUR_WALLPAPER_ID) legacy else "wallpaper.$wallpaperId.$legacy"
internal val motionModeKey = stringPreferencesKey("wallpaper_motion_mode")

/**
 * Shared, application-context DataStore. A successfully read missing key is the product default
 * (`true`); an I/O failure is deliberately `null`, so both Hub and service fail closed instead of
 * claiming a setting that was never read.
 */
open class WallpaperSettingsRepository constructor(
    private val store: DataStore<Preferences>,
    val wallpaperId: String,
) {
    init { require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(wallpaperId)) }
    constructor(context: Context, wallpaperId: String) : this(context.applicationContext.contourWallpaperSettings, wallpaperId)

    private val touchReactionsKey = booleanPreferencesKey(keyName(wallpaperId, "touch_reactions_enabled"))
    private val motionModeKey = stringPreferencesKey(keyName(wallpaperId, "wallpaper_motion_mode"))

    /** Same application store and serialized writer; changing owner never reads or writes another owner's keys. */
    open fun forWallpaper(wallpaperId: String): WallpaperSettingsRepository =
        if (this.wallpaperId == wallpaperId) this else WallpaperSettingsRepository(store, wallpaperId)

    open val touchReactionsEnabled: Flow<Boolean?> = store.data
        .map { preferences: Preferences -> (preferences[touchReactionsKey] ?: true) as Boolean? }
        .distinctUntilChanged()
        .catch { error ->
            if (error is IOException) emit(null) else throw error
        }

    /** Missing after a successful read is the product default; unreadable or invalid values fail closed. */
    open val motionMode: Flow<WallpaperMotionMode?> = store.data
        .map { preferences ->
            preferences[motionModeKey]
                ?.let { stored -> WallpaperMotionMode.entries.firstOrNull { it.name == stored } }
                ?: if (motionModeKey !in preferences) WallpaperMotionMode.NORMAL else null
        }
        .distinctUntilChanged()
        .catch { error ->
            if (error is IOException) emit(null) else throw error
        }

    open suspend fun setTouchReactionsEnabled(enabled: Boolean) {
        store.edit { preferences -> preferences[touchReactionsKey] = enabled }
    }

    open suspend fun setMotionMode(mode: WallpaperMotionMode) {
        store.edit { preferences -> preferences[motionModeKey] = mode.name }
    }
}
