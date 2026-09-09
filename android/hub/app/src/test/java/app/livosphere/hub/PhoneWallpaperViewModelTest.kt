package app.livosphere.hub

import androidx.lifecycle.ViewModelStore
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneWallpaperViewModelTest {
    private val now = Instant.parse("2026-09-05T12:00:00Z")
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var claims = 0
    private val unknown = WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO)
    private val facts = PhoneWallpaperSnapshot(now, WallpaperComponent("app.livosphere", "ContourService"), 29, 34,
        WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Known(true),
        WallpaperFact.Known(WallpaperPresence.AVAILABLE), WallpaperFact.Known(true), WallpaperFact.Known(true), unknown, unknown, "contour-wallpaper")
    private val observedTargets = mutableListOf<WallpaperTarget>()
    private val requests = mutableListOf<CompletableDeferred<PhoneWallpaperSnapshot>>()
    private val gateway = object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = facts.target
        override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
        override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot {
            observedTargets += target
            return CompletableDeferred<PhoneWallpaperSnapshot>().also(requests::add).await()
        }
    }
    private val repository = object : HubSettingsRepository {
        override val history = flowOf(Outcome.Success(InvitationHistory()))
        override fun retryHistory() = Unit
        override suspend fun claimInvitation(now: Instant): Outcome<InvitationClaim, SettingsFailure> {
            claims++
            return Outcome.Success(InvitationClaim.Suppressed)
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun vm() = HubViewModel(repository, gateway, Clock.fixed(now, ZoneOffset.UTC)).also {
        store.put("hub", it)
        it.onAction(HubAction.ForegroundStarted(HubSection.THEME))
    }

    @Test fun actorConsumesOnceAndResultDoesNotCreateApplicationFacts() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        requests[0].complete(facts)
        runCurrent()
        repeat(10) { vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)) }
        runCurrent()
        assertEquals(2, requests.size)
        requests[1].complete(facts)
        runCurrent()
        val request = requireNotNull(vm.state.value.phone.readyRequest)
        val claims = List(2) { async { vm.consumeWallpaperRequest(request) } }
        runCurrent()
        assertEquals(listOf(true, false), claims.awaitAll())
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.Returned))
        runCurrent()
        assertNull(vm.state.value.phone.snapshot)
        requests[2].complete(facts)
        runCurrent()
        assertEquals(ApplicationKnowledge.Unknown, vm.state.value.knowledge)
        assertNull(vm.state.value.phone.readyRequest)
        assertFalse(vm.state.value.phone.busy)
    }

    @Test fun staleForegroundResultCannotRestoreInactiveOrShowOnboarding() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        vm.onAction(HubAction.ForegroundStopped)
        vm.onAction(HubAction.ForegroundStarted(HubSection.THEME))
        runCurrent()
        val inactive = WallpaperFact.Known(WallpaperApplication.INACTIVE)
        requests[0].complete(facts.copy(home = inactive, lock = inactive))
        runCurrent()
        assertEquals(ApplicationKnowledge.Unknown, vm.state.value.knowledge)
        assertNull(vm.state.value.phone.snapshot)
        assertEquals(0, claims)
        requests[1].complete(facts.copy(home = WallpaperFact.Known(WallpaperApplication.ACTIVE)))
        runCurrent()
        assertEquals(ApplicationKnowledge.Applied, vm.state.value.knowledge)
        assertEquals(0, claims)
    }

    @Test fun navigationAndHelpWorkWhileProbeSuspendsAndWatchNeverLaunchesPhone() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        vm.onAction(HubAction.SectionSelected(HubSection.DEVICES))
        vm.onAction(HubAction.OpenOnboarding)
        runCurrent()
        assertEquals(HubSection.DEVICES, vm.state.value.selectedSection)
        assertTrue(vm.state.value.onboardingVisible)
        requests[0].complete(facts)
        runCurrent()
        vm.onAction(HubAction.GoToTheme)
        vm.onAction(HubAction.SurfaceSelected(HubSurface.WATCH_FACE))
        runCurrent()
        val beforeWatchTryOn = requests.size
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn))
        runCurrent()
        assertEquals(beforeWatchTryOn, requests.size)
        assertNull(vm.state.value.phone.readyRequest)
        assertEquals(HubSurface.WATCH_FACE, vm.state.value.selectedSurface)
    }

    @Test fun failedProbeDropsActiveAndManualRefreshRecovers() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        requests[0].complete(facts.copy(home = WallpaperFact.Known(WallpaperApplication.ACTIVE)))
        runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh))
        runCurrent()
        assertNull(vm.state.value.phone.snapshot)
        requests[1].completeExceptionally(IllegalStateException("private platform message"))
        runCurrent()
        assertEquals(ApplicationKnowledge.Unknown, vm.state.value.knowledge)
        assertFalse(vm.state.value.phone.refreshing)
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh))
        runCurrent()
        requests.last().complete(facts)
        runCurrent()
        assertEquals(WallpaperRoute.DIRECT, vm.state.value.phone.path.route)
    }

    @Test fun suspendedProbeDeadlineReleasesBusyAndAllowsNavigationAndRetryWithoutAcceptingLateResult() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        requests[0].complete(facts)
        runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn))
        runCurrent()
        val timedOutGeneration = vm.state.value.phone.generation
        assertTrue(vm.state.value.phone.busy)
        advanceTimeBy(HubViewModel.OBSERVATION_DEADLINE_MILLIS)
        runCurrent()
        assertFalse(vm.state.value.phone.busy)
        assertFalse(vm.state.value.phone.refreshing)
        assertNull(vm.state.value.phone.snapshot)
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.Observed(timedOutGeneration, facts)))
        vm.onAction(HubAction.SectionSelected(HubSection.DEVICES))
        vm.onAction(HubAction.OpenOnboarding)
        runCurrent()
        assertNull(vm.state.value.phone.snapshot)
        assertEquals(HubSection.DEVICES, vm.state.value.selectedSection)
        assertTrue(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh))
        runCurrent()
        requests.last().complete(facts)
        runCurrent()
        assertEquals(WallpaperRoute.DIRECT, vm.state.value.phone.path.route)
        requests[1].complete(facts.copy(allowed = WallpaperFact.Known(false)))
        runCurrent()
        assertEquals(facts, vm.state.value.phone.snapshot)
    }

    @Test fun explicitSystemRequestNeverClaimsInvitationWhenTryOnProbeReturnsInactive() = runTest(dispatcher) {
        val vm = vm()
        runCurrent()
        requests[0].complete(facts)
        runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn))
        runCurrent()
        val inactive = WallpaperFact.Known(WallpaperApplication.INACTIVE)
        requests[1].complete(facts.copy(home = inactive, lock = inactive))
        runCurrent()
        assertNotNull(vm.state.value.phone.readyRequest)
        assertEquals(0, claims)
        val consumed = async { vm.consumeWallpaperRequest(requireNotNull(vm.state.value.phone.readyRequest)) }
        runCurrent()
        assertTrue(consumed.await())
        vm.onAction(HubAction.EvaluateInvitation(now))
        runCurrent()
        assertEquals(0, claims)
        assertFalse(vm.state.value.onboardingVisible)
    }
    @Test fun foregroundSelectionBUsesBObservationAndImmutableLaunchAndMissingTargetDisablesIt() = runTest(dispatcher) {
        val vm = vm(); runCurrent()
        val bFacts = facts.copy(component = WallpaperComponent("app.livosphere", "FixtureService"), wallpaperId = "isolation-fixture-wallpaper")
        requests[0].complete(facts); runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)); runCurrent()
        val lateA = requests.last()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(bFacts.target))); runCurrent()
        assertEquals(bFacts.target, observedTargets.last())
        requests.last().complete(bFacts); runCurrent()
        lateA.complete(facts); runCurrent()
        assertEquals(bFacts, vm.state.value.phone.snapshot)
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)); runCurrent()
        assertEquals(bFacts.target, observedTargets.last())
        requests.last().complete(bFacts); runCurrent()
        val requestB = requireNotNull(vm.state.value.phone.readyRequest)
        assertEquals(bFacts.target, requestB.target)
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(null))); runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)); runCurrent()
        assertNull(vm.state.value.phone.readyRequest)
        assertNull(vm.state.value.phone.path.route)
        val staleConsume = async { vm.consumeWallpaperRequest(requestB) }; runCurrent(); assertFalse(staleConsume.await())
    }

    @Test fun sameTargetNavigationPreservesRejectedDirectRoute() = runTest(dispatcher) {
        val vm = vm(); runCurrent(); requests.last().complete(facts); runCurrent()
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)); runCurrent()
        requests.last().complete(facts); runCurrent()
        val request = requireNotNull(vm.state.value.phone.readyRequest)
        val consume = async { vm.consumeWallpaperRequest(request) }; runCurrent(); assertTrue(consume.await())
        vm.onAction(HubAction.Phone(PhoneWallpaperAction.LaunchFailed(request, WallpaperLaunchFailure.NO_HANDLER)))
        runCurrent()
        vm.onAction(HubAction.SectionSelected(HubSection.DEVICES)); runCurrent()
        requests.last().complete(facts); runCurrent()
        vm.onAction(HubAction.GoToTheme); runCurrent()
        requests.last().complete(facts); runCurrent()
        assertEquals(WallpaperRoute.CHOOSER, vm.state.value.phone.path.route)
    }

}
