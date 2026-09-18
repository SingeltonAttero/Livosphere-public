package app.livosphere.settings

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import app.livosphere.contract.*
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EffectLevelSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun legacyRecordDefaultsFullAndLevelSurvivesReopenWithoutChangingOtherOwners() = runBlocking {
        val old = buildJsonObject { put("interactionsEnabled", false); put("motionMode", "OFF"); put("revision", 7) }
        assertEquals(WallpaperEffectLevel.FULL, old.wallpaper().effectLevel)
        val file = temporary.newFile("settings.json")
        file.outputStream().use { SurfaceSettingsSerializer.writeTo(StoredSurfaceSettings(schemaVersion = 2,
            wallpapers = mapOf("a" to old), widgets = mapOf("42" to WidgetPreferences("clock", WidgetSize.M, null).encode())), it) }
        var job = SupervisorJob()
        fun store() = DataStoreFactory.create(serializer = SurfaceSettingsSerializer, scope = CoroutineScope(job + Dispatchers.IO)) { file }
        var repository = SurfaceSettingsRepository(store())
        val port = repository.wallpapers { true }
        assertTrue(port.setEffectLevel("a", WallpaperEffectLevel.SUBTLE) is SettingsOutcome.Success)
        assertTrue(port.setEffectLevel("b", WallpaperEffectLevel.BALANCED) is SettingsOutcome.Success)
        job.cancelAndJoin(); job = SupervisorJob()
        try {
            repository = SurfaceSettingsRepository(store())
            val a = (repository.wallpapers { true }.observe("a").first() as SettingsOutcome.Success).value
            val b = (repository.wallpapers { true }.observe("b").first() as SettingsOutcome.Success).value
            assertEquals(WallpaperEffectLevel.SUBTLE, a.effectLevel)
            assertEquals(WallpaperMotionMode.OFF, a.motionMode); assertFalse(a.interactionsEnabled); assertEquals(8, a.revision)
            assertEquals(WallpaperEffectLevel.BALANCED, b.effectLevel)
            assertEquals(WidgetPreferences("clock", WidgetSize.M, null),
                (repository.widgets { true }.observe(42).first() as SettingsOutcome.Success).value)
        } finally { job.cancelAndJoin() }
    }
    @Test fun failedWriteLeavesSavedLevelAndMalformedValueFailsClosed() = runBlocking {
        val file = temporary.newFile("settings.json")
        file.outputStream().use { SurfaceSettingsSerializer.writeTo(StoredSurfaceSettings(schemaVersion = 2), it) }
        val job = SupervisorJob()
        val serializer = object : Serializer<StoredSurfaceSettings> by SurfaceSettingsSerializer {
            override suspend fun writeTo(t: StoredSurfaceSettings, output: OutputStream) { throw IOException("controlled") }
        }
        val store = DataStoreFactory.create(serializer = serializer, scope = CoroutineScope(job + Dispatchers.IO)) { file }
        try {
            val port = SurfaceSettingsRepository(store).wallpapers { true }
            assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.Write), port.setEffectLevel("a", WallpaperEffectLevel.SUBTLE))
            assertEquals(WallpaperEffectLevel.FULL, (port.observe("a").first() as SettingsOutcome.Success).value.effectLevel)
        } finally { job.cancelAndJoin() }
        val malformed = WallpaperPreferences().encode().toMutableMap().apply { put("effectLevel", JsonPrimitive("INVALID")) }
        try { JsonObject(malformed).wallpaper(); fail("invalid effect level accepted") } catch (_: IllegalArgumentException) { }
    }
}
