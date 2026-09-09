package app.livosphere.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.*
import app.livosphere.contract.ClockTarget
import app.livosphere.contract.PreviewSurface
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.SettingsOwner
import app.livosphere.contract.SurfaceSettingsFailure
import app.livosphere.contract.WidgetSize
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real DataStore filesystem transactions, including migration initialization and reopening. */
class WallpaperSettingsIsolationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val a = LEGACY_CONTOUR_WALLPAPER_ID
    private val b = "isolation-fixture-wallpaper"
    private val known: (String) -> Boolean = { it == a || it == b }
    private val jobs = mutableListOf<Job>()
    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob().also(jobs::add) + Dispatchers.IO)
    private fun destination(file: File, source: suspend () -> Preferences? = { null }, serializer: Serializer<StoredSurfaceSettings> = SurfaceSettingsSerializer): DataStore<StoredSurfaceSettings> =
        DataStoreFactory.create(serializer = serializer, scope = scope(), migrations = listOf(LegacyWallpaperMigration(source))) { file }
    private suspend fun closeAll() { jobs.forEach { it.cancelAndJoin() }; jobs.clear() }
    private suspend fun <T> test(body: suspend () -> T): T = try { body() } finally { closeAll() }
    private fun legacy(file: File) = PreferenceDataStoreFactory.create(scope = scope()) { file }
    private fun <T> SettingsOutcome<T>.value(): T = (this as SettingsOutcome.Success).value

    @Test fun legacyAAndPerIdBValuesMigrateAtomicallyAndNeverReplayAfterReopen() = runBlocking { test {
        val sourceFile = File(temporary.root, "legacy.preferences_pb")
        val source = legacy(sourceFile)
        source.edit {
            it[booleanPreferencesKey("touch_reactions_enabled")] = false
            it[stringPreferencesKey("wallpaper_motion_mode")] = "OFF"
            it[booleanPreferencesKey("wallpaper.$b.touch_reactions_enabled")] = true
            it[stringPreferencesKey("wallpaper.$b.wallpaper_motion_mode")] = "REDUCED"
        }
        val sourceBytes = sourceFile.readBytes()
        val file = File(temporary.root, "settings.json")
        val store = destination(file, { source.data.first() })
        val repository = SurfaceSettingsRepository(store)
        val port = repository.wallpapers(known)
        assertEquals(WallpaperMotionMode.OFF, port.observe(a).first().value().motionMode)
        assertFalse(port.observe(a).first().value().interactionsEnabled)
        assertEquals(WallpaperMotionMode.REDUCED, port.observe(b).first().value().motionMode)
        assertTrue(port.observe(b).first().value().interactionsEnabled)
        assertEquals(1, store.data.first().schemaVersion)
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
        port.setMotion(a, WallpaperMotionMode.NORMAL).value()
        closeAll()
        var migrationCalls = 0
        val reopened = SurfaceSettingsRepository(destination(file, { migrationCalls++; error("migration repeated") }))
        assertEquals(WallpaperMotionMode.NORMAL, reopened.wallpapers(known).observe(a).first().value().motionMode)
        assertEquals(0, migrationCalls)
        assertArrayEquals(sourceBytes, sourceFile.readBytes())
    } }

    @Test fun missingSourceIsNewInstallButExistingCorruptSourceBlocksDestinationAndRetainsBytes() = runBlocking { test {
        val fresh = destination(File(temporary.root, "fresh.json"))
        assertEquals(1, fresh.data.first().schemaVersion)
        assertTrue(fresh.data.first().wallpapers.isEmpty())
        val sourceFile = File(temporary.root, "corrupt.preferences_pb").apply { writeBytes(byteArrayOf(0, 1, 2, 3, 4)) }
        val bytes = sourceFile.readBytes()
        val source = legacy(sourceFile)
        val file = File(temporary.root, "blocked.json")
        val port = SurfaceSettingsRepository(destination(file, { source.data.first() })).wallpapers(known)
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptFile), port.observe(a).first())
        assertFalse(file.exists()); assertArrayEquals(bytes, sourceFile.readBytes())
    } }

    @Test fun failureBeforeMigrationCommitDoesNotPublishPartialDataAndRetryCommitsOnce() = runBlocking { test {
        val file = File(temporary.root, "migration.json")
        var failWrite = true; var calls = 0
        val serializer = object : Serializer<StoredSurfaceSettings> by SurfaceSettingsSerializer {
            override suspend fun writeTo(t: StoredSurfaceSettings, output: OutputStream) {
                if (failWrite) { output.write("partial".toByteArray()); throw IOException("controlled before commit") }
                SurfaceSettingsSerializer.writeTo(t, output)
            }
        }
        val store = destination(file, {
            calls++
            mutablePreferencesOf(stringPreferencesKey("wallpaper_motion_mode") to "OFF")
        }, serializer)
        val port = SurfaceSettingsRepository(store).wallpapers(known)
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.Read), port.observe(a).first())
        assertFalse(file.exists())
        failWrite = false
        assertEquals(WallpaperMotionMode.OFF, port.observe(a).first().value().motionMode)
        assertEquals(2, calls); assertEquals(1, store.data.first().schemaVersion)
        assertEquals(WallpaperMotionMode.OFF, port.observe(a).first().value().motionMode)
        assertEquals(2, calls)
    } }

    @Test fun independentWritersShareCurrentTransactionAndAllOwnersSurviveReopen() = runBlocking { test {
        val file = File(temporary.root, "concurrent.json")
        val store = destination(file)
        val repository = SurfaceSettingsRepository(store)
        val wallpapers = repository.wallpapers(known)
        val widgets = repository.widgets { it in setOf("clock-a", "clock-b") }
        val target = ClockTarget("com.example.clock", "com.example.clock.Alarms", "android.intent.action.SHOW_ALARMS")
        val gate = CompletableDeferred<Unit>()
        listOf(
            async(Dispatchers.Default) { gate.await(); repeat(20) { wallpapers.setMotion(a, WallpaperMotionMode.OFF).value() } },
            async(Dispatchers.Default) { gate.await(); repeat(20) { wallpapers.setInteractions(a, false).value() } },
            async(Dispatchers.Default) { gate.await(); wallpapers.setMotion(b, WallpaperMotionMode.REDUCED).value() },
            async(Dispatchers.Default) { gate.await(); repeat(20) { widgets.configure(101, "clock-a", WidgetSize.S, target).value() } },
            async(Dispatchers.Default) { gate.await(); repeat(20) { widgets.configure(202, "clock-b", WidgetSize.L, null).value() } },
            async(Dispatchers.Default) { gate.await(); repository.setBrowsing("set-b", PreviewSurface.CLOCK_WIDGET) { it == "set-b" }.value() },
        ).also { gate.complete(Unit) }.awaitAll()
        closeAll()
        val reopened = SurfaceSettingsRepository(destination(file))
        val aPreferences = reopened.wallpapers(known).observe(a).first().value()
        assertEquals(WallpaperMotionMode.OFF, aPreferences.motionMode); assertFalse(aPreferences.interactionsEnabled)
        assertEquals(40L, aPreferences.revision)
        assertEquals(WallpaperMotionMode.REDUCED, reopened.wallpapers(known).observe(b).first().value().motionMode)
        val rWidgets = reopened.widgets { true }
        assertEquals(target, rWidgets.observe(101).first().value().clockTarget)
        assertEquals(20L, rWidgets.observe(101).first().value().configurationRevision)
        assertEquals(WidgetSize.L, rWidgets.observe(202).first().value().size)
        assertNull(rWidgets.observe(202).first().value().clockTarget)
        assertEquals("set-b", reopened.browsing { true }.first().value()!!.setId)
        rWidgets.delete(101).value()
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(101), null)), rWidgets.observe(101).first())
        assertEquals(20L, rWidgets.observe(202).first().value().configurationRevision)
        val persisted = file.readText()
        listOf("\"active\"", "\"installed\"", "\"host\"", "\"HOME\"", "\"LOCK\"").forEach { assertFalse(persisted.contains(it)) }
    } }

    @Test fun missingReferencesRetainIdsAndOtherInstancesWithoutFallback() = runBlocking { test {
        val store = destination(File(temporary.root, "missing.json"))
        val repository = SurfaceSettingsRepository(store)
        repository.wallpapers { true }.setMotion(a, WallpaperMotionMode.OFF).value()
        repository.widgets { true }.configure(101, "removed-clock", WidgetSize.M, null).value()
        repository.widgets { true }.configure(202, "present-clock", WidgetSize.S, null).value()
        repository.setBrowsing("removed-set", PreviewSurface.WALLPAPER) { true }.value()
        val before = store.data.first()
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Wallpaper(a), a)), repository.wallpapers { false }.observe(a).first())
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(101), "removed-clock")), repository.widgets { it == "present-clock" }.observe(101).first())
        assertTrue(repository.browsing { false }.first() is SettingsOutcome.Failure)
        assertEquals("present-clock", repository.widgets { it == "present-clock" }.observe(202).first().value().widgetId)
        assertTrue(repository.wallpapers { false }.setMotion(a, WallpaperMotionMode.NORMAL) is SettingsOutcome.Failure)
        assertEquals(before, store.data.first())
    } }

    @Test fun corruptRecordsStayRawWhileUnrelatedRecordsRemainReadableAndWritable() = runBlocking { test {
        val file = File(temporary.root, "corrupt-record.json")
        val store = destination(file, { mutablePreferencesOf(stringPreferencesKey("wallpaper_motion_mode") to "TURBO") })
        val repository = SurfaceSettingsRepository(store)
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(SettingsOwner.Wallpaper(a))), repository.wallpapers(known).observe(a).first())
        val raw = store.data.first().wallpapers.getValue(a)
        store.updateData { it.copy(widgets = mapOf("101" to JsonPrimitive("malformed"))) }
        val widgetRaw = store.data.first().widgets.getValue("101")
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.CorruptRecord(SettingsOwner.Widget(101))), repository.widgets { true }.observe(101).first())
        repository.wallpapers(known).setMotion(b, WallpaperMotionMode.REDUCED).value()
        repository.widgets { true }.configure(202, "clock-b", WidgetSize.M, null).value()
        assertTrue(repository.wallpapers(known).setMotion(a, WallpaperMotionMode.NORMAL) is SettingsOutcome.Failure)
        closeAll()
        val reopened = destination(file)
        assertEquals(raw, reopened.data.first().wallpapers.getValue(a))
        assertEquals(widgetRaw, reopened.data.first().widgets.getValue("101"))
        assertEquals(WallpaperMotionMode.REDUCED, SurfaceSettingsRepository(reopened).wallpapers(known).observe(b).first().value().motionMode)
    } }

    @Test fun corruptFileAndUnknownVersionAreNotResetOrOverwritten() = runBlocking { test {
        listOf("{broken" to SurfaceSettingsFailure.CorruptFile,
            "{\"schemaVersion\":999,\"future\":true}" to SurfaceSettingsFailure.UnsupportedVersion(999)).forEachIndexed { index, (raw, reason) ->
            val file = File(temporary.root, "invalid-$index.json").apply { writeText(raw) }
            val port = SurfaceSettingsRepository(destination(file)).wallpapers(known)
            assertEquals(SettingsOutcome.Failure(reason), port.observe(a).first())
            assertEquals(SettingsOutcome.Failure(reason), port.setMotion(a, WallpaperMotionMode.OFF))
            assertEquals(raw, file.readText())
        }
    } }

    @Test fun typedFacadeProjectionsDoNotNotifyAOnBChangesAndOpeningDoesNotWrite() = runBlocking { test {
        val file = File(temporary.root, "facade.json")
        val store = destination(file)
        val repository = SurfaceSettingsRepository(store)
        val first = WallpaperSettingsRepository(repository, a, known)
        val second = first.forWallpaper(b)
        val events = mutableListOf<WallpaperMotionMode?>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { first.motionMode.collect { events += it } }
        try {
            withTimeout(5000) { while (events.isEmpty()) yield() }
            val before = file.readBytes()
            assertTrue(first.touchReactionsEnabled.first()!!)
            assertEquals(WallpaperMotionMode.NORMAL, second.motionMode.first())
            assertArrayEquals(before, file.readBytes())
            second.setMotionMode(WallpaperMotionMode.REDUCED)
            delay(100)
            assertEquals(listOf(WallpaperMotionMode.NORMAL), events)
        } finally { observer.cancelAndJoin() }
    } }
}
