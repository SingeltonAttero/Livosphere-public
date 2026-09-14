package app.livosphere.widgets

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.TextClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.MainActivity
import app.livosphere.R
import app.livosphere.widgets.runtime.ClockProbeKind
import app.livosphere.widgets.runtime.NativeClockProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ClockWidgetProbeTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun digitalAndAnalogProbesInflateWithNativeRemoteViews() {
        withAttachedProbe(ClockProbeKind.DIGITAL, widthDp = 250, heightDp = 110) { scenario, digital ->
            waitUntil(scenario) { digital.isAttachedToWindow && digital.width > 0 && digital.height > 0 }
            scenario.onActivity {
                assertTrue(digital.findViewById<View>(R.id.clock_probe_time) is TextClock)
                assertTrue(digital.findViewById<View>(R.id.clock_probe_date) is TextClock)
            }
        }
        withAttachedProbe(ClockProbeKind.ANALOG, widthDp = 110, heightDp = 110) { scenario, analog ->
            waitUntil(scenario) { analog.isAttachedToWindow && analog.width > 0 && analog.height > 0 }
            scenario.onActivity {
                assertTrue(analog.findViewById<View>(R.id.clock_probe_analog) is AnalogClock)
            }
        }
    }

    @Test
    fun digitalProbeUsesTextClockWithoutPeriodicProviderWork() {
        withAttachedProbe(ClockProbeKind.DIGITAL, widthDp = 250, heightDp = 110) { scenario, root ->
            waitUntil(scenario) {
                root.findViewById<TextClock>(R.id.clock_probe_time).text.isNotEmpty() &&
                    root.findViewById<TextClock>(R.id.clock_probe_date).text.isNotEmpty()
            }
            scenario.onActivity {
                val time = root.findViewById<TextClock>(R.id.clock_probe_time)
                val date = root.findViewById<TextClock>(R.id.clock_probe_date)
                assertEquals("h:mm", time.format12Hour.toString())
                assertEquals("HH:mm", time.format24Hour.toString())
                assertTrue(time.text.isNotEmpty())
                assertTrue(date.text.isNotEmpty())
            }
        }
        assertEquals(0, providerAttribute(R.xml.clock_probe_digital_info, "updatePeriodMillis"))
    }

    @Test
    fun analogProbeRecordsSupportedNativeAttributes() {
        withAttachedProbe(ClockProbeKind.ANALOG, widthDp = 110, heightDp = 110) { scenario, root ->
            waitUntil(scenario) { root.isAttachedToWindow && root.width > 0 && root.height > 0 }
            scenario.onActivity { assertNotNull(root.findViewById<AnalogClock>(R.id.clock_probe_analog)) }
        }

        val attributes = layoutAttributes(R.layout.ls_contour_probe_analog, "AnalogClock")
        assertNotEquals(0, attributes.getValue("dial"))
        assertNotEquals(0, attributes.getValue("hand_hour"))
        assertNotEquals(0, attributes.getValue("hand_minute"))
        assertEquals(0, providerAttribute(R.xml.clock_probe_analog_info, "updatePeriodMillis"))
    }

    private fun withAttachedProbe(
        kind: ClockProbeKind,
        widthDp: Int,
        heightDp: Int,
        block: (ActivityScenario<MainActivity>, View) -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: View
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                val parent = FrameLayout(activity)
                activity.addContentView(
                    parent,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                view = NativeClockProbe.remoteViews(activity, kind).apply(activity, parent)
                parent.addView(
                    view,
                    FrameLayout.LayoutParams((widthDp * density).toInt(), (heightDp * density).toInt()),
                )
            }
            block(scenario, view)
        }
    }

    private fun waitUntil(
        scenario: ActivityScenario<MainActivity>,
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            var satisfied = false
            scenario.onActivity { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        var finalValue = false
        scenario.onActivity { finalValue = condition() }
        assertTrue("Timed out waiting for attached native clock content", finalValue)
    }

    private fun providerAttribute(resourceId: Int, name: String): Int {
        val parser = context.resources.getXml(resourceId)
        parser.use {
            advanceToStartTag(parser, "appwidget-provider")
            return parser.getAttributeIntValue(ANDROID_NAMESPACE, name, -1)
        }
    }

    private fun layoutAttributes(resourceId: Int, element: String): Map<String, Int> {
        val parser = context.resources.getLayout(resourceId)
        parser.use {
            advanceToStartTag(parser, element)
            return listOf("dial", "hand_hour", "hand_minute").associateWith { name ->
                parser.getAttributeResourceValue(ANDROID_NAMESPACE, name, 0)
            }
        }
    }

    private fun advanceToStartTag(parser: XmlPullParser, element: String) {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == element) return
            parser.next()
        }
        error("Missing XML element: $element")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
