package app.livosphere.wallpapers.engine

import app.livosphere.contract.WallpaperMotionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMotionRuntimeTest {
    private val scene = testSceneDefinition()
    private fun motion(level: AuthoredEffectLevel) = EffectiveMotionPolicy().evaluate(
        scene, level, WallpaperMotionMode.NORMAL, systemReduced = false,
        ScenePowerFacts(powerSaver = false, batteryPercent = 80, charging = false),
    )

    @Test fun elapsedTimeChangesIndividualObjectGeometryNotOnlyWholeSceneTransform() {
        val runtime = SceneMotionRuntime(scene)
        val first = runtime.frame(0, motion(AuthoredEffectLevel.FULL))
        val second = runtime.frame(1_000, motion(AuthoredEffectLevel.FULL))
        assertNotEquals(first.objects.single { it.id == "orb" }.x, second.objects.single { it.id == "orb" }.x)
        assertNotEquals(first.objects.single { it.id == "satellite" }.y, second.objects.single { it.id == "satellite" }.y)
        assertEquals(first.objects.single { it.id == "orb" }.y, second.objects.single { it.id == "orb" }.y)
    }

    @Test fun subtleBalancedAndFullProduceDistinctAuthoredEffectSets() {
        val runtime = SceneMotionRuntime(scene)
        val subtle = runtime.frame(700, motion(AuthoredEffectLevel.SUBTLE)).activeEffectIds
        val balanced = runtime.frame(700, motion(AuthoredEffectLevel.BALANCED)).activeEffectIds
        val full = runtime.frame(700, motion(AuthoredEffectLevel.FULL)).activeEffectIds
        assertTrue(subtle != balanced && balanced.containsAll(subtle))
        assertTrue(balanced != full && full.containsAll(balanced))
    }

    @Test fun stopClearsTransientStateAndResumeDerivesCurrentFrameWithoutReplay() {
        val runtime = SceneMotionRuntime(scene)
        val decision = motion(AuthoredEffectLevel.FULL)
        runtime.trigger(SceneTrigger.TAP, 100, decision, interactionsEnabled = true, signalAvailable = true)
        assertEquals(1, runtime.pendingTriggerCount())
        runtime.stop()
        assertEquals(0, runtime.pendingTriggerCount())
        runtime.resume()
        val frame = runtime.frame(50_000, decision)
        assertTrue("tap must not replay", "tap-bounce" !in frame.activeEffectIds)
        assertTrue("ambient motion resumes from elapsed time", "orb-drift" in frame.activeEffectIds)
    }
}
