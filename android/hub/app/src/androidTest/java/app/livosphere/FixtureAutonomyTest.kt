package app.livosphere

import android.content.ComponentName
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.hub.wallpaper.AndroidWallpaperProbe
import app.livosphere.hub.wallpaper.UnknownReason
import app.livosphere.hub.wallpaper.WallpaperApplication
import app.livosphere.hub.wallpaper.WallpaperComponent
import app.livosphere.hub.wallpaper.WallpaperFact
import app.livosphere.hub.wallpaper.WallpaperObservation
import app.livosphere.hub.wallpaper.WallpaperPresence
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import app.livosphere.wallpapers.engine.PhasePolicy
import app.livosphere.wallpapers.engine.ScenePowerFacts
import app.livosphere.wallpapers.fixture.FixtureRuntimeTestApi
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureAutonomyTest {
    @Test fun activeAndPreviewEnginesStayIndependentAndRecoverFreshFactsWithoutActivity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val active = ensureFixtureActive(context)
        if (Build.VERSION.SDK_INT >= 35) assertTrue("HOME Engine must not be a preview", !active.preview)
        val settings = WallpaperSettingsRepository(context, FixtureWallpaperService.WALLPAPER_ID)
        val savedMotion = settings.motionMode.first() ?: WallpaperMotionMode.NORMAL
        val savedInteractions = settings.touchReactionsEnabled.first() ?: true
        val expectedPhase = PhasePolicy().select(Clock.systemUTC(), ZoneId.systemDefault()).phase
        assertEquals(expectedPhase, active.phase)
        assertTrue("fresh power facts must be sampled by service", active.powerFacts.batteryPercent != null)
        val componentName = ComponentName(context, FixtureWallpaperService::class.java)
        val component = WallpaperComponent(componentName.packageName, componentName.className)
        val observedAt = Instant.now()
        val observation = WallpaperObservation.capture(
            AndroidWallpaperProbe(context, componentName), component, 29, observedAt, FixtureWallpaperService.WALLPAPER_ID)
        assertEquals(observedAt, observation.observedAt)
        assertEquals(WallpaperFact.Known(WallpaperPresence.AVAILABLE), observation.presence)
        when (val home = observation.home) {
            WallpaperFact.Known(WallpaperApplication.ACTIVE) -> Unit
            is WallpaperFact.Unknown -> assertTrue(home.reason in setOf(
                UnknownReason.LEGACY_API, UnknownReason.NO_COMPONENT_INFO, UnknownReason.PROBE_FAILED))
            else -> throw AssertionError("Fresh platform observation must not claim stale INACTIVE: $home")
        }

        startFixturePreview(context)
        val preview = awaitFixtureEngine(exclude = setOf(active.engineId), preview = true)
        assertTrue("Second Engine must be the system preview", preview.preview)
        try {
            settings.setMotionMode(WallpaperMotionMode.NORMAL)
            settings.setTouchReactionsEnabled(true)
            listOf(active.engineId, preview.engineId).forEach { engineId ->
                FixtureRuntimeTestApi.setPowerFacts(engineId, ScenePowerFacts(false, 80, false))
                FixtureRuntimeTestApi.setSystemReduced(engineId, false)
            }
            FixtureRuntimeTestApi.setLevel(active.engineId, AuthoredEffectLevel.FULL)
            FixtureRuntimeTestApi.setLevel(preview.engineId, AuthoredEffectLevel.SUBTLE)
            val activeAfter = awaitSnapshot(active.engineId) {
                it.requestedLevel == AuthoredEffectLevel.FULL &&
                    it.effectiveLevel == AuthoredEffectLevel.FULL &&
                    it.interactionsEnabled && "tap-bounce" in it.allowedEffectIds
            }
            val previewAfter = awaitSnapshot(preview.engineId) {
                it.requestedLevel == AuthoredEffectLevel.SUBTLE &&
                    it.effectiveLevel == AuthoredEffectLevel.SUBTLE &&
                    it.interactionsEnabled && "tap-bounce" in it.allowedEffectIds
            }
            assertNotEquals(activeAfter.requestedLevel, previewAfter.requestedLevel)
            assertNotEquals(activeAfter.engineId, previewAfter.engineId)

            awaitSnapshot(active.engineId) { it.pendingTriggerCount == 0 }
            FixtureRuntimeTestApi.dispatch(preview.engineId, app.livosphere.wallpapers.engine.SceneTrigger.TAP)
            assertEquals(0, FixtureRuntimeTestApi.snapshot(active.engineId).pendingTriggerCount)
            assertEquals(1, FixtureRuntimeTestApi.snapshot(preview.engineId).pendingTriggerCount)

            settings.setMotionMode(savedMotion)
            settings.setTouchReactionsEnabled(savedInteractions)
            awaitSnapshot(active.engineId) { !it.visible && !it.schedulerActive }
            shell("input keyevent KEYCODE_BACK")
            val resumed = awaitSnapshot(active.engineId) {
                it.visible && it.schedulerActive && it.renderCount > activeAfter.renderCount
            }
            assertEquals(savedMotion, settings.motionMode.first())
            assertEquals(savedInteractions, settings.touchReactionsEnabled.first())
            assertEquals(expectedPhase, resumed.phase)
            assertTrue(resumed.schedulerActive)
            assertTrue(resumed.powerFacts.batteryPercent != null)
        } finally {
            settings.setMotionMode(savedMotion)
            settings.setTouchReactionsEnabled(savedInteractions)
            listOf(active.engineId, preview.engineId).forEach { engineId ->
                if (engineId in FixtureRuntimeTestApi.engineIds()) {
                    FixtureRuntimeTestApi.setPowerFacts(engineId, null)
                    FixtureRuntimeTestApi.setSystemReduced(engineId, null)
                }
            }
            shell("input keyevent KEYCODE_BACK")
        }
    }
}
