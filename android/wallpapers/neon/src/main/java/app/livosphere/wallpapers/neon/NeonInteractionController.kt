package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import kotlin.math.PI
import kotlin.math.sin

data class NeonInteractionFrame(val blink: Float = 0f, val cloudOffset: Float = 0f,
    val lightGroup: Int = -1, val lightBoost: Float = 0f, val cloudOffsetY: Float = 0f)

/** Tilt responses are isolated per Engine and discarded when its visible session ends. */
class NeonInteractionController(private val theme: NeonTheme) {
    private var allowed = false
    private var phase = DayPhase.DAY
    private var level = AuthoredEffectLevel.FULL
    private var width = 0
    private var height = 0
    private var blinkAt: Long? = null
    private var lightAt: Long? = null
    private var lightGroup = -1
    private var shiftX = 0f
    private var shiftY = 0f
    private var sampleAt: Long? = null
    var blinks = 0L; private set
    var pulses = 0L; private set

    fun configure(enabled: Boolean, phase: DayPhase, level: AuthoredEffectLevel, width: Int, height: Int) {
        if (!enabled || !allowed || this.phase != phase || this.level != level || this.width != width || this.height != height) cancel()
        allowed = enabled && width > 0 && height > 0
        this.phase = phase; this.level = level; this.width = width; this.height = height
    }
    fun cancel() {
        blinkAt = null; lightAt = null; lightGroup = -1; shiftX = 0f; shiftY = 0f; sampleAt = null
    }
    fun tilt(value: NeonTiltFrame, now: Long) {
        if (!allowed || !value.x.isFinite() || !value.y.isFinite()) return
        shiftX = value.x.coerceIn(-1f, 1f) * MAX_SHIFT
        shiftY = value.y.coerceIn(-1f, 1f) * .006f
        sampleAt = now
        if (!value.react) return
        blinkAt = now; blinks++
        if (phase == DayPhase.NIGHT) {
            val count = NeonScenePolicy.groupCount(level, theme)
            lightGroup = if (value.x < 0) 0 else count - 1
            lightAt = now; pulses++
        }
    }
    fun frame(now: Long): NeonInteractionFrame {
        if (!allowed) return NeonInteractionFrame()
        val blink = blinkAt?.let { start ->
            when (val t = now - start) {
                in 0L..50L -> t / 50f
                in 51L..120L -> 1f
                in 121L..239L -> 1f - (t - 120) / 120f
                else -> 0f
            }
        } ?: 0f
        val boost = lightAt?.let { start ->
            val t = now - start
            if (t in 0L..1199L) sin(t / 1200.0 * PI).toFloat() * .4f else 0f
        } ?: 0f
        // A failed/stalled sensor cannot leave the sky permanently displaced.
        val gain = sampleAt?.let { (1f - (now - it - 500).coerceAtLeast(0) / 500f).coerceIn(0f, 1f) } ?: 0f
        return NeonInteractionFrame(blink, if (gain == 0f) 0f else shiftX * gain,
            if (boost > 0) lightGroup else -1, boost, if (gain == 0f) 0f else shiftY * gain)
    }
    companion object { const val MAX_SHIFT = .014f }
}
