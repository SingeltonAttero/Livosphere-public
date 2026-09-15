package app.livosphere

import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Handler
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.DayPhase
import app.livosphere.settings.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.engine.PhaseCallbackScheduler
import app.livosphere.wallpapers.engine.WallpaperFrameResult
import app.livosphere.wallpapers.engine.WallpaperRenderer
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FixturePhaseCallbacksTest {
    private class MutableClock(@Volatile var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private class CallbackQueue : PhaseCallbackScheduler {
        private data class Entry(val original: Runnable, val dispatched: Runnable)
        private val entries = Collections.synchronizedList(mutableListOf<Entry>())
        @Volatile var handler: Handler? = null
        override fun post(delayMillis: Long, callback: Runnable) {
            entries += Entry(callback, Runnable { checkNotNull(handler).post(callback) })
        }
        override fun cancel(callback: Runnable) { entries.removeAll { it.original === callback } }
        fun size(): Int = entries.size
        fun peek(): Runnable = entries.single().dispatched
        fun fire() = entries.removeAt(0).dispatched.run()
    }

    @Test fun actualEngineReschedulesStaticPhaseAcrossBoundaryAndLifecycleInvalidation() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val clock = MutableClock(Instant.parse("2026-09-14T17:59:00Z"))
        val queue = CallbackQueue()
        val zone = AtomicReference<ZoneId>(ZoneOffset.UTC)
        val renderedStaticFrame = AtomicReference<Boolean?>()
        lateinit var timeReceiver: BroadcastReceiver
        val renders = Collections.synchronizedList(mutableListOf<DayPhase>())
        val fixture = OwnedSettingsFixture()
        val settings = object : WallpaperSettingsRepository(
            fixture.settings,
            FixtureWallpaperService.WALLPAPER_ID,
        ) {
            override val motionMode = emptyFlow<WallpaperMotionMode?>()
            override val touchReactionsEnabled = emptyFlow<Boolean?>()
        }
        val service = object : FixtureWallpaperService() {
            init { attachBaseContext(context) }
            override fun createSettings() = settings
            override fun createPhaseClock(): Clock = clock
            override fun currentZoneId(): ZoneId = zone.get()
            override fun isSurfaceValid(holder: SurfaceHolder) = true
            override fun createPhaseCallbackScheduler(handler: Handler): PhaseCallbackScheduler {
                queue.handler = handler
                return queue
            }
            override fun registerTimeChangeReceiver(receiver: BroadcastReceiver): Boolean {
                timeReceiver = receiver
                return true
            }
            override fun unregisterTimeChangeReceiver(receiver: BroadcastReceiver) = Unit
            override fun createRenderer(holder: SurfaceHolder, phase: () -> DayPhase) = object : WallpaperRenderer {
                override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
                    renderedStaticFrame.set(reducedMotion)
                    return WallpaperFrameResult.DRAWN
                }
                override fun close() = Unit
            }
            override fun onPhaseRendered(phase: DayPhase, renderCount: Long) { renders += phase }
        }
        var engine: WallpaperService.Engine? = null
        try {
            instrumentation.runOnMainSync {
                engine = service.onCreateEngine().also {
                    it.onCreate(it.surfaceHolder)
                    it.onSurfaceChanged(it.surfaceHolder, 1, 320, 600)
                    it.onVisibilityChanged(true)
                }
            }
            await {
                renders.lastOrNull() == DayPhase.DAY && queue.size() == 1 &&
                    renderedStaticFrame.get() != null
            }
            assertEquals(false, renderedStaticFrame.get())
            val beforeBoundary = renders.size
            clock.now = Instant.parse("2026-09-14T18:00:00Z")
            queue.fire()
            await { renders.lastOrNull() == DayPhase.EVENING }
            assertEquals(beforeBoundary + 1, renders.size)
            assertEquals(1, queue.size())

            val stale = queue.peek()
            instrumentation.runOnMainSync { engine!!.onVisibilityChanged(false) }
            assertEquals(0, queue.size())
            stale.run()
            Thread.sleep(100)
            assertEquals(beforeBoundary + 1, renders.size)

            clock.now = Instant.parse("2026-09-14T21:00:00Z")
            instrumentation.runOnMainSync { engine!!.onVisibilityChanged(true) }
            await { renders.lastOrNull() == DayPhase.NIGHT && queue.size() == 1 }

            clock.now = Instant.parse("2026-09-15T05:00:00Z")
            timeReceiver.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))
            await { renders.lastOrNull() == DayPhase.MORNING }
            clock.now = Instant.parse("2026-09-15T08:00:00Z")
            timeReceiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
            await { renders.lastOrNull() == DayPhase.DAY }
            clock.now = Instant.parse("2026-09-15T17:00:00Z")
            zone.set(ZoneId.of("Europe/Kaliningrad"))
            timeReceiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
            await { renders.lastOrNull() == DayPhase.EVENING }

            instrumentation.runOnMainSync { engine!!.onSurfaceDestroyed(engine!!.surfaceHolder) }
            assertEquals(0, queue.size())
        } finally {
            instrumentation.runOnMainSync { engine?.onDestroy() }
            fixture.close()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition()) {
            if (System.nanoTime() >= deadline) error("Timed out waiting for Fixture phase callback")
            Thread.sleep(10)
        }
    }
}
