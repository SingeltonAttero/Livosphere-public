package app.livosphere

import android.content.Context
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.DayPhase
import app.livosphere.settings.*
import app.livosphere.wallpapers.engine.*
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FixtureEngineCallbacksTest {
    @Test fun actualBEngineReadsItsOwnerAndRedrawsBeforeVisibilityThenStops() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = OwnedSettingsFixture()
        val a = fixture.repository("contour-wallpaper")
        val b = fixture.repository(FixtureWallpaperService.WALLPAPER_ID)
        val motionRead = CountDownLatch(1); val touchRead = CountDownLatch(1)
        val frames = AtomicInteger(); val released = AtomicInteger()
        val renderedReducedMotion = AtomicReference<Boolean?>()
        val settings = object : WallpaperSettingsRepository(fixture.settings, b.wallpaperId) {
            override val motionMode = super.motionMode.onEach {
                if (it == WallpaperMotionMode.REDUCED) motionRead.countDown()
            }
            override val touchReactionsEnabled = super.touchReactionsEnabled.onEach {
                if (it == false) touchRead.countDown()
            }
        }
        val service = object : FixtureWallpaperService() {
            init { attachBaseContext(context) }
            override fun createSettings() = settings
            override fun isSurfaceValid(holder: SurfaceHolder) = true
            override fun createRenderer(holder: SurfaceHolder, phase: () -> DayPhase) = object : WallpaperRenderer {
                override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
                    renderedReducedMotion.set(reducedMotion)
                    frames.incrementAndGet()
                    return WallpaperFrameResult.DRAWN
                }
                override fun close() { released.incrementAndGet() }
            }
        }
        var engine: WallpaperService.Engine? = null
        try {
            a.setMotionMode(WallpaperMotionMode.OFF); a.setTouchReactionsEnabled(true)
            b.setMotionMode(WallpaperMotionMode.REDUCED); b.setTouchReactionsEnabled(false)
            instrumentation.runOnMainSync {
                engine = service.onCreateEngine().also {
                    it.onCreate(it.surfaceHolder)
                    it.onSurfaceChanged(it.surfaceHolder, 1, 320, 600)
                }
            }
            assertTrue(motionRead.await(5, TimeUnit.SECONDS)); assertTrue(touchRead.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { engine!!.onSurfaceRedrawNeeded(engine!!.surfaceHolder) }
            assertEquals(false, renderedReducedMotion.get())
            assertEquals(1, frames.get()); assertEquals(1, released.get())
            instrumentation.runOnMainSync {
                engine!!.onVisibilityChanged(true)
                engine!!.onSurfaceRedrawNeeded(engine!!.surfaceHolder)
                engine!!.onVisibilityChanged(false)
            }
            assertTrue(frames.get() >= 2)
            val stopped = frames.get()
            instrumentation.runOnMainSync {
                engine!!.onSurfaceDestroyed(engine!!.surfaceHolder)
                engine!!.onSurfaceRedrawNeeded(engine!!.surfaceHolder)
            }
            assertEquals(stopped, frames.get())
            assertEquals(WallpaperMotionMode.OFF, a.motionMode.first())
        } finally {
            instrumentation.runOnMainSync { engine?.onDestroy() }
            fixture.close()
        }
    }
}
