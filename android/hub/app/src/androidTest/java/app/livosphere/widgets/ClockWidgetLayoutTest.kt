package app.livosphere.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.MainActivity
import app.livosphere.R
import app.livosphere.content.AuthoredContentCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.runtime.ClockLayoutAdapter

@RunWith(AndroidJUnit4::class)
class ClockWidgetLayoutTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun smallShowsTimeAndMediumLargeKeepDateAcrossHostOptions() {
        val options = listOf(
            Triple(WidgetSize.S, 110, 110),
            Triple(WidgetSize.M, 250, 110),
            Triple(WidgetSize.L, 252, 180),
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            options.forEach { (size, width, height) ->
                scenario.onActivity { activity ->
                    val density = activity.resources.displayMetrics.density
                    val largeFont = Configuration(activity.resources.configuration).apply { fontScale = 2f }
                    val renderContext = activity.createConfigurationContext(largeFont)
                    val hostOptions = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                    }
                    val parent = FrameLayout(activity)
                    activity.addContentView(parent, ViewGroup.LayoutParams(-1, -1))
                    val catalog = RegistryWidgetCatalog()
                    val layout = catalog.layoutResource(renderContext, "harbor-clock", size)
                    val remote = android.widget.RemoteViews(activity.packageName, layout)
                    ClockLayoutAdapter.adapt(renderContext, remote, size, hostOptions, catalog)
                    val view = remote.apply(renderContext, parent)
                    parent.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
                    val widthPx = (width * density).toInt()
                    val heightPx = (height * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, widthPx, heightPx)
                    val time = view.findViewById<TextClock>(R.id.clock_widget_time)
                    val contentWidth = widthPx - view.paddingLeft - view.paddingRight
                    val timeWidth = time.paint.measureText("23:59")
                    assertTrue(
                        "time clipped: size=$size bounds=${width}x${height}dp fontScale=${largeFont.fontScale} " +
                            "textSizePx=${time.textSize} textWidthPx=$timeWidth contentWidthPx=$contentWidth",
                        timeWidth <= contentWidth,
                    )
                    val date = view.findViewById<TextClock?>(R.id.clock_widget_date)
                    if (size == WidgetSize.S) assertNull(date) else {
                        assertNotNull(date)
                        val dateWidth = date!!.paint.measureText("Wed, 30 Sep")
                        assertTrue(
                            "date clipped: size=$size bounds=${width}x${height}dp fontScale=${largeFont.fontScale} " +
                                "textSizePx=${date.textSize} textWidthPx=$dateWidth contentWidthPx=$contentWidth",
                            dateWidth <= contentWidth,
                        )
                        assertTrue(date.measuredHeight <= heightPx)
                    }
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
        val catalog = RegistryWidgetCatalog()
        assertEquals(AuthoredContentCatalog.clockSets.map { checkNotNull(it.clockWidget).componentId.value }.toSet(), catalog.items().map { it.widgetId }.toSet())
        assertTrue(catalog.items().map { it.widgetId }.containsAll(setOf("sakura-clock", "harbor-clock", "sunset-clock")))
        assertEquals("sakura-clock", catalog.itemForSet("night-sakura")?.widgetId)
        assertNotEquals(
            catalog.layoutResource(context, "sakura-clock", WidgetSize.M),
            catalog.layoutResource(context, "harbor-clock", WidgetSize.M),
        )
    }

    private companion object { const val NS = "http://schemas.android.com/apk/res/android" }
}
