package app.livosphere.widgets.runtime

import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import org.junit.Assert.*
import org.junit.Test

class WidgetConfigurationFlowTest {
    private val old = WidgetPreferences("clock-a", WidgetSize.S, null, 1, 1)
    private val updated = old.copy(size = WidgetSize.M, configurationRevision = 2)

    @Test fun cancelPreservesExistingConfigurationAndCannotReturnOk() {
        val state = WidgetConfigurationFlow.cancel(WidgetConfigurationState(old, updated))
        assertEquals(old, state.existing)
        assertNull(state.draft)
        assertFalse(WidgetConfigurationFlow.canReturnOk(state))
    }

    @Test fun saveRequiresSuccessfulInitialUpdate() {
        val committed = WidgetConfigurationFlow.committed(WidgetConfigurationState(old, updated), updated)
        assertFalse(WidgetConfigurationFlow.canReturnOk(committed))
        assertTrue(WidgetConfigurationFlow.canReturnOk(WidgetConfigurationFlow.updated(committed, true)))
        assertFalse(WidgetConfigurationFlow.canReturnOk(WidgetConfigurationFlow.updated(committed, false)))
    }
}
