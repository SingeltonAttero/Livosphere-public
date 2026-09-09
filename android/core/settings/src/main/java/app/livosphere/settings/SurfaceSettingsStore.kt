package app.livosphere.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.livosphere.contract.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

const val SURFACE_SETTINGS_FILE = "phone-surface-settings.json"
const val SURFACE_SETTINGS_SCHEMA = 1
const val LEGACY_CONTOUR_WALLPAPER_ID = "contour-wallpaper"
const val LEGACY_WALLPAPER_SETTINGS_FILE = "contour-wallpaper-settings"
private val Context.legacyWallpaperSettings by preferencesDataStore(name = LEGACY_WALLPAPER_SETTINGS_FILE)

/** Individual raw records are retained even when one cannot be projected into its typed owner. */
@Serializable
data class StoredSurfaceSettings(
    val schemaVersion: Int = 0,
    val browsing: JsonElement? = null,
    val wallpapers: Map<String, JsonElement> = emptyMap(),
    val widgets: Map<String, JsonElement> = emptyMap(),
)

internal class UnsupportedSettingsVersion(val version: Int) : IOException("Unsupported settings version")
object SurfaceSettingsSerializer : Serializer<StoredSurfaceSettings> {
    override val defaultValue = StoredSurfaceSettings()
    private val json = Json { encodeDefaults = true }
    override suspend fun readFrom(input: InputStream): StoredSurfaceSettings = try {
        val text = input.readBytes().decodeToString(throwOnInvalidSequence = true)
        UniqueJsonKeys.check(text)
        val root = json.parseToJsonElement(text).jsonObject
        val marker = root.getValue("schemaVersion").jsonPrimitive
        require(!marker.isString && Regex("-?(0|[1-9][0-9]*)").matches(marker.content))
        val version = marker.int
        if (version != SURFACE_SETTINGS_SCHEMA) throw UnsupportedSettingsVersion(version)
        require(root.keys == setOf("schemaVersion", "browsing", "wallpapers", "widgets"))
        json.decodeFromString<StoredSurfaceSettings>(text)
    } catch (error: UnsupportedSettingsVersion) { throw error }
    catch (error: java.nio.charset.CharacterCodingException) { throw CorruptionException("Invalid surface settings encoding", error) }
    catch (error: SerializationException) { throw CorruptionException("Invalid surface settings file", error) }
    catch (error: IllegalArgumentException) { throw CorruptionException("Invalid surface settings file", error) }
    catch (error: NoSuchElementException) { throw CorruptionException("Missing surface settings schema", error) }

    override suspend fun writeTo(t: StoredSurfaceSettings, output: OutputStream) {
        if (t.schemaVersion != SURFACE_SETTINGS_SCHEMA) throw UnsupportedSettingsVersion(t.schemaVersion)
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

/** DataStore commits this result, including the version marker, before publishing any consumer data.
 * Legacy source is read-only, so failed initialization can retry and already migrated stores never replay it.
 */
class LegacyWallpaperMigration(private val source: suspend () -> Preferences?) : DataMigration<StoredSurfaceSettings> {
    override suspend fun shouldMigrate(currentData: StoredSurfaceSettings) = currentData.schemaVersion == 0
    override suspend fun migrate(currentData: StoredSurfaceSettings): StoredSurfaceSettings {
        if (currentData.schemaVersion != 0) return currentData
        val legacy = source()?.asMap()?.mapKeys { it.key.name }.orEmpty()
        val ids = buildSet {
            if ("touch_reactions_enabled" in legacy || "wallpaper_motion_mode" in legacy) add(LEGACY_CONTOUR_WALLPAPER_ID)
            legacy.keys.forEach { key ->
                Regex("wallpaper\\.([a-z0-9]+(?:-[a-z0-9]+)*)\\.(touch_reactions_enabled|wallpaper_motion_mode)")
                    .matchEntire(key)?.groupValues?.get(1)?.let(::add)
            }
        }
        val records = ids.associateWith { id ->
            val prefix = if (id == LEGACY_CONTOUR_WALLPAPER_ID) "" else "wallpaper.$id."
            buildJsonObject {
                put("interactionsEnabled", legacy["${prefix}touch_reactions_enabled"].asLegacyJson(JsonPrimitive(true)))
                put("motionMode", legacy["${prefix}wallpaper_motion_mode"].asLegacyJson(JsonPrimitive("NORMAL")))
                put("revision", 0)
            }
        }
        return currentData.copy(schemaVersion = SURFACE_SETTINGS_SCHEMA, wallpapers = currentData.wallpapers + records)
    }
    override suspend fun cleanUp() = Unit
}

private fun Any?.asLegacyJson(default: JsonElement): JsonElement = when (this) {
    null -> default
    is Boolean -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    else -> JsonNull // Preserve an invalid record instead of silently accepting a wrong legacy value type.
}

/** Application-lifetime holder: every service, preview, Hub and future widget uses this same writer.
 * No Activity is required, including after a normal process recreation following first unlock.
 */
object ApplicationSurfaceSettings {
    @Volatile private var instance: SurfaceSettingsRepository? = null
    fun get(context: Context): SurfaceSettingsRepository = instance ?: synchronized(this) {
        instance ?: run {
            val app = context.applicationContext
            SurfaceSettingsRepository(DataStoreFactory.create(
                serializer = SurfaceSettingsSerializer,
                migrations = listOf(LegacyWallpaperMigration { legacySnapshot(app) }),
                produceFile = { app.dataStoreFile(SURFACE_SETTINGS_FILE) },
            )).also { instance = it }
        }
    }

    /** Read-only test/report seam shares the same delegated source handle used by migration. */
    suspend fun legacySnapshot(context: Context): Preferences? {
        val app = context.applicationContext
        val sourceFile = app.dataStoreFile("$LEGACY_WALLPAPER_SETTINGS_FILE.preferences_pb")
        return if (sourceFile.exists()) app.legacyWallpaperSettings.data.first() else null
    }
}

internal fun WallpaperPreferences.encode() = buildJsonObject {
    put("interactionsEnabled", interactionsEnabled); put("motionMode", motionMode.name); put("revision", revision)
}
internal fun BrowsingPreferences.encode() = buildJsonObject {
    put("setId", setId); put("surface", surface.name); put("revision", revision)
}
internal fun WidgetPreferences.encode() = buildJsonObject {
    put("widgetId", widgetId); put("size", size.name); put("configurationRevision", configurationRevision)
    put("clockTarget", clockTarget?.let { target -> buildJsonObject {
        put("packageName", target.packageName); put("className", target.className); put("action", target.action)
    } } ?: JsonNull)
}
internal fun JsonElement.wallpaper(): WallpaperPreferences = jsonObject.let { obj ->
    WallpaperPreferences(obj.getValue("interactionsEnabled").strictBoolean(),
        WallpaperMotionMode.valueOf(obj.getValue("motionMode").strictString()), obj.getValue("revision").strictLong())
}
internal fun JsonElement.browsing(): BrowsingPreferences = jsonObject.let { obj ->
    BrowsingPreferences(obj.getValue("setId").strictString(), PreviewSurface.valueOf(obj.getValue("surface").strictString()),
        obj.getValue("revision").strictLong())
}
internal fun JsonElement.widget(): WidgetPreferences = jsonObject.let { obj ->
    val target = obj.getValue("clockTarget").takeUnless { it == JsonNull }?.jsonObject?.let {
        ClockTarget(it.getValue("packageName").strictString(), it.getValue("className").strictString(), it.getValue("action").strictString())
    }
    WidgetPreferences(obj.getValue("widgetId").strictString(), WidgetSize.valueOf(obj.getValue("size").strictString()),
        target, obj.getValue("configurationRevision").strictLong())
}
private fun JsonElement.strictString() = jsonPrimitive.also { require(it.isString) }.content
private fun JsonElement.strictLong() = jsonPrimitive.also { require(!it.isString) }.long
private fun JsonElement.strictBoolean() = jsonPrimitive.also { require(!it.isString) }.boolean
