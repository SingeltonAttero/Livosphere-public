package app.livosphere.widgets

import android.app.PendingIntent
import android.content.Intent
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import app.livosphere.R
import app.livosphere.contract.ClockTarget
import app.livosphere.widgets.runtime.ClockWidgetRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After

@RunWith(AndroidJUnit4::class)
class ClockLaunchRouterTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun resetHooks() = ClockActivityHooks.reset()

    @Test fun tapUsesUniqueExplicitPendingIntentForItsInstance() {
        val first = ClockWidgetRuntime.routerPendingIntent(context, 101)
        val second = ClockWidgetRuntime.routerPendingIntent(context, 202)
        assertNotEquals(first, second)
        assertEquals(PendingIntent.getActivity(context, 101, ClockWidgetRuntime.configurationIntent(context, 101),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).creatorPackage, first.creatorPackage)
    }

    @Test fun removedTargetKeepsClockAndOpensLocalRecovery() {
        val removed = ClockTarget("org.removed.clock", "org.removed.clock.Alarms", "android.intent.action.SHOW_ALARMS")
        ClockActivityHooks.loadPreferences = { _, _ ->
            app.livosphere.contract.WidgetPreferences("isolation-fixture-clock-widget",
                app.livosphere.contract.WidgetSize.S, removed, 1, 1)
        }
        ClockActivityHooks.resolves = { _, _ -> true }
        var launches = 0
        var recoveredId: Int? = null
        ClockActivityHooks.launch = { _, _ -> launches++; throw android.content.ActivityNotFoundException("removed after resolve") }
        ClockActivityHooks.recover = { _, id -> recoveredId = id }
        val intent = ClockWidgetRuntime.routerPendingIntent(context, 909)
        assertNotNull(intent)
        ActivityScenario.launch<ClockLaunchRouterActivity>(
            Intent(context, ClockLaunchRouterActivity::class.java)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 909),
        ).use { scenario ->
            val deadline = System.currentTimeMillis() + 5_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
        assertEquals(1, launches)
        assertEquals(909, recoveredId)
        val parent = android.widget.FrameLayout(context)
        val view = RemoteViews(context.packageName, R.layout.clock_widget_fixture_s).apply(context, parent)
        assertTrue(view.findViewById<android.view.View>(R.id.clock_widget_time) is TextClock)
        assertEquals(ClockWidgetRuntime.CONFIGURATION_ACTIVITY,
            ClockWidgetRuntime.configurationIntent(context, 101).component?.className)
    }
}
