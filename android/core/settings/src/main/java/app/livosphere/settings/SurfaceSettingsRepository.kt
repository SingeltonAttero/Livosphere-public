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
            override suspend fun setEffectLevel(wallpaperId: String, level: WallpaperEffectLevel) = updateWallpaper(wallpaperId, available) {
                it.copy(effectLevel = level, revision = nextRevision(it.revision))
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
                val generation = previous?.value?.generation ?: 0L
                val updated = WidgetPreferences(widgetId, size, clockTarget, revision, generation)
                current.copy(widgets = current.widgets + (appWidgetId.toString() to updated.encode())) to updated
            }
        }
        override suspend fun delete(appWidgetId: Int): SettingsOutcome<Unit> {
            SettingsOwner.Widget(appWidgetId)
            return transaction { current -> current.copy(widgets = current.widgets - appWidgetId.toString()) to Unit }
        }
        override suspend fun remap(mapping: Map<Int, Int>): SettingsOutcome<Map<Int, WidgetPreferences>> {
            require(mapping.isNotEmpty() && mapping.keys.all { it > 0 } && mapping.values.all { it > 0 })
            require(mapping.keys.size == mapping.values.toSet().size)
            return transaction { current ->
                val decoded = mapping.mapValues { (oldId, newId) ->
                    val owner = SettingsOwner.Widget(oldId)
                    val raw = current.widgets[oldId.toString()]
                        ?: fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(newId), null))
                    when (val value = decode(owner) { raw.widget() }) {
                        is SettingsOutcome.Success -> value.value
                        is SettingsOutcome.Failure -> fail(value.reason)
                    }
                }
                val unaffected = current.widgets - mapping.keys.map(Int::toString).toSet()
                if (mapping.values.any { it.toString() in unaffected }) fail(SurfaceSettingsFailure.Write)
                val remapped = decoded.map { (oldId, value) ->
                    val newId = mapping.getValue(oldId)
                    newId to value.copy(generation = nextRevision(value.generation))
                }.toMap()
                current.copy(widgets = unaffected + remapped.mapKeys { it.key.toString() }.mapValues { it.value.encode() }) to remapped
            }
        }
        override suspend fun restoreAfterFailedUpdate(
            appWidgetId: Int,
            failedRevision: Long,
            previous: WidgetPreferences?,
        ): SettingsOutcome<Unit> {
            val owner = SettingsOwner.Widget(appWidgetId)
            require(failedRevision > 0)
            previous?.let { require(it.configurationRevision < failedRevision) }
            return transaction { current ->
                val raw = current.widgets[appWidgetId.toString()] ?: fail(SurfaceSettingsFailure.NeedsConfiguration(owner, null))
                val failed = when (val value = decode(owner) { raw.widget() }) {
                    is SettingsOutcome.Success -> value.value
                    is SettingsOutcome.Failure -> fail(value.reason)
                }
                if (failed.configurationRevision != failedRevision) fail(SurfaceSettingsFailure.Write)
                val restored = if (previous == null) current.widgets - appWidgetId.toString()
                    else current.widgets + (appWidgetId.toString() to previous.encode())
                current.copy(widgets = restored) to Unit
            }
        }
    }

    fun pendingPins(available: (String) -> Boolean): PendingPinRepository = object : PendingPinRepository {
        override fun observe(token: String): Flow<SettingsOutcome<PendingWidgetPin?>> {
            requirePinToken(token)
            return observeRecord { current ->
                val raw = current.pendingPins[token] ?: return@observeRecord SettingsOutcome.Success(null)
                decode(SettingsOwner.PendingPin(token)) { raw.pendingPin() }
            }
        }

        override suspend fun create(pin: PendingWidgetPin): SettingsOutcome<PendingWidgetPin> = transaction { current ->
            if (!available(pin.widgetId)) fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Browsing, pin.widgetId))
            if (pin.status != PendingPinStatus.PENDING || pin.token in current.pendingPins) fail(SurfaceSettingsFailure.Write)
            current.copy(pendingPins = current.pendingPins + (pin.token to pin.encode())) to pin
        }

        override suspend fun consume(token: String, providerClassName: String, appWidgetId: Int, nowEpochMillis: Long): SettingsOutcome<PendingPinConsumeResult> {
            requirePinToken(token); require(appWidgetId > 0 && nowEpochMillis >= 0)
            return transaction { current ->
                val raw = current.pendingPins[token] ?: fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(appWidgetId), null))
                val pin = when (val value = decode(SettingsOwner.PendingPin(token)) { raw.pendingPin() }) {
                    is SettingsOutcome.Success -> value.value
                    is SettingsOutcome.Failure -> fail(value.reason)
                }
                if (pin.providerClassName != providerClassName || nowEpochMillis - pin.createdAtEpochMillis > PIN_VALID_MILLIS)
                    fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(appWidgetId), pin.widgetId))
                if (pin.status != PendingPinStatus.PENDING) {
                    if (pin.status == PendingPinStatus.CONSUMED && pin.boundAppWidgetId == appWidgetId)
                        return@transaction current to PendingPinConsumeResult.Replay(appWidgetId)
                    fail(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(appWidgetId), pin.widgetId))
                }
                if (appWidgetId.toString() in current.widgets) fail(SurfaceSettingsFailure.Write)
                val preferences = WidgetPreferences(pin.widgetId, pin.size, pin.clockTarget, configurationRevision = 1, generation = 1)
                val consumed = pin.copy(status = PendingPinStatus.CONSUMED, boundAppWidgetId = appWidgetId, resolvedAtEpochMillis = nowEpochMillis)
                current.copy(
                    widgets = current.widgets + (appWidgetId.toString() to preferences.encode()),
                    pendingPins = current.pendingPins + (token to consumed.encode()),
                ) to PendingPinConsumeResult.Consumed(preferences)
            }
        }

        override suspend fun cleanup(nowEpochMillis: Long): SettingsOutcome<Unit> {
            require(nowEpochMillis >= 0)
            return transaction { current ->
                val retained = current.pendingPins.mapNotNull { (token, raw) ->
                    val pin = when (val value = decode(SettingsOwner.PendingPin(token)) { raw.pendingPin() }) {
                        is SettingsOutcome.Success -> value.value
                        is SettingsOutcome.Failure -> fail(value.reason)
                    }
                    when {
                        pin.status == PendingPinStatus.PENDING && nowEpochMillis - pin.createdAtEpochMillis > PIN_VALID_MILLIS ->
                            token to pin.copy(status = PendingPinStatus.EXPIRED, boundAppWidgetId = null, resolvedAtEpochMillis = nowEpochMillis)
                        pin.status != PendingPinStatus.PENDING && nowEpochMillis - requireNotNull(pin.resolvedAtEpochMillis) > PIN_TOMBSTONE_MILLIS -> null
                        else -> token to pin
                    }
                }.toMap()
                current.copy(pendingPins = retained.mapValues { it.value.encode() }) to Unit
            }
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

const val PIN_VALID_MILLIS = 24L * 60 * 60 * 1000
const val PIN_TOMBSTONE_MILLIS = 7L * 24 * 60 * 60 * 1000
private fun requirePinToken(token: String) { require(Regex("[A-Za-z0-9_-]{16,128}").matches(token)) }

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
