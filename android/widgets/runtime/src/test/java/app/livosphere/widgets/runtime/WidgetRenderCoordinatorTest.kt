package app.livosphere.widgets.runtime

import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import org.junit.Assert.*
import org.junit.Test

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
}
