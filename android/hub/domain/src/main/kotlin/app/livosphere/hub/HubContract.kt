package app.livosphere.hub

import app.livosphere.hub.onboarding.ApplicationKnowledge
import app.livosphere.hub.onboarding.InvitationClaim
import app.livosphere.hub.onboarding.InvitationHistory
import app.livosphere.hub.onboarding.InvitationPolicy
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.onboarding.SettingsFailure
import java.time.Instant

enum class HubSection {
    THEME,
    DEVICES,
    SETTINGS,
}

enum class HubSurface {
    WALLPAPER,
    WATCH_FACE,
}

data class HubState(
    val selectedSection: HubSection = HubSection.THEME,
    val selectedSurface: HubSurface = HubSurface.WALLPAPER,
    val hasSeenThemePreview: Boolean = false,
    val knowledge: ApplicationKnowledge = ApplicationKnowledge.Unknown,
    val settings: Outcome<InvitationHistory, SettingsFailure>? = null,
    val onboardingVisible: Boolean = false,
    val pendingInvitation: ApplicationKnowledge.NotApplied? = null,
    val automaticAttempted: Boolean = false,
    val foreground: Boolean = false,
    val onboardingIsAutomatic: Boolean = false,
)

sealed interface HubAction {
    data class SectionSelected(val section: HubSection) : HubAction

    data class NavigationRestored(val section: HubSection) : HubAction

    data class SurfaceSelected(val surface: HubSurface) : HubAction

    data object ThemePreviewSeen : HubAction
    data class KnowledgeChanged(val knowledge: ApplicationKnowledge) : HubAction
    data class HistoryChanged(val result: Outcome<InvitationHistory, SettingsFailure>) : HubAction
    data class EvaluateInvitation(val now: Instant) : HubAction
    data class InvitationFinished(
        val result: Outcome<InvitationClaim, SettingsFailure>,
        val knowledge: ApplicationKnowledge,
        val now: Instant,
    ) : HubAction
    data object OpenOnboarding : HubAction
    data object DismissOnboarding : HubAction
    data object GoToTheme : HubAction
    data object RetryHistory : HubAction
    data class ForegroundStarted(val restoredSection: HubSection) : HubAction
    data object ForegroundStopped : HubAction
}

sealed interface HubCommand {
    data class ShowSection(val section: HubSection) : HubCommand
    data class ClaimInvitation(val now: Instant) : HubCommand
    data object RetryHistory : HubCommand
}

data class HubTransition(
    val state: HubState,
    val commands: List<HubCommand> = emptyList(),
)

object HubReducer {
    fun reduce(state: HubState, action: HubAction): HubTransition = when (action) {
        HubAction.RetryHistory -> HubTransition(state, listOf(HubCommand.RetryHistory))
        is HubAction.ForegroundStarted -> HubTransition(
            state.copy(foreground = true, selectedSection = action.restoredSection),
            listOf(HubCommand.RetryHistory),
        )
        HubAction.ForegroundStopped -> HubTransition(state.copy(
            foreground = false,
            pendingInvitation = null,
            onboardingVisible = state.onboardingVisible && !state.onboardingIsAutomatic,
            onboardingIsAutomatic = false,
        ))
        is HubAction.SectionSelected -> selectSection(state, action.section)
        is HubAction.NavigationRestored -> HubTransition(
            state = state.copy(selectedSection = action.section,
                onboardingVisible = state.onboardingVisible &&
                    (!state.onboardingIsAutomatic || action.section == HubSection.THEME),
                pendingInvitation = if (state.selectedSection == action.section) state.pendingInvitation else null),
        )
        is HubAction.SurfaceSelected -> HubTransition(
            state = state.copy(selectedSurface = action.surface),
        )
        HubAction.ThemePreviewSeen -> HubTransition(
            state = state.copy(hasSeenThemePreview = true),
        )
        is HubAction.KnowledgeChanged -> HubTransition(state.copy(
            knowledge = action.knowledge,
            pendingInvitation = state.pendingInvitation?.takeIf { it == action.knowledge },
        ))
        is HubAction.HistoryChanged -> HubTransition(state.copy(settings = action.result))
        is HubAction.EvaluateInvitation -> {
            val history = (state.settings as? Outcome.Success)?.value
            if (state.foreground && state.selectedSection == HubSection.THEME &&
                !state.automaticAttempted && !state.onboardingVisible && history != null &&
                InvitationPolicy.eligible(history, action.now) &&
                InvitationPolicy.freshNotApplied(state.knowledge, action.now)
            ) {
                HubTransition(
                    state.copy(
                        automaticAttempted = true,
                        pendingInvitation = state.knowledge as ApplicationKnowledge.NotApplied,
                    ),
                    listOf(HubCommand.ClaimInvitation(action.now)),
                )
            } else HubTransition(state)
        }
        is HubAction.InvitationFinished -> {
            val granted = (action.result as? Outcome.Success)?.value is InvitationClaim.Granted
            val show = granted && state.foreground && state.selectedSection == HubSection.THEME &&
                state.settings !is Outcome.Failure && state.pendingInvitation != null &&
                state.pendingInvitation == action.knowledge && state.knowledge == action.knowledge &&
                InvitationPolicy.freshNotApplied(action.knowledge, action.now)
            HubTransition(state.copy(
                pendingInvitation = null,
                onboardingVisible = state.onboardingVisible || show,
                onboardingIsAutomatic = state.onboardingIsAutomatic || show,
                settings = if (action.result is Outcome.Failure) action.result else state.settings,
            ))
        }
        HubAction.OpenOnboarding -> HubTransition(state.copy(
            onboardingVisible = true, onboardingIsAutomatic = false,
            pendingInvitation = null, automaticAttempted = true,
        ))
        HubAction.DismissOnboarding -> HubTransition(state.copy(
            onboardingVisible = false, onboardingIsAutomatic = false, pendingInvitation = null,
        ))
        HubAction.GoToTheme -> HubTransition(
            state.copy(selectedSection = HubSection.THEME, onboardingVisible = false,
                onboardingIsAutomatic = false, pendingInvitation = null),
            listOf(HubCommand.ShowSection(HubSection.THEME)),
        )
    }

    private fun selectSection(state: HubState, section: HubSection): HubTransition {
        if (state.selectedSection == section) return HubTransition(state)

        return HubTransition(
            state = state.copy(selectedSection = section, pendingInvitation = null,
                onboardingVisible = state.onboardingVisible && !state.onboardingIsAutomatic,
                onboardingIsAutomatic = false),
            commands = listOf(HubCommand.ShowSection(section)),
        )
    }
}
