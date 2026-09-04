package app.livosphere.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.hub.onboarding.InvitationClaim
import app.livosphere.hub.onboarding.InvitationHistory
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.settings.UnknownApplicationKnowledgeProvider
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun rememberTestHubViewModel(): HubViewModel {
    val store = remember { ViewModelStore() }
    val viewModel = remember {
        HubViewModel(object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }, UnknownApplicationKnowledgeProvider(), Clock.systemUTC()).also { store.put("hub", it) }
    }
    DisposableEffect(store) { onDispose { store.clear() } }
    return viewModel
}
