package app.livosphere.hub

import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PhoneWallpaperFlowTest {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private val unknown = WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO)
    private val snapshot = PhoneWallpaperSnapshot(now, WallpaperComponent("app.livosphere", "ContourService"), 29, 34,
        WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Known(true),
        WallpaperFact.Known(WallpaperPresence.AVAILABLE), WallpaperFact.Known(true), WallpaperFact.Known(true), unknown, unknown)
    private fun step(state: PhoneWallpaperState, action: PhoneWallpaperAction, foreground: Boolean = true, eligible: Boolean = true) =
        PhoneWallpaperReducer.reduce(state, action, foreground, eligible)
    private fun ready(): PhoneWallpaperState {
        val query = step(PhoneWallpaperState(snapshot), PhoneWallpaperAction.TryOn).state
        return step(query, PhoneWallpaperAction.Observed(query.generation, snapshot)).state
    }

    @Test fun directAndChooserDoNotDependOnApplicationKnowledge() {
        assertEquals(WallpaperRoute.DIRECT, WallpaperRoutePolicy.path(snapshot).route)
        assertEquals(WallpaperRoute.CHOOSER, WallpaperRoutePolicy.path(snapshot.copy(directPreview = WallpaperFact.Known(false))).route)
        assertEquals(WallpaperRoute.CHOOSER, WallpaperRoutePolicy.path(snapshot.copy(directPreview = unknown)).route)
        assertEquals(ApplicationKnowledge.Unknown, WallpaperRoutePolicy.knowledge(snapshot))
    }

    @Test fun knownBlocksCannotBeBypassedByChooser() {
        val cases = listOf(
            snapshot.copy(feature = WallpaperFact.Known(false)) to WallpaperBlock.UNSUPPORTED,
            snapshot.copy(supported = WallpaperFact.Known(false)) to WallpaperBlock.UNSUPPORTED,
            snapshot.copy(deviceApi = 28) to WallpaperBlock.UNSUPPORTED,
            snapshot.copy(allowed = WallpaperFact.Known(false)) to WallpaperBlock.POLICY,
            snapshot.copy(presence = WallpaperFact.Known(WallpaperPresence.MISSING)) to WallpaperBlock.MISSING,
            snapshot.copy(presence = WallpaperFact.Known(WallpaperPresence.DISABLED)) to WallpaperBlock.DISABLED,
            snapshot.copy(presence = unknown) to WallpaperBlock.UNKNOWN_COMPONENT,
            snapshot.copy(directPreview = WallpaperFact.Known(false), chooser = WallpaperFact.Known(false)) to WallpaperBlock.NO_HANDLER,
        )
        cases.forEach { (facts, block) ->
            assertEquals(WallpaperPath(block = block), WallpaperRoutePolicy.path(facts))
            assertNull(step(PhoneWallpaperState(facts), PhoneWallpaperAction.TryOn).state.queryingRequestId)
        }
    }

    @Test fun anyActiveSuppressesInvitationAndBothInactiveNeedAvailableRoute() {
        val active = WallpaperFact.Known(WallpaperApplication.ACTIVE)
        val inactive = WallpaperFact.Known(WallpaperApplication.INACTIVE)
        assertEquals(ApplicationKnowledge.Applied, WallpaperRoutePolicy.knowledge(snapshot.copy(home = active)))
        assertEquals(ApplicationKnowledge.Applied, WallpaperRoutePolicy.knowledge(snapshot.copy(lock = active)))
        assertEquals(ApplicationKnowledge.Unknown, WallpaperRoutePolicy.knowledge(snapshot.copy(home = inactive)))
        assertEquals(ApplicationKnowledge.Unknown, WallpaperRoutePolicy.knowledge(snapshot.copy(lock = inactive)))
        val both = snapshot.copy(home = inactive, lock = inactive)
        assertEquals(ApplicationKnowledge.NotApplied(now, now.plusSeconds(60)), WallpaperRoutePolicy.knowledge(both))
        assertEquals(ApplicationKnowledge.Unknown, WallpaperRoutePolicy.knowledge(both.copy(allowed = WallpaperFact.Known(false))))
    }

    @Test fun doubleTapAndDoubleConsumptionProduceOneLaunch() {
        val querying = step(PhoneWallpaperState(snapshot), PhoneWallpaperAction.TryOn)
        assertEquals(1, querying.effects.size)
        assertTrue(step(querying.state, PhoneWallpaperAction.TryOn).effects.isEmpty())
        val ready = step(querying.state, PhoneWallpaperAction.Observed(querying.state.generation, snapshot)).state
        val request = requireNotNull(ready.readyRequest)
        assertTrue(step(ready, PhoneWallpaperAction.TryOn).effects.isEmpty())
        val launched = step(ready, PhoneWallpaperAction.Consume(request)).state
        assertNull(launched.readyRequest)
        assertEquals(request.id, launched.launchedRequestId)
        assertEquals(launched, step(launched, PhoneWallpaperAction.Consume(request)).state)
        assertEquals(launched, step(launched, PhoneWallpaperAction.TryOn).state)
    }

    @Test fun returnNeverCreatesActiveAndAllowsExplicitRetry() {
        val ready = ready()
        val launch = step(ready, PhoneWallpaperAction.Consume(requireNotNull(ready.readyRequest))).state
        val returned = step(launch, PhoneWallpaperAction.Returned).state
        assertNull(returned.snapshot)
        assertNull(returned.launchedRequestId)
        assertNull(returned.failure)
        val fresh = step(returned, PhoneWallpaperAction.Observed(returned.generation, snapshot)).state
        assertEquals(unknown, fresh.snapshot?.home)
        assertNotNull(step(fresh, PhoneWallpaperAction.TryOn).state.queryingRequestId)
    }

    @Test fun cancelledConsumerOrRejectedSelectionReleasesClaimForSafeRetry() {
        val ready = ready()
        val request = requireNotNull(ready.readyRequest)
        val consumed = step(ready, PhoneWallpaperAction.Consume(request)).state
        val switched = step(consumed, PhoneWallpaperAction.SelectionChanged, eligible = false).state
        val abandoned = step(switched, PhoneWallpaperAction.Abandoned(request), eligible = false).state
        assertFalse(abandoned.busy)
        assertNull(abandoned.failure)
        val retry = step(abandoned, PhoneWallpaperAction.TryOn).state
        assertNotNull(retry.queryingRequestId)
        // An old cancelled consumer cannot clear a newer request.
        assertEquals(retry, step(retry, PhoneWallpaperAction.Abandoned(request)).state)
    }

    @Test fun rotationBackgroundAndLateProbeCannotLaunchOnReturn() {
        val query = step(PhoneWallpaperState(snapshot), PhoneWallpaperAction.TryOn).state
        val stopped = step(query, PhoneWallpaperAction.Background, foreground = false, eligible = false).state
        val resumed = step(stopped, PhoneWallpaperAction.Foreground).state
        assertEquals(resumed, step(resumed, PhoneWallpaperAction.Observed(query.generation, snapshot)).state)
        val fresh = step(resumed, PhoneWallpaperAction.Observed(resumed.generation, snapshot)).state
        assertNull(fresh.readyRequest)
        assertFalse(fresh.busy)
    }

    @Test fun selectionChangedDuringProbeCancelsLaunchWithoutBlockingFreshFacts() {
        val query = step(PhoneWallpaperState(snapshot), PhoneWallpaperAction.TryOn).state
        val switched = step(query, PhoneWallpaperAction.SelectionChanged, eligible = false).state
        val result = step(switched, PhoneWallpaperAction.Observed(query.generation, snapshot), eligible = false).state
        assertEquals(snapshot, result.snapshot)
        assertNull(result.readyRequest)
        assertNull(step(ready(), PhoneWallpaperAction.TryOn, eligible = false).state.queryingRequestId)
    }

    @Test fun launchRaceFailureOffersChooserOnExplicitNextTap() {
        val ready = ready()
        val request = requireNotNull(ready.readyRequest)
        val launched = step(ready, PhoneWallpaperAction.Consume(request)).state
        val failed = step(launched, PhoneWallpaperAction.LaunchFailed(request, WallpaperLaunchFailure.NO_HANDLER)).state
        assertTrue(failed.helpVisible)
        assertNull(failed.readyRequest)
        val observed = step(failed, PhoneWallpaperAction.Observed(failed.generation, snapshot)).state
        assertEquals(WallpaperRoute.CHOOSER, observed.path.route)
        assertNull(observed.readyRequest)
        val retry = step(observed, PhoneWallpaperAction.TryOn).state
        assertEquals(WallpaperRoute.CHOOSER, step(retry, PhoneWallpaperAction.Observed(retry.generation, snapshot)).state.readyRequest?.route)
    }

    @Test fun policyChangeBetweenTapAndLaunchBlocksRequest() {
        val query = step(PhoneWallpaperState(snapshot), PhoneWallpaperAction.TryOn).state
        val blocked = step(query, PhoneWallpaperAction.Observed(query.generation, snapshot.copy(allowed = WallpaperFact.Known(false)))).state
        assertNull(blocked.readyRequest)
        assertTrue(blocked.helpVisible)
        assertEquals(WallpaperBlock.POLICY, blocked.path.block)
    }

    @Test fun explicitRefreshClearsPastFailurePreservesFallbackAndTwoMissingHandlersEndAtHelp() {
        val ready = ready()
        val direct = requireNotNull(ready.readyRequest)
        val launched = step(ready, PhoneWallpaperAction.Consume(direct)).state
        val failed = step(launched, PhoneWallpaperAction.LaunchFailed(direct, WallpaperLaunchFailure.NO_HANDLER)).state
        val observed = step(failed, PhoneWallpaperAction.Observed(failed.generation, snapshot)).state
        val manual = step(observed, PhoneWallpaperAction.Refresh).state
        assertNull(manual.failure)
        assertTrue(manual.skipDirect)
        val fresh = step(manual, PhoneWallpaperAction.Observed(manual.generation, snapshot)).state
        assertEquals(WallpaperRoute.CHOOSER, fresh.path.route)
        val query = step(fresh, PhoneWallpaperAction.TryOn).state
        val chooserReady = step(query, PhoneWallpaperAction.Observed(query.generation, snapshot)).state
        val chooser = requireNotNull(chooserReady.readyRequest)
        val chooserLaunched = step(chooserReady, PhoneWallpaperAction.Consume(chooser)).state
        val chooserFailed = step(chooserLaunched, PhoneWallpaperAction.LaunchFailed(chooser, WallpaperLaunchFailure.NO_HANDLER)).state
        val bothFailed = step(chooserFailed, PhoneWallpaperAction.Observed(chooserFailed.generation, snapshot)).state
        assertEquals(WallpaperBlock.NO_HANDLER, bothFailed.path.block)
        assertTrue(bothFailed.helpVisible)
        assertFalse(bothFailed.busy)
        val refreshed = step(bothFailed, PhoneWallpaperAction.Foreground).state
        val resumed = step(refreshed, PhoneWallpaperAction.Observed(refreshed.generation, snapshot)).state
        assertNull(resumed.path.route)
        assertTrue(step(resumed, PhoneWallpaperAction.TryOn).effects.isEmpty())
    }

    @Test fun automaticInvitationCannotShowAfterUserStartsExplicitWallpaperRequest() {
        val known = ApplicationKnowledge.NotApplied(now, now.plusSeconds(60))
        val pending = HubState(foreground = true, knowledge = known, pendingInvitation = known,
            phone = ready(), settings = Outcome.Success(InvitationHistory()))
        assertTrue(HubReducer.reduce(pending, HubAction.EvaluateInvitation(now)).commands.isEmpty())
        val result = Outcome.Success(InvitationClaim.Granted(InvitationPolicy.claimed(InvitationHistory(), now)))
        assertFalse(HubReducer.reduce(pending, HubAction.InvitationFinished(result, known, now)).state.onboardingVisible)
    }

    @Test fun rejectedSessionRoutesDoNotProjectNotAppliedInvitationKnowledge() {
        val inactive = WallpaperFact.Known(WallpaperApplication.INACTIVE)
        val facts = snapshot.copy(home = inactive, lock = inactive)
        val state = HubState(foreground = true, phone = PhoneWallpaperState(generation = 1, refreshing = true,
            skipDirect = true, skipChooser = true), settings = Outcome.Success(InvitationHistory()))
        val observed = HubReducer.reduce(state, HubAction.Phone(PhoneWallpaperAction.Observed(1, facts))).state
        assertEquals(ApplicationKnowledge.Unknown, observed.knowledge)
        assertTrue(HubReducer.reduce(observed, HubAction.EvaluateInvitation(now)).commands.isEmpty())
    }

    @Test fun failedRefreshClearsOldActiveAndOldOnboardingKnowledgeBeforeEvaluation() {
        val active = snapshot.copy(home = WallpaperFact.Known(WallpaperApplication.ACTIVE))
        val initial = HubState(foreground = true, phone = PhoneWallpaperState(active), knowledge = ApplicationKnowledge.Applied)
        val refreshing = HubReducer.reduce(initial, HubAction.Phone(PhoneWallpaperAction.Refresh)).state
        assertNull(refreshing.phone.snapshot)
        assertEquals(ApplicationKnowledge.Unknown, refreshing.knowledge)
        val failed = HubReducer.reduce(refreshing, HubAction.Phone(PhoneWallpaperAction.Observed(refreshing.phone.generation, null))).state
        assertNull(failed.phone.snapshot)
        assertFalse(failed.phone.refreshing)
        val notApplied = initial.copy(knowledge = ApplicationKnowledge.NotApplied(now, now.plusSeconds(60)),
            settings = Outcome.Success(InvitationHistory()))
        val resumed = HubReducer.reduce(notApplied, HubAction.ForegroundStarted(HubSection.THEME)).state
        assertEquals(ApplicationKnowledge.Unknown, resumed.knowledge)
        assertTrue(HubReducer.reduce(resumed, HubAction.EvaluateInvitation(now)).commands.isEmpty())
    }
}
