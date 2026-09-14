package app.livosphere.wallpapers.engine

import app.livosphere.contract.DayPhase
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class PhaseSelection(
    val phase: DayPhase,
    val nextDeadline: Instant,
)

/** Four half-open local-day phases with deadlines expressed on the real UTC timeline. */
class PhasePolicy {
    fun select(clock: Clock, zoneId: ZoneId): PhaseSelection = select(clock.instant(), zoneId)

    fun select(now: Instant, zoneId: ZoneId): PhaseSelection = PhaseSelection(
        phase = phaseAt(now, zoneId),
        nextDeadline = nextDeadline(now, zoneId),
    )

    fun phaseAt(instant: Instant, zoneId: ZoneId): DayPhase = phaseAt(instant.atZone(zoneId).toLocalTime())

    private fun phaseAt(localTime: LocalTime): DayPhase = when {
        localTime < MORNING_START -> DayPhase.NIGHT
        localTime < DAY_START -> DayPhase.MORNING
        localTime < EVENING_START -> DayPhase.DAY
        localTime < NIGHT_START -> DayPhase.EVENING
        else -> DayPhase.NIGHT
    }

    private fun nextDeadline(now: Instant, zoneId: ZoneId): Instant {
        val today = now.atZone(zoneId).toLocalDate()
        val candidates = mutableSetOf<Instant>()
        for (offset in 0L..2L) {
            val date = today.plusDays(offset)
            BOUNDARIES.forEach { time -> candidates += resolvedInstants(date.atTime(time), zoneId) }
        }

        // An offset jump can itself cross a phase boundary, including a backward jump that
        // returns local time to the previous phase before the next authored wall-clock boundary.
        var transition = zoneId.rules.nextTransition(now.minusNanos(1))
        repeat(MAX_TRANSITIONS_TO_INSPECT) {
            val current = transition ?: return@repeat
            if (current.instant > now.plusSeconds(MAX_LOOKAHEAD_SECONDS)) return@repeat
            candidates += current.instant
            transition = zoneId.rules.nextTransition(current.instant)
        }

        return candidates.asSequence()
            .filter { it > now }
            .filter { phaseAt(it.minusNanos(1), zoneId) != phaseAt(it, zoneId) }
            .minOrNull()
            ?: error("No next local-day phase transition for $now in $zoneId")
    }

    private fun resolvedInstants(local: LocalDateTime, zoneId: ZoneId): Set<Instant> {
        val rules = zoneId.rules
        val offsets = rules.getValidOffsets(local)
        if (offsets.isNotEmpty()) return offsets.mapTo(linkedSetOf()) { local.toInstant(it) }
        val transition = checkNotNull(rules.getTransition(local))
        return setOf(transition.instant)
    }

    private companion object {
        val MORNING_START: LocalTime = LocalTime.of(5, 0)
        val DAY_START: LocalTime = LocalTime.of(8, 0)
        val EVENING_START: LocalTime = LocalTime.of(18, 0)
        val NIGHT_START: LocalTime = LocalTime.of(21, 0)
        val BOUNDARIES = listOf(MORNING_START, DAY_START, EVENING_START, NIGHT_START)
        const val MAX_TRANSITIONS_TO_INSPECT = 8
        const val MAX_LOOKAHEAD_SECONDS = 3 * 24 * 60 * 60L
    }
}
