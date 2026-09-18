package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.*
import kotlin.math.PI
import kotlin.math.sin

enum class NeonTheme(val setId: String, val widgetId: String, val label: String, val cloudDirection: Float) {
    SAKURA("night-sakura", "sakura-clock", "Ночная сакура", 1f),
    HARBOR("electric-harbor", "harbor-clock", "Неоновая набережная", -1f),
    SUNSET("last-light", "sunset-clock", "Последний свет", 1f);
    val wallpaperId get() = "$setId-wallpaper"
    val resourcePrefix get() = "ls_" + setId.replace('-', '_') + "_wallpaper"
}

object NeonScenePolicy {
    val definition = SceneDefinition(
        objects = listOf(SceneObjectDefinition("clouds", .5f, .12f, .5f), SceneObjectDefinition("city", .7f, .4f, .1f)),
        effects = listOf(
            effect("far-clouds", "clouds", AuthoredEffectLevel.entries.toSet()),
            effect("city-lights", "city", AuthoredEffectLevel.entries.toSet()),
            effect("near-clouds", "clouds", setOf(AuthoredEffectLevel.BALANCED, AuthoredEffectLevel.FULL)),
            effect("extra-lights", "city", setOf(AuthoredEffectLevel.FULL)),
        ),
        declaredTriggers = setOf(SceneTrigger.AMBIENT),
    )
    private fun effect(id: String, objectId: String, levels: Set<AuthoredEffectLevel>) =
        SceneEffectDefinition(id, objectId, SceneTrigger.AMBIENT, 0, 180_000, .1f, 0f, levels,
            SceneEffectType.TRANSLATE, EffectStopRule.REPLACE)

    /** Day/dawn never inherit emissive pixels from the previous phase. */
    fun lightsEnabled(phase: DayPhase) = phase == DayPhase.NIGHT

    fun lightIntensity(phase: DayPhase, elapsed: Long, group: Int, theme: NeonTheme): Float {
        if (!lightsEnabled(phase)) return 0f
        val period = when (theme) { NeonTheme.SAKURA -> 14_000; NeonTheme.HARBOR -> 19_000; NeonTheme.SUNSET -> 24_000 }.toLong()
        val local = (elapsed + group * period / 5) % period
        // A slow isolated pulse followed by a long hold, never a full-city flash.
        val pulse = if (local < 2800) sin(local.toDouble() / 2800 * PI).toFloat() else 0f
        return .6f + .4f * pulse
    }

    fun groupCount(level: AuthoredEffectLevel) = when (level) {
        AuthoredEffectLevel.SUBTLE -> 1
        AuthoredEffectLevel.BALANCED -> 3
        AuthoredEffectLevel.FULL -> 5
    }
}

/** Per-Engine visible time, frozen across hidden and static intervals. */
class VisibleSceneClock {
    private var accumulated = 0L
    private var startedAt = 0L
    private var running = false
    fun setRunning(value: Boolean, now: Long) {
        if (running == value) return
        if (running) accumulated += (now - startedAt).coerceAtLeast(0)
        startedAt = now
        running = value
    }
    fun value(now: Long): Long = accumulated + if (running) (now - startedAt).coerceAtLeast(0) else 0
}
