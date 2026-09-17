package app.livosphere.wallpapers.engine

import app.livosphere.contract.WallpaperMotionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveMotionPolicyTest {
    private val scene = testSceneDefinition()

    @Test fun fullIsDefaultSafeDecisionAndAuthoredLevelsRemainDistinct() {
        val policy = EffectiveMotionPolicy()
        val full = policy.evaluate(scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.NORMAL, false,
            ScenePowerFacts(false, 80, false))
        val balanced = policy.evaluate(scene, AuthoredEffectLevel.BALANCED, WallpaperMotionMode.NORMAL, false,
            ScenePowerFacts(false, 80, false))
        val subtle = policy.evaluate(scene, AuthoredEffectLevel.SUBTLE, WallpaperMotionMode.NORMAL, false,
            ScenePowerFacts(false, 80, false))
        assertEquals(AuthoredEffectLevel.FULL, full.requestedLevel)
        assertTrue(subtle.allowedEffectIds != balanced.allowedEffectIds &&
            balanced.allowedEffectIds.containsAll(subtle.allowedEffectIds))
        assertTrue(balanced.allowedEffectIds != full.allowedEffectIds &&
            full.allowedEffectIds.containsAll(balanced.allowedEffectIds))
    }

    @Test fun saverLowBatteryAndUnknownPowerApplyReducedSafeCapWithoutErasingFull() {
        listOf(
            ScenePowerFacts(true, 80, false),
            ScenePowerFacts(false, 20, false),
            ScenePowerFacts(null, null, null),
        ).forEach { facts ->
            val decision = EffectiveMotionPolicy().evaluate(
                scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.NORMAL, false, facts)
            assertEquals(AuthoredEffectLevel.FULL, decision.requestedLevel)
            assertEquals(AuthoredEffectLevel.SUBTLE, decision.effectiveLevel)
            assertTrue(decision.powerCapped)
            assertTrue(decision.allowedEffectIds.all { scene.effect(it).reducedSafe })
        }
    }

    @Test fun powerHysteresisExitsOnlyWhenSaverOffAndBatteryAtLeastTwentyFive() {
        val policy = EffectiveMotionPolicy()
        fun at(saver: Boolean?, percent: Int?) = policy.evaluate(
            scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.NORMAL, false,
            ScenePowerFacts(saver, percent, false),
        )
        assertTrue(at(false, 20).powerCapped)
        assertTrue(at(false, 24).powerCapped)
        assertTrue(at(true, 90).powerCapped)
        assertFalse(at(false, 25).powerCapped)
        assertFalse(at(false, 21).powerCapped)
        assertTrue(at(false, 20).powerCapped)
    }

    @Test fun localSystemReducedAndOffAreMonotonicCapsThatTriggersCannotBypass() {
        val policy = EffectiveMotionPolicy()
        val safePower = ScenePowerFacts(false, 80, false)
        val local = policy.evaluate(scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.REDUCED, false, safePower)
        val system = policy.evaluate(scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.NORMAL, true, safePower)
        val off = policy.evaluate(scene, AuthoredEffectLevel.FULL, WallpaperMotionMode.OFF, false, safePower)
        assertEquals(local.allowedEffectIds, system.allowedEffectIds)
        assertTrue(local.allowedEffectIds.all { scene.effect(it).reducedSafe })
        assertTrue(off.staticFrame)
        assertTrue(off.allowedEffectIds.isEmpty())
        assertEquals(null, off.effectiveLevel)
    }
}
