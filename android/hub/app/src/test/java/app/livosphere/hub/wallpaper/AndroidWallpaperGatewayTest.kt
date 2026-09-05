package app.livosphere.hub.wallpaper

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AndroidWallpaperGatewayTest {
    @Test fun `cancellation during blocking probe preserves last completed snapshot`() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val block = AtomicBoolean(false)
        val target = WallpaperComponent("app.livosphere", "TargetService")
        val probe = object : WallpaperPlatformProbe {
            override val deviceApi = 34
            override fun hasLiveWallpaperFeature() = true
            override fun isWallpaperSupported() = true
            override fun isSetWallpaperAllowed(): Boolean {
                if (block.get()) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return true
            }
            override fun servicePresence() = WallpaperPresence.AVAILABLE
            override fun resolvesDirectPreview() = true
            override fun resolvesChooser() = true
            override fun appliedComponent(surface: WallpaperSurface) = if (block.get()) null else target
        }
        val gateway = AndroidWallpaperGateway(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) { at ->
            WallpaperObservation.capture(probe, target, 29, at)
        }
        val baseline = gateway.refresh()
        block.set(true)
        val refresh = launch(Dispatchers.Default) { gateway.refresh() }
        try {
            assertTrue("Refresh never entered the platform probe", entered.await(5, TimeUnit.SECONDS))
            refresh.cancel()
        } finally {
            release.countDown()
            refresh.join()
        }
        assertTrue(refresh.isCancelled)
        assertSame(baseline, gateway.snapshots.value)
        block.set(false)
        assertEquals(baseline, gateway.refresh())
    }
}
