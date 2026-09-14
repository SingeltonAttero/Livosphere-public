package app.livosphere.wallpapers.engine

import app.livosphere.contract.DayPhase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhasePolicyTest {
    private val policy = PhasePolicy()
    private val utc = ZoneId.of("UTC")

    @Test fun boundariesAreHalfOpenAndMapMorningToDawnAndEveningToDusk() {
        val cases = listOf(
            "2026-09-14T04:59:59.999Z" to DayPhase.NIGHT,
            "2026-09-14T05:00:00Z" to DayPhase.MORNING,
            "2026-09-14T07:59:59.999Z" to DayPhase.MORNING,
            "2026-09-14T08:00:00Z" to DayPhase.DAY,
            "2026-09-14T17:59:59.999Z" to DayPhase.DAY,
            "2026-09-14T18:00:00Z" to DayPhase.EVENING,
            "2026-09-14T20:59:59.999Z" to DayPhase.EVENING,
            "2026-09-14T21:00:00Z" to DayPhase.NIGHT,
        )
        cases.forEach { (instant, phase) -> assertEquals(phase, policy.phaseAt(Instant.parse(instant), utc)) }
    }

    @Test fun midnightAndNewDateKeepNightAndPointAtThatDatesMorning() {
        val selection = policy.select(Instant.parse("2026-09-15T00:00:00Z"), utc)
        assertEquals(DayPhase.NIGHT, selection.phase)
        assertEquals(Instant.parse("2026-09-15T05:00:00Z"), selection.nextDeadline)
    }

    @Test fun dstGapAndOverlapProduceRealFutureDeadlines() {
        val newYork = ZoneId.of("America/New_York")
        val beforeGap = policy.select(Instant.parse("2024-03-10T06:59:59Z"), newYork)
        assertEquals(DayPhase.NIGHT, beforeGap.phase)
        assertEquals(Instant.parse("2024-03-10T09:00:00Z"), beforeGap.nextDeadline)

        val firstOverlap = policy.select(Instant.parse("2024-11-03T05:30:00Z"), newYork)
        val secondOverlap = policy.select(Instant.parse("2024-11-03T06:30:00Z"), newYork)
        assertEquals(DayPhase.NIGHT, firstOverlap.phase)
        assertEquals(DayPhase.NIGHT, secondOverlap.phase)
        assertEquals(Instant.parse("2024-11-03T10:00:00Z"), firstOverlap.nextDeadline)
        assertEquals(firstOverlap.nextDeadline, secondOverlap.nextDeadline)
    }

    @Test fun offsetTransitionThatCrossesBoundaryBecomesDeadlineItself() {
        val casey = ZoneId.of("Antarctica/Casey")
        val beforeThreeHourGap = Instant.parse("2009-10-17T17:59:59Z")
        val selection = policy.select(beforeThreeHourGap, casey)
        assertEquals(DayPhase.NIGHT, selection.phase)
        assertEquals(Instant.parse("2009-10-17T18:00:00Z"), selection.nextDeadline)
        assertEquals(DayPhase.MORNING, policy.phaseAt(selection.nextDeadline, casey))
        assertTrue(Duration.between(beforeThreeHourGap, selection.nextDeadline).toMillis() > 0)
    }
}
