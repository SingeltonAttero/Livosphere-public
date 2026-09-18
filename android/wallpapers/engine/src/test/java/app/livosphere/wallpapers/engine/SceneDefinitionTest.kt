package app.livosphere.wallpapers.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SceneDefinitionTest {
    @Test fun tiltTriggerIsParsed() { assertEquals(SceneTrigger.TILT, sceneTrigger("tilt")) }
    @Test fun acceptsDeclaredObjectsTriggersAndThreeStrictAuthoredLevels() {
        val scene = testSceneDefinition()
        assertEquals(3, scene.effectsFor(AuthoredEffectLevel.SUBTLE).size)
        assertEquals(5, scene.effectsFor(AuthoredEffectLevel.BALANCED).size)
        assertEquals(6, scene.effectsFor(AuthoredEffectLevel.FULL).size)
    }

    @Test fun rejectsBrokenObjectAndUndeclaredTriggerReferences() {
        val scene = testSceneDefinition()
        assertThrows(IllegalArgumentException::class.java) {
            scene.copy(effects = scene.effects.mapIndexed { index, effect ->
                if (index == 0) effect.copy(objectId = "missing-object") else effect
            })
        }
        assertThrows(IllegalArgumentException::class.java) {
            scene.copy(declaredTriggers = scene.declaredTriggers - SceneTrigger.CHARGING)
        }
    }

    @Test fun rejectsUnknownEffectVocabularyAtDeclarationBoundary() {
        assertThrows(IllegalArgumentException::class.java) { sceneTrigger("shake-device") }
        assertThrows(IllegalArgumentException::class.java) { authoredEffectLevels("subtle,cinematic") }
        assertThrows(IllegalArgumentException::class.java) { sceneEffectType("particle-system") }
        assertThrows(IllegalArgumentException::class.java) { effectStopRule("queue") }
    }

    @Test fun rejectsMissingOrNonDistinctAuthoredLevels() {
        val scene = testSceneDefinition()
        assertThrows(IllegalArgumentException::class.java) {
            scene.copy(effects = scene.effects.map { it.copy(levels = AuthoredEffectLevel.entries.toSet()) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            scene.copy(effects = scene.effects.filterNot { AuthoredEffectLevel.SUBTLE in it.levels })
        }
    }
}
internal fun testSceneDefinition(): SceneDefinition {
    val all = AuthoredEffectLevel.entries.toSet()
    val balancedFull = setOf(AuthoredEffectLevel.BALANCED, AuthoredEffectLevel.FULL)
    return SceneDefinition(
        objects = listOf(
            SceneObjectDefinition("orb", .28f, .32f, .10f),
            SceneObjectDefinition("satellite", .68f, .48f, .07f),
            SceneObjectDefinition("beacon", .52f, .66f, .08f),
        ),
        effects = listOf(
            SceneEffectDefinition("orb-drift", "orb", SceneTrigger.AMBIENT, 1, 4_000, .035f, 0f, all,
                SceneEffectType.TRANSLATE, EffectStopRule.REPLACE, reducedSafe = true),
            SceneEffectDefinition("tap-bounce", "orb", SceneTrigger.TAP, 30, 450, 0f, -.12f, all,
                SceneEffectType.TRANSLATE, EffectStopRule.REPLACE, reducedSafe = true),
            SceneEffectDefinition("charging-glow", "beacon", SceneTrigger.CHARGING, 40, 900, 0f, -.03f, all,
                SceneEffectType.TRANSLATE_PULSE, EffectStopRule.REPLACE, reducedSafe = true),
            SceneEffectDefinition("satellite-bob", "satellite", SceneTrigger.AMBIENT, 2, 3_000, 0f, .045f, balancedFull,
                SceneEffectType.TRANSLATE, EffectStopRule.REPLACE),
            SceneEffectDefinition("offset-shift", "satellite", SceneTrigger.OFFSET, 20, 600, .14f, 0f, balancedFull,
                SceneEffectType.TRANSLATE, EffectStopRule.REPLACE),
            SceneEffectDefinition("beacon-pulse", "beacon", SceneTrigger.AMBIENT, 3, 2_200, .025f, -.025f,
                setOf(AuthoredEffectLevel.FULL), SceneEffectType.TRANSLATE, EffectStopRule.REPLACE),
        ),
        declaredTriggers = SceneTrigger.entries.toSet(),
    )
}
