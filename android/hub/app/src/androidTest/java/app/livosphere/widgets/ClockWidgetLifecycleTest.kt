package app.livosphere.widgets

import androidx.datastore.core.DataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.*
import app.livosphere.settings.*
import app.livosphere.widgets.runtime.WidgetRenderCoordinator
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockWidgetLifecycleTest {
    private fun isolated(name: String): Pair<SurfaceSettingsRepository, Job> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "epic11-$name-${System.nanoTime()}.json")
        val job = SupervisorJob()
        return SurfaceSettingsRepository(DataStoreFactory.create(
            serializer = SurfaceSettingsSerializer,
            scope = CoroutineScope(job + Dispatchers.IO),
            migrations = listOf(LegacyWallpaperMigration { null }, SurfaceSettingsV2Migration()),
        ) { file }) to job
    }
    private fun <T> SettingsOutcome<T>.value() = (this as SettingsOutcome.Success).value

    @Test fun threeInstancesStayIndependentAcrossReconfigureDeleteAndRestore() = runBlocking {
        val (repository, job) = isolated("instances")
        try {
            val widgets = repository.widgets { true }
            widgets.configure(101, "clock-a", WidgetSize.S, null).value()
            widgets.configure(202, "clock-b", WidgetSize.M, null).value()
            widgets.configure(303, "clock-c", WidgetSize.L, null).value()
            widgets.configure(202, "clock-b", WidgetSize.L, null).value()
            widgets.delete(101).value()
            widgets.remap(mapOf(303 to 404)).value()
            assertTrue(widgets.observe(101).first() is SettingsOutcome.Failure)
            assertEquals(WidgetSize.L, widgets.observe(202).first().value().size)
            assertEquals("clock-c", widgets.observe(404).first().value().widgetId)
        } finally { job.cancelAndJoin() }
    }

    @Test fun restoredIdsPublishOnlyAfterAtomicRemap() = runBlocking {
        val (repository, job) = isolated("restore")
        try {
            val widgets = repository.widgets { true }
            val old = widgets.configure(101, "clock-a", WidgetSize.S, null).value()
            val coordinator = WidgetRenderCoordinator()
            val oldTicket = coordinator.ticket(101, old)
            val restored = widgets.remap(mapOf(101 to 202)).value().getValue(202)
            coordinator.restore(101, 202, restored.generation)
            assertFalse(coordinator.isCurrent(oldTicket, old))
            assertTrue(coordinator.isCurrent(coordinator.ticket(202, restored), restored))
            assertTrue(widgets.observe(101).first() is SettingsOutcome.Failure)
        } finally { job.cancelAndJoin() }
    }
}
