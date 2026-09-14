package app.livosphere.settings

import androidx.datastore.core.DataStoreFactory
import app.livosphere.contract.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetSettingsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private fun repository(file: File): SurfaceSettingsRepository = SurfaceSettingsRepository(DataStoreFactory.create(
        serializer = SurfaceSettingsSerializer,
        scope = CoroutineScope(SupervisorJob().also(jobs::add) + Dispatchers.IO),
        migrations = listOf(LegacyWallpaperMigration { null }, SurfaceSettingsV2Migration()),
    ) { file })
    private suspend fun close() { jobs.forEach { it.cancelAndJoin() }; jobs.clear() }
    private fun <T> SettingsOutcome<T>.value() = (this as SettingsOutcome.Success).value

    @Test fun configureDeleteAndReopenRemainPerInstance() = runBlocking {
        val file = File(temporary.root, "widgets.json")
        val widgets = repository(file).widgets { true }
        widgets.configure(101, "clock-a", WidgetSize.S, null).value()
        widgets.configure(202, "clock-b", WidgetSize.L, null).value()
        widgets.configure(101, "clock-a", WidgetSize.M, null).value()
        widgets.delete(202).value()
        close()

        val reopened = repository(file).widgets { true }
        assertEquals(WidgetSize.M, reopened.observe(101).first().value().size)
        assertEquals(2L, reopened.observe(101).first().value().configurationRevision)
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.NeedsConfiguration(SettingsOwner.Widget(202), null)), reopened.observe(202).first())
        close()
    }

    @Test fun restoreRemapsAtomicallyOrLeavesNeedsConfiguration() = runBlocking {
        val file = File(temporary.root, "restore.json")
        val repository = repository(file)
        val widgets = repository.widgets { true }
        widgets.configure(101, "clock-a", WidgetSize.S, null).value()
        widgets.configure(202, "clock-b", WidgetSize.L, null).value()

        val restored = widgets.remap(mapOf(101 to 301, 202 to 302)).value()
        assertEquals(setOf(301, 302), restored.keys)
        assertEquals(1L, restored.getValue(301).generation)
        assertTrue(widgets.observe(101).first() is SettingsOutcome.Failure)
        assertEquals("clock-b", widgets.observe(302).first().value().widgetId)
        val reconfigured = widgets.configure(301, "clock-a", WidgetSize.M, null).value()
        assertEquals(1L, reconfigured.generation)

        val before = repository.metadata.first().value()
        assertTrue(widgets.remap(mapOf(999 to 401)) is SettingsOutcome.Failure)
        assertEquals(before, repository.metadata.first().value())
        assertEquals("clock-a", widgets.observe(301).first().value().widgetId)
        close()
    }
}
