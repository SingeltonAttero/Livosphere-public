package app.livosphere.hub.onboarding

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface Outcome<out V, out F> {
    data class Success<V>(val value: V) : Outcome<V, Nothing>
    data class Failure<F>(val reason: F) : Outcome<Nothing, F>
}

sealed interface SettingsFailure {
    data object Corrupt : SettingsFailure
    data object Read : SettingsFailure
    data object Write : SettingsFailure
    data object Unavailable : SettingsFailure
}

/** App-owned invitation history only. Never a source of platform application truth. */
data class InvitationHistory(
    val lastInvitedAt: Instant? = null,
    val invitationCount: Int = 0,
    val repeatUsed: Boolean = false,
) {
    init {
        require(invitationCount in 0..2)
        require((invitationCount == 0) == (lastInvitedAt == null))
        require(repeatUsed == (invitationCount == 2))
    }
}

sealed interface ApplicationKnowledge {
    data object Unknown : ApplicationKnowledge
    data object Applied : ApplicationKnowledge
    /** Ephemeral observation with a provider-owned validity window, never persisted. */
    data class NotApplied(val observedAt: Instant, val validUntil: Instant) : ApplicationKnowledge
}

interface ApplicationKnowledgeProvider {
    val knowledge: StateFlow<ApplicationKnowledge>
}

object InvitationPolicy {
    val repeatInterval: Duration = Duration.ofDays(7)

    fun freshNotApplied(knowledge: ApplicationKnowledge, now: Instant): Boolean =
        knowledge is ApplicationKnowledge.NotApplied &&
            !now.isBefore(knowledge.observedAt) && now.isBefore(knowledge.validUntil)

    fun eligible(history: InvitationHistory, now: Instant): Boolean {
        if (history.repeatUsed) return false
        val last = history.lastInvitedAt ?: return true
        return !now.isBefore(last) && Duration.between(last, now) >= repeatInterval
    }

    fun claimed(history: InvitationHistory, now: Instant): InvitationHistory =
        InvitationHistory(now, history.invitationCount + 1, history.invitationCount == 1)
}

sealed interface InvitationClaim {
    data class Granted(val history: InvitationHistory) : InvitationClaim
    data object Suppressed : InvitationClaim
}

interface HubSettingsRepository {
    /** Expected failures are values; subsequent collection/emissions can recover. */
    val history: Flow<Outcome<InvitationHistory, SettingsFailure>>
    fun retryHistory()

    /** Transaction rechecks current durable history; callers cannot supply a stale snapshot. */
    suspend fun claimInvitation(now: Instant): Outcome<InvitationClaim, SettingsFailure>
}
