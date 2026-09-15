package app.livosphere.wallpapers.fixture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.util.Xml
import android.view.MotionEvent
import android.view.SurfaceHolder
import app.livosphere.contract.DayPhase
import app.livosphere.settings.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import app.livosphere.wallpapers.engine.PhaseCallbackScheduler
import app.livosphere.wallpapers.engine.PhasePolicy
import app.livosphere.wallpapers.engine.PhaseScheduler
import app.livosphere.wallpapers.engine.PhaseSelection
import app.livosphere.wallpapers.engine.ScenePowerFacts
import app.livosphere.wallpapers.engine.SceneTrigger
import app.livosphere.wallpapers.engine.TriggerDispatch
import app.livosphere.wallpapers.engine.WallpaperClock
import app.livosphere.wallpapers.engine.WallpaperFrameResult
import app.livosphere.wallpapers.engine.WallpaperFrameScheduler
import app.livosphere.wallpapers.engine.WallpaperRenderLoop
import app.livosphere.wallpapers.engine.WallpaperRenderer
import java.io.FileDescriptor
import java.io.PrintWriter
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

/** Technical B fixture: its identity, settings and each Engine exist without an Activity. */
open class FixtureWallpaperService : WallpaperService() {
    private val engines = ConcurrentHashMap.newKeySet<FixtureEngine>()
    private val scenesByHolder = ConcurrentHashMap<SurfaceHolder, FixtureSceneController>()

    override fun onCreateEngine(): Engine = FixtureEngine().also { engines += it }

    /** Injectable boundaries share the exact production Engine callback path. */
    protected open fun createSettings(): WallpaperSettingsRepository =
        WallpaperSettingsRepository(applicationContext, WALLPAPER_ID)
    protected open fun createRenderer(holder: SurfaceHolder, phase: () -> DayPhase): WallpaperRenderer =
        FixtureRenderer(resources, holder, phase, checkNotNull(scenesByHolder[holder]))
    protected open fun isSurfaceValid(holder: SurfaceHolder): Boolean = holder.surface.isValid
    protected open fun createPhaseClock(): Clock = Clock.systemUTC()
    protected open fun currentZoneId(): ZoneId = ZoneId.systemDefault()
    protected open fun createPhaseCallbackScheduler(handler: Handler): PhaseCallbackScheduler =
        object : PhaseCallbackScheduler {
            override fun post(delayMillis: Long, callback: Runnable) { handler.postDelayed(callback, delayMillis) }
            override fun cancel(callback: Runnable) { handler.removeCallbacks(callback) }
        }
    protected open fun registerTimeChangeReceiver(receiver: BroadcastReceiver): Boolean {
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        return true
    }
    protected open fun unregisterTimeChangeReceiver(receiver: BroadcastReceiver) = unregisterReceiver(receiver)
    protected open fun registerPowerReceiver(receiver: BroadcastReceiver): Boolean {
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        })
        return true
    }
    protected open fun unregisterPowerReceiver(receiver: BroadcastReceiver) = unregisterReceiver(receiver)
    protected open fun readPowerFacts(): ScenePowerFacts {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
            else -> null
        }
        val saver = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode
        return ScenePowerFacts(saver, percent, charging)
    }
    protected open fun isSystemReducedMotion(): Boolean = try {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: RuntimeException) { true }

    /** Test observer for the real Engine/render path; production observation remains read-only. */
    protected open fun onPhaseRendered(phase: DayPhase, renderCount: Long) = Unit

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        super.dump(fd, writer, args)
        writer.println("livosphereFixture engines=${engines.size}")
        engines.forEach { writer.println("  ${it.debugState()}") }
    }

    private inner class FixtureEngine : Engine() {
        private val engineId = NEXT_ENGINE_ID.incrementAndGet()
        private val settings = createSettings().also { require(it.wallpaperId == WALLPAPER_ID) }
        private val scene = FixtureSceneController(loadFixtureScene(resources))
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val worker = HandlerThread("FixtureWallpaper").apply { start() }
        private val handler = Handler(worker.looper)
        private val phaseClock = createPhaseClock()
        private val phasePolicy = PhasePolicy()
        private var holder: SurfaceHolder? = null
        @Volatile private var currentPhase = DayPhase.NIGHT
        @Volatile private var nextDeadline: Instant? = null
        @Volatile private var schedulerActive = false
        @Volatile private var engineVisible = false
        @Volatile private var surfaceActive = false
        @Volatile private var motionPreference: WallpaperMotionMode? = null
        @Volatile private var interactionsPreference: Boolean? = null
        @Volatile private var injectedPowerFacts: ScenePowerFacts? = null
        @Volatile private var injectedSystemReduced: Boolean? = null
        private var previousOffset: Float? = null
        private var observingPower = false
        private val renderCount = AtomicLong()
        private val loop = WallpaperRenderLoop(WallpaperClock { android.os.SystemClock.elapsedRealtime() },
            object : WallpaperFrameScheduler {
                override fun post(delayMillis: Long, frame: Runnable) { handler.postDelayed(frame, delayMillis) }
                override fun cancel(frame: Runnable) { handler.removeCallbacks(frame) }
            }) {
                val delegate = createRenderer(checkNotNull(holder)) { currentPhase }
                object : WallpaperRenderer {
                    override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
                        val result = delegate.render(timeMillis, reducedMotion)
                        if (result == WallpaperFrameResult.DRAWN) {
                            val count = renderCount.incrementAndGet()
                            onPhaseRendered(currentPhase, count)
                        }
                        return result
                    }

                    override fun close() = delegate.close()
                }
            }
        private val phaseScheduler = PhaseScheduler(
            clock = phaseClock,
            zoneId = ::currentZoneId,
            callbackScheduler = createPhaseCallbackScheduler(handler),
            policy = phasePolicy,
            onSelection = ::applySelection,
        )
        private var observingTime = false
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post { phaseScheduler.invalidate() }
            }
        }
        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.post { refreshPlatformFacts(triggerCharging = true) }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loop.setReducedMotion(true)
            setTouchEventsEnabled(SceneTrigger.TAP in scene.definition.declaredTriggers)
            try {
                observingTime = registerTimeChangeReceiver(timeReceiver)
            } catch (_: RuntimeException) { /* Resume still samples fresh clock and ZoneId. */ }
            try {
                observingPower = registerPowerReceiver(powerReceiver)
            } catch (_: RuntimeException) { /* Resume still samples fresh power facts. */ }
            scope.launch { settings.motionMode.collect { mode ->
                handler.post {
                    motionPreference = mode
                    scene.setMotionMode(mode)
                    refreshLoopPolicy()
                }
            } }
            scope.launch { settings.touchReactionsEnabled.collect { enabled -> handler.post {
                interactionsPreference = enabled
                scene.setInteractionsEnabled(enabled)
                loop.invalidate()
            } } }
            FixtureRuntimeTestApi.register(engineId, testControl())
            handler.post { refreshPlatformFacts(triggerCharging = false) }
        }

        override fun onSurfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(surfaceHolder, format, width, height)
            handler.post {
                phaseScheduler.stop()
                schedulerActive = false
                surfaceActive = false
                loop.setSurfaceValid(false)
                holder?.let(scenesByHolder::remove)
                holder = surfaceHolder
                scenesByHolder[surfaceHolder] = scene
                val valid = width > 0 && height > 0 && isSurfaceValid(surfaceHolder)
                surfaceActive = valid
                if (valid && engineVisible) startPhaseScheduler()
                loop.setSurfaceValid(valid)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            engineVisible = visible
            if (visible) handler.post {
                scene.resume()
                refreshPlatformFacts(triggerCharging = false)
                if (surfaceActive) startPhaseScheduler()
                loop.setVisible(true)
            } else fence { done ->
                phaseScheduler.stop()
                schedulerActive = false
                scene.stop()
                loop.setVisible(false)
                done()
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                handler.post { routeSignal(SceneTrigger.TAP, signalAvailable = true) }
            }
            super.onTouchEvent(event)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            val previous = previousOffset
            previousOffset = xOffset
            if (previous != null && abs(xOffset - previous) >= OFFSET_EPSILON) {
                val supported = xOffsetStep > 0f
                handler.post {
                    routeSignal(SceneTrigger.OFFSET, supported, (xOffset - previous).coerceIn(-1f, 1f))
                }
            }
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
        }

        override fun onSurfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
            fence { complete ->
                if (!schedulerActive) applySelection(phasePolicy.select(phaseClock, currentZoneId()))
                loop.requestRedraw(complete)
            }
            super.onSurfaceRedrawNeeded(surfaceHolder)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            fence { done ->
                phaseScheduler.stop()
                schedulerActive = false
                surfaceActive = false
                scene.stop()
                loop.setSurfaceValid(false)
                scenesByHolder.remove(holder)
                this.holder = null
                done()
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            scope.cancel()
            try {
                fence { done ->
                    phaseScheduler.close()
                    schedulerActive = false
                    surfaceActive = false
                    scene.stop()
                    loop.close()
                    holder?.let(scenesByHolder::remove)
                    holder = null
                    done()
                }
            } finally {
                try { if (observingTime) unregisterTimeChangeReceiver(timeReceiver) }
                catch (_: RuntimeException) { /* Receiver may already be detached. */ }
                try { if (observingPower) unregisterPowerReceiver(powerReceiver) }
                catch (_: RuntimeException) { /* Receiver may already be detached. */ }
                FixtureRuntimeTestApi.unregister(engineId)
                engines -= this
                handler.removeCallbacksAndMessages(null)
                worker.quitSafely()
                super.onDestroy()
            }
        }

        override fun dump(prefix: String, fd: FileDescriptor, out: PrintWriter, args: Array<out String>) {
            super.dump(prefix, fd, out, args)
            out.println("${prefix}livosphereFixture ${debugState()}")
        }

        fun debugState(): String = "engineId=$engineId phase=$currentPhase renderCount=${renderCount.get()} " +
            "schedulerActive=$schedulerActive nextDeadline=${nextDeadline ?: "none"} " +
            "visible=$engineVisible surface=$surfaceActive motionPreference=${motionPreference ?: "UNKNOWN"} " +
            "interactions=${interactionsPreference ?: "UNKNOWN"} requested=${scene.requestedLevel} " +
            "effective=${scene.motion().effectiveLevel ?: "OFF"} effects=${scene.motion().allowedEffectIds.sorted()} " +
            "power=${scene.powerFacts} pendingTriggers=${scene.pendingTriggerCount()}"

        private fun startPhaseScheduler() {
            schedulerActive = true
            phaseScheduler.start()
        }

        private fun applySelection(selection: PhaseSelection) {
            val changed = currentPhase != selection.phase
            currentPhase = selection.phase
            nextDeadline = selection.nextDeadline
            if (changed) loop.invalidate()
        }

        private fun refreshPlatformFacts(triggerCharging: Boolean) {
            val oldCharging = scene.powerFacts.charging
            val fresh = injectedPowerFacts ?: try { readPowerFacts() } catch (_: RuntimeException) {
                ScenePowerFacts(null, null, null)
            }
            scene.setPowerFacts(fresh)
            scene.setSystemReduced(injectedSystemReduced ?: isSystemReducedMotion())
            refreshLoopPolicy()
            if (triggerCharging && oldCharging != true && fresh.charging == true) {
                routeSignal(SceneTrigger.CHARGING, signalAvailable = true)
            }
        }

        private fun refreshLoopPolicy() {
            loop.setReducedMotion(scene.motion().staticFrame)
            loop.invalidate()
        }

        private fun routeSignal(trigger: SceneTrigger, signalAvailable: Boolean, magnitude: Float = 1f): TriggerDispatch {
            val result = scene.trigger(
                trigger = trigger,
                nowMillis = android.os.SystemClock.elapsedRealtime(),
                signalAvailable = signalAvailable,
                magnitude = magnitude,
            )
            if (result is TriggerDispatch.Applied) loop.invalidate()
            return result
        }

        private fun testControl() = object : FixtureEngineControl {
            override fun snapshot(): FixtureRuntimeSnapshot {
                var snapshot: FixtureRuntimeSnapshot? = null
                fence { done -> snapshot = snapshotUnsafe(); done() }
                return checkNotNull(snapshot)
            }

            override fun setLevel(level: AuthoredEffectLevel) = fence { done ->
                scene.setRequestedLevel(level); refreshLoopPolicy(); done()
            }
            override fun setSystemReduced(reduced: Boolean?) = fence { done ->
                injectedSystemReduced = reduced; refreshPlatformFacts(false); done()
            }
            override fun setPowerFacts(facts: ScenePowerFacts?) = fence { done ->
                injectedPowerFacts = facts; refreshPlatformFacts(true); done()
            }
            override fun dispatch(trigger: SceneTrigger, available: Boolean, magnitude: Float): TriggerDispatch {
                var result: TriggerDispatch? = null
                fence { done -> result = routeSignal(trigger, available, magnitude); done() }
                return checkNotNull(result)
            }
            override fun stopForTest() = fence { done -> scene.stop(); loop.setVisible(false); done() }
            override fun resumeForTest() = fence { done ->
                scene.resume(); refreshPlatformFacts(false); loop.setVisible(engineVisible); done()
            }
        }

        private fun snapshotUnsafe(): FixtureRuntimeSnapshot {
            val motion = scene.motion()
            return FixtureRuntimeSnapshot(
                engineId = engineId,
                phase = currentPhase,
                renderCount = renderCount.get(),
                preview = isPreview,
                visible = engineVisible,
                surfaceValid = surfaceActive,
                schedulerActive = schedulerActive,
                requestedLevel = scene.requestedLevel,
                effectiveLevel = motion.effectiveLevel,
                allowedEffectIds = motion.allowedEffectIds,
                staticFrame = motion.staticFrame,
                powerFacts = scene.powerFacts,
                interactionsEnabled = scene.interactionsEnabled,
                pendingTriggerCount = scene.pendingTriggerCount(),
                lastDispatch = scene.lastDispatch,
                objects = scene.lastFrame?.objects.orEmpty().associate { value ->
                    value.id to FixtureObjectSnapshot(value.x, value.y, value.size)
                },
            )
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

    companion object {
        const val WALLPAPER_ID = "isolation-fixture-wallpaper"
        private const val OFFSET_EPSILON = 0.01f
        private val NEXT_ENGINE_ID = AtomicLong()
    }
}

data class FixtureObjectSnapshot(val x: Float, val y: Float, val size: Float)
data class FixtureRuntimeSnapshot(
    val engineId: Long,
    val phase: DayPhase,
    val renderCount: Long,
    val preview: Boolean,
    val visible: Boolean,
    val surfaceValid: Boolean,
    val schedulerActive: Boolean,
    val requestedLevel: AuthoredEffectLevel,
    val effectiveLevel: AuthoredEffectLevel?,
    val allowedEffectIds: Set<String>,
    val staticFrame: Boolean,
    val powerFacts: ScenePowerFacts,
    val interactionsEnabled: Boolean,
    val pendingTriggerCount: Int,
    val lastDispatch: TriggerDispatch?,
    val objects: Map<String, FixtureObjectSnapshot>,
)

internal interface FixtureEngineControl {
    fun snapshot(): FixtureRuntimeSnapshot
    fun setLevel(level: AuthoredEffectLevel)
    fun setSystemReduced(reduced: Boolean?)
    fun setPowerFacts(facts: ScenePowerFacts?)
    fun dispatch(trigger: SceneTrigger, available: Boolean, magnitude: Float): TriggerDispatch
    fun stopForTest()
    fun resumeForTest()
}

/** Debug-fixture-only seam. It targets the same serial route used by platform Engine callbacks. */
object FixtureRuntimeTestApi {
    private val controls = ConcurrentHashMap<Long, FixtureEngineControl>()
    internal fun register(engineId: Long, control: FixtureEngineControl) { controls[engineId] = control }
    internal fun unregister(engineId: Long) { controls.remove(engineId) }
    fun engineIds(): Set<Long> = controls.keys.toSet()
    fun snapshot(engineId: Long): FixtureRuntimeSnapshot = control(engineId).snapshot()
    fun setLevel(engineId: Long, level: AuthoredEffectLevel) = control(engineId).setLevel(level)
    fun setSystemReduced(engineId: Long, reduced: Boolean?) = control(engineId).setSystemReduced(reduced)
    fun setPowerFacts(engineId: Long, facts: ScenePowerFacts?) = control(engineId).setPowerFacts(facts)
    fun dispatch(
        engineId: Long,
        trigger: SceneTrigger,
        available: Boolean = true,
        magnitude: Float = 1f,
    ): TriggerDispatch = control(engineId).dispatch(trigger, available, magnitude)
    fun stop(engineId: Long) = control(engineId).stopForTest()
    fun resume(engineId: Long) = control(engineId).resumeForTest()
    private fun control(engineId: Long) = checkNotNull(controls[engineId]) { "Unknown fixture Engine: $engineId" }
}

private data class PhaseEnvironment(
    val background: Int,
    val ground: Int,
    val objectColor: Int,
    val geometry: String,
)

/** Reads the four manifest-declared raw environments and renders their distinct geometry. */
private class FixtureRenderer(
    resources: Resources,
    private val holder: SurfaceHolder,
    private val phase: () -> DayPhase,
    private val scene: FixtureSceneController,
) : WallpaperRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val environments = mapOf(
        DayPhase.MORNING to readEnvironment(resources, R.raw.ls_isolation_fixture_wallpaper_morning),
        DayPhase.DAY to readEnvironment(resources, R.raw.ls_isolation_fixture_wallpaper_day),
        DayPhase.EVENING to readEnvironment(resources, R.raw.ls_isolation_fixture_wallpaper_evening),
        DayPhase.NIGHT to readEnvironment(resources, R.raw.ls_isolation_fixture_wallpaper_night),
    )

    override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
        if (!holder.surface.isValid) return WallpaperFrameResult.INVALID
        val canvas = try { holder.lockCanvas() } catch (_: RuntimeException) { return retry() }
            ?: return retry()
        var result = WallpaperFrameResult.DRAWN
        try {
            drawEnvironment(canvas, checkNotNull(environments[phase()]))
            drawObjects(canvas, scene.frame(timeMillis))
        }
        finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (_: RuntimeException) { result = retry() }
        }
        return result
    }

    private fun drawEnvironment(canvas: Canvas, environment: PhaseEnvironment) {
        canvas.drawColor(environment.background)
        paint.color = environment.ground
        canvas.drawRect(0f, canvas.height * 0.68f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
        paint.color = environment.objectColor
        when (environment.geometry) {
            "sunrise" -> {
                canvas.drawCircle(canvas.width * 0.24f, canvas.height * 0.68f, canvas.width * 0.13f, paint)
                canvas.drawRect(canvas.width * 0.58f, canvas.height * 0.42f, canvas.width * 0.66f, canvas.height * 0.68f, paint)
            }
            "high-sun" -> {
                canvas.drawCircle(canvas.width * 0.76f, canvas.height * 0.20f, canvas.width * 0.10f, paint)
                canvas.drawOval(0f, canvas.height * 0.56f, canvas.width * 0.62f, canvas.height * 0.78f, paint)
            }
            "skyline" -> {
                canvas.drawRect(canvas.width * 0.15f, canvas.height * 0.43f, canvas.width * 0.31f, canvas.height * 0.68f, paint)
                canvas.drawRect(canvas.width * 0.39f, canvas.height * 0.52f, canvas.width * 0.57f, canvas.height * 0.68f, paint)
                canvas.drawCircle(canvas.width * 0.78f, canvas.height * 0.58f, canvas.width * 0.09f, paint)
            }
            "moon-stars" -> {
                canvas.drawCircle(canvas.width * 0.25f, canvas.height * 0.20f, canvas.width * 0.10f, paint)
                repeat(4) { index ->
                    val x = canvas.width * (0.52f + index * 0.11f)
                    val y = canvas.height * (0.15f + (index % 2) * 0.12f)
                    canvas.drawCircle(x, y, canvas.width * 0.012f, paint)
                }
            }
            else -> error("Unknown fixture geometry: ${environment.geometry}")
        }
    }

    private fun drawObjects(canvas: Canvas, frame: app.livosphere.wallpapers.engine.SceneFrame) {
        frame.objects.forEachIndexed { index, objectState ->
            paint.color = when (objectState.id) {
                "orb" -> Color.WHITE
                "satellite" -> Color.CYAN
                else -> Color.MAGENTA
            }
            val x = canvas.width * objectState.x
            val y = canvas.height * objectState.y
            val radius = canvas.width * objectState.size
            if (index == 1) canvas.drawRect(x - radius, y - radius, x + radius, y + radius, paint)
            else canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun retry() = if (holder.surface.isValid) WallpaperFrameResult.RETRY else WallpaperFrameResult.INVALID
    override fun close() = Unit
}

private fun readEnvironment(resources: Resources, resourceId: Int): PhaseEnvironment {
    resources.openRawResource(resourceId).use { input ->
        val parser = Xml.newPullParser().apply { setInput(input, Charsets.UTF_8.name()) }
        while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) parser.next()
        require(parser.name == "phase-environment") { "Expected phase-environment fixture" }
        fun attribute(name: String) = requireNotNull(parser.getAttributeValue(null, name)) { "Missing $name" }
        return PhaseEnvironment(
            background = Color.parseColor(attribute("background")),
            ground = Color.parseColor(attribute("ground")),
            objectColor = Color.parseColor(attribute("objectColor")),
            geometry = attribute("geometry"),
        )
    }
}
