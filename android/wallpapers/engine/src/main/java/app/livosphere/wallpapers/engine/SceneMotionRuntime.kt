package app.livosphere.wallpapers.engine

import kotlin.math.PI
import kotlin.math.sin

data class SceneObjectGeometry(val id: String, val x: Float, val y: Float, val size: Float)
data class SceneFrame(val objects: List<SceneObjectGeometry>, val activeEffectIds: Set<String>)

/** Pure elapsed-time runtime. Frame state is derived, so hidden time cannot accumulate replay work. */
class SceneMotionRuntime(private val definition: SceneDefinition) {
    private val triggers = TriggerController(definition)
    private var stopped = false

    fun trigger(
        trigger: SceneTrigger,
        nowMillis: Long,
        motion: EffectiveMotion,
        interactionsEnabled: Boolean,
        signalAvailable: Boolean,
        magnitude: Float = 1f,
    ): TriggerDispatch {
        if (stopped) return TriggerDispatch.Ignored(TriggerIgnoreReason.DISABLED)
        return triggers.dispatch(trigger, nowMillis, motion.allowedEffectIds, interactionsEnabled, signalAvailable, magnitude)
    }

    fun frame(nowMillis: Long, motion: EffectiveMotion): SceneFrame {
        val active = if (stopped) null else triggers.current(nowMillis, motion.allowedEffectIds)
        val effects = definition.effects.filter {
            it.id in motion.allowedEffectIds && (it.trigger == SceneTrigger.AMBIENT || it.id == active?.effect?.id)
        }
        val geometries = definition.objects.map { objectDefinition ->
            var x = objectDefinition.x
            var y = objectDefinition.y
            var size = objectDefinition.size
            effects.filter { it.objectId == objectDefinition.id }.forEach { effect ->
                val wave = if (effect.trigger == SceneTrigger.AMBIENT) {
                    sin(2.0 * PI * ((nowMillis % effect.durationMillis).toDouble() / effect.durationMillis)).toFloat()
                } else {
                    val elapsed = (nowMillis - checkNotNull(active).startedAtMillis).coerceIn(0, effect.durationMillis)
                    val progress = elapsed.toDouble() / effect.durationMillis
                    (sin(PI * progress) * active.magnitude).toFloat()
                }
                x += effect.amplitudeX * wave
                y += effect.amplitudeY * wave
                if (effect.type == SceneEffectType.TRANSLATE_PULSE) {
                    size *= 1f + 0.20f * wave.coerceAtLeast(0f)
                }
            }
            SceneObjectGeometry(objectDefinition.id, x, y, size)
        }
        return SceneFrame(geometries, effects.mapTo(linkedSetOf()) { it.id })
    }

    fun stop() { stopped = true; triggers.stop() }
    fun resume() { stopped = false }
    fun pendingTriggerCount(): Int = triggers.pendingCount()
}
