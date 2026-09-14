package app.livosphere.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.runtime.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockWidgetConfigurationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun resetHooks() = ClockActivityHooks.reset()

    @Test fun pickerStartsCancelledAndReturnsOkOnlyAfterSaveAndUpdate() {
        val old = WidgetPreferences("isolation-fixture-clock-widget", WidgetSize.S, null, 7, 2)
        val drafts = mutableListOf<WidgetPreferences>()
        var attempts = 0
        ClockActivityHooks.ownProvider = { ctx, _ -> ComponentName(ctx, SmallClockWidgetProvider::class.java) }
        ClockActivityHooks.loadPreferences = { _, _ -> old }
        ClockActivityHooks.configure = { _, _, widgetId, size, target ->
            attempts++
            drafts += WidgetPreferences(widgetId, size, target, old.configurationRevision + 1, old.generation)
            if (attempts == 1) ConfigurationCommitResult.ROLLED_BACK else ConfigurationCommitResult.UPDATED
        }
        val intent = Intent(context, ClockWidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 901)
        ActivityScenario.launch<ClockWidgetConfigurationActivity>(intent).use { scenario ->
            compose.onNodeWithText("Изоляция — технические часы").performClick().assertIsSelected()
            scenario.recreate()
            compose.onNodeWithText("Изоляция — технические часы").assertIsSelected()
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil { attempts == 1 }
            compose.onNodeWithText("Не удалось обновить часы. Прежние настройки сохранены; можно повторить или отменить.")
                .assertIsDisplayed()
            compose.onNodeWithText("Повторить").performClick()
            compose.waitUntil { attempts == 2 }
            compose.waitUntil { scenario.state == Lifecycle.State.DESTROYED }
        }
        assertEquals(2, attempts)
        assertEquals(drafts[0], drafts[1])

        ClockActivityHooks.configure = { _, _, _, _, _ -> ConfigurationCommitResult.ROLLBACK_FAILED }
        ActivityScenario.launch<ClockWidgetConfigurationActivity>(
            Intent(context, ClockWidgetConfigurationActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 902),
        ).use {
            compose.onNodeWithText("Сохранить").performClick()
            compose.onNodeWithText(
                "Не удалось обновить часы и восстановить прежние настройки. Откройте настройку экземпляра снова и проверьте выбранные значения.",
            ).assertIsDisplayed()
            compose.onNodeWithText("Отмена").performClick()
        }

        ClockActivityHooks.pin = { _, _, _, _ -> PinRequestResult.UNSUPPORTED }
        ActivityScenario.launch<ClockWidgetPrePinActivity>(
            ClockWidgetRuntime.prePinIntent(context, "contour-debug-clock-widget"),
        ).use {
            compose.onNodeWithText("Добавить виджет").performClick()
            compose.onNodeWithText("Перейти на главный экран").assertIsDisplayed()
            compose.onNodeWithText("Отмена").performClick()
        }
    }

    @Test fun invalidOrForeignIdIsRejected() {
        var pinCalls = 0
        ClockActivityHooks.pin = { _, _, _, _ -> pinCalls++; PinRequestResult.REQUESTED }
        val exported = context.packageManager.getActivityInfo(
            ComponentName(context, ClockWidgetConfigurationActivity::class.java), 0,
        )
        val internal = context.packageManager.getActivityInfo(
            ComponentName(context, ClockWidgetPrePinActivity::class.java), 0,
        )
        assertTrue(exported.exported)
        assertFalse(internal.exported)

        val spoof = Intent(context, ClockWidgetConfigurationActivity::class.java)
            .putExtra("app.livosphere.extra.PRE_PIN", true)
            .putExtra(ClockWidgetRuntime.EXTRA_WIDGET_ID, "contour-debug-clock-widget")
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        ActivityScenario.launch<ClockWidgetConfigurationActivity>(spoof).use { scenario ->
            compose.waitUntil { scenario.state == Lifecycle.State.DESTROYED }
        }
        assertEquals(0, pinCalls)
        assertNull(ClockWidgetRuntime.sizeForProvider("foreign.Provider"))
    }
}
