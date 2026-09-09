package app.livosphere.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import app.livosphere.contract.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** All owner adapters share this DataStore writer. Transformations always read current inside updateData. */
class SurfaceSettingsRepository(private val store: DataStore<StoredSurfaceSettings>) {
    /** Structural migration report only; no raw records or platform observations. */
    val metadata: Flow<SettingsOutcome<SurfaceSettingsMetadata>> = observeRecord {
        SettingsOutcome.Success(SurfaceSettingsMetadata(it.schemaVersion, it.wallpapers.keys.toSet(), it.widgets.keys.toSet()))
    }

    fun wallpapers(available: (String) -> Boolean): app.livosphere.contract.WallpaperSettingsRepository =
        object : app.livosphere.contract.WallpaperSettingsRepository {
            override fun observe(wallpaperId: String): Flow<SettingsOutcome<WallpaperPreferences>> {
                SettingsOwner.Wallpaper(wallpaperId)
                return observeRecord { current -> wallpaper(current, wallpaperId, available) }
            }
            override suspend fun setInteractions(wallpaperId: String, enabled: Boolean) = updateWallpaper(wallpaperId, available) {
                it.copy(interactionsEnabled = enabled, revision = nextRevision(it.revision))
            }
            override suspend fun setMotion(wallpaperId: String, mode: WallpaperMotionMode) = updateWallpaper(wallpaperId, available) {
                it.copy(motionMode = mode, revision = nextRevision(it.revision))
            }
        }

    fun widgets(available: (String) -> Boolean): WidgetSettingsRepository = object : WidgetSettingsRepository {
        override fun observe(appWidgetId: Int): Flow<SettingsOutcome<WidgetPreferences>> {
            SettingsOwner.Widget(appWidgetId)
            return observeRecord { current -> widget(current, appWidgetId, available) }
        }
        override suspend fun configure(appWidgetId: Int, widgetId: String, size: WidgetSize, clockTarget: ClockTarget?): SettingsOutcome<WidgetPreferences> {
            val owner = SettingsOwner.Widget(appWidgetId)
            WidgetPreferences(widgetId, size, clockTarget)
            return transaction { current ->
                if (!available(widgetId)) fail(SurfaceSettingsFailure.NeedsConfiguration(owner, widgetId))
                // Invalid records remain intact until an explicit recovery flow can explain the loss.
                val previous = current.widgets[appWidgetId.toString()]?.let { decode(owner) { it.widget() } }
                val revision = when (previous) {
                    is SettingsOutcome.Success -> nextRevision(previous.value.configurationRevision)
                    is SettingsOutcome.Failure -> fail(previous.reason)
                    null -> 1L
                }
                val updated = WidgetPreferences(widgetId, size, clockTarget, revision)
                current.copy(widgets = current.widgets + (appWidgetId.toString() to updated.encode())) to updated
            }
        }
        override suspend fun delete(appWidgetId: Int): SettingsOutcome<Unit> {
            SettingsOwner.Widget(appWidgetId)
            return transaction { current -> current.copy(widgets = current.widgets - appWidgetId.toString()) to Unit }
        }
    }

    fun browsing(availableSet: (String) -> Boolean): Flow<SettingsOutcome<BrowsingPreferences?>> = observeRecord { current ->
        val raw = current.browsing ?: return@observeRecord SettingsOutcome.Success(null)
        when (val result = decode(SettingsOwner.Browsing) { raw.browsing() }) {
            is SettingsOutcome.Failure -> result
            is SettingsOutcome.Success -> if (availableSet(result.value.setId)) result
                else SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Browsing, result.value.setId))
        }
    }

    suspend fun setBrowsing(setId: String, surface: PreviewSurface, availableSet: (String) -> Boolean): SettingsOutcome<BrowsingPreferences> {
        BrowsingPreferences(setId, surface)
        return transaction { current ->
            if (!availableSet(setId)) fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Browsing, setId))
            val old = current.browsing?.let { decode(SettingsOwner.Browsing) { it.browsing() } }
            val revision = when (old) {
                is SettingsOutcome.Success -> nextRevision(old.value.revision)
                is SettingsOutcome.Failure -> fail(old.reason)
                null -> 1L
            }
            val updated = BrowsingPreferences(setId, surface, revision)
            current.copy(browsing = updated.encode()) to updated
        }

    }

    private fun wallpaper(current: StoredSurfaceSettings, id: String, available: (String) -> Boolean): SettingsOutcome<WallpaperPreferences> {
        val owner = SettingsOwner.Wallpaper(id)
        if (!available(id)) return SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(owner, id))
        return current.wallpapers[id]?.let { raw -> decode(owner) { raw.wallpaper() } }
            ?: SettingsOutcome.Success(WallpaperPreferences())
    }
    private fun widget(current: StoredSurfaceSettings, id: Int, available: (String) -> Boolean): SettingsOutcome<WidgetPreferences> {
        val owner = SettingsOwner.Widget(id)
        val raw = current.widgets[id.toString()]
            ?: return SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(owner, null))
        return when (val result = decode(owner) { raw.widget() }) {
            is SettingsOutcome.Failure -> result
            is SettingsOutcome.Success -> if (available(result.value.widgetId)) result
                else SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(owner, result.value.widgetId))
        }
    }
    private suspend fun updateWallpaper(id: String, available: (String) -> Boolean, transform: (WallpaperPreferences) -> WallpaperPreferences): SettingsOutcome<WallpaperPreferences> {
        SettingsOwner.Wallpaper(id)
        return transaction { current ->
            val previous = when (val result = wallpaper(current, id, available)) {
                is SettingsOutcome.Success -> result.value
                is SettingsOutcome.Failure -> fail(result.reason)
            }
            val updated = transform(previous)
            current.copy(wallpapers = current.wallpapers + (id to updated.encode())) to updated
        }

    }

    private fun <T> observeRecord(project: (StoredSurfaceSettings) -> SettingsOutcome<T>): Flow<SettingsOutcome<T>> = store.data
        .map { current -> requireCurrent(current); project(current) }
        .distinctUntilChanged()
        .catch { error ->
            if (error is CancellationException) throw error
            if (error !is Exception) throw error
            emit(SettingsOutcome.Failure(error.failure(writing = false)))
        }

    private suspend fun <T> transaction(transform: (StoredSurfaceSettings) -> Pair<StoredSurfaceSettings, T>): SettingsOutcome<T> = try {
        var result: T? = null
        store.updateData { current ->
            requireCurrent(current)
            val (updated, value) = transform(current)
            result = value
            updated
        }
        @Suppress("UNCHECKED_CAST")
        SettingsOutcome.Success(result as T)
    } catch (error: CancellationException) { throw error }
    catch (error: Exception) { SettingsOutcome.Failure(error.failure(writing = true)) }
}

data class SurfaceSettingsMetadata(val schemaVersion: Int, val wallpaperIds: Set<String>, val appWidgetIds: Set<String>)

private fun requireCurrent(value: StoredSurfaceSettings) {
    if (value.schemaVersion != SURFACE_SETTINGS_SCHEMA) throw UnsupportedSettingsVersion(value.schemaVersion)
}
private fun nextRevision(value: Long): Long {
    if (value == Long.MAX_VALUE) throw IOException("Settings revision exhausted")
    return value + 1
}
private fun <T> decode(owner: SettingsOwner, decoder: () -> T): SettingsOutcome<T> = try {
    SettingsOutcome.Success(decoder())
} catch (error: IllegalArgumentException) { SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(owner)) }
catch (error: NoSuchElementException) { SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(owner)) }
class SurfaceSettingsException(val reason: SurfaceSettingsFailure) : IOException("Surface settings unavailable: ${reason::class.simpleName}")
private fun fail(reason: SurfaceSettingsFailure): Nothing = throw SurfaceSettingsException(reason)
private fun Exception.failure(writing: Boolean): SurfaceSettingsFailure {
    // DataStore's file layer wraps serializer IOExceptions while preserving the cause.
    var cause: Throwable? = this
    repeat(16) {
        when (val current = cause) {
            is SurfaceSettingsException -> return current.reason
            is UnsupportedSettingsVersion -> return SurfaceSettingsFailure.UnsupportedVersion(current.version)
            is CorruptionException -> return SurfaceSettingsFailure.CorruptFile
        }
        cause = cause?.cause
    }
    return if (writing) SurfaceSettingsFailure.Write else SurfaceSettingsFailure.Read
}
