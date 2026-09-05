package app.livosphere.wallpapers.engine

fun interface WallpaperClock { fun nowMillis(): Long }

/** An asynchronous, single worker scheduler. It must never run a posted callback inline. */
interface WallpaperFrameScheduler {
    fun post(delayMillis: Long, frame: Runnable)
    fun cancel(frame: Runnable)
}

enum class WallpaperFrameResult { DRAWN, RETRY, INVALID, STATIC }

interface WallpaperRenderer : AutoCloseable {
    /** RETRY: valid surface temporarily unavailable; INVALID: release until recreated. */
    fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult
}

/** Per-Engine owner. The monitor is also a teardown fence against an in-flight frame. */
class WallpaperRenderLoop(
    private val clock: WallpaperClock,
    private val scheduler: WallpaperFrameScheduler,
    private val createRenderer: () -> WallpaperRenderer,
) : AutoCloseable {
    private var visible = false
    private var valid = false
    private var destroyed = false
    private var reduced = false
    private var terminal = false
    private var generation = 0L
    private var pending: Runnable? = null
    private var renderer: WallpaperRenderer? = null
    private var redrawComplete: (() -> Unit)? = null
    private var retryCount = 0
    private var redrawAttempts = 0

    @Synchronized fun setVisible(value: Boolean) {
        if (destroyed) return
        visible = value
        refresh()
    }

    @Synchronized fun setSurfaceValid(value: Boolean) {
        if (destroyed) return
        if (value && !valid) terminal = false
        valid = value
        refresh()
    }

    @Synchronized fun setReducedMotion(value: Boolean) {
        if (destroyed || reduced == value) return
        reduced = value
        refresh()
    }

    private fun refresh() {
        cancel()
        if (visible && valid && !terminal) schedule(0) else release()
    }

    private fun cancel() {
        generation++
        pending?.let(scheduler::cancel)
        pending = null
        finishRedraw()
    }

    private fun schedule(delay: Long) {
        val token = generation
        val frame = Runnable { draw(token) }
        pending = frame
        scheduler.post(delay, frame)
    }

    /** Explicit system redraw only: valid hidden surfaces paint once and immediately release. */
    @Synchronized fun requestRedraw(onComplete: () -> Unit) {
        cancel()
        if (destroyed || !valid || terminal) { onComplete(); return }
        redrawComplete = onComplete
        redrawAttempts = 0
        draw(generation)
    }

    @Synchronized private fun draw(token: Long) {
        if (token != generation || destroyed || (!visible && redrawComplete == null) || !valid || terminal) return
        pending = null
        if (redrawComplete != null) redrawAttempts++
        val started = clock.nowMillis()
        val current = renderer ?: try {
            createRenderer().also { renderer = it }
        } catch (_: RuntimeException) {
            terminal = true
            finishRedraw()
            return
        } catch (_: OutOfMemoryError) {
            // Renderer creation is the bitmap/paint allocation boundary, not an arbitrary frame.
            terminal = true
            finishRedraw()
            return
        }
        val result = try { current.render(started, reduced) }
        catch (_: RuntimeException) { WallpaperFrameResult.STATIC }
        if (result == WallpaperFrameResult.INVALID) {
            valid = false
            release()
            cancel()
        } else if (result == WallpaperFrameResult.STATIC) {
            terminal = true
            release()
            cancel()
        } else if (result == WallpaperFrameResult.RETRY) {
            retryCount++
            // A system callback must not wait forever for future lifecycle callbacks on main.
            // This bounds only our retry queue, never claims to bound graphics-driver latency.
            if (redrawComplete != null && redrawAttempts >= 3) {
                release()
                finishRedraw()
                if (visible && !reduced) schedule(250)
            } else {
                schedule((34L * (1L shl (retryCount - 1).coerceIn(0, 5))).coerceAtMost(1000))
            }
        } else {
            retryCount = 0
            if (!visible) release()
            finishRedraw()
            if (visible && !reduced) schedule((34L - (clock.nowMillis() - started)).coerceAtLeast(1))
        }
    }

    private fun finishRedraw() {
        val complete = redrawComplete
        redrawComplete = null
        complete?.invoke()
    }

    private fun release() {
        val previous = renderer
        renderer = null
        try { previous?.close() } catch (_: RuntimeException) {
            // Cleanup failure must not retain the owner, queue, or system-redraw completion.
        }
    }

    @Synchronized override fun close() {
        destroyed = true
        visible = false
        valid = false
        cancel()
        release()
    }
}
