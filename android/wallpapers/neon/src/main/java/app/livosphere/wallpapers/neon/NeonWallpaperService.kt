package app.livosphere.wallpapers.neon

import android.content.*
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.MotionEvent
import android.view.ViewConfiguration
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.DayPhase
import app.livosphere.contract.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.engine.*
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.PrintWriter
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

abstract class NeonWallpaperService(private val theme: NeonTheme) : WallpaperService() {
    private val engines = ConcurrentHashMap.newKeySet<NeonEngine>()
    override fun onCreateEngine(): Engine = NeonEngine().also { engines += it }
    override fun dump(fd: FileDescriptor, out: PrintWriter, args: Array<out String>) {
        super.dump(fd, out, args)
        engines.forEach { out.println(it.debugState()) }
    }
    private inner class NeonEngine : Engine() {
        private val thread = HandlerThread("neon-" + theme.setId).apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val settings = WallpaperSettingsRepository(applicationContext, theme.wallpaperId)
        private val policy = EffectiveMotionPolicy()
        private val clock = VisibleSceneClock()
        private var holder: SurfaceHolder? = null
        private var visible = false
        private var valid = false
        private var mode: WallpaperMotionMode? = null
        private var phase = PhasePolicy().select(Clock.systemUTC(), ZoneId.systemDefault()).phase
        private var previousPhase = phase
        private var phaseChangedAt = 0L
        private var frames = 0L
        private var effectLevel = AuthoredEffectLevel.FULL
        private var interactionsEnabled = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private val interactions = NeonInteractionController(theme, ViewConfiguration.get(applicationContext).scaledTouchSlop.toFloat())
        private var effective = policy.evaluate(NeonScenePolicy.definition, effectLevel, WallpaperMotionMode.OFF,
            true, ScenePowerFacts(null, null, null))
        private val loop = WallpaperRenderLoop(WallpaperClock { SystemClock.elapsedRealtime() },
            object : WallpaperFrameScheduler {
                override fun post(delayMillis: Long, frame: Runnable) {
                    handler.postDelayed(frame, if (delayMillis == 0L) 0 else delayMillis.coerceAtLeast(50))
                }
                override fun cancel(frame: Runnable) { handler.removeCallbacks(frame) }
            }) {
            NeonSceneRenderer(applicationContext, theme, checkNotNull(holder)) {
                frames++
                NeonFrame(phase, previousPhase, phaseChangedAt, clock.value(SystemClock.elapsedRealtime()), effective, interactions.frame(SystemClock.elapsedRealtime()))
            }
        }
        private val phases = PhaseScheduler(Clock.systemUTC(), { ZoneId.systemDefault() },
            object : PhaseCallbackScheduler {
                override fun post(delayMillis: Long, callback: Runnable) { handler.postDelayed(callback, delayMillis) }
                override fun cancel(callback: Runnable) { handler.removeCallbacks(callback) }
            }, PhasePolicy()) { selection ->
                if (phase != selection.phase) {
                    previousPhase = phase
                    phase = selection.phase
                    phaseChangedAt = SystemClock.elapsedRealtime()
                    updateInteractions()
                    loop.invalidate()
                }
            }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                handler.post {
                    if (visible && valid) phases.invalidate()
                    updateMotion()
                }
            }
        }
        private val motionObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) { updateMotion() }
        }
        private var receiverRegistered = false
        private var observerRegistered = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            loop.setReducedMotion(true)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            try { registerReceiver(receiver, filter); receiverRegistered = true } catch (_: RuntimeException) { }
            try {
                contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
                observerRegistered = true
            } catch (_: RuntimeException) { }
            scope.launch { settings.settings.collect { result -> handler.post {
                val preference = (result as? SettingsOutcome.Success)?.value
                mode = preference?.motionMode
                interactionsEnabled = preference?.interactionsEnabled == true
                effectLevel = preference?.effectLevel?.let { AuthoredEffectLevel.valueOf(it.name) } ?: AuthoredEffectLevel.SUBTLE
                updateMotion()
            } } }
        }
        private fun updateMotion() {
            val battery = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: RuntimeException) { null }
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) (level.toLong() * 100 / scale).toInt().coerceIn(0, 100) else null
            val saver = try { (getSystemService(POWER_SERVICE) as? PowerManager)?.isPowerSaveMode } catch (_: RuntimeException) { null }
            val reduced = try { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (_: RuntimeException) { true }
            effective = policy.evaluate(NeonScenePolicy.definition, effectLevel, mode ?: WallpaperMotionMode.OFF,
                reduced, ScenePowerFacts(saver, percent, null))
            updateInteractions()
            clock.setRunning(visible && valid && !effective.staticFrame, SystemClock.elapsedRealtime())
            loop.setReducedMotion(effective.staticFrame)
            if (visible && valid) loop.invalidate()
        }
        private fun updateInteractions() = interactions.configure(
            visible && valid && interactionsEnabled && !effective.staticFrame, phase, effectLevel, surfaceWidth, surfaceHeight)

        override fun onTouchEvent(event: MotionEvent) {
            // MotionEvent is recycled by Android after this callback. Post primitive values only.
            val action = event.actionMasked
            val x = event.x; val y = event.y; val pointers = event.pointerCount
            val now = SystemClock.elapsedRealtime()
            handler.post {
                if (pointers > 1 || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) interactions.pointerCancel(now)
                else when (action) {
                    MotionEvent.ACTION_DOWN -> interactions.down(x, y, now)
                    MotionEvent.ACTION_MOVE -> interactions.move(x, y, now)
                    MotionEvent.ACTION_UP -> interactions.up(x, y, now)
                }
            }
            super.onTouchEvent(event)
        }
        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int) {
            val now = SystemClock.elapsedRealtime()
            handler.post { interactions.offset(xOffset, xOffsetStep, now) }
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
        }
        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            fence {
                visible = isVisible
                if (visible && valid) phases.start() else phases.stop()
                updateMotion()
                loop.setVisible(visible)
            }
        }
        override fun onSurfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            fence {
                phases.stop()
                loop.setSurfaceValid(false)
                holder = surfaceHolder
                surfaceWidth = width; surfaceHeight = height
                valid = width > 0 && height > 0 && surfaceHolder.surface.isValid
                if (visible && valid) phases.start()
                updateMotion()
                loop.setSurfaceValid(valid)
            }
        }
        override fun onSurfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
            val done = CountDownLatch(1)
            if (handler.post {
                val selected = PhasePolicy().select(Clock.systemUTC(), ZoneId.systemDefault()).phase
                if (!visible) { phase = selected; previousPhase = selected }
                loop.requestRedraw { done.countDown() }
            }) await(done)
            super.onSurfaceRedrawNeeded(surfaceHolder)
        }
        override fun onSurfaceDestroyed(surfaceHolder: SurfaceHolder) {
            fence {
                phases.stop(); valid = false; updateInteractions()
                clock.setRunning(false, SystemClock.elapsedRealtime())
                loop.setSurfaceValid(false); holder = null
            }
            super.onSurfaceDestroyed(surfaceHolder)
        }
        override fun onDestroy() {
            scope.cancel()
            if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
            if (observerRegistered) runCatching { contentResolver.unregisterContentObserver(motionObserver) }
            fence { phases.close(); loop.close(); interactions.cancel(); valid = false; visible = false; holder = null }
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            engines -= this
            super.onDestroy()
        }
        fun debugState() = "neon theme=${theme.setId} phase=$phase visible=$visible valid=$valid static=${effective.staticFrame} requested=$effectLevel effective=${effective.effectiveLevel} frames=$frames lights=${NeonScenePolicy.lightsEnabled(phase)} interactions=$interactionsEnabled blinks=${interactions.blinks} swipes=${interactions.swipes} pulses=${interactions.pulses} blink=${interactions.frame(SystemClock.elapsedRealtime()).blink} shift=${interactions.frame(SystemClock.elapsedRealtime()).cloudOffset}"
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
