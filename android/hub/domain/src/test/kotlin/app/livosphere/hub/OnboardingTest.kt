package app.livosphere.hub

import app.livosphere.hub.onboarding.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {
    private val now = Instant.parse("2026-09-04T12:00:00Z")
    private val known = ApplicationKnowledge.NotApplied(now.minusSeconds(10), now.plusSeconds(60))
    private val initial = HubState(knowledge = known, settings = Outcome.Success(InvitationHistory()), foreground = true)

    @Test fun weekBoundaryRollbackAndLifetimeLimit() {
        val once = InvitationPolicy.claimed(InvitationHistory(), now)
        assertFalse(InvitationPolicy.eligible(once, now.minusSeconds(1)))
        assertFalse(InvitationPolicy.eligible(once, now.plusSeconds(604799)))
        assertTrue(InvitationPolicy.eligible(once, now.plusSeconds(604800)))
        val twice = InvitationPolicy.claimed(once, now.plusSeconds(604800))
        assertFalse(InvitationPolicy.eligible(twice, now.plusSeconds(60480000)))
        assertEquals(2, twice.invitationCount)
        assertTrue(twice.repeatUsed)
    }

    @Test fun unknownAppliedLoadingFailureAndStaleFactsNeverInvite() {
        listOf(
            initial.copy(knowledge = ApplicationKnowledge.Unknown),
            initial.copy(knowledge = ApplicationKnowledge.Applied),
            initial.copy(knowledge = known.copy(validUntil = now)),
            initial.copy(knowledge = known.copy(observedAt = now.plusSeconds(1))),
            initial.copy(settings = null),
            initial.copy(settings = Outcome.Failure(SettingsFailure.Read)),
        ).forEach { assertTrue(HubReducer.reduce(it, HubAction.EvaluateInvitation(now)).commands.isEmpty()) }
    }

    @Test fun duplicateEvaluationDoesNotClaimTwiceAndShowRequiresDurableSuccess() {
        val pending = HubReducer.reduce(initial, HubAction.EvaluateInvitation(now))
        assertEquals(listOf(HubCommand.ClaimInvitation(now)), pending.commands)
        assertFalse(pending.state.onboardingVisible)
        assertTrue(HubReducer.reduce(pending.state, HubAction.EvaluateInvitation(now)).commands.isEmpty())
        val failure = HubReducer.reduce(pending.state, HubAction.InvitationFinished(
            Outcome.Failure(SettingsFailure.Write), known, now))
        assertFalse(failure.state.onboardingVisible)
        assertEquals(Outcome.Failure(SettingsFailure.Write), failure.state.settings)
    }

    @Test fun completionRechecksKnowledgeExpiryAndPendingInteraction() {
        val pending = HubReducer.reduce(initial, HubAction.EvaluateInvitation(now)).state
        val granted = Outcome.Success(InvitationClaim.Granted(InvitationPolicy.claimed(InvitationHistory(), now)))
        assertTrue(HubReducer.reduce(pending, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
        val sameDestination = HubReducer.reduce(pending, HubAction.NavigationRestored(HubSection.THEME)).state
        assertTrue(HubReducer.reduce(sameDestination, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
        assertFalse(HubReducer.reduce(pending, HubAction.InvitationFinished(granted, ApplicationKnowledge.Unknown, now)).state.onboardingVisible)
        assertFalse(HubReducer.reduce(pending, HubAction.InvitationFinished(granted, known, now.plusSeconds(61))).state.onboardingVisible)
        val failedRead = HubReducer.reduce(pending, HubAction.HistoryChanged(Outcome.Failure(SettingsFailure.Read))).state
        assertFalse(HubReducer.reduce(failedRead, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
        val changed = HubReducer.reduce(pending, HubAction.KnowledgeChanged(ApplicationKnowledge.Applied)).state
        val returned = HubReducer.reduce(changed, HubAction.KnowledgeChanged(known)).state
        assertFalse(HubReducer.reduce(returned, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
        val restored = HubReducer.reduce(pending, HubAction.NavigationRestored(HubSection.DEVICES)).state
        assertFalse(HubReducer.reduce(restored, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
        val dismissed = HubReducer.reduce(pending, HubAction.DismissOnboarding).state
        assertFalse(HubReducer.reduce(dismissed, HubAction.InvitationFinished(granted, known, now)).state.onboardingVisible)
    }

    @Test fun manualHelpAndExitsNeverWriteHistory() {
        val opened = HubReducer.reduce(HubState(selectedSection = HubSection.DEVICES), HubAction.OpenOnboarding)
        assertTrue(opened.state.onboardingVisible)
        assertTrue(opened.commands.isEmpty())
        val skipped = HubReducer.reduce(opened.state, HubAction.DismissOnboarding)
        assertEquals(HubSection.DEVICES, skipped.state.selectedSection)
        assertFalse(skipped.state.onboardingVisible)
        assertTrue(skipped.commands.isEmpty())
        val go = HubReducer.reduce(opened.state, HubAction.GoToTheme)
        assertFalse(go.state.onboardingVisible)
        assertEquals(listOf(HubCommand.ShowSection(HubSection.THEME)), go.commands)
    }

    @Test fun inconsistentHistoryCannotSilentlyResetQuota() {
        listOf({ InvitationHistory(now, 0, false) }, { InvitationHistory(null, 1, false) },
            { InvitationHistory(now, 2, false) }, { InvitationHistory(now, 3, true) }).forEach { create ->
            assertThrows(IllegalArgumentException::class.java) { create() }
        }
    }

    @Test fun backgroundAndRestoredNonThemeCannotStartAutomaticClaim() {
        listOf(initial.copy(foreground = false), initial.copy(selectedSection = HubSection.DEVICES),
            initial.copy(selectedSection = HubSection.SETTINGS)).forEach {
            assertTrue(HubReducer.reduce(it, HubAction.EvaluateInvitation(now)).commands.isEmpty())
        }
        val started = HubReducer.reduce(initial.copy(foreground = false), HubAction.ForegroundStarted(HubSection.SETTINGS))
        assertEquals(listOf(HubCommand.RetryHistory), started.commands)
        assertTrue(HubReducer.reduce(started.state, HubAction.EvaluateInvitation(now)).commands.isEmpty())
    }

    @Test fun stopInvalidatesPendingAndVisibleAutomaticButPreservesManualDialog() {
        val pending = HubReducer.reduce(initial, HubAction.EvaluateInvitation(now)).state
        val stopped = HubReducer.reduce(pending, HubAction.ForegroundStopped).state
        val result = Outcome.Success(InvitationClaim.Granted(InvitationPolicy.claimed(InvitationHistory(), now)))
        assertFalse(HubReducer.reduce(stopped, HubAction.InvitationFinished(result, known, now)).state.onboardingVisible)
        val resumed = HubReducer.reduce(stopped, HubAction.ForegroundStarted(HubSection.THEME)).state
        assertFalse(HubReducer.reduce(resumed, HubAction.InvitationFinished(result, known, now)).state.onboardingVisible)
        val shown = HubReducer.reduce(pending, HubAction.InvitationFinished(result, known, now)).state
        assertFalse(HubReducer.reduce(shown, HubAction.ForegroundStopped).state.onboardingVisible)
        val manual = HubReducer.reduce(initial, HubAction.OpenOnboarding).state
        assertTrue(HubReducer.reduce(manual, HubAction.ForegroundStopped).state.onboardingVisible)
    }
}
