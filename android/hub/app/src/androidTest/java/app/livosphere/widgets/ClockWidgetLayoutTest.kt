package app.livosphere.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.MainActivity
import app.livosphere.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ClockWidgetLayoutTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun smallShowsTimeAndMediumLargeKeepDateAcrossHostOptions() {
        val options = listOf(
            Triple(R.layout.clock_widget_fixture_s, 110, 110),
            Triple(R.layout.clock_widget_fixture_m, 250, 110),
            Triple(R.layout.clock_widget_fixture_l, 250, 180),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            options.forEachIndexed { index, (layout, width, height) ->
                scenario.onActivity { activity ->
                    val density = activity.resources.displayMetrics.density
                    val hostOptions = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    }
                    assertEquals(width, hostOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
                    val parent = FrameLayout(activity)
                    activity.addContentView(parent, ViewGroup.LayoutParams(-1, -1))
                    val view = android.widget.RemoteViews(activity.packageName, layout).apply(activity, parent)
                    parent.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
                    assertTrue(view.findViewById<View>(R.id.clock_widget_time) is TextClock)
                    if (index == 0) assertNull(view.findViewById<View?>(R.id.clock_widget_date))
                    else assertTrue(view.findViewById<View>(R.id.clock_widget_date) is TextClock)
                }
            }
        }
    }

    @Test fun providerDescriptorsDeclareFallbackAndApi31CellTargets() {
        val expected = listOf(
            R.xml.clock_widget_small_info to (2 to 2),
            R.xml.clock_widget_medium_info to (4 to 2),
            R.xml.clock_widget_large_info to (4 to 3),
        )
        expected.forEach { (resource, cells) ->
            val parser = context.resources.getXml(resource)
            parser.use {
                while (parser.eventType != XmlPullParser.END_DOCUMENT && parser.name != "appwidget-provider") parser.next()
                assertEquals(0, parser.getAttributeIntValue(NS, "updatePeriodMillis", -1))
                assertTrue(parser.getAttributeResourceValue(NS, "initialLayout", 0) != 0)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    assertEquals(cells.first, parser.getAttributeIntValue(NS, "targetCellWidth", -1))
                    assertEquals(cells.second, parser.getAttributeIntValue(NS, "targetCellHeight", -1))
                }
            }
        }
    }

    private companion object { const val NS = "http://schemas.android.com/apk/res/android" }
}
