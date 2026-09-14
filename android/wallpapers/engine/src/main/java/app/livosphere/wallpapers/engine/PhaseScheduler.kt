package app.livosphere.wallpapers.engine

import java.time.Clock
import java.time.Duration
import java.time.ZoneId

interface PhaseCallbackScheduler {
    fun post(delayMillis: Long, callback: Runnable)
    fun cancel(callback: Runnable)
}

/** Owns exactly one cancellable callback for the next real local-time phase transition. */
class PhaseScheduler(
    private val clock: Clock,
    private val zoneId: () -> ZoneId,
    private val callbackScheduler: PhaseCallbackScheduler,
    private val policy: PhasePolicy = PhasePolicy(),
    private val onSelection: (PhaseSelection) -> Unit,
) : AutoCloseable {
    private var active = false
    private var generation = 0L
    private var pending: Runnable? = null

    @Synchronized fun start() {
        if (active) return
        active = true
        reschedule()
    }

    /** Re-read wall time and ZoneId after a platform time/date/timezone signal. */
    @Synchronized fun invalidate() {
        if (active) reschedule()
    }

    @Synchronized fun stop() {
        active = false
        cancelPending()
    }

    @Synchronized override fun close() = stop()

    private fun reschedule() {
        cancelPending()
        val selection = policy.select(clock, zoneId())
        onSelection(selection)
        val token = generation
        val callback = Runnable { dispatch(token) }
        pending = callback
        val delay = Duration.between(clock.instant(), selection.nextDeadline).toMillis().coerceAtLeast(1L)
        callbackScheduler.post(delay, callback)
    }

    @Synchronized private fun dispatch(token: Long) {
        if (!active || token != generation) return
        pending = null
        // Early and late delivery both re-sample the clock; no phase or deadline is replayed.
        reschedule()
    }

    private fun cancelPending() {
        generation++
        pending?.let(callbackScheduler::cancel)
        pending = null
    }
}
