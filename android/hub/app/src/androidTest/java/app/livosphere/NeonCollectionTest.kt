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
        val sizes = listOf(Triple(WidgetSize.S, 110, 110), Triple(WidgetSize.M, 250, 110), Triple(WidgetSize.L, 250, 180))
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
                val options = Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width) }
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
                } else {
                    val time = view.findViewById<TextClock>(catalog.timeViewId(context))
                    assertNotNull(time)
                    assertTrue("Native TextClock must show real time", time.text.isNotBlank())
                    assertTrue("${item.widgetId}/$size clips time", time.paint.measureText("23:59") <= time.width)
                    assertTrue(time.height <= h)
                }
                val date = view.findViewById<TextClock?>(catalog.dateViewId(context))
                if (size == WidgetSize.S) assertNull(date) else {
                    assertNotNull(date)
                    assertTrue("Native TextClock must show a date", date!!.text.isNotBlank())
                    val sample = if (catalog.isAnalog(item.widgetId)) "30 сент." else "ср, 30 сентября"
                    assertTrue("${item.widgetId}/$size clips date at $fontScale: ${date!!.width}", date.paint.measureText(sample) <= date.width)
                    assertTrue(date.bottom <= h)
                }
                if (fontScale == 1f) {
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val out = File(context.getExternalFilesDir("neon-review"), "${item.widgetId}-${size.name.lowercase()}.png")
                    out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
                (parent.parent as android.view.ViewGroup).removeView(parent)
            }
        }
    }
}
