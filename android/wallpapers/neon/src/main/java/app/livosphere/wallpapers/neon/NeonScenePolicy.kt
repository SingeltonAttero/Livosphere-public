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

/** Authored visible water patches; foreground occlusion is excluded by construction. */
data class NeonReflection(val x: Float, val top: Float, val width: Float, val height: Float)
data class NeonLightGroup(val x: Float, val y: Float, val reflection: NeonReflection? = null)

object NeonScenePolicy {
    fun lightGroups(theme: NeonTheme): List<NeonLightGroup> = when (theme) {
        NeonTheme.SAKURA -> listOf(
            NeonLightGroup(.605f, .366f, NeonReflection(.605f, .421f, .012f, .027f)),
            NeonLightGroup(.745f, .357f, NeonReflection(.745f, .424f, .010f, .025f)),
            NeonLightGroup(.804f, .349f, NeonReflection(.804f, .423f, .009f, .010f)),
            NeonLightGroup(.635f, .525f), NeonLightGroup(.699f, .354f))
        NeonTheme.HARBOR -> listOf(
            NeonLightGroup(.14f, .32f, NeonReflection(.14f, .430f, .013f, .026f)),
            NeonLightGroup(.31f, .295f, NeonReflection(.31f, .427f, .012f, .052f)),
            NeonLightGroup(.645f, .322f, NeonReflection(.645f, .430f, .015f, .050f)),
            NeonLightGroup(.467f, .291f), // reflection occluded by the character
            NeonLightGroup(.792f, .357f, NeonReflection(.792f, .438f, .013f, .045f)))
        NeonTheme.SUNSET -> listOf(
            NeonLightGroup(.72f, .37f, NeonReflection(.72f, .435f, .008f, .020f)),
            NeonLightGroup(.80f, .356f, NeonReflection(.80f, .393f, .008f, .016f)),
            NeonLightGroup(.60f, .407f, NeonReflection(.60f, .440f, .009f, .018f)),
            NeonLightGroup(.93f, .415f), NeonLightGroup(.12f, .42f))
    }

    fun emitterIntensity(phase: DayPhase, elapsed: Long, group: Int, theme: NeonTheme, motion: EffectiveMotion): Float {
        val level = motion.effectiveLevel ?: return 0f
        if (!lightsEnabled(phase)) return 0f
        return if (motion.staticFrame) .7f else lightIntensity(phase, elapsed, group, theme, level)
    }
    fun nightEntryGain(phase: DayPhase, previous: DayPhase, sinceChange: Long, group: Int, staticFrame: Boolean): Float =
        if (phase != DayPhase.NIGHT || previous == DayPhase.NIGHT || staticFrame) 1f
        else ((sinceChange - group * 650L) / 1800f).coerceIn(0f, 1f)

    fun reflectionIntensity(emitterIntensity: Float) = emitterIntensity * .32f

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

    fun lightIntensity(phase: DayPhase, elapsed: Long, group: Int, theme: NeonTheme, level: AuthoredEffectLevel = AuthoredEffectLevel.FULL): Float {
        if (!lightsEnabled(phase)) return 0f
        val period = if (level == AuthoredEffectLevel.SUBTLE) when (theme) {
            NeonTheme.SAKURA -> 24_000L; NeonTheme.HARBOR -> 30_000L; NeonTheme.SUNSET -> 36_000L
        } else when (theme) { NeonTheme.SAKURA -> 14_000L; NeonTheme.HARBOR -> 19_000L; NeonTheme.SUNSET -> 24_000L }
        val pulseDuration = when (theme) { NeonTheme.SAKURA -> 2400; NeonTheme.HARBOR -> 3200; NeonTheme.SUNSET -> 3600 }
        val minimum = when (theme) { NeonTheme.SAKURA -> .55f; NeonTheme.HARBOR -> .6f; NeonTheme.SUNSET -> .65f }
        val local = (elapsed + group * period / groupCount(level, theme)) % period
        // A slow isolated pulse followed by a long hold, never a full-city flash.
        val pulse = if (local < pulseDuration) sin(local.toDouble() / pulseDuration * PI).toFloat() else 0f
        return minimum + (1f - minimum) * pulse
    }

    fun groupCount(level: AuthoredEffectLevel, theme: NeonTheme = NeonTheme.SAKURA) = when (level) {
        AuthoredEffectLevel.SUBTLE -> 1
        AuthoredEffectLevel.BALANCED -> 3
        AuthoredEffectLevel.FULL -> if (theme == NeonTheme.SUNSET) 4 else 5
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
