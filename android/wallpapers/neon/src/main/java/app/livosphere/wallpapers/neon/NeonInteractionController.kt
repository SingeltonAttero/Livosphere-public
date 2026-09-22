package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

data class NeonInteractionFrame(val blink: Float = 0f, val cloudOffset: Float = 0f,
    val lightGroup: Int = -1, val lightBoost: Float = 0f, val cloudOffsetY: Float = 0f)

/** Ambient blinking and tilt responses are isolated per visible Engine. */
class NeonInteractionController(private val theme: NeonTheme) {
    private var allowed = false
    private var tiltEnabled = true
    private var phase = DayPhase.DAY
    private var level = AuthoredEffectLevel.FULL
    private var width = 0
    private var height = 0
    private val blink = NeonBlinkController(theme.ordinal)
    private var lightAt: Long? = null
    private var lightGroup = -1
    private var shiftX = 0f
    private var shiftY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var cloudAt: Long? = null
    private var sampleAt: Long? = null
    val blinks get() = blink.blinks
    var pulses = 0L; private set

    fun configure(enabled: Boolean, phase: DayPhase, level: AuthoredEffectLevel, width: Int, height: Int,
        now: Long = 0L, tiltEnabled: Boolean = true) {
        val nextAllowed = enabled && width > 0 && height > 0
        if (allowed && (!nextAllowed || this.phase != phase || this.level != level || this.width != width || this.height != height)) cancel()
        if (!tiltEnabled) clearTilt()
        allowed = nextAllowed
        this.tiltEnabled = tiltEnabled
        this.phase = phase; this.level = level; this.width = width; this.height = height
        blink.configure(allowed, now)
    }
    fun cancel() {
        allowed = false
        blink.cancel()
        clearTilt()
    }
    private fun clearTilt() {
        lightAt = null; lightGroup = -1; shiftX = 0f; shiftY = 0f
        targetX = 0f; targetY = 0f; sampleAt = null; cloudAt = null
    }
    fun onUnlocked(now: Long) { blink.onUnlocked(now) }

    fun tilt(value: NeonTiltFrame, now: Long) {
        if (!allowed || !tiltEnabled || !value.x.isFinite() || !value.y.isFinite() || now < (cloudAt ?: now)) return
        advanceClouds(now)
        targetX = value.x.coerceIn(-1f, 1f) * MAX_SHIFT
        targetY = value.y.coerceIn(-1f, 1f) * MAX_SHIFT_Y
        sampleAt = now
        if (!value.react) return
        blink.onTilt(now)
        if (phase == DayPhase.NIGHT) {
            val count = NeonScenePolicy.groupCount(level, theme)
            lightGroup = if (value.x < 0) 0 else count - 1
            lightAt = now; pulses++
        }
    }
    fun frame(now: Long): NeonInteractionFrame {
        if (!allowed) return NeonInteractionFrame()
        advanceClouds(now)
        val boost = lightAt?.let { start ->
            val t = now - start
            if (t in 0L..1199L) sin(t / 1200.0 * PI).toFloat() * .4f else 0f
        } ?: 0f
        return NeonInteractionFrame(blink.frame(now), shiftX, if (boost > 0) lightGroup else -1, boost, shiftY)
    }

    private fun advanceClouds(now: Long) {
        val previous = cloudAt ?: now
        if (now < previous) return
        // Integrate across the sensor timeout boundary so the result does not depend on FPS.
        val expires = sampleAt?.plus(500L) ?: previous
        val trackingEnd = now.coerceAtMost(expires).coerceAtLeast(previous)
        smooth(targetX, targetY, trackingEnd - previous)
        smooth(0f, 0f, now - trackingEnd)
        cloudAt = now
    }

    private fun smooth(x: Float, y: Float, elapsed: Long) {
        if (elapsed <= 0) return
        val gain = (1.0 - exp(-elapsed / 350.0)).toFloat()
        shiftX += (x - shiftX) * gain
        shiftY += (y - shiftY) * gain
        if (x == 0f && abs(shiftX) < .00001f) shiftX = 0f
        if (y == 0f && abs(shiftY) < .00001f) shiftY = 0f
    }

    companion object {
        const val MAX_SHIFT = .04f
        const val MAX_SHIFT_Y = .012f
    }
}
