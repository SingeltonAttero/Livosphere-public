package app.livosphere.widgets.runtime

import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*

class WidgetRenderCoordinatorTest {
    private fun preferences(revision: Long, generation: Long) = WidgetPreferences("clock-a", WidgetSize.S, null, revision, generation)

    @Test fun lateRevisionIsDropped() {
        val coordinator = WidgetRenderCoordinator()
        val stale = coordinator.ticket(101, preferences(1, 1))
        val latest = coordinator.ticket(101, preferences(2, 1))
        assertFalse(coordinator.isCurrent(stale, preferences(2, 1)))
        assertTrue(coordinator.isCurrent(latest, preferences(2, 1)))
    }

    @Test fun deleteAndRestoreInvalidateOldGeneration() {
        val coordinator = WidgetRenderCoordinator()
        val deleted = coordinator.ticket(101, preferences(1, 1))
        coordinator.delete(101)
        assertFalse(coordinator.isCurrent(deleted, preferences(1, 1)))
        coordinator.restore(101, 202, generation = 2)
        val restored = coordinator.ticket(202, preferences(1, 2))
        assertTrue(coordinator.isCurrent(restored, preferences(1, 2)))
    }

    @Test fun commitAndPublicationCannotInterleave() = runBlocking {
        val coordinator = WidgetRenderCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val first = launch(Dispatchers.Default) {
            coordinator.serialize {
                events += "r1-commit"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "r1-publish"
            }
        }
        firstEntered.await()
        val second = launch(Dispatchers.Default) {
            coordinator.serialize { events += "r2-commit"; events += "r2-publish" }
        }
        yield()
        assertEquals(listOf("r1-commit"), events)
        releaseFirst.complete(Unit)
        joinAll(first, second)
        assertEquals(listOf("r1-commit", "r1-publish", "r2-commit", "r2-publish"), events)
    }

    @Test fun narrowLargeWidgetAtTwoHundredPercentUsesCompactDateMetrics() {
        val metrics = ClockGeometryPolicy.metrics(WidgetSize.L, widthDp = 252, fontScale = 2f)
        assertEquals("EEE, d MMM", metrics.datePattern)
        assertTrue(metrics.dateSp <= 8f)
        assertTrue(metrics.timeSp <= 22f)
        assertEquals("EEEE, d MMMM", ClockGeometryPolicy.metrics(WidgetSize.L, 320, 1f).datePattern)

        val small = ClockGeometryPolicy.metrics(WidgetSize.S, widthDp = 110, fontScale = 2f)
        assertEquals(16f, small.timeSp)
    }
}
