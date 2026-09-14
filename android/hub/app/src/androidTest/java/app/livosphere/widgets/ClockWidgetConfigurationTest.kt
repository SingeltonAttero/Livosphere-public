package app.livosphere.widgets

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.runtime.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockWidgetConfigurationTest {
    @Test fun pickerStartsCancelledAndReturnsOkOnlyAfterSaveAndUpdate() {
        val old = WidgetPreferences("clock-a", WidgetSize.S, null, 1, 1)
        val draft = old.copy(size = WidgetSize.M, configurationRevision = 2)
        val initial = WidgetConfigurationState(old, draft)
        assertFalse(WidgetConfigurationFlow.canReturnOk(initial))
        assertFalse(WidgetConfigurationFlow.canReturnOk(WidgetConfigurationFlow.cancel(initial)))
        val saved = WidgetConfigurationFlow.committed(initial, draft)
        assertFalse(WidgetConfigurationFlow.canReturnOk(saved))
        assertTrue(WidgetConfigurationFlow.canReturnOk(WidgetConfigurationFlow.updated(saved, true)))
    }

    @Test fun invalidOrForeignIdIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNull(ClockWidgetRuntime.ownProvider(context, AppWidgetManager.INVALID_APPWIDGET_ID))
        assertNull(ClockWidgetRuntime.sizeForProvider("foreign.Provider"))
        assertEquals(WidgetSize.S, ClockWidgetRuntime.sizeForProvider(SmallClockWidgetProvider::class.java.name))
    }
}
