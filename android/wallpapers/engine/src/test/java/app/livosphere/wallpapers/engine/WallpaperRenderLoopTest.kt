package app.livosphere.wallpapers.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WallpaperRenderLoopTest {
    private class Harness {
        var now = 0L
        val queue = mutableListOf<Pair<Long, Runnable>>()
        val frames = mutableListOf<Pair<Long, Boolean>>()
        var created = 0
        var closed = 0
        var surfaceValid = true
        var temporaryUnavailable = false
        var staticFallback = false
        var creationFailure: Error? = null
        var creationException: RuntimeException? = null
        var drawException = false
        var closeException = false
        val loop = WallpaperRenderLoop(WallpaperClock { now }, object : WallpaperFrameScheduler {
            override fun post(delayMillis: Long, frame: Runnable) { queue += (now + delayMillis) to frame }
            override fun cancel(frame: Runnable) { queue.removeAll { it.second === frame } }
        }) {
            created++
            creationFailure?.let { throw it }
            creationException?.let { throw it }
            object : WallpaperRenderer {
                override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
                    frames += timeMillis to reducedMotion
                    if (drawException) error("Permanent scene failure")
                    return if (!surfaceValid) WallpaperFrameResult.INVALID
                    else if (staticFallback) WallpaperFrameResult.STATIC
                    else if (temporaryUnavailable) WallpaperFrameResult.RETRY else WallpaperFrameResult.DRAWN
                }
                override fun close() { closed++; if (closeException) error("Simulated cleanup failure") }
            }
        }
        fun ready() { loop.setSurfaceValid(true); loop.setVisible(true) }
        fun frame() {
            val (at, task) = queue.removeAt(0)
            now = maxOf(now, at)
            task.run()
        }
    }

    @Test fun hiddenAndInvalidStatesNeverAllocateOrSchedule() {
        val h = Harness()
        h.loop.setVisible(true)
        assertTrue(h.queue.isEmpty())
        h.loop.setVisible(false)
        h.loop.setSurfaceValid(true)
        assertTrue(h.queue.isEmpty())
        assertEquals(0, h.created)
    }

    @Test fun coldStartSchedulesAtMost30FpsAndLateFramesUseCurrentClock() {
        val h = Harness()
        h.ready(); h.frame()
        assertEquals(listOf(0L to false), h.frames)
        assertEquals(34L, h.queue.single().first)
        h.now = 48_003; h.frame()
        assertEquals(48_003L, h.frames.last().first)
        assertEquals(1, h.queue.size)
        assertEquals(48_037L, h.queue.single().first)
    }

    @Test fun hideReleasesAndStaleCallbacksCannotDrawEvenAfterReturn() {
        val h = Harness()
        h.ready(); h.frame()
        val stale = h.queue.single().second
        h.loop.setVisible(false)
        assertTrue(h.queue.isEmpty())
        assertEquals(1, h.closed)
        h.now = 24_000
        h.loop.setVisible(true)
        stale.run()
        assertEquals(1, h.frames.size)
        h.frame()
        assertEquals(24_000L, h.frames.last().first)
        assertEquals(2, h.created)
    }

    @Test fun invalidRenderAndSurfaceDestroyReleaseUntilRecreated() {
        val h = Harness()
        h.ready(); h.frame()
        h.surfaceValid = false; h.frame()
        assertTrue(h.queue.isEmpty())
        assertEquals(1, h.closed)
        h.loop.setSurfaceValid(false)
        h.surfaceValid = true
        h.loop.setSurfaceValid(true); h.frame()
        assertEquals(2, h.created)
        h.loop.setSurfaceValid(false)
        assertTrue(h.queue.isEmpty())
        assertEquals(2, h.closed)
    }

    @Test fun reducedDrawsOnceThenChangesResumeWithoutReplay() {
        val h = Harness()
        h.ready(); h.frame()
        val stale = h.queue.single().second
        h.loop.setReducedMotion(true); h.frame(); stale.run()
        assertEquals(2, h.frames.size)
        assertTrue(h.frames.last().second)
        assertTrue(h.queue.isEmpty())
        h.now = 36_000
        h.loop.setReducedMotion(false); h.frame()
        assertEquals(36_000L to false, h.frames.last())
        assertEquals(1, h.queue.size)
    }

    @Test fun temporarilyUnavailableValidCanvasRetriesEvenWhenReducedThenStops() {
        val h = Harness()
        h.loop.setReducedMotion(true)
        h.temporaryUnavailable = true
        h.ready(); h.frame()
        assertEquals(34L, h.queue.single().first)
        assertEquals(0, h.closed)
        h.temporaryUnavailable = false; h.frame()
        assertTrue(h.queue.isEmpty())
        assertEquals(1, h.created)
        assertEquals(2, h.frames.size)
    }

    @Test fun systemRedrawBeforeVisibilityDrawsOnceReleasesAndThenVisibleCreatesFreshRenderer() {
        val h = Harness()
        h.loop.setSurfaceValid(true)
        var completed = false
        h.loop.requestRedraw { completed = true; assertEquals(1, h.closed) }
        assertTrue(completed)
        assertEquals(1, h.frames.size)
        assertTrue(h.queue.isEmpty())
        h.loop.setVisible(true); h.frame()
        assertEquals(2, h.created)
    }

    @Test fun reducedRedrawReplacesPendingQueueAndCompletesOnlyAfterPublishedFrame() {
        val h = Harness()
        h.loop.setReducedMotion(true)
        h.ready()
        val stale = h.queue.single().second
        h.temporaryUnavailable = true
        var completed = false
        h.loop.requestRedraw { completed = true }
        assertFalse(completed)
        assertEquals(34L, h.queue.single().first)
        stale.run()
        assertEquals(1, h.frames.size)
        h.temporaryUnavailable = false; h.frame()
        assertTrue(completed)
        assertTrue(h.queue.isEmpty())
        h.loop.requestRedraw { completed = true }
        assertEquals(3, h.frames.size)
        assertTrue(h.queue.isEmpty())
    }

    @Test fun invalidDestroyAndHideCompletePendingRedrawWithoutFurtherDrawing() {
        listOf<(WallpaperRenderLoop) -> Unit>({ it.setSurfaceValid(false) },
            { it.close() }, { it.setVisible(false) }).forEach { stop ->
            val h = Harness()
            h.ready(); h.temporaryUnavailable = true
            var complete = false
            h.loop.requestRedraw { complete = true }
            val stale = h.queue.single().second
            stop(h.loop)
            assertTrue(complete)
            assertTrue(h.queue.isEmpty())
            stale.run()
            assertEquals(1, h.frames.size)
            assertEquals(1, h.closed)
        }
    }

    @Test fun permanentRenderFailureAndStaticFallbackStayStoppedUntilSurfaceRecreation() {
        for (throws in listOf(true, false)) {
            val h = Harness()
            h.drawException = throws; h.staticFallback = !throws
            h.ready(); h.frame()
            assertTrue(h.queue.isEmpty())
            assertEquals(1, h.closed)
            h.loop.setVisible(false); h.loop.setVisible(true)
            h.loop.requestRedraw { }
            assertTrue(h.queue.isEmpty())
            assertEquals(1, h.frames.size)
            h.drawException = false; h.staticFallback = false
            h.loop.setSurfaceValid(false); h.loop.setSurfaceValid(true); h.frame()
            assertEquals(2, h.created)
        }
    }

    @Test fun factoryAllocationFailureIsTerminalAndAcknowledgesRedrawWithoutCrashOrRetry() {
        for (oom in listOf(true, false)) {
            val h = Harness()
            if (oom) h.creationFailure = OutOfMemoryError("Simulated bitmap allocation")
            else h.creationException = IllegalArgumentException("Simulated factory failure")
            h.ready()
            var complete = false
            h.loop.requestRedraw { complete = true }
            assertTrue(complete)
            assertTrue(h.queue.isEmpty())
            assertTrue(h.frames.isEmpty())
            h.loop.setVisible(false); h.loop.setVisible(true)
            assertEquals(1, h.created)
            assertTrue(h.queue.isEmpty())
            h.creationFailure = null; h.creationException = null
            h.loop.setSurfaceValid(false); h.loop.setSurfaceValid(true); h.frame()
            assertEquals(2, h.created)
        }
    }

    @Test fun permanentlyUnavailableSystemRedrawCompletesAfterThreeAttemptsWithoutMainLifecycleHelp() {
        for (visible in listOf(false, true)) {
            val h = Harness()
            h.loop.setReducedMotion(true)
            h.loop.setSurfaceValid(true)
            h.loop.setVisible(visible)
            h.temporaryUnavailable = true
            var complete = false
            h.loop.requestRedraw { complete = true }
            assertFalse(complete)
            h.frame(); assertFalse(complete)
            h.frame(); assertTrue(complete)
            assertEquals(3, h.frames.size)
            assertTrue(h.queue.isEmpty())
            assertEquals(1, h.closed)
            h.temporaryUnavailable = false
            h.loop.requestRedraw { }
            assertEquals(4, h.frames.size)
        }
    }

    @Test fun rendererCleanupFailureStillCancelsQueueAndCompletesPendingRedraw() {
        val h = Harness()
        h.ready(); h.temporaryUnavailable = true; h.closeException = true
        var complete = false
        h.loop.requestRedraw { complete = true }
        val stale = h.queue.single().second
        h.loop.close()
        assertTrue(complete)
        assertTrue(h.queue.isEmpty())
        assertEquals(1, h.closed)
        stale.run()
        assertEquals(1, h.frames.size)
    }

    @Test fun enginesOwnIndependentResourcesAndDestroyIsTerminal() {
        val preview = Harness(); val home = Harness()
        preview.ready(); home.ready(); preview.frame(); home.frame()
        val stale = preview.queue.single().second
        preview.loop.close(); preview.loop.close()
        preview.loop.setVisible(true); preview.loop.setSurfaceValid(true); stale.run()
        home.frame()
        assertEquals(1, preview.closed)
        assertEquals(1, preview.frames.size)
        assertEquals(2, home.frames.size)
        assertEquals(0, home.closed)
    }

    @Test fun teardownWaitsForInflightFrameAndThenReleases() {
        val entered = CountDownLatch(1)
        val releaseFrame = CountDownLatch(1)
        val teardownReturned = CountDownLatch(1)
        lateinit var scheduled: Runnable
        var closed = false
        val loop = WallpaperRenderLoop(WallpaperClock { 0 }, object : WallpaperFrameScheduler {
            override fun post(delayMillis: Long, frame: Runnable) { scheduled = frame }
            override fun cancel(frame: Runnable) = Unit
        }) {
            object : WallpaperRenderer {
                override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
                    entered.countDown()
                    check(releaseFrame.await(2, TimeUnit.SECONDS))
                    return WallpaperFrameResult.DRAWN
                }
                override fun close() { closed = true }
            }
        }
        loop.setSurfaceValid(true); loop.setVisible(true)
        val worker = Thread { scheduled.run() }.apply { start() }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val teardown = Thread { loop.setSurfaceValid(false); teardownReturned.countDown() }.apply { start() }
        assertFalse(teardownReturned.await(50, TimeUnit.MILLISECONDS))
        releaseFrame.countDown()
        assertTrue(teardownReturned.await(2, TimeUnit.SECONDS))
        worker.join(); teardown.join()
        assertTrue(closed)
        scheduled.run() // A scheduler delivering a cancelled callback still cannot draw.
    }
}
