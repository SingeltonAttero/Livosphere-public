package app.livosphere.wallpapers.fixture

import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.engine.WallpaperClock
import app.livosphere.wallpapers.engine.WallpaperFrameResult
import app.livosphere.wallpapers.engine.WallpaperFrameScheduler
import app.livosphere.wallpapers.engine.WallpaperRenderLoop
import app.livosphere.wallpapers.engine.WallpaperRenderer
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Technical B fixture: its identity, settings and each Engine exist without an Activity. */
open class FixtureWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = FixtureEngine()

    /** Injectable boundaries share the exact production Engine callback path. */
    protected open fun createSettings(): WallpaperSettingsRepository = WallpaperSettingsRepository(applicationContext, WALLPAPER_ID)
    protected open fun createRenderer(holder: SurfaceHolder): WallpaperRenderer = FixtureRenderer(holder)
    protected open fun isSurfaceValid(holder: SurfaceHolder): Boolean = holder.surface.isValid

    private inner class FixtureEngine : Engine() {
        private val settings = createSettings().also { require(it.wallpaperId == WALLPAPER_ID) }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val worker = HandlerThread("FixtureWallpaper").apply { start() }
        private val handler = Handler(worker.looper)
        private var holder: SurfaceHolder? = null
        private val loop = WallpaperRenderLoop(WallpaperClock { SystemClock.elapsedRealtime() },
            object : WallpaperFrameScheduler {
                override fun post(delayMillis: Long, frame: Runnable) { handler.postDelayed(frame, delayMillis) }
                override fun cancel(frame: Runnable) { handler.removeCallbacks(frame) }
            }) { createRenderer(checkNotNull(holder)) }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // This diagnostic scene is always static, including NORMAL. Observation never writes.
            loop.setReducedMotion(true)
            scope.launch { settings.motionMode.collect { handler.post { loop.invalidate() } } }
            scope.launch { settings.touchReactionsEnabled.collect { handler.post { loop.invalidate() } } }
        }

        override fun onSurfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            handler.post {
                loop.setSurfaceValid(false)
                holder = surfaceHolder
                loop.setSurfaceValid(width > 0 && height > 0 && isSurfaceValid(surfaceHolder))
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) handler.post { loop.setVisible(true) }
            else fence { done -> loop.setVisible(false); done() }
        }

        override fun onSurfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
            fence { complete -> loop.requestRedraw(complete) }
            super.onSurfaceRedrawNeeded(surfaceHolder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            fence { done -> loop.setSurfaceValid(false); this.holder = null; done() }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            scope.cancel()
            try { fence { done -> loop.close(); holder = null; done() } }
            finally {
                handler.removeCallbacksAndMessages(null)
                worker.quitSafely()
                super.onDestroy()
            }
        }

        private fun fence(action: (complete: () -> Unit) -> Unit) {
            val complete = CountDownLatch(1)
            if (!handler.post {
                try { action { complete.countDown() } }
                catch (_: RuntimeException) { complete.countDown() }
            }) return
            var interrupted = false
            while (true) {
                try { complete.await(); break } catch (_: InterruptedException) { interrupted = true }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    companion object { const val WALLPAPER_ID = "isolation-fixture-wallpaper" }
}

/** No Contour runtime or resource dependency, timers, tap effects, or claimed widget rendering. */
private class FixtureRenderer(private val holder: SurfaceHolder) : WallpaperRenderer {
    private val paint = Paint()
    override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
        if (!holder.surface.isValid) return WallpaperFrameResult.INVALID
        val canvas = try { holder.lockCanvas() } catch (_: RuntimeException) { return retry() }
            ?: return retry()
        var result = WallpaperFrameResult.DRAWN
        try {
            canvas.drawColor(Color.rgb(240, 174, 48))
            paint.color = Color.rgb(28, 71, 115)
            canvas.drawRect(canvas.width / 2f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: RuntimeException) { result = retry() }
        }
        return result
    }
    private fun retry() = if (holder.surface.isValid) WallpaperFrameResult.RETRY else WallpaperFrameResult.INVALID
    override fun close() = Unit
}
