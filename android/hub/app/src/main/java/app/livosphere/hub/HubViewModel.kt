package app.livosphere.hub

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

@HiltViewModel
class HubViewModel @Inject constructor() : ViewModel() {
    private val mutableState = MutableStateFlow(HubState())
    private val commandChannel = Channel<HubCommand>(capacity = Channel.BUFFERED)

    val state: StateFlow<HubState> = mutableState.asStateFlow()
    val commands = commandChannel.receiveAsFlow()

    fun onAction(action: HubAction) {
        val transition = HubReducer.reduce(mutableState.value, action)
        mutableState.value = transition.state
        transition.commands.forEach { command ->
            check(commandChannel.trySend(command).isSuccess) {
                "Hub navigation command channel is unavailable"
            }
        }
    }
}
