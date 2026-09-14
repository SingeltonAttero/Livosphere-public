package app.livosphere.wallpapers.engine

import app.livosphere.contract.DayPhase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseSchedulerTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private class Queue : PhaseCallbackScheduler {
        val callbacks = mutableListOf<Pair<Long, Runnable>>()
        override fun post(delayMillis: Long, callback: Runnable) { callbacks += delayMillis to callback }
        override fun cancel(callback: Runnable) { callbacks.removeAll { it.second === callback } }
        fun fire() = callbacks.removeAt(0).second.run()
    }

    @Test fun keepsOneCallbackAndEarlyOrLateDispatchUsesFreshTime() {
        val clock = MutableClock(Instant.parse("2026-09-14T17:59:00Z"))
        val queue = Queue()
        val selected = mutableListOf<PhaseSelection>()
        val scheduler = PhaseScheduler(clock, { ZoneOffset.UTC }, queue, onSelection = selected::add)

        scheduler.start()
        assertEquals(DayPhase.DAY, selected.last().phase)
        assertEquals(60_000L, queue.callbacks.single().first)

        clock.now = Instant.parse("2026-09-14T17:59:30Z")
        queue.fire()
        assertEquals(DayPhase.DAY, selected.last().phase)
        assertEquals(30_000L, queue.callbacks.single().first)

        clock.now = Instant.parse("2026-09-14T18:04:00Z")
        queue.fire()
        assertEquals(DayPhase.EVENING, selected.last().phase)
        assertEquals(Instant.parse("2026-09-14T21:00:00Z"), selected.last().nextDeadline)
        assertEquals(1, queue.callbacks.size)
    }

    @Test fun invalidateReadsFreshZoneAndDoesNotLeaveStaleDeadline() {
        val clock = MutableClock(Instant.parse("2026-09-14T17:30:00Z"))
        val queue = Queue()
        var zone: ZoneId = ZoneOffset.UTC
        val selected = mutableListOf<PhaseSelection>()
        val scheduler = PhaseScheduler(clock, { zone }, queue, onSelection = selected::add)
        scheduler.start()
        val stale = queue.callbacks.single().second
        assertEquals(DayPhase.DAY, selected.last().phase)

        zone = ZoneId.of("Europe/Kaliningrad")
        scheduler.invalidate()
        assertEquals(DayPhase.EVENING, selected.last().phase)
        assertEquals(Instant.parse("2026-09-14T19:00:00Z"), selected.last().nextDeadline)
        assertEquals(1, queue.callbacks.size)
        stale.run()
        assertEquals(2, selected.size)
    }

    @Test fun stopCloseAndResumeCancelWithoutReplay() {
        val clock = MutableClock(Instant.parse("2026-09-14T17:59:00Z"))
        val queue = Queue()
        val phases = mutableListOf<DayPhase>()
        val scheduler = PhaseScheduler(clock, { ZoneOffset.UTC }, queue) { phases += it.phase }
        scheduler.start()
        val stale = queue.callbacks.single().second
        scheduler.stop()
        assertTrue(queue.callbacks.isEmpty())
        clock.now = Instant.parse("2026-09-14T18:10:00Z")
        stale.run()
        scheduler.start()
        assertEquals(listOf(DayPhase.DAY, DayPhase.EVENING), phases)
        assertEquals(1, queue.callbacks.size)
        scheduler.close()
        assertTrue(queue.callbacks.isEmpty())
    }
}
