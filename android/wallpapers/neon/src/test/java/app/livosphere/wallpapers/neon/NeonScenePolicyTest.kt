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

    @Test fun authoredLevelsSelectCloudLayersAndLightGroupsWhilePowerRetainsRequest() {
        val policy = EffectiveMotionPolicy()
        for (level in AuthoredEffectLevel.entries) {
            val motion = policy.evaluate(NeonScenePolicy.definition, level, WallpaperMotionMode.NORMAL, false, ScenePowerFacts(false, 80, false))
            assertEquals(level, motion.effectiveLevel)
            assertEquals(level != AuthoredEffectLevel.SUBTLE, "near-clouds" in motion.allowedEffectIds)
            assertEquals(listOf(1, 3, 5)[level.ordinal], NeonScenePolicy.groupCount(level))
        }
        val capped = policy.evaluate(NeonScenePolicy.definition, AuthoredEffectLevel.FULL, WallpaperMotionMode.NORMAL, false, ScenePowerFacts(true, 80, false))
        assertTrue(capped.staticFrame); assertEquals(AuthoredEffectLevel.FULL, capped.requestedLevel)
        assertEquals(AuthoredEffectLevel.SUBTLE, capped.effectiveLevel)
    }

    @Test fun reflectionsFollowTheirEmitterAndNeverLightUpDayOrOff() {
        val policy = EffectiveMotionPolicy()
        for (theme in NeonTheme.entries) {
            assertTrue(NeonScenePolicy.lightGroups(theme).count { it.reflection != null } >= 3)
            for (mode in WallpaperMotionMode.entries) {
                val motion = policy.evaluate(NeonScenePolicy.definition, AuthoredEffectLevel.FULL, mode, false, ScenePowerFacts(false, 80, false))
                for (phase in DayPhase.entries) for (time in listOf(1400L, 8000L)) {
                    val source = NeonScenePolicy.emitterIntensity(phase, time, 0, theme, motion)
                    val reflection = NeonScenePolicy.reflectionIntensity(source)
                    assertEquals(source * .32f, reflection, .0001f)
                    if (phase != DayPhase.NIGHT || mode == WallpaperMotionMode.OFF) assertEquals(0f, reflection)
                }
                if (mode == WallpaperMotionMode.REDUCED) assertEquals(
                    NeonScenePolicy.emitterIntensity(DayPhase.NIGHT, 1400, 0, theme, motion),
                    NeonScenePolicy.emitterIntensity(DayPhase.NIGHT, 8000, 0, theme, motion))
            }
        }
    }

    @Test fun subtlePausesAreLongerAndNightSourcesEnterInGroups() {
        assertEquals(4, NeonScenePolicy.groupCount(AuthoredEffectLevel.FULL, NeonTheme.SUNSET))
        for (theme in NeonTheme.entries) {
            val minimum = NeonScenePolicy.lightIntensity(DayPhase.NIGHT, 8000, 0, theme, AuthoredEffectLevel.SUBTLE)
            assertEquals(minimum, NeonScenePolicy.lightIntensity(DayPhase.NIGHT, 17000, 0, theme, AuthoredEffectLevel.SUBTLE))
        }
        assertTrue(NeonScenePolicy.nightEntryGain(DayPhase.NIGHT, DayPhase.EVENING, 1000, 0, false) >
            NeonScenePolicy.nightEntryGain(DayPhase.NIGHT, DayPhase.EVENING, 1000, 2, false))
        assertEquals(1f, NeonScenePolicy.nightEntryGain(DayPhase.NIGHT, DayPhase.EVENING, 1000, 4, true))
    }
}
