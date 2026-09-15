package app.livosphere.wallpapers.engine

import app.livosphere.contract.WallpaperMotionMode

data class ScenePowerFacts(
    val powerSaver: Boolean?,
    val batteryPercent: Int?,
    val charging: Boolean?,
) {
    init { require(batteryPercent == null || batteryPercent in 0..100) }
}
data class EffectiveMotion(
    val requestedLevel: AuthoredEffectLevel,
    val effectiveLevel: AuthoredEffectLevel?,
    val allowedEffectIds: Set<String>,
    val staticFrame: Boolean,
    val reduced: Boolean,
    val powerCapped: Boolean,
)

/** Per-Engine hysteresis owner. Requested authored intensity is never overwritten by a cap. */
class EffectiveMotionPolicy {
    private var powerCapped = true

    fun evaluate(
        definition: SceneDefinition,
        requestedLevel: AuthoredEffectLevel,
        motionMode: WallpaperMotionMode,
        systemReduced: Boolean,
        power: ScenePowerFacts,
    ): EffectiveMotion {
        powerCapped = if (powerCapped) {
            !(power.powerSaver == false && power.batteryPercent != null && power.batteryPercent >= EXIT_PERCENT)
        } else {
            power.powerSaver != false || power.batteryPercent == null || power.batteryPercent <= ENTER_PERCENT
        }
        if (motionMode == WallpaperMotionMode.OFF) {
            return EffectiveMotion(requestedLevel, null, emptySet(), staticFrame = true, reduced = true, powerCapped)
        }
        val reduced = motionMode == WallpaperMotionMode.REDUCED || systemReduced || powerCapped
        val effectiveLevel = if (reduced) AuthoredEffectLevel.SUBTLE else requestedLevel
        val allowed = definition.effectsFor(effectiveLevel)
            .filter { !reduced || it.reducedSafe }
            .mapTo(linkedSetOf()) { it.id }
        return EffectiveMotion(
            requestedLevel = requestedLevel,
            effectiveLevel = effectiveLevel,
            allowedEffectIds = allowed,
            staticFrame = allowed.isEmpty(),
            reduced = reduced,
            powerCapped = powerCapped,
        )
    }

    companion object {
        const val ENTER_PERCENT = 20
        const val EXIT_PERCENT = 25
    }
}
