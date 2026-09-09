package app.livosphere.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.livosphere.hub.onboarding.ApplicationKnowledgeProvider
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.hub.settings.DataStoreHubRuntimeSettings
import app.livosphere.hub.settings.HubMotionMode
import app.livosphere.hub.wallpaper.*
import app.livosphere.settings.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val wallpaperSettingsRepository: WallpaperSettingsRepository?,
    private val runtimeSettingsRepository: DataStoreHubRuntimeSettings? = null,
    private val beforeAcknowledgement: (suspend (WallpaperLaunchRequest) -> Unit)? = null,
) : ViewModel() {
    @Inject constructor(repository: HubSettingsRepository, wallpaperGateway: PhoneWallpaperGateway, clock: Clock,
        wallpaperSettingsRepository: WallpaperSettingsRepository, runtimeSettingsRepository: DataStoreHubRuntimeSettings) :
        this(repository, null, clock, wallpaperGateway, wallpaperSettingsRepository, runtimeSettingsRepository)

    /** Explicit onboarding test seam; production derives knowledge from the single phone gateway. */
    internal constructor(repository: HubSettingsRepository, knowledgeProvider: ApplicationKnowledgeProvider, clock: Clock) :
        this(repository, knowledgeProvider, clock, null, null, null)

    /** Runtime-settings seam for reducer tests; production still uses the injected constructor. */
    internal constructor(
        repository: HubSettingsRepository,
        knowledgeProvider: ApplicationKnowledgeProvider,
        clock: Clock,
        runtimeSettingsRepository: DataStoreHubRuntimeSettings,
    ) : this(repository, knowledgeProvider, clock, null, null, runtimeSettingsRepository)

    /** Production wallpaper gateway seam without the shared settings control, used by UI tests. */
    internal constructor(repository: HubSettingsRepository, wallpaperGateway: PhoneWallpaperGateway, clock: Clock) :
        this(repository, null, clock, wallpaperGateway, null, null)

    /** Shared wallpaper settings seam: runtime preferences intentionally remain unavailable in this path. */
    internal constructor(
        repository: HubSettingsRepository,
        wallpaperGateway: PhoneWallpaperGateway,
        clock: Clock,
        wallpaperSettingsRepository: WallpaperSettingsRepository,
    ) : this(repository, null, clock, wallpaperGateway, wallpaperSettingsRepository, null)

    /** Delays only the UI acknowledgement in lifecycle tests; the real actor still consumes the request. */
    internal constructor(repository: HubSettingsRepository, wallpaperGateway: PhoneWallpaperGateway, clock: Clock,
        beforeAcknowledgement: suspend (WallpaperLaunchRequest) -> Unit) :
        this(repository, null, clock, wallpaperGateway, null, null, beforeAcknowledgement)

    private val mutableState = MutableStateFlow(HubState(phone = PhoneWallpaperState(target = wallpaperGateway?.initialBrowsingTarget)))
    private val mutableTouchReactions = MutableStateFlow<Boolean?>(null)
    private val mutableWallpaperMotion = MutableStateFlow<WallpaperMotionMode?>(null)
    private val mutableHubMotion = MutableStateFlow<HubMotionMode?>(null)
    private val mutableDismissedReleaseVersion = MutableStateFlow<String?>(null)
    private val commandChannel = Channel<HubCommand.ShowSection>(capacity = Channel.CONFLATED)
    private data class ActionEnvelope(val action: HubAction, val consumed: CompletableDeferred<Boolean>? = null)
    private val actions = Channel<ActionEnvelope>(capacity = Channel.UNLIMITED)
    private val settingsObservation = MutableStateFlow(wallpaperGateway?.initialBrowsingTarget?.wallpaperId to 0L)
    private var observationJob: Job? = null
    private var observationDeadline: Job? = null
    private var touchWriteGeneration = 0L
    private var motionWriteGeneration = 0L
    private var motionSettingsWriteUnavailable = false
    private var touchSettingsWriteUnavailable = false
    private var hubMotionWriteUnavailable = false

    val state: StateFlow<HubState> = mutableState.asStateFlow()
    val commands = commandChannel.receiveAsFlow()
    val touchReactions = mutableTouchReactions.asStateFlow()
    val wallpaperMotion = mutableWallpaperMotion.asStateFlow()
    val hubMotion = mutableHubMotion.asStateFlow()
    val dismissedReleaseVersion = mutableDismissedReleaseVersion.asStateFlow()

    init {
        viewModelScope.launch {
            for ((action, consumed) in actions) {
                val before = mutableState.value
                val transition = HubReducer.reduce(mutableState.value, action)
                mutableState.value = transition.state
                if (before.phone.target != transition.state.phone.target || action is HubAction.ForegroundStarted) {
                    touchWriteGeneration++
                    motionWriteGeneration++
                    touchSettingsWriteUnavailable = false
                    motionSettingsWriteUnavailable = false
                    mutableTouchReactions.value = null
                    mutableWallpaperMotion.value = null
                    settingsObservation.value = transition.state.phone.target?.wallpaperId to (settingsObservation.value.second + 1)
                }
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
                            is PhoneWallpaperEffect.Observe -> observePhone(effect.generation, effect.target)
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
                if (action is HubAction.ForegroundStarted) {
                    // A read error emits the honest unavailable model, then closes its inner
                    // DataStore flow. Foreground is the user-visible retry boundary.
                    hubMotionWriteUnavailable = false
                    runtimeSettingsRepository?.retrySettings()
                    knowledgeProvider?.let { onAction(HubAction.KnowledgeChanged(it.knowledge.value)) }
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
        wallpaperSettingsRepository?.let { repository -> viewModelScope.launch {
            settingsObservation.collectLatest { observation ->
                val wallpaperId = observation.first
                mutableTouchReactions.value = null
                mutableWallpaperMotion.value = null
                if (wallpaperId != null) kotlinx.coroutines.coroutineScope {
                    val settings = repository.forWallpaper(wallpaperId)
                    launch { settings.touchReactionsEnabled.collect { value ->
                        if (!touchSettingsWriteUnavailable && settingsObservation.value == observation) mutableTouchReactions.value = value
                    } }
                    launch { settings.motionMode.collect { value ->
                        if (!motionSettingsWriteUnavailable && settingsObservation.value == observation)
                            mutableWallpaperMotion.value = value
                    } }
                }
            }
        } }
        runtimeSettingsRepository?.let { settings -> viewModelScope.launch {
            settings.settings.collect { value ->
                if (!hubMotionWriteUnavailable) mutableHubMotion.value = value?.hubMotionMode
                mutableDismissedReleaseVersion.value = value?.dismissedReleaseVersion
            }
        } }
        if (knowledgeProvider != null) viewModelScope.launch {
            knowledgeProvider.knowledge.collect { onAction(HubAction.KnowledgeChanged(it)) }
        }
    }

    fun onAction(action: HubAction) {
        actions.trySend(ActionEnvelope(action))
    }

    fun setTouchReactionsEnabled(enabled: Boolean) {
        val writeGeneration = ++touchWriteGeneration
        val owner = state.value.phone.target?.wallpaperId ?: return
        val settings = wallpaperSettingsRepository?.forWallpaper(owner) ?: return
        viewModelScope.launch {
            try {
                settings.setTouchReactionsEnabled(enabled)
                if (state.value.phone.target?.wallpaperId == owner && touchWriteGeneration == writeGeneration) {
                    touchSettingsWriteUnavailable = false
                    mutableTouchReactions.value = enabled
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not leave an optimistic enabled/disabled switch after a failed DataStore edit.
                if (state.value.phone.target?.wallpaperId == owner && touchWriteGeneration == writeGeneration) {
                    touchSettingsWriteUnavailable = true
                    mutableTouchReactions.value = null
                }
            }
        }
    }

    fun setWallpaperMotionMode(mode: WallpaperMotionMode) {
        val writeGeneration = ++motionWriteGeneration
        val owner = state.value.phone.target?.wallpaperId ?: return
        val settings = wallpaperSettingsRepository?.forWallpaper(owner) ?: return
        viewModelScope.launch {
            try {
                settings.setMotionMode(mode)
                if (state.value.phone.target?.wallpaperId == owner && motionWriteGeneration == writeGeneration) {
                    motionSettingsWriteUnavailable = false
                    mutableWallpaperMotion.value = mode
                }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (state.value.phone.target?.wallpaperId == owner && motionWriteGeneration == writeGeneration) {
                    motionSettingsWriteUnavailable = true
                    mutableWallpaperMotion.value = null
                }
            }
        }
    }

    fun setHubMotionMode(mode: HubMotionMode) {
        val settings = runtimeSettingsRepository ?: return
        viewModelScope.launch {
            try {
                settings.setHubMotionMode(mode)
                hubMotionWriteUnavailable = false
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                hubMotionWriteUnavailable = true
                mutableHubMotion.value = null
            }
        }
    }

    fun dismissReleaseNote(versionName: String) {
        val settings = runtimeSettingsRepository ?: return
        viewModelScope.launch {
            try { settings.dismissReleaseNote(versionName) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Keep the current release note eligible. A failed write must not be rendered as
                // an acknowledgement, and must not escape this UI event coroutine as a crash.
            }
        }
    }

    suspend fun consumeWallpaperRequest(request: WallpaperLaunchRequest): Boolean {
        val result = CompletableDeferred<Boolean>()
        actions.send(ActionEnvelope(HubAction.Phone(PhoneWallpaperAction.Consume(request)), result))
        val consumed = result.await()
        if (consumed) beforeAcknowledgement?.invoke(request)
        return consumed
    }

    private fun observePhone(generation: Long, target: WallpaperTarget) {
        var deadline: Job? = null
        val probe = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                if (wallpaperGateway != null) {
                    val snapshot = try { wallpaperGateway.refresh(target) }
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
