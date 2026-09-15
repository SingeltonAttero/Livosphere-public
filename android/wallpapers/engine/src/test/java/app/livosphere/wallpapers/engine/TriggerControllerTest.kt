package app.livosphere.wallpapers.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerControllerTest {
    private val scene = testSceneDefinition()
    private val all = scene.effects.mapTo(linkedSetOf()) { it.id }

    @Test fun declaredTapOffsetAndChargingUsePriorityAndCompletionMap() {
        val controller = TriggerController(scene)
        assertTrue(controller.dispatch(SceneTrigger.OFFSET, 0, all, true, true) is TriggerDispatch.Applied)
        val charging = controller.dispatch(SceneTrigger.CHARGING, 10, all, interactionsEnabled = false, signalAvailable = true)
        assertEquals(TriggerDispatch.Applied("charging-glow", replaced = true), charging)
        assertEquals("charging-glow", controller.current(100, all)?.effect?.id)
        assertEquals(null, controller.current(1_000, all))
    }

    @Test fun interactionsToggleDisablesTapAndOffsetButNotCharging() {
        val controller = TriggerController(scene)
        assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.DISABLED),
            controller.dispatch(SceneTrigger.TAP, 0, all, interactionsEnabled = false, signalAvailable = true))
        assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.DISABLED),
            controller.dispatch(SceneTrigger.OFFSET, 0, all, interactionsEnabled = false, signalAvailable = true))
        assertTrue(controller.dispatch(SceneTrigger.CHARGING, 0, all, interactionsEnabled = false, signalAvailable = true)
            is TriggerDispatch.Applied)
    }

    @Test fun unsupportedAndCappedSignalsStayInactive() {
        val controller = TriggerController(scene)
        assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.UNSUPPORTED),
            controller.dispatch(SceneTrigger.OFFSET, 0, all, true, signalAvailable = false))
        assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.CAPPED),
            controller.dispatch(SceneTrigger.OFFSET, 0, setOf("orb-drift"), true, signalAvailable = true))
        assertEquals(0, controller.pendingCount())
    }

    @Test fun rapidTriggersReplaceOneActiveEffectWithoutQueueAndLowerPriorityCannotPreempt() {
        val controller = TriggerController(scene)
        repeat(20) { index ->
            controller.dispatch(SceneTrigger.TAP, index.toLong(), all, true, true)
        }
        assertEquals(1, controller.pendingCount())
        controller.dispatch(SceneTrigger.CHARGING, 30, all, false, true)
        assertEquals(TriggerDispatch.Ignored(TriggerIgnoreReason.LOWER_PRIORITY),
            controller.dispatch(SceneTrigger.OFFSET, 40, all, true, true))
        assertEquals(1, controller.pendingCount())
        assertEquals("charging-glow", controller.current(50, all)?.effect?.id)
    }

    @Test fun stopClearsActiveEffectAndStaleCompletionCannotReplay() {
        val controller = TriggerController(scene)
        controller.dispatch(SceneTrigger.TAP, 0, all, true, true)
        controller.stop()
        assertEquals(null, controller.current(100, all))
        assertEquals(0, controller.pendingCount())
    }
}
