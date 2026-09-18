package app.livosphere.wallpapers.neon

import kotlin.math.*

data class NeonTiltFrame(val x: Float = 0f, val y: Float = 0f, val react: Boolean = false)

/** Gravity in device coordinates. Pure, per-Engine state; timestamps are monotonic milliseconds. */
class NeonTiltController {
    private var time: Long? = null
    private var calibratedAt = 0L
    private var rotation = -1
    private var gx = 0f; private var gy = 0f; private var gz = 0f
    private var originX = 0f; private var originY = 0f
    private var armed = true
    private var lastReaction: Long? = null

    fun reset() { time = null; rotation = -1; armed = true; lastReaction = null }

    fun sample(x: Float, y: Float, z: Float, rotation: Int, now: Long): NeonTiltFrame? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || rotation !in 0..3) return null
        val magnitude = sqrt(x * x + y * y + z * z)
        // Reject strong acceleration/shaking, not merely clamp it into a deliberate tilt.
        if (!magnitude.isFinite() || magnitude !in 7f..12.5f) return null
        val previous = time
        if (previous != null && now <= previous) return null
        val fresh = previous == null || now - previous > 1000 || this.rotation != rotation
        val alpha = if (fresh) 1f else (1 - exp(-(now - checkNotNull(previous)) / 220.0)).toFloat()
        gx += (x / magnitude - gx) * alpha
        gy += (y / magnitude - gy) * alpha
        gz += (z / magnitude - gz) * alpha
        time = now
        val screenX = when (rotation) { 1 -> gy; 2 -> -gx; 3 -> -gy; else -> gx }
        val screenY = when (rotation) { 1 -> -gx; 2 -> -gy; 3 -> gx; else -> gy }
        val angleX = atan2(screenX, hypot(screenY, gz))
        val angleY = atan2(screenY, gz)
        if (fresh) {
            this.rotation = rotation; calibratedAt = now; armed = true; lastReaction = null
            originX = angleX; originY = angleY
            return NeonTiltFrame()
        }
        // Let the gravity filter settle after HOME/preview/rotation before accepting a gesture.
        if (now - calibratedAt < 300) { originX = angleX; originY = angleY; return NeonTiltFrame() }
        val dx = wrapped(angleX - originX)
        val dy = wrapped(angleY - originY)
        val distance = max(abs(dx), abs(dy))
        if (distance < radians(3f)) armed = true
        val react = armed && distance > radians(9f) && (lastReaction == null || now - checkNotNull(lastReaction) >= 2500)
        if (react) { armed = false; lastReaction = now }
        return NeonTiltFrame(amount(dx), amount(dy), react)
    }
    private fun amount(angle: Float): Float = sign(angle) * ((abs(angle) - radians(2f)) / radians(18f)).coerceIn(0f, 1f)
    private fun wrapped(angle: Float): Float = atan2(sin(angle), cos(angle))
    private fun radians(degrees: Float) = degrees * PI.toFloat() / 180f
}
