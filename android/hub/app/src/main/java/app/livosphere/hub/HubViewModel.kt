package app.livosphere.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.livosphere.hub.onboarding.ApplicationKnowledgeProvider
import app.livosphere.hub.onboarding.HubSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HubViewModel @Inject constructor(
    private val repository: HubSettingsRepository,
    private val knowledgeProvider: ApplicationKnowledgeProvider,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HubState())
    private val commandChannel = Channel<HubCommand.ShowSection>(capacity = Channel.CONFLATED)
    private val actions = Channel<HubAction>(capacity = Channel.UNLIMITED)

    val state: StateFlow<HubState> = mutableState.asStateFlow()
    val commands = commandChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            for (action in actions) {
                val transition = HubReducer.reduce(mutableState.value, action)
                mutableState.value = transition.state
                transition.commands.forEach { command ->
                    when (command) {
                        HubCommand.RetryHistory -> repository.retryHistory()
                        is HubCommand.ShowSection -> commandChannel.trySend(command)
                        is HubCommand.ClaimInvitation -> viewModelScope.launch {
                            val result = repository.claimInvitation(command.now)
                            onAction(HubAction.InvitationFinished(result, knowledgeProvider.knowledge.value, clock.instant()))
                        }
                    }
                }
                if (action is HubAction.HistoryChanged || action is HubAction.KnowledgeChanged ||
                    action is HubAction.ForegroundStarted || action is HubAction.NavigationRestored ||
                    action is HubAction.SectionSelected
                ) {
                    onAction(HubAction.EvaluateInvitation(clock.instant()))
                }
            }
        }
        viewModelScope.launch { repository.history.collect { onAction(HubAction.HistoryChanged(it)) } }
        viewModelScope.launch { knowledgeProvider.knowledge.collect { onAction(HubAction.KnowledgeChanged(it)) } }
    }

    fun onAction(action: HubAction) {
        actions.trySend(action)
    }

    override fun onCleared() {
        actions.close()
        commandChannel.close()
    }
}
