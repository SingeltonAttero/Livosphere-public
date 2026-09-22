package app.livosphere.wallpapers.neon

import android.content.*
import android.database.ContentObserver
import android.os.*
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
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
        private var screenInteractive = (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive == true
        private var mode: WallpaperMotionMode? = null
        private var phase = PhasePolicy().select(Clock.systemUTC(), ZoneId.systemDefault()).phase
        private var previousPhase = phase
        private var phaseChangedAt = 0L
        private var frames = 0L
        private var effectLevel = AuthoredEffectLevel.FULL
        private var interactionsEnabled = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private val interactions = NeonInteractionController(theme)
        @Volatile private var lastInteractionFrame = NeonInteractionFrame()
        private val tiltSensor = NeonTiltSensor(this@NeonWallpaperService, handler) {
            interactions.tilt(it, SystemClock.elapsedRealtime())
        }
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
                val now = SystemClock.elapsedRealtime()
                val interaction = interactions.frame(now).also { lastInteractionFrame = it }
                NeonFrame(phase, previousPhase, phaseChangedAt, clock.value(now), effective, interaction)
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
                val action = intent?.action
                handler.post {
                    when (action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            screenInteractive = false
                            interactions.cancel()
                            phases.stop()
                        }
                        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> screenInteractive = true
                    }
                    if (visible && valid && screenInteractive) {
                        if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) phases.start()
                        else phases.invalidate()
                    }
                    updateMotion()
                    if (action == Intent.ACTION_USER_PRESENT && !isPreview && !effective.staticFrame) {
                        interactions.onUnlocked(SystemClock.elapsedRealtime())
                    }
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
            setTouchEventsEnabled(false)
            setOffsetNotificationsEnabled(false)
            loop.setReducedMotion(true)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED); addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
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
            clock.setRunning(visible && valid && screenInteractive && !effective.staticFrame, SystemClock.elapsedRealtime())
            loop.setReducedMotion(effective.staticFrame)
            loop.setVisible(visible && screenInteractive)
            if (visible && valid && screenInteractive) loop.invalidate()
        }
        private fun updateInteractions() {
            val enabled = visible && valid && screenInteractive && !effective.staticFrame
            // Static/power/screen-off transitions also discard a pending unlock-before-HOME event.
            if (effective.staticFrame || !screenInteractive) interactions.cancel()
            interactions.configure(enabled, phase, effectLevel, surfaceWidth, surfaceHeight,
                SystemClock.elapsedRealtime(), tiltEnabled = interactionsEnabled)
            tiltSensor.setActive(enabled && interactionsEnabled)
            if (!enabled) lastInteractionFrame = NeonInteractionFrame()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            fence {
                visible = isVisible
                if (visible && valid && screenInteractive) phases.start() else phases.stop()
                updateMotion()
            }
        }
        override fun onSurfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            fence {
                phases.stop()
                loop.setSurfaceValid(false)
                holder = surfaceHolder
                if (surfaceWidth != width || surfaceHeight != height) tiltSensor.recalibrate()
                surfaceWidth = width; surfaceHeight = height
                valid = width > 0 && height > 0 && surfaceHolder.surface.isValid
                if (visible && valid && screenInteractive) phases.start()
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
                phases.stop(); valid = false; interactions.cancel(); updateInteractions()
                clock.setRunning(false, SystemClock.elapsedRealtime())
                loop.setSurfaceValid(false); holder = null
            }
            super.onSurfaceDestroyed(surfaceHolder)
        }
        override fun onDestroy() {
            scope.cancel()
            if (receiverRegistered) runCatching { unregisterReceiver(receiver) }
            if (observerRegistered) runCatching { contentResolver.unregisterContentObserver(motionObserver) }
            fence { tiltSensor.setActive(false); phases.close(); loop.close(); interactions.cancel(); valid = false; visible = false; holder = null }
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            engines -= this
            super.onDestroy()
        }
        fun debugState(): String {
            // Dumps read the last rendered value; they must not advance the blink timer off-thread.
            val frame = lastInteractionFrame
            return "neon theme=${theme.setId} phase=$phase visible=$visible valid=$valid screenInteractive=$screenInteractive static=${effective.staticFrame} requested=$effectLevel effective=${effective.effectiveLevel} frames=$frames lights=${NeonScenePolicy.lightsEnabled(phase)} interactions=$interactionsEnabled blinks=${interactions.blinks} sensor=${tiltSensor.sensor?.stringType ?: "none"} sensing=${tiltSensor.active} pulses=${interactions.pulses} blink=${frame.blink} shift=${frame.cloudOffset} shiftY=${frame.cloudOffsetY}"
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
