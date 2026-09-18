package app.livosphere

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.WidgetSize
import app.livosphere.hub.HubSurface
import app.livosphere.hub.theme.PreviewAssetResolver
import app.livosphere.hub.wallpaper.AndroidWallpaperTarget
import app.livosphere.widgets.RegistryWidgetCatalog
import app.livosphere.widgets.runtime.ClockLayoutAdapter
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NeonCollectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun threeVisibleCollectionsSelectTheirOwnPreviewAndSystemTarget() {
        val sets = AuthoredContentCatalog.sets
        assertEquals(setOf("night-sakura", "electric-harbor", "last-light"), sets.map { it.setId.value }.toSet())
        for ((index, set) in sets.withIndex()) {
            if (index > 0) compose.onNodeWithTag("wallpaper-next").performClick()
            val preview = checkNotNull(PreviewAssetResolver.resolve(context, set, HubSurface.WALLPAPER))
            compose.onNodeWithTag("theme-preview-art-${preview.symbolicName}").assertExists()
            val target = checkNotNull(AndroidWallpaperTarget.resolve(context, set.wallpaper.componentId.value))
            val intent = AndroidWallpaperTarget.directPreviewIntent(target)
            assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, intent.action)
            @Suppress("DEPRECATION") val component = intent.getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT)
            assertEquals(set.wallpaper.serviceClassName, component?.className)
            val info = context.packageManager.getServiceInfo(checkNotNull(component), 0)
            assertTrue(info.enabled)
            assertEquals("android.permission.BIND_WALLPAPER", info.permission)
            for (ref in set.wallpaper.phaseRefs.values) {
                val asset = set.wallpaper.resources.single { it.symbolicName == ref }
                assertNotEquals(0, context.resources.getIdentifier(asset.resourcePath.substringAfter('/').substringBeforeLast('.'), "drawable", context.packageName))
            }
        }
        @Suppress("DEPRECATION")
        val all = context.packageManager.queryIntentServices(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER), 0)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        for (name in listOf("app.livosphere.wallpapers.fixture.FixtureWallpaperService", "app.livosphere.wallpapers.contour.ContourWallpaperService")) {
            assertFalse(context.packageManager.getServiceInfo(ComponentName(context, name), flags).enabled)
        }
        for (name in listOf("DigitalClockProbeProvider", "AnalogClockProbeProvider")) {
            assertFalse(context.packageManager.getReceiverInfo(ComponentName(context, "app.livosphere.widgets.runtime.$name"), flags).enabled)
        }
    }

    @Test fun nineRemoteViewsRenderTimeAndDateAtNormalAndLargeFont() {
        val catalog = RegistryWidgetCatalog(context)
        val sizes = listOf(Triple(WidgetSize.S, 110, 110), Triple(WidgetSize.M, 250, 110), Triple(WidgetSize.L, 250, 180),
            Triple(WidgetSize.S, 168, 180), Triple(WidgetSize.M, 340, 200), Triple(WidgetSize.L, 340, 300))
        for (item in catalog.items()) for ((size, width, height) in sizes) for (fontScale in listOf(1f, 2f)) {
            lateinit var view: View
            lateinit var parent: FrameLayout
            var w = 0
            var h = 0
            compose.runOnUiThread {
                val activity = compose.activity
                val config = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale; setLocale(java.util.Locale.forLanguageTag("ru")) }
                val renderContext = activity.createConfigurationContext(config)
                val density = renderContext.resources.displayMetrics.density
                parent = FrameLayout(activity)
                activity.addContentView(parent, android.view.ViewGroup.LayoutParams(-1, -1))
                val remote = RemoteViews(context.packageName, catalog.layoutResource(context, item.widgetId, size))
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
                }
                ClockLayoutAdapter.adapt(renderContext, remote, size, options, catalog, item.widgetId)
                view = remote.apply(renderContext, parent)
                w = (width * density).toInt(); h = (height * density).toInt()
                parent.addView(view, FrameLayout.LayoutParams(w, h))
            }
            compose.waitForIdle()
            compose.runOnUiThread {
                view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, w, h)
                if (catalog.isAnalog(item.widgetId)) {
                    val analogId = context.resources.getIdentifier("clock_widget_analog", "id", context.packageName)
                    val clock = view.findViewById<AnalogClock>(analogId)
                    assertNotNull(clock); assertTrue(clock.width > 0); assertTrue(clock.height <= h)
                    // Large artwork must fill the available face instead of staying at a 110dp intrinsic size.
                    if (size == WidgetSize.L && width == 340) assertTrue(clock.height > 170 * context.resources.displayMetrics.density)
                } else {
                    val time = view.findViewById<TextClock>(catalog.timeViewId(context))
                    assertNotNull(time)
                    assertTrue("Native TextClock must show real time", time.text.isNotBlank())
                    assertTrue("${item.widgetId}/$size clips time", time.paint.measureText("23:59") <= time.width)
                    assertTrue(time.height <= h)
                }
                val date = view.findViewById<TextClock?>(catalog.dateViewId(context))
                val weekdayId = context.resources.getIdentifier("clock_widget_weekday", "id", context.packageName)
                val weekday = view.findViewById<TextClock?>(weekdayId)
                if (size == WidgetSize.S) assertNull(date) else {
                    assertNotNull(date)
                    assertTrue("Native TextClock must show a date", date!!.text.isNotBlank())
                    val sample = "30 сентября"
                    assertEquals("d MMMM", date.format24Hour.toString())
                    assertTrue("${item.widgetId}/$size clips date at $fontScale: ${date!!.width}", date.paint.measureText(sample) <= date.width)
                    assertNotNull(weekday)
                    assertEquals("EEEE", weekday!!.format24Hour.toString())
                    assertTrue("${item.widgetId}/$size clips weekday", weekday.paint.measureText("понедельник") <= weekday.width)
                    for (text in listOf(date, weekday)) {
                        val rect = android.graphics.Rect(0, 0, text.width, text.height)
                        (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(text, rect)
                        assertTrue("${item.widgetId}/$size clips text vertically: $rect in $w x $h", rect.top >= 0 && rect.bottom <= h)
                    }
                }
                if (size == WidgetSize.S) assertNull(weekday)
                if (fontScale == 1f) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val suffix = if (width in listOf(168, 340)) "-host" else ""
                    val out = File(context.getExternalFilesDir("neon-review"), "${item.widgetId}-${size.name.lowercase()}$suffix.png")
                    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
                (parent.parent as android.view.ViewGroup).removeView(parent)
            }
        }
    }
}
