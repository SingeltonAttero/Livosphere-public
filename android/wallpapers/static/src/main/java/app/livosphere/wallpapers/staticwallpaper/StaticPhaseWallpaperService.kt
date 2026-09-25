package app.livosphere.wallpapers.staticwallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.PhaseCallbackScheduler
import app.livosphere.wallpapers.engine.PhasePolicy
import app.livosphere.wallpapers.engine.PhaseScheduler
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.engine.WallpaperClock
import app.livosphere.wallpapers.engine.WallpaperFrameResult
import app.livosphere.wallpapers.engine.WallpaperFrameScheduler
import app.livosphere.wallpapers.engine.WallpaperRenderLoop
import app.livosphere.wallpapers.engine.WallpaperRenderer
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import kotlin.math.roundToInt

/** Thin per-Engine runtime: one frame now and one frame at the next local phase boundary. */
abstract class StaticPhaseWallpaperService(private val wallpaperId: String) : WallpaperService() {
    protected abstract fun scene(): StaticPhaseScene

    override fun onCreateEngine(): Engine = StaticEngine()

    private inner class StaticEngine : Engine() {
        private val worker = HandlerThread("static-$wallpaperId").apply { start() }
        private val handler = Handler(worker.looper)
        private val phaseClock = Clock.systemUTC()
        private var holder: SurfaceHolder? = null
        private var visible = false
        private var valid = false
        private var phase = PhasePolicy().select(phaseClock, ZoneId.systemDefault()).phase
        private val phaseScene = scene()
        private val loop = WallpaperRenderLoop(
            WallpaperClock { SystemClock.elapsedRealtime() },
            object : WallpaperFrameScheduler {
                override fun post(delayMillis: Long, frame: Runnable) { handler.postDelayed(frame, delayMillis) }
                override fun cancel(frame: Runnable) { handler.removeCallbacks(frame) }
            },
        ) { StaticPhaseRenderer(resources, checkNotNull(holder), phaseScene) { phase } }
        private val phases = PhaseScheduler(
            phaseClock,
            { ZoneId.systemDefault() },
            object : PhaseCallbackScheduler {
                override fun post(delayMillis: Long, callback: Runnable) { handler.postDelayed(callback, delayMillis) }
                override fun cancel(callback: Runnable) { handler.removeCallbacks(callback) }
            },
        ) { selection ->
            phase = selection.phase
            loop.invalidate()
        }
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { handler.post { phases.invalidate() } }
        }
        private var receiverRegistered = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            loop.setReducedMotion(true)
            try {
                registerReceiver(timeReceiver, IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                })
                receiverRegistered = true
            } catch (_: RuntimeException) {
                // Visibility resume still samples fresh wall time and zone.
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            fence {
                visible = isVisible
                loop.setVisible(isVisible)
                if (isVisible && valid) phases.start() else phases.stop()
            }
        }

        override fun onSurfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            fence {
                phases.stop()
                loop.setSurfaceValid(false)
                holder = surfaceHolder
                valid = width > 0 && height > 0 && surfaceHolder.surface.isValid
                loop.setSurfaceValid(valid)
                if (visible && valid) phases.start()
            }
        }

        override fun onSurfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
            val done = CountDownLatch(1)
            if (handler.post {
                phase = PhasePolicy().select(phaseClock, ZoneId.systemDefault()).phase
                loop.requestRedraw { done.countDown() }
            }) await(done)
            super.onSurfaceRedrawNeeded(surfaceHolder)
        }

        override fun onSurfaceDestroyed(surfaceHolder: SurfaceHolder) {
            fence {
                phases.stop()
                valid = false
                loop.setSurfaceValid(false)
                holder = null
            }
            super.onSurfaceDestroyed(surfaceHolder)
        }

        override fun onDestroy() {
            if (receiverRegistered) runCatching { unregisterReceiver(timeReceiver) }
            fence {
                phases.close()
                loop.close()
                valid = false
                visible = false
                holder = null
            }
            handler.removeCallbacksAndMessages(null)
            worker.quitSafely()
            super.onDestroy()
        }

        private fun fence(action: () -> Unit) {
            val done = CountDownLatch(1)
            if (handler.post { try { action() } finally { done.countDown() } }) await(done)
        }

        private fun await(done: CountDownLatch) {
            var interrupted = false
            while (true) try { done.await(); break } catch (_: InterruptedException) { interrupted = true }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}

private class StaticPhaseRenderer(
    private val resources: Resources,
    private val holder: SurfaceHolder,
    private val scene: StaticPhaseScene,
    private val phase: () -> DayPhase,
) : WallpaperRenderer {
    private var bitmapResourceId: Int? = null
    private var bitmap: Bitmap? = null

    override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
        if (!holder.surface.isValid) return WallpaperFrameResult.INVALID
        val resourceId = scene.plate(phase())
        val bitmap = bitmapFor(resourceId)
        val canvas = try { holder.lockCanvas() } catch (_: RuntimeException) { null }
            ?: return WallpaperFrameResult.RETRY
        return try {
            val crop = scene.aspectFillCrop(bitmap.width, bitmap.height, canvas.width, canvas.height)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(
                bitmap,
                Rect(crop.left.roundToInt(), crop.top.roundToInt(), crop.right.roundToInt(), crop.bottom.roundToInt()),
                Rect(0, 0, canvas.width, canvas.height),
                null,
            )
            WallpaperFrameResult.DRAWN
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun close() {
        bitmap?.let { if (!it.isRecycled) it.recycle() }
        bitmap = null
        bitmapResourceId = null
    }

    private fun bitmapFor(resourceId: Int): Bitmap {
        bitmap?.takeIf { bitmapResourceId == resourceId && !it.isRecycled }?.let { return it }
        val decoded = checkNotNull(BitmapFactory.decodeResource(resources, resourceId)) {
            "Unable to decode static phase plate $resourceId"
        }
        val previous = bitmap
        bitmap = decoded
        bitmapResourceId = resourceId
        if (previous != null && previous !== decoded && !previous.isRecycled) previous.recycle()
        return decoded
    }
}
