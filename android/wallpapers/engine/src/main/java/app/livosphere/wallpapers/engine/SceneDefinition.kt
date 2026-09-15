package app.livosphere.wallpapers.engine

private val SCENE_ID = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

enum class AuthoredEffectLevel { SUBTLE, BALANCED, FULL }
enum class SceneTrigger { AMBIENT, TAP, OFFSET, CHARGING }
enum class SceneEffectType { TRANSLATE, TRANSLATE_PULSE }
enum class EffectStopRule { REPLACE }

data class SceneObjectDefinition(
    val id: String,
    val x: Float,
    val y: Float,
    val size: Float,
) {
    init {
        require(SCENE_ID.matches(id)) { "Invalid scene object id: $id" }
        require(x in 0f..1f && y in 0f..1f && size > 0f) { "Invalid geometry for scene object: $id" }
    }
}

data class SceneEffectDefinition(
    val id: String,
    val objectId: String,
    val trigger: SceneTrigger,
    val priority: Int,
    val durationMillis: Long,
    val amplitudeX: Float,
    val amplitudeY: Float,
    val levels: Set<AuthoredEffectLevel>,
    val type: SceneEffectType,
    val stopRule: EffectStopRule,
    val reducedSafe: Boolean = false,
) {
    init {
        require(SCENE_ID.matches(id)) { "Invalid scene effect id: $id" }
        require(SCENE_ID.matches(objectId)) { "Invalid scene object ref: $objectId" }
        require(priority >= 0) { "Effect priority must not be negative: $id" }
        require(durationMillis > 0) { "Effect duration must be positive: $id" }
        require(levels.isNotEmpty()) { "Effect must belong to at least one authored level: $id" }
    }
}

/** Immutable authored contract. Validation happens before a renderer owns any surface resource. */
data class SceneDefinition(
    val objects: List<SceneObjectDefinition>,
    val effects: List<SceneEffectDefinition>,
    val declaredTriggers: Set<SceneTrigger>,
) {
    init {
        require(objects.isNotEmpty()) { "Scene must declare objects" }
        require(objects.map { it.id }.distinct().size == objects.size) { "Duplicate scene object id" }
        require(effects.isNotEmpty()) { "Scene must declare effects" }
        require(effects.map { it.id }.distinct().size == effects.size) { "Duplicate scene effect id" }
        val objectIds = objects.mapTo(mutableSetOf()) { it.id }
        require(effects.all { it.objectId in objectIds }) { "Scene effect contains an undeclared object ref" }
        require(effects.all { it.trigger in declaredTriggers }) { "Scene effect contains an undeclared trigger" }
        val subtle = effectIds(AuthoredEffectLevel.SUBTLE)
        val balanced = effectIds(AuthoredEffectLevel.BALANCED)
        val full = effectIds(AuthoredEffectLevel.FULL)
        require(subtle.isNotEmpty() && subtle != balanced && balanced.containsAll(subtle) &&
            balanced != full && full.containsAll(balanced)) {
            "Authored levels must be distinct strict subsets: Subtle < Balanced < Full"
        }
        require(effects.any { it.trigger == SceneTrigger.AMBIENT }) { "Scene requires authored object motion" }
        require(effects.filter { it.reducedSafe }.all { AuthoredEffectLevel.SUBTLE in it.levels }) {
            "Reduced-safe effects must belong to Subtle"
        }
    }

    fun effectsFor(level: AuthoredEffectLevel): List<SceneEffectDefinition> = effects.filter { level in it.levels }
    fun effect(id: String): SceneEffectDefinition = effects.single { it.id == id }
    private fun effectIds(level: AuthoredEffectLevel) = effectsFor(level).mapTo(mutableSetOf()) { it.id }
}

fun sceneTrigger(value: String): SceneTrigger = when (value) {
    "ambient" -> SceneTrigger.AMBIENT
    "tap" -> SceneTrigger.TAP
    "offset" -> SceneTrigger.OFFSET
    "charging" -> SceneTrigger.CHARGING
    else -> throw IllegalArgumentException("Unknown scene trigger: $value")
}

fun authoredEffectLevels(value: String): Set<AuthoredEffectLevel> = value.split(',').mapTo(linkedSetOf()) {
    when (it.trim()) {
        "subtle" -> AuthoredEffectLevel.SUBTLE
        "balanced" -> AuthoredEffectLevel.BALANCED
        "full" -> AuthoredEffectLevel.FULL
        else -> throw IllegalArgumentException("Unknown authored effect level: ${it.trim()}")
    }
}

fun sceneEffectType(value: String): SceneEffectType = when (value) {
    "translate" -> SceneEffectType.TRANSLATE
    "translate-pulse" -> SceneEffectType.TRANSLATE_PULSE
    else -> throw IllegalArgumentException("Unknown scene effect type: $value")
}

fun effectStopRule(value: String): EffectStopRule = when (value) {
    "replace" -> EffectStopRule.REPLACE
    else -> throw IllegalArgumentException("Unknown effect stop rule: $value")
}
