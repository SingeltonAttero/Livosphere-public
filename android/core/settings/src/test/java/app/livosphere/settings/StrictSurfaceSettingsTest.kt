package app.livosphere.settings

import androidx.datastore.core.DataStoreFactory
import app.livosphere.contract.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StrictSurfaceSettingsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private fun store(file: File) = DataStoreFactory.create(
        serializer = SurfaceSettingsSerializer,
        scope = CoroutineScope(SupervisorJob().also(jobs::add) + Dispatchers.IO),
        migrations = listOf(LegacyWallpaperMigration { null }, SurfaceSettingsV2Migration()),
    ) { file }
    private suspend fun close() { jobs.forEach { it.cancelAndJoin() }; jobs.clear() }
    private suspend fun test(block: suspend () -> Unit) { try { block() } finally { close() } }
    private val envelope = """{"schemaVersion":1,"browsing":null,"wallpapers":{},"widgets":{}}"""
    private fun <T> SettingsOutcome<T>.value() = (this as SettingsOutcome.Success).value

    @Test fun malformedEnvelopeAndDuplicateMembersRetainExactFileOnReadAndWrite() = runBlocking { test {
        val cases = mutableListOf<String>()
        val valid = Json.parseToJsonElement(envelope).jsonObject
        valid.keys.forEach { missing -> cases += JsonObject(valid - missing).toString() }
        cases += envelope.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\"")
        cases += envelope.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0")
        cases += envelope.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")
        cases += envelope.replace("\"wallpapers\":{}", "\"wallpapers\":{\"a\":{},\"a\":{}}")
        cases += envelope.replace("\"widgets\":{}", "\"widgets\":{\"101\":{},\"101\":{}}")
        cases += envelope.replace("\"browsing\":null", "\"browsing\":{\"setId\":\"a\",\"setId\":\"b\"}")
        cases += envelope.replace("\"wallpapers\":{}", "\"wallpapers\":{},\"wallpap\\u0065rs\":{}")
        cases += envelope.replace("\"widgets\":{}", "\"widgets\":{\"101\":{\"clockTarget\":{\"action\":\"a\",\"act\\u0069on\":\"b\"}}}")
        cases += envelope.replace("\"widgets\":{}", "\"widgets\":{\"101\":{},\"\\u003101\":{}}")
        cases.forEachIndexed { index, raw ->
            val file = File(temporary.root, "invalid-$index.json").apply { writeText(raw) }
            val port = SurfaceSettingsRepository(store(file)).wallpapers { true }
            assertEquals("read case $index", SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptFile), port.observe("a").first())
            assertEquals("write case $index", SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptFile), port.setMotion("a", WallpaperMotionMode.OFF))
            assertEquals(raw, file.readText())
        }
    } }

    @Test fun uniqueEscapedKeysAndStringPunctuationRemainValidJson() = runBlocking { test {
        val raw = envelope.replace("\"wallpapers\":{}", "\"wallpap\\u0065rs\":{}")
        val file = File(temporary.root, "valid.json").apply { writeText(raw) }
        val repository = SurfaceSettingsRepository(store(file))
        assertEquals(2, repository.metadata.first().value().schemaVersion)
        val target = ClockTarget("example.clock", "Alarm\\\"{}[]", "clock:action")
        assertEquals(target, repository.widgets { true }.configure(101, "clock-a", WidgetSize.S, target).value().clockTarget)
        close()
        assertEquals(target, SurfaceSettingsRepository(store(file)).widgets { true }.observe(101).first().value().clockTarget)
    } }

    @Test fun corruptBrowsingAndStructuredWidgetsStayRawAcrossUnrelatedWriteAndReopen() = runBlocking { test {
        val invalidBrowsing = Json.parseToJsonElement("""{"setId":"saved-set","surface":"UNKNOWN","revision":1}""")
        val widget = """{"widgetId":"clock-a","size":"M","clockTarget":null,"configurationRevision":2}"""
        val invalid = listOf(
            widget.replace("\"clockTarget\":null", "\"clockTarget\":{\"packageName\":\"clock.app\",\"className\":\"Alarm\"}"),
            widget.replace("\"clockTarget\":null", "\"clockTarget\":{\"packageName\":4,\"className\":\"Alarm\",\"action\":\"show\"}"),
            widget.replace("\"clockTarget\":null", "\"clockTarget\":false"),
            widget.replace("\"size\":\"M\"", "\"size\":1"),
            widget.replace("\"configurationRevision\":2", "\"configurationRevision\":-1"),
            widget.replace("\"configurationRevision\":2", "\"configurationRevision\":\"2\""),
        ).mapIndexed { index, raw -> (101 + index).toString() to Json.parseToJsonElement(raw) }.toMap()
        val file = File(temporary.root, "records.json")
        val data = store(file)
        data.updateData { it.copy(browsing = invalidBrowsing, widgets = invalid) }
        val repository = SurfaceSettingsRepository(data)
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(SettingsOwner.Browsing)), repository.browsing { true }.first())
        assertTrue(repository.setBrowsing("other-set", PreviewSurface.WALLPAPER) { true } is SettingsOutcome.Failure)
        invalid.keys.forEach { id ->
            assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(SettingsOwner.Widget(id.toInt()))), repository.widgets { true }.observe(id.toInt()).first())
            assertTrue(repository.widgets { true }.configure(id.toInt(), "clock-b", WidgetSize.L, null) is SettingsOutcome.Failure)
        }
        repository.wallpapers { true }.setMotion("wallpaper-b", WallpaperMotionMode.OFF).value()
        repository.widgets { true }.configure(999, "clock-b", WidgetSize.L, null).value()
        close()
        val reopened = store(file)
        assertEquals(invalidBrowsing, reopened.data.first().browsing)
        invalid.forEach { (id, raw) -> assertEquals(raw, reopened.data.first().widgets[id]) }
        val fresh = SurfaceSettingsRepository(reopened)
        assertTrue(fresh.browsing { true }.first() is SettingsOutcome.Failure)
        assertEquals(WidgetSize.L, fresh.widgets { true }.observe(999).first().value().size)
        assertEquals(WallpaperMotionMode.OFF, fresh.wallpapers { true }.observe("wallpaper-b").first().value().motionMode)
    } }

    @Test fun browsingSurfaceAndMonotonicRevisionSurviveTwoWritesAndReopen() = runBlocking { test {
        val file = File(temporary.root, "browsing.json")
        val repository = SurfaceSettingsRepository(store(file))
        assertEquals(BrowsingPreferences("set-a", PreviewSurface.WALLPAPER, 1), repository.setBrowsing("set-a", PreviewSurface.WALLPAPER) { true }.value())
        assertEquals(BrowsingPreferences("set-b", PreviewSurface.CLOCK_WIDGET, 2), repository.setBrowsing("set-b", PreviewSurface.CLOCK_WIDGET) { true }.value())
        close()
        assertEquals(BrowsingPreferences("set-b", PreviewSurface.CLOCK_WIDGET, 2), SurfaceSettingsRepository(store(file)).browsing { true }.first().value())
    } }

    @Test fun invalidProgrammerIdsThrowBeforeStorageOrIoTranslation() = runBlocking { test {
        val file = File(temporary.root, "untouched.json")
        val repository = SurfaceSettingsRepository(store(file))
        val invalidCalls: List<suspend () -> Unit> = listOf(
            { repository.wallpapers { true }.observe("BAD ID") },
            { repository.wallpapers { true }.setMotion("BAD ID", WallpaperMotionMode.NORMAL) },
            { repository.wallpapers { true }.setInteractions("", true) },
            { repository.widgets { true }.observe(0) },
            { repository.widgets { true }.configure(-1, "clock-a", WidgetSize.S, null) },
            { repository.widgets { true }.configure(101, "BAD ID", WidgetSize.S, null) },
            { repository.widgets { true }.delete(0) },
            { repository.setBrowsing("BAD ID", PreviewSurface.WALLPAPER) { true } },
        )
        invalidCalls.forEach { call ->
            try { call(); fail("invalid programmer input accepted") } catch (_: IllegalArgumentException) { }
        }
        assertFalse(file.exists())
    } }
}
