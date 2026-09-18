package app.livosphere.hub.devices

import app.livosphere.contract.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WidgetInstancesTest {
    @Test fun existingIdsKeepTheirOwnSizeAndConfiguration() = runTest {
        val reader = WidgetInstancesReader(
            ids = { listOf(9 to WidgetSize.L, 3 to WidgetSize.S, 9 to WidgetSize.L) },
            preferences = { id -> if (id == 3) SettingsOutcome.Failure(SurfaceSettingsFailure.Read)
                else SettingsOutcome.Success(WidgetPreferences("harbor-clock", WidgetSize.L, null)) },
            name = { "Harbor" },
        )
        val result = reader.read()
        assertFalse(result.failed)
        assertEquals(listOf(3, 9), result.items.map { it.id })
        assertTrue(result.items.first().needsConfiguration)
        assertEquals(WidgetSize.S, result.items.first().size)
        assertEquals("Harbor", result.items.last().name)
    }
    @Test fun emptyAndUnavailableAreDifferentAndRetryReadsAgain() = runTest {
        var fail = true
        val reader = WidgetInstancesReader(ids = { if (fail) error("unavailable") else emptyList() },
            preferences = { error("No IDs") }, name = { null })
        assertTrue(reader.read().failed)
        fail = false
        assertEquals(WidgetInstances(), reader.read())
    }
    @Test fun cancellationIsNotAnEmptyInventory() = runTest {
        val reader = WidgetInstancesReader(ids = { throw CancellationException() },
            preferences = { error("No IDs") }, name = { null })
        try { reader.read(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
}
