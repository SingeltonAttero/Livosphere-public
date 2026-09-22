package app.livosphere.wallpapers.neon

import org.junit.Assert.*
import org.junit.Test

class NeonBlinkControllerTest {
    @Test fun idleBlinksUseVariedIntervalsAndFinishWithOpenEyes() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        val starts = mutableListOf<Long>()
        for (now in 0L..30_000L step 50) {
            val before = c.blinks
            val amount = c.frame(now)
            assertTrue(amount in 0f..1f)
            if (c.blinks != before) starts += now
        }
        assertTrue(starts.size >= 5)
        assertTrue(starts.first() in 4_000L..7_000L)
        val intervals = starts.zipWithNext { a, b -> b - a }
        assertTrue(intervals.all { it in 4_000L..7_000L })
        assertTrue(intervals.distinct().size > 1)
        val last = starts.last()
        assertEquals(0f, c.frame(last + 240), 0f)
    }

    @Test fun unlockBlinksTwiceThenReturnsToIdleRhythm() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        c.onUnlocked(1_000)
        assertEquals(0f, c.frame(1_349), 0f)
        c.frame(1_350)
        assertEquals(1L, c.blinks)
        assertEquals(1f, c.frame(1_450), 0f)
        assertEquals(0f, c.frame(1_590), 0f)
        c.frame(2_100)
        assertEquals(2L, c.blinks)
        assertEquals(1f, c.frame(2_200), 0f)
        assertEquals(0f, c.frame(2_340), 0f)
        c.frame(6_099)
        assertEquals(2L, c.blinks)
        c.frame(8_400)
        assertEquals(3L, c.blinks)
    }

    @Test fun unlockBeforeHomeIsHandedOverOnlyWithinTwoSeconds() {
        val fresh = NeonBlinkController()
        fresh.onUnlocked(1_000)
        fresh.configure(false, 1_100)
        fresh.configure(true, 1_500)
        fresh.frame(1_850)
        assertEquals(1L, fresh.blinks)
        fresh.frame(2_600)
        assertEquals(2L, fresh.blinks)

        val expired = NeonBlinkController()
        expired.onUnlocked(1_000)
        expired.configure(true, 3_001)
        expired.frame(4_500)
        assertEquals(0L, expired.blinks)
        expired.frame(7_201)
        assertEquals(1L, expired.blinks)
    }

    @Test fun repeatedUnlockDoesNotPostponeOrMultiplyTheWelcome() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        c.onUnlocked(1_000)
        c.onUnlocked(1_200)
        c.frame(1_350)
        c.onUnlocked(1_500)
        c.frame(2_100)
        c.frame(3_000)
        assertEquals(2L, c.blinks)
    }

    @Test fun tiltDoesNotRestartBlinkOrInterruptUnlockSequence() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        c.onTilt(1_000)
        assertEquals(1f, c.frame(1_100), 0f)
        c.onTilt(1_110)
        assertEquals(0f, c.frame(1_240), 0f)
        assertEquals(1L, c.blinks)
        c.onUnlocked(2_000)
        c.onTilt(2_100)
        c.frame(2_350)
        c.onTilt(2_600)
        c.frame(3_100)
        assertEquals(3L, c.blinks)
    }

    @Test fun disabledSessionAndScreenOffDiscardAllPendingBlinks() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        c.onUnlocked(1_000)
        c.frame(1_350)
        c.configure(false, 1_400)
        c.onTilt(1_500)
        assertEquals(0f, c.frame(2_100), 0f)
        c.configure(true, 20_000)
        assertEquals(0f, c.frame(20_350), 0f)
        assertEquals(1L, c.blinks)
        c.configure(false, 21_000)
        c.onUnlocked(21_100)
        c.cancel()
        c.configure(true, 21_200)
        c.frame(21_550)
        assertEquals(1L, c.blinks)
    }

    @Test fun lateFramesDoNotReplayMissedIdleOrUnlockBlinks() {
        val c = NeonBlinkController()
        c.configure(true, 0)
        c.frame(60_000)
        c.frame(60_000)
        assertEquals(1L, c.blinks)
        c.onUnlocked(61_000)
        assertEquals(0f, c.frame(65_000), 0f)
        assertEquals(1L, c.blinks)
    }

    @Test fun enginesHaveIndependentSchedulesAndUnchangedConfigurationPreservesTimer() {
        val a = NeonBlinkController()
        val b = NeonBlinkController(1)
        a.configure(true, 0)
        b.configure(true, 0)
        a.configure(true, 4_000)
        a.frame(4_200)
        b.frame(4_200)
        assertEquals(1L, a.blinks)
        assertEquals(0L, b.blinks)
        a.cancel()
        b.frame(5_700)
        assertEquals(1L, b.blinks)
    }
}
