package app.livosphere

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import app.livosphere.wallpapers.engine.ScenePowerFacts
import app.livosphere.wallpapers.engine.SceneTrigger
import app.livosphere.wallpapers.engine.TriggerDispatch
import app.livosphere.wallpapers.engine.TriggerIgnoreReason
import app.livosphere.wallpapers.fixture.FixtureRuntimeSnapshot
import app.livosphere.wallpapers.fixture.FixtureRuntimeTestApi
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureSceneRuntimeTest {
    @Test fun installedFixtureProvesMotionLevelsCapsSignalsAndLifecycleStop(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engineId = ensureFixtureActive(context).engineId
        val settings = WallpaperSettingsRepository(context, FixtureWallpaperService.WALLPAPER_ID)
        val originalMotion = settings.motionMode.first() ?: WallpaperMotionMode.NORMAL
        val originalInteractions = settings.touchReactionsEnabled.first() ?: true
        try {
            settings.setMotionMode(WallpaperMotionMode.NORMAL)
            settings.setTouchReactionsEnabled(true)
            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 80, false))
            FixtureRuntimeTestApi.setSystemReduced(engineId, false)

            val authored = linkedMapOf<AuthoredEffectLevel, FixtureRuntimeSnapshot>()
            AuthoredEffectLevel.entries.forEach { level ->
                FixtureRuntimeTestApi.setLevel(engineId, level)
                authored[level] = awaitSnapshot(engineId) { it.requestedLevel == level && !it.staticFrame && it.objects.isNotEmpty() }
            }
            val subtle = authored.getValue(AuthoredEffectLevel.SUBTLE).allowedEffectIds
            val balanced = authored.getValue(AuthoredEffectLevel.BALANCED).allowedEffectIds
            val full = authored.getValue(AuthoredEffectLevel.FULL).allowedEffectIds
            assertTrue(subtle != balanced && balanced.containsAll(subtle))
            assertTrue(balanced != full && full.containsAll(balanced))

            FixtureRuntimeTestApi.setLevel(engineId, AuthoredEffectLevel.FULL)
            val before = awaitSnapshot(engineId) { it.objects.containsKey("orb") }
            val moving = awaitSnapshot(engineId) {
                it.renderCount > before.renderCount && it.objects["orb"]?.x != before.objects["orb"]?.x
            }
            assertNotEquals(before.objects["orb"]?.x, moving.objects["orb"]?.x)

            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 20, false))
            val low = awaitSnapshot(engineId) { it.powerFacts.batteryPercent == 20 && it.effectiveLevel == AuthoredEffectLevel.SUBTLE }
            assertEquals(AuthoredEffectLevel.FULL, low.requestedLevel)
            assertTrue(low.allowedEffectIds.all { it in subtle })
            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 24, false))
            assertEquals(AuthoredEffectLevel.SUBTLE, awaitSnapshot(engineId) { it.powerFacts.batteryPercent == 24 }.effectiveLevel)
            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 25, false))
            assertEquals(AuthoredEffectLevel.FULL, awaitSnapshot(engineId) { it.powerFacts.batteryPercent == 25 }.effectiveLevel)
            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(null, null, null))
            assertEquals(AuthoredEffectLevel.SUBTLE, awaitSnapshot(engineId) { it.powerFacts.batteryPercent == null }.effectiveLevel)

            FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 80, false))
            FixtureRuntimeTestApi.setSystemReduced(engineId, true)
            assertEquals(AuthoredEffectLevel.SUBTLE, awaitSnapshot(engineId) { it.effectiveLevel == AuthoredEffectLevel.SUBTLE }.effectiveLevel)
            FixtureRuntimeTestApi.setSystemReduced(engineId, false)
            settings.setMotionMode(WallpaperMotionMode.OFF)
            assertTrue(awaitSnapshot(engineId) { it.staticFrame }.allowedEffectIds.isEmpty())
            settings.setMotionMode(WallpaperMotionMode.NORMAL)
            awaitSnapshot(engineId) { !it.staticFrame }

            assertTrue(FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.TAP) is TriggerDispatch.Applied)
            Thread.sleep(500)
            assertTrue(FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.OFFSET, magnitude = .5f) is TriggerDispatch.Applied)
            Thread.sleep(650)
            assertTrue(FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.CHARGING) is TriggerDispatch.Applied)
            settings.setTouchReactionsEnabled(false)
            awaitSnapshot(engineId) { !it.interactionsEnabled }
            assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.DISABLED),
                FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.TAP))
            assertTrue(FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.CHARGING) is TriggerDispatch.Applied)
            assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.UNSUPPORTED),
                FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.OFFSET, available = false))
            Thread.sleep(950)
            settings.setTouchReactionsEnabled(true)
            awaitSnapshot(engineId) { it.interactionsEnabled }
            repeat(20) { FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.TAP) }
            assertEquals(1, FixtureRuntimeTestApi.snapshot(engineId).pendingTriggerCount)

            shell("am start -W -a android.settings.SETTINGS")
            val hidden = awaitSnapshot(engineId) { !it.visible && it.pendingTriggerCount == 0 }
            val stoppedAt = hidden.renderCount
            Thread.sleep(150)
            assertEquals(stoppedAt, FixtureRuntimeTestApi.snapshot(engineId).renderCount)
            shell("input keyevent KEYCODE_HOME")
            val visibleAgain = awaitSnapshot(engineId) { it.visible && it.renderCount > stoppedAt }

            FixtureRuntimeTestApi.recreateSurface(engineId)
            val recreated = awaitSnapshot(engineId) {
                it.visible && it.surfaceValid && it.schedulerActive && it.renderCount > visibleAgain.renderCount
            }
            assertEquals(engineId, recreated.engineId)
            assertTrue(
                "tap must resume after surface recreation without visibility callback",
                FixtureRuntimeTestApi.dispatch(engineId, SceneTrigger.TAP) is TriggerDispatch.Applied,
            )
        } finally {
            settings.setMotionMode(originalMotion)
            settings.setTouchReactionsEnabled(originalInteractions)
            FixtureRuntimeTestApi.setPowerFacts(engineId, null)
            FixtureRuntimeTestApi.setSystemReduced(engineId, null)
        }
    }
}

internal fun ensureFixtureActive(context: Context): FixtureRuntimeSnapshot {
    val expected = ComponentName(context, FixtureWallpaperService::class.java)
    @Suppress("DEPRECATION")
    val service = context.packageManager.getServiceInfo(expected, PackageManager.MATCH_DISABLED_COMPONENTS)
    assertTrue("Installed fixture service must be enabled", service.enabled)
    val componentText = "app.livosphere/app.livosphere.wallpapers.fixture.FixtureWallpaperService"
    if (componentText in shell("dumpsys wallpaper") && FixtureRuntimeTestApi.engineIds().isEmpty()) {
        if (Build.VERSION.SDK_INT >= 35) {
            startFixturePreview(context, enablePreview = true)
            val preview = awaitFixtureEngine(preview = true)
            shell("input keyevent KEYCODE_BACK")
            shell("input keyevent KEYCODE_HOME")
            return awaitFixtureEngine(
                exclude = setOf(preview.engineId),
                requireVisible = true,
                preview = false,
            )
        } else {
            applyWallpaper(
                context,
                ComponentName(context.packageName, "app.livosphere.wallpapers.contour.ContourWallpaperService"),
                "app.livosphere/app.livosphere.wallpapers.contour.ContourWallpaperService",
            )
        }
    }
    if (componentText !in shell("dumpsys wallpaper") || FixtureRuntimeTestApi.engineIds().isEmpty()) {
        applyWallpaper(context, expected, componentText)
    }
    return awaitFixtureEngine(requireVisible = true)
}

internal fun startFixturePreview(context: Context, enablePreview: Boolean = true): Pair<Int, Int> {
    return startWallpaperPreview(
        context,
        ComponentName(context, FixtureWallpaperService::class.java),
        enablePreview,
    )
}

private fun applyWallpaper(context: Context, component: ComponentName, componentText: String) {
    val (width, height) = startWallpaperPreview(context, component, enablePreview = false)
    clickSystemText("Set wallpaper")
    if (Build.VERSION.SDK_INT >= 35) {
        Thread.sleep(500)
        shell("input tap ${width / 2} ${(height * .47f).toInt()}")
    } else {
        clickSystemTextIfPresent("Home screen", timeoutMillis = 1_500)
        Thread.sleep(500)
    }
    shell("input keyevent KEYCODE_HOME")
    awaitCondition { componentText in shell("dumpsys wallpaper") }
    Thread.sleep(1_000)
}

private fun clickSystemText(text: String) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    awaitCondition {
        val node = automation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull { it.isClickable }
            ?: return@awaitCondition false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}

private fun clickSystemTextIfPresent(text: String, timeoutMillis: Long): Boolean {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (System.nanoTime() < deadline) {
        val node = automation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text)
            ?.firstOrNull { it.isClickable && it.text?.toString() == text }
        if (node != null) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Thread.sleep(25)
    }
    return false
}

private fun startWallpaperPreview(
    context: Context,
    component: ComponentName,
    enablePreview: Boolean,
): Pair<Int, Int> {
    shell("am force-stop com.android.wallpaper.livepicker")
    context.startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    val size = Regex("Physical size: (\\d+)x(\\d+)").find(shell("wm size"))
        ?: error("Cannot read emulator display size")
    val width = size.groupValues[1].toInt()
    val height = size.groupValues[2].toInt()
    if (Build.VERSION.SDK_INT >= 35) setSystemPreviewEnabled(enablePreview)
    return width to height
}

private fun setSystemPreviewEnabled(enabled: Boolean) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    awaitCondition {
        val node = automation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText("Preview")
            ?.firstOrNull { it.isCheckable }
            ?: return@awaitCondition false
        if (node.isChecked == enabled) return@awaitCondition true
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        false
    }
}

internal fun awaitFixtureEngine(
    exclude: Set<Long> = emptySet(),
    requireVisible: Boolean = false,
    preview: Boolean? = null,
): FixtureRuntimeSnapshot {
    var result: FixtureRuntimeSnapshot? = null
    awaitCondition {
        val id = FixtureRuntimeTestApi.engineIds().firstOrNull { it !in exclude } ?: return@awaitCondition false
        result = FixtureRuntimeTestApi.snapshot(id)
        result!!.surfaceValid && (!requireVisible || result!!.visible) &&
            (preview == null || result!!.preview == preview)
    }
    return checkNotNull(result)
}

internal fun awaitSnapshot(engineId: Long, condition: (FixtureRuntimeSnapshot) -> Boolean): FixtureRuntimeSnapshot {
    var result: FixtureRuntimeSnapshot? = null
    awaitCondition {
        result = FixtureRuntimeTestApi.snapshot(engineId)
        condition(result!!)
    }
    return checkNotNull(result)
}

internal fun shell(command: String): String {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return descriptor.use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
}

internal fun awaitCondition(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!condition()) {
        if (System.nanoTime() >= deadline) error("Timed out waiting for installed fixture Engine")
        Thread.sleep(25)
    }
}
