package app.livosphere.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.livosphere.hub.onboarding.ApplicationKnowledgeProvider
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.hub.wallpaper.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay

@HiltViewModel
class HubViewModel private constructor(
    private val repository: HubSettingsRepository,
    private val knowledgeProvider: ApplicationKnowledgeProvider?,
    private val clock: Clock,
    private val wallpaperGateway: PhoneWallpaperGateway?,
    private val beforeAcknowledgement: (suspend (WallpaperLaunchRequest) -> Unit)? = null,
) : ViewModel() {
    @Inject constructor(repository: HubSettingsRepository, wallpaperGateway: PhoneWallpaperGateway, clock: Clock) :
        this(repository, null, clock, wallpaperGateway)

    /** Explicit onboarding test seam; production derives knowledge from the single phone gateway. */
    internal constructor(repository: HubSettingsRepository, knowledgeProvider: ApplicationKnowledgeProvider, clock: Clock) :
        this(repository, knowledgeProvider, clock, null)

    /** Delays only the UI acknowledgement in lifecycle tests; the real actor still consumes the request. */
    internal constructor(repository: HubSettingsRepository, wallpaperGateway: PhoneWallpaperGateway, clock: Clock,
        beforeAcknowledgement: suspend (WallpaperLaunchRequest) -> Unit) :
        this(repository, null, clock, wallpaperGateway, beforeAcknowledgement)

    private val mutableState = MutableStateFlow(HubState())
    private val commandChannel = Channel<HubCommand.ShowSection>(capacity = Channel.CONFLATED)
    private data class ActionEnvelope(val action: HubAction, val consumed: CompletableDeferred<Boolean>? = null)
    private val actions = Channel<ActionEnvelope>(capacity = Channel.UNLIMITED)
    private var observationJob: Job? = null
    private var observationDeadline: Job? = null

    val state: StateFlow<HubState> = mutableState.asStateFlow()
    val commands = commandChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            for ((action, consumed) in actions) {
                val before = mutableState.value
                val transition = HubReducer.reduce(mutableState.value, action)
                mutableState.value = transition.state
                if (before.phone.generation != transition.state.phone.generation) {
                    observationJob?.cancel()
                    observationDeadline?.cancel()
                }
                val consume = (action as? HubAction.Phone)?.action as? PhoneWallpaperAction.Consume
                consumed?.complete(consume != null && before.phone.readyRequest == consume.request &&
                    transition.state.phone.launchedRequestId == consume.request.id)
                transition.commands.forEach { command ->
                    when (command) {
                        is HubCommand.Phone -> when (val effect = command.effect) {
                            is PhoneWallpaperEffect.Observe -> observePhone(effect.generation)
                        }
                        HubCommand.RetryHistory -> repository.retryHistory()
                        is HubCommand.ShowSection -> commandChannel.trySend(command)
                        is HubCommand.ClaimInvitation -> viewModelScope.launch {
                            val result = repository.claimInvitation(command.now)
                            onAction(HubAction.InvitationFinished(result,
                                knowledgeProvider?.knowledge?.value ?: mutableState.value.knowledge, clock.instant()))
                        }
                    }
                }
                if (action is HubAction.HistoryChanged || action is HubAction.KnowledgeChanged ||
                    action is HubAction.ForegroundStarted || action is HubAction.NavigationRestored ||
                    action is HubAction.SectionSelected || action is HubAction.Phone
                ) {
                    onAction(HubAction.EvaluateInvitation(clock.instant()))
                }
            }
        }
        viewModelScope.launch { repository.history.collect { onAction(HubAction.HistoryChanged(it)) } }
        if (knowledgeProvider != null) viewModelScope.launch {
            knowledgeProvider.knowledge.collect { onAction(HubAction.KnowledgeChanged(it)) }
        }
    }

    fun onAction(action: HubAction) {
        actions.trySend(ActionEnvelope(action))
    }

    suspend fun consumeWallpaperRequest(request: WallpaperLaunchRequest): Boolean {
        val result = CompletableDeferred<Boolean>()
        actions.send(ActionEnvelope(HubAction.Phone(PhoneWallpaperAction.Consume(request)), result))
        val consumed = result.await()
        if (consumed) beforeAcknowledgement?.invoke(request)
        return consumed
    }

    private fun observePhone(generation: Long) {
        var deadline: Job? = null
        val probe = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (wallpaperGateway != null) {
                    val snapshot = try { wallpaperGateway.refresh() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    onAction(HubAction.Phone(PhoneWallpaperAction.Observed(generation, snapshot)))
                } else if (mutableState.value.foreground && mutableState.value.phone.generation == generation) {
                    onAction(HubAction.Phone(PhoneWallpaperAction.Observed(generation, null)))
                    onAction(HubAction.KnowledgeChanged(requireNotNull(knowledgeProvider).knowledge.value))
                }
            } finally { deadline?.cancel() }
        }
        observationJob = probe
        // An independent UI deadline releases loading even when an OEM Binder call ignores cancellation.
        // Cancellation does not promise to interrupt that blocking platform call; late results are rejected.
        deadline = viewModelScope.launch {
            delay(OBSERVATION_DEADLINE_MILLIS)
            probe.cancel()
            onAction(HubAction.Phone(PhoneWallpaperAction.Observed(generation, null)))
        }
        observationDeadline = deadline
        probe.start()
    }

    override fun onCleared() {
        actions.close()
        commandChannel.close()
    }

    internal companion object { const val OBSERVATION_DEADLINE_MILLIS = 5_000L }
}
