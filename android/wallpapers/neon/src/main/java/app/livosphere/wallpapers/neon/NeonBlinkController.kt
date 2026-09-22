package app.livosphere.wallpapers.neon

/** Frame-driven, per-Engine timing. No callbacks survive a visible motion session. */
class NeonBlinkController(seed: Int = 0) {
    private var enabled = false
    private var blinkAt: Long? = null
    private var nextAt: Long? = null
    private var pendingUnlockAt: Long? = null
    private var lastUnlockAt: Long? = null
    private var unlockRemaining = 0
    private var unlockExpiresAt = 0L
    private var intervalIndex = Math.floorMod(seed, INTERVALS.size)
    var blinks = 0L
        private set

    fun configure(enabled: Boolean, now: Long) {
        if (this.enabled == enabled) return
        if (!enabled) { cancel(); return }
        this.enabled = true
        val pending = pendingUnlockAt
        pendingUnlockAt = null
        if (pending != null && now - pending in 0L..UNLOCK_WINDOW) scheduleUnlock(now)
        else nextAt = now + nextInterval()
    }

    fun cancel() {
        enabled = false
        blinkAt = null; nextAt = null; pendingUnlockAt = null; lastUnlockAt = null
        unlockRemaining = 0; unlockExpiresAt = 0L
    }

    /** May arrive just before HOME becomes visible. Expired events are never replayed. */
    fun onUnlocked(now: Long) {
        if (lastUnlockAt?.let { now - it in 0L..UNLOCK_WINDOW } == true) return
        lastUnlockAt = now
        if (enabled) scheduleUnlock(now) else pendingUnlockAt = now
    }

    /** A tilt cannot restart closing eyelids or interrupt the two-blink welcome. */
    fun onTilt(now: Long) {
        if (!enabled || unlockRemaining > 0 || isBlinking(now)) return
        start(now)
        nextAt = now + nextInterval()
    }

    fun frame(now: Long): Float {
        if (!enabled) return 0f
        if (unlockRemaining > 0 && now > unlockExpiresAt) {
            unlockRemaining = 0
            nextAt = now + nextInterval()
        }
        if (nextAt?.let { now >= it } == true && !isBlinking(now)) {
            start(now)
            if (unlockRemaining > 0) unlockRemaining--
            nextAt = now + if (unlockRemaining > 0) 750L else nextInterval()
        }
        return blinkAt?.let { start ->
            when (val elapsed = now - start) {
                in 0L..50L -> elapsed / 50f
                in 51L..120L -> 1f
                in 121L..239L -> 1f - (elapsed - 120) / 120f
                else -> 0f
            }
        } ?: 0f
    }

    private fun scheduleUnlock(now: Long) {
        unlockRemaining = 2
        unlockExpiresAt = now + UNLOCK_WINDOW
        nextAt = now + 350L
    }

    private fun isBlinking(now: Long) = blinkAt?.let { now - it in 0L..239L } == true
    private fun start(now: Long) { blinkAt = now; blinks++ }
    private fun nextInterval(): Long = INTERVALS[intervalIndex].also {
        intervalIndex = (intervalIndex + 1) % INTERVALS.size
    }

    companion object {
        private const val UNLOCK_WINDOW = 2_000L
        private val INTERVALS = longArrayOf(4_200L, 5_700L, 4_800L, 6_300L)
    }
}
