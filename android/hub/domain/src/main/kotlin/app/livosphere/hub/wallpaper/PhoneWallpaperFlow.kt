package app.livosphere.hub.wallpaper

import app.livosphere.hub.onboarding.ApplicationKnowledge

enum class WallpaperRoute { DIRECT, CHOOSER }
enum class WallpaperBlock { CHECKING, UNSUPPORTED, POLICY, MISSING, DISABLED, UNKNOWN_COMPONENT, NO_HANDLER }
data class WallpaperPath(val route: WallpaperRoute? = null, val block: WallpaperBlock? = null)
enum class WallpaperLaunchFailure { NO_HANDLER, ACCESS_DENIED, INVALID_REQUEST, PLATFORM_INCIDENT }
data class WallpaperLaunchRequest(val id: Long, val generation: Long, val route: WallpaperRoute, val target: WallpaperTarget)

object WallpaperRoutePolicy {
    fun path(snapshot: PhoneWallpaperSnapshot?, skipDirect: Boolean = false, skipChooser: Boolean = false): WallpaperPath {
        if (snapshot == null) return WallpaperPath(block = WallpaperBlock.CHECKING)
        fun WallpaperFact<Boolean>.isFalse() = this == WallpaperFact.Known(false)
        return when {
            !snapshot.compatible || snapshot.feature.isFalse() || snapshot.supported.isFalse() -> WallpaperPath(block = WallpaperBlock.UNSUPPORTED)
            snapshot.allowed.isFalse() -> WallpaperPath(block = WallpaperBlock.POLICY)
            snapshot.presence == WallpaperFact.Known(WallpaperPresence.MISSING) -> WallpaperPath(block = WallpaperBlock.MISSING)
            snapshot.presence == WallpaperFact.Known(WallpaperPresence.DISABLED) -> WallpaperPath(block = WallpaperBlock.DISABLED)
            snapshot.presence !is WallpaperFact.Known -> WallpaperPath(block = WallpaperBlock.UNKNOWN_COMPONENT)
            !skipDirect && snapshot.directPreview == WallpaperFact.Known(true) -> WallpaperPath(WallpaperRoute.DIRECT)
            !skipChooser && snapshot.chooser == WallpaperFact.Known(true) -> WallpaperPath(WallpaperRoute.CHOOSER)
            else -> WallpaperPath(block = WallpaperBlock.NO_HANDLER)
        }
    }

    fun knowledge(snapshot: PhoneWallpaperSnapshot?): ApplicationKnowledge = when {
        snapshot == null -> ApplicationKnowledge.Unknown
        snapshot.home == WallpaperFact.Known(WallpaperApplication.ACTIVE) ||
            snapshot.lock == WallpaperFact.Known(WallpaperApplication.ACTIVE) -> ApplicationKnowledge.Applied
        snapshot.home == WallpaperFact.Known(WallpaperApplication.INACTIVE) &&
            snapshot.lock == WallpaperFact.Known(WallpaperApplication.INACTIVE) && path(snapshot).route != null ->
            ApplicationKnowledge.NotApplied(snapshot.observedAt, snapshot.observedAt.plusSeconds(60))
        else -> ApplicationKnowledge.Unknown
    }
}

data class PhoneWallpaperState(
    val snapshot: PhoneWallpaperSnapshot? = null,
    val target: WallpaperTarget? = snapshot?.target,
    val generation: Long = 0,
    val refreshing: Boolean = false,
    val nextRequestId: Long = 1,
    val queryingRequestId: Long? = null,
    val readyRequest: WallpaperLaunchRequest? = null,
    val launchedRequestId: Long? = null,
    val failure: WallpaperLaunchFailure? = null,
    val skipDirect: Boolean = false,
    val skipChooser: Boolean = false,
    val helpVisible: Boolean = false,
) {
    val path get() = if (target == null) WallpaperPath(block = WallpaperBlock.UNKNOWN_COMPONENT)
        else WallpaperRoutePolicy.path(snapshot, skipDirect, skipChooser)
    val busy get() = queryingRequestId != null || readyRequest != null || launchedRequestId != null
}

sealed interface PhoneWallpaperAction {
    data object Foreground : PhoneWallpaperAction
    data object Background : PhoneWallpaperAction
    data object SelectionChanged : PhoneWallpaperAction
    data class TargetSelected(val target: WallpaperTarget?) : PhoneWallpaperAction
    data object Refresh : PhoneWallpaperAction
    data object TryOn : PhoneWallpaperAction
    data class Observed(val generation: Long, val snapshot: PhoneWallpaperSnapshot?) : PhoneWallpaperAction
    data class Consume(val request: WallpaperLaunchRequest) : PhoneWallpaperAction
    data class Abandoned(val request: WallpaperLaunchRequest) : PhoneWallpaperAction
    data class LaunchFailed(val request: WallpaperLaunchRequest, val failure: WallpaperLaunchFailure) : PhoneWallpaperAction
    data object Returned : PhoneWallpaperAction
    data object ToggleHelp : PhoneWallpaperAction
}

sealed interface PhoneWallpaperEffect {
    data class Observe(val generation: Long, val target: WallpaperTarget) : PhoneWallpaperEffect
}
data class PhoneWallpaperTransition(val state: PhoneWallpaperState, val effects: List<PhoneWallpaperEffect> = emptyList())

object PhoneWallpaperReducer {
    /** eligible means the current foreground selection still permits this exact phone action. */
    fun reduce(state: PhoneWallpaperState, action: PhoneWallpaperAction, foreground: Boolean, eligible: Boolean): PhoneWallpaperTransition = when (action) {
        PhoneWallpaperAction.Foreground -> refresh(state.copy(launchedRequestId = null, queryingRequestId = null, readyRequest = null))
        PhoneWallpaperAction.Background -> PhoneWallpaperTransition(state.copy(
            snapshot = null, refreshing = false, generation = state.generation + 1,
            queryingRequestId = null, readyRequest = null,
        ))
        PhoneWallpaperAction.SelectionChanged -> invalidateSelection(state, state.target, foreground)
        is PhoneWallpaperAction.TargetSelected -> if (state.target == action.target) PhoneWallpaperTransition(state)
            else invalidateSelection(state, action.target, foreground)
        PhoneWallpaperAction.Refresh -> if (foreground && !state.busy) refresh(state.copy(failure = null)) else PhoneWallpaperTransition(state)
        PhoneWallpaperAction.TryOn -> if (eligible && !state.busy && state.path.route != null) {
            refresh(state.copy(queryingRequestId = state.nextRequestId, nextRequestId = state.nextRequestId + 1, failure = null))
        } else PhoneWallpaperTransition(state)
        is PhoneWallpaperAction.Observed -> if (foreground && state.refreshing && action.generation == state.generation &&
            (action.snapshot == null || action.snapshot.target == state.target)) {
            val path = WallpaperRoutePolicy.path(action.snapshot, state.skipDirect, state.skipChooser)
            PhoneWallpaperTransition(state.copy(snapshot = action.snapshot, refreshing = false,
                queryingRequestId = null,
                readyRequest = if (eligible && path.route != null) state.queryingRequestId?.let {
                    WallpaperLaunchRequest(it, state.generation, path.route, checkNotNull(state.target))
                } else null,
                helpVisible = state.helpVisible || (state.queryingRequestId != null && path.route == null)))
        } else PhoneWallpaperTransition(state)
        is PhoneWallpaperAction.Consume -> if (eligible && state.readyRequest == action.request) {
            PhoneWallpaperTransition(state.copy(readyRequest = null, launchedRequestId = action.request.id))
        } else PhoneWallpaperTransition(state)
        is PhoneWallpaperAction.Abandoned -> PhoneWallpaperTransition(state.copy(
            readyRequest = state.readyRequest?.takeUnless { it.id == action.request.id },
            launchedRequestId = state.launchedRequestId?.takeUnless { it == action.request.id },
        ))
        is PhoneWallpaperAction.LaunchFailed -> if (state.launchedRequestId == action.request.id) {
            val failed = state.copy(launchedRequestId = null, failure = action.failure, helpVisible = true,
                skipDirect = state.skipDirect || (action.request.route == WallpaperRoute.DIRECT && action.failure == WallpaperLaunchFailure.NO_HANDLER),
                skipChooser = state.skipChooser || (action.request.route == WallpaperRoute.CHOOSER && action.failure == WallpaperLaunchFailure.NO_HANDLER))
            if (foreground) refresh(failed) else PhoneWallpaperTransition(failed)
        } else PhoneWallpaperTransition(state)
        PhoneWallpaperAction.Returned -> if (foreground) refresh(state.copy(launchedRequestId = null))
            else PhoneWallpaperTransition(state.copy(launchedRequestId = null))
        PhoneWallpaperAction.ToggleHelp -> PhoneWallpaperTransition(state.copy(helpVisible = !state.helpVisible))
    }

    private fun invalidateSelection(state: PhoneWallpaperState, target: WallpaperTarget?, foreground: Boolean): PhoneWallpaperTransition {
        val invalid = state.copy(target = target, snapshot = null, generation = state.generation + 1,
            refreshing = false, queryingRequestId = null, readyRequest = null, launchedRequestId = null,
            failure = state.failure.takeIf { state.target == target },
            skipDirect = state.skipDirect && state.target == target,
            skipChooser = state.skipChooser && state.target == target, helpVisible = false)
        return if (foreground) refresh(invalid) else PhoneWallpaperTransition(invalid)
    }

    private fun refresh(state: PhoneWallpaperState): PhoneWallpaperTransition {
        if (state.target == null) return PhoneWallpaperTransition(state.copy(snapshot = null, refreshing = false, generation = state.generation + 1))
        val next = state.copy(snapshot = null, refreshing = true, generation = state.generation + 1, readyRequest = null)
        return PhoneWallpaperTransition(next, listOf(PhoneWallpaperEffect.Observe(next.generation, checkNotNull(next.target))))
    }
}
