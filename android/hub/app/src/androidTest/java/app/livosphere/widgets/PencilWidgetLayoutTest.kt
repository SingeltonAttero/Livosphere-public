package app.livosphere.widgets

import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.livosphere.MainActivity
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.ClockStyle
import app.livosphere.contract.ClockViewRole
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.runtime.ClockLayoutAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Representative native layouts cover split, combined and analog role profiles without pack-specific cases. */
@RunWith(AndroidJUnit4::class)
class PencilWidgetLayoutTest {
    @Test fun representativeLayoutsRespectHostBoundsAndClockRoles() {
        val representatives = listOf(
            "moscow-facets-clock" to ClockStyle.DIGITAL,
            "orbital-window-clock" to ClockStyle.ANALOG,
            "star-river-clock" to ClockStyle.DIGITAL,
            "golden-dunes-clock" to ClockStyle.DIGITAL,
        )
        val bounds = listOf(
            WidgetSize.S to (110 to 110), WidgetSize.M to (250 to 110), WidgetSize.L to (250 to 180),
            WidgetSize.S to (168 to 180), WidgetSize.M to (340 to 200), WidgetSize.L to (340 to 300),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val catalog = RegistryWidgetCatalog()
                representatives.forEach { (widgetId, expectedStyle) ->
                    val clock = AuthoredContentCatalog.clockSets.single { it.clockWidget?.componentId?.value == widgetId }.clockWidget!!
                    assertEquals(expectedStyle, clock.style)
                    bounds.forEach { (size, host) -> listOf(1f, 2f).forEach { fontScale ->
                        val configuration = Configuration(activity.resources.configuration).apply {
                            this.fontScale = fontScale
                            setLocale(java.util.Locale.forLanguageTag("ru"))
                        }
                        val renderContext = activity.createConfigurationContext(configuration)
                        val density = renderContext.resources.displayMetrics.density
                        val width = (host.first * density).toInt()
                        val height = (host.second * density).toInt()
                        val parent = FrameLayout(activity)
                        activity.addContentView(parent, ViewGroup.LayoutParams(width, height))
                        try {
                            val options = Bundle().apply {
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, host.first)
                                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, host.second)
                            }
                            val remote = RemoteViews(activity.packageName, catalog.layoutResource(renderContext, widgetId, size))
                            ClockLayoutAdapter.adapt(renderContext, remote, size, options, catalog, widgetId)
                            val view = remote.apply(renderContext, parent)
                            parent.addView(view, FrameLayout.LayoutParams(width, height))
                            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            view.layout(0, 0, width, height)

                            val roles = clock.viewRoles.getValue(size)
                            assertNotEquals(0, catalog.viewId(renderContext, widgetId, size, ClockViewRole.ROOT))
                            if (expectedStyle == ClockStyle.DIGITAL) {
                                assertDigitalTimeRoles(view, catalog, renderContext, widgetId, size, roles, fontScale)
                            } else {
                                assertTrue(ClockViewRole.ANALOG in roles)
                                assertFalse(setOf(ClockViewRole.TIME, ClockViewRole.HOURS, ClockViewRole.MINUTES).any { it in roles })
                                assertTrue(view.findViewById<AnalogClock>(catalog.viewId(renderContext, widgetId, size, ClockViewRole.ANALOG)).width > 0)
                            }
                            assertDateRoles(view, catalog, renderContext, widgetId, size, roles, fontScale)
                            allClockViews(view).forEach { child -> assertInsideHost(view, child, width, height, widgetId, size, fontScale) }
                        } finally {
                            (parent.parent as? ViewGroup)?.removeView(parent)
                        }
                    } }
                }
            }
        }
    }

    private fun assertDigitalTimeRoles(view: View, catalog: RegistryWidgetCatalog, context: android.content.Context, widgetId: String, size: WidgetSize, roles: Map<ClockViewRole, String>, fontScale: Float) {
        val timeRoles = roles.keys.intersect(setOf(ClockViewRole.TIME, ClockViewRole.HOURS, ClockViewRole.MINUTES))
        val combined = timeRoles == setOf(ClockViewRole.TIME)
        val split = timeRoles == setOf(ClockViewRole.HOURS, ClockViewRole.MINUTES)
        assertTrue("$widgetId/$size must declare TIME or HOURS+MINUTES", combined.xor(split))
        assertFalse(ClockViewRole.ANALOG in roles)
        if (combined) {
            assertLiveTextClock(view, catalog.viewId(context, widgetId, size, ClockViewRole.TIME), "23:59", widgetId, size, fontScale)
        } else {
            assertLiveTextClock(view, catalog.viewId(context, widgetId, size, ClockViewRole.HOURS), "23", widgetId, size, fontScale)
            assertLiveTextClock(view, catalog.viewId(context, widgetId, size, ClockViewRole.MINUTES), "59", widgetId, size, fontScale)
        }
    }

    private fun assertDateRoles(view: View, catalog: RegistryWidgetCatalog, context: android.content.Context, widgetId: String, size: WidgetSize, roles: Map<ClockViewRole, String>, fontScale: Float) {
        val dateRoles = setOf(ClockViewRole.DATE, ClockViewRole.DAY, ClockViewRole.MONTH, ClockViewRole.WEEKDAY)
        if (size == WidgetSize.S) {
            assertTrue(dateRoles.none { it in roles })
        } else {
            assertTrue(dateRoles.any { it in roles })
            dateRoles.filter { it in roles }.forEach { role ->
                assertLiveTextClock(view, catalog.viewId(context, widgetId, size, role), sampleFor(role), widgetId, size, fontScale)
            }
        }
    }

    private fun assertLiveTextClock(view: View, id: Int, sample: String, widgetId: String, size: WidgetSize, fontScale: Float) {
        val clock = view.findViewById<TextClock>(id)
        assertNotNull(clock)
        assertTrue("TextClock must render live system time/date", clock.text.isNotBlank())
        assertTrue(clock.width > 0)
        assertTrue("$widgetId/$size fontScale=$fontScale clips '$sample': width=${clock.width}", clock.paint.measureText(sample) <= clock.width)
    }

    private fun sampleFor(role: ClockViewRole) = when (role) {
        ClockViewRole.DATE -> "ПН, 28 СЕНТЯБРЯ"
        ClockViewRole.DAY -> "28"
        ClockViewRole.MONTH -> "СЕНТЯБРЯ"
        ClockViewRole.WEEKDAY -> "ПОНЕДЕЛЬНИК"
        else -> error("Not a date role: $role")
    }

    private fun allClockViews(root: View): List<View> = buildList {
        fun visit(view: View) {
            if (view is TextClock || view is AnalogClock) add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    private fun assertInsideHost(root: View, child: View, width: Int, height: Int, widgetId: String, size: WidgetSize, fontScale: Float) {
        val rect = Rect(0, 0, child.width, child.height)
        (root as ViewGroup).offsetDescendantRectToMyCoords(child, rect)
        assertTrue("$widgetId/$size fontScale=$fontScale clips ${child.javaClass.simpleName}: $rect in ${width}x$height", rect.left >= 0 && rect.top >= 0 && rect.right <= width && rect.bottom <= height)
    }
}
