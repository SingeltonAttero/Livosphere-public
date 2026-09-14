package app.livosphere.widgets

import android.app.PendingIntent
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.R
import app.livosphere.contract.ClockTarget
import app.livosphere.widgets.runtime.ClockTargetResolver
import app.livosphere.widgets.runtime.ClockWidgetRuntime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClockLaunchRouterTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun tapUsesUniqueExplicitPendingIntentForItsInstance() {
        val first = ClockWidgetRuntime.routerPendingIntent(context, 101)
        val second = ClockWidgetRuntime.routerPendingIntent(context, 202)
        assertNotEquals(first, second)
        assertEquals(PendingIntent.getActivity(context, 101, ClockWidgetRuntime.configurationIntent(context, 101),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).creatorPackage, first.creatorPackage)
    }

    @Test fun removedTargetKeepsClockAndOpensLocalRecovery() {
        val removed = ClockTarget("org.missing.clock", "org.missing.clock.Alarms", "android.intent.action.SHOW_ALARMS")
        assertFalse(ClockTargetResolver(context).resolves(removed))
        val parent = android.widget.FrameLayout(context)
        val view = RemoteViews(context.packageName, R.layout.clock_widget_fixture_s).apply(context, parent)
        assertTrue(view.findViewById<android.view.View>(R.id.clock_widget_time) is TextClock)
        assertEquals(ClockWidgetRuntime.CONFIGURATION_ACTIVITY,
            ClockWidgetRuntime.configurationIntent(context, 101).component?.className)
    }
}
