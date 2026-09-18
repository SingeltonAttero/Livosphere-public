package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.wallpapers.engine.*
import org.junit.Assert.*
import org.junit.Test

class NeonScenePolicyTest {
    @Test fun daylightNeverEmitsAndNightChangesSlowly() {
        NeonTheme.entries.forEach { theme ->
            listOf(DayPhase.MORNING, DayPhase.DAY, DayPhase.EVENING).forEach { phase ->
                (0..60).forEach { t -> assertEquals(0f, NeonScenePolicy.lightIntensity(phase, t * 1000L, 0, theme)) }
            }
            assertTrue(NeonScenePolicy.lightIntensity(DayPhase.NIGHT, 1400, 0, theme) >
                NeonScenePolicy.lightIntensity(DayPhase.NIGHT, 8000, 0, theme))
        }
    }
    @Test fun hiddenTimeDoesNotJumpCloudPositionOnResume() {
        val clock = VisibleSceneClock()
        clock.setRunning(true, 100); assertEquals(1000, clock.value(1100))
        clock.setRunning(false, 1100); assertEquals(1000, clock.value(90000))
        clock.setRunning(true, 90000); assertEquals(1500, clock.value(90500))
    }
    @Test fun reducedOffAndLowBatteryFreezeTheScene() {
        val policy = EffectiveMotionPolicy()
        fun evaluate(mode: WallpaperMotionMode, power: ScenePowerFacts = ScenePowerFacts(false, 80, false)) =
            policy.evaluate(NeonScenePolicy.definition, AuthoredEffectLevel.FULL, mode, false, power)
        assertFalse(evaluate(WallpaperMotionMode.NORMAL).staticFrame)
        assertTrue(evaluate(WallpaperMotionMode.REDUCED).staticFrame)
        assertTrue(evaluate(WallpaperMotionMode.OFF).staticFrame)
        assertTrue(evaluate(WallpaperMotionMode.NORMAL, ScenePowerFacts(false, 15, false)).staticFrame)
    }
}
