package app.livosphere.wallpapers.engine

data class ActiveSceneEffect(
    val effect: SceneEffectDefinition,
    val startedAtMillis: Long,
    val magnitude: Float,
) {
    val endsAtMillis: Long = startedAtMillis + effect.durationMillis
}
enum class TriggerIgnoreReason { UNDECLARED, DISABLED, UNSUPPORTED, CAPPED, LOWER_PRIORITY }
sealed interface TriggerDispatch {
    data class Applied(val effectId: String, val replaced: Boolean) : TriggerDispatch
    data class Ignored(val reason: TriggerIgnoreReason) : TriggerDispatch
}

/** Owns at most one transient effect. Repeated input replaces; it never appends a decorative queue. */
class TriggerController(private val definition: SceneDefinition) {
    private var active: ActiveSceneEffect? = null

    fun dispatch(
        trigger: SceneTrigger,
        nowMillis: Long,
        allowedEffectIds: Set<String>,
        interactionsEnabled: Boolean,
        signalAvailable: Boolean,
        magnitude: Float = 1f,
    ): TriggerDispatch {
        if (trigger !in definition.declaredTriggers) return TriggerDispatch.Ignored(TriggerIgnoreReason.UNDECLARED)
        if (!signalAvailable) return TriggerDispatch.Ignored(TriggerIgnoreReason.UNSUPPORTED)
        if ((trigger == SceneTrigger.TAP || trigger == SceneTrigger.OFFSET) && !interactionsEnabled) {
            return TriggerDispatch.Ignored(TriggerIgnoreReason.DISABLED)
        }
        val candidate = definition.effects
            .filter { it.trigger == trigger && it.id in allowedEffectIds }
            .maxWithOrNull(compareBy<SceneEffectDefinition> { it.priority }.thenBy { it.id })
            ?: return TriggerDispatch.Ignored(TriggerIgnoreReason.CAPPED)
        val previous = current(nowMillis, allowedEffectIds)
        if (previous != null && candidate.priority < previous.effect.priority) {
            return TriggerDispatch.Ignored(TriggerIgnoreReason.LOWER_PRIORITY)
        }
        active = ActiveSceneEffect(candidate, nowMillis, magnitude.coerceIn(-1f, 1f))
        return TriggerDispatch.Applied(candidate.id, replaced = previous != null)
    }

    fun current(nowMillis: Long, allowedEffectIds: Set<String>): ActiveSceneEffect? {
        val value = active
        if (value != null && (nowMillis >= value.endsAtMillis || value.effect.id !in allowedEffectIds)) active = null
        return active
    }

    fun stop() { active = null }
    fun pendingCount(): Int = if (active == null) 0 else 1
}
