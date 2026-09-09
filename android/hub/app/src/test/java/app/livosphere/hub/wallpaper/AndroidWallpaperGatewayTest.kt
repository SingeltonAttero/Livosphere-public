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
        val gateway = AndroidWallpaperGateway(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), WallpaperTarget("contour-wallpaper", target, 29)) { _, at ->
            WallpaperObservation.capture(probe, target, 29, at, "contour-wallpaper")
        }
        val baseline = gateway.refresh(checkNotNull(gateway.initialBrowsingTarget))
        block.set(true)
        val refresh = launch(Dispatchers.Default) { gateway.refresh(checkNotNull(gateway.initialBrowsingTarget)) }
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
        assertEquals(baseline, gateway.refresh(checkNotNull(gateway.initialBrowsingTarget)))
    }
    @Test fun `blocked A does not prevent B and late A cannot replace B snapshot`() = runBlocking {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val a = WallpaperTarget("a-wallpaper", WallpaperComponent("app.livosphere", "A"), 29)
        val b = WallpaperTarget("b-wallpaper", WallpaperComponent("app.livosphere", "B"), 29)
        val gateway = AndroidWallpaperGateway(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), a) { target, at ->
            if (target == a) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            val unknown = WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO)
            PhoneWallpaperSnapshot(at, target.component, 29, 37, WallpaperFact.Known(true),
                WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Known(WallpaperPresence.AVAILABLE),
                WallpaperFact.Known(true), WallpaperFact.Known(true), unknown, unknown, target.wallpaperId)
        }
        val blocked = launch(Dispatchers.Default) { gateway.refresh(a) }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val resultB = kotlinx.coroutines.withTimeout(2_000) { gateway.refresh(b) }
            assertEquals(b, resultB.target)
            assertEquals(resultB, gateway.snapshots.value)
            release.countDown(); blocked.join()
            assertEquals(resultB, gateway.snapshots.value)
        } finally { release.countDown(); blocked.join() }
    }

}
