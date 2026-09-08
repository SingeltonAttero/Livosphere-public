package app.livosphere.hub

import androidx.lifecycle.ViewModelStore
import androidx.datastore.core.DataStore
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.settings.DataStoreHubRuntimeSettings
import app.livosphere.hub.settings.HubMotionMode
import app.livosphere.hub.settings.StoredHubSettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {
    private val now = Instant.parse("2026-09-04T12:00:00Z")
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val knowledge = object : ApplicationKnowledgeProvider {
        override val knowledge = MutableStateFlow<ApplicationKnowledge>(ApplicationKnowledge.Unknown)
    }
    private val result = CompletableDeferred<Outcome<InvitationClaim, SettingsFailure>>()
    private var claims = 0
    private var retries = 0
    private val repository = object : HubSettingsRepository {
        override val history = MutableStateFlow<Outcome<InvitationHistory, SettingsFailure>>(Outcome.Success(InvitationHistory()))
        override fun retryHistory() { retries++ }
        override suspend fun claimInvitation(now: Instant): Outcome<InvitationClaim, SettingsFailure> {
            claims++
            return result.await()
        }
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun vm(started: Boolean = true, section: HubSection = HubSection.THEME) =
        HubViewModel(repository, knowledge, Clock.fixed(now, ZoneOffset.UTC)).also {
            store.put("hub", it)
            if (started) it.onAction(HubAction.ForegroundStarted(section))
        }
    private fun known() { knowledge.knowledge.value = ApplicationKnowledge.NotApplied(now.minusSeconds(1), now.plusSeconds(60)) }
    private fun grant() { result.complete(Outcome.Success(InvitationClaim.Granted(InvitationPolicy.claimed(InvitationHistory(), now)))) }

    @Test fun unknownStartsOnThemeAndHelpWorksDuringLoadingAndFailure() = runTest(dispatcher) {
        repository.history.value = Outcome.Failure(SettingsFailure.Read)
        val vm = vm()
        runCurrent()
        assertEquals(HubSection.THEME, vm.state.value.selectedSection)
        assertFalse(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.OpenOnboarding)
        runCurrent()
        assertTrue(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.GoToTheme)
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        assertEquals(0, claims)
    }

    @Test fun pendingPersistenceDoesNotBlockNavigationOrManualDismiss() = runTest(dispatcher) {
        known()
        val vm = vm()
        runCurrent()
        vm.onAction(HubAction.EvaluateInvitation(now))
        vm.onAction(HubAction.SectionSelected(HubSection.DEVICES))
        vm.onAction(HubAction.OpenOnboarding)
        runCurrent()
        assertEquals(1, claims)
        assertEquals(HubSection.DEVICES, vm.state.value.selectedSection)
        assertTrue(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.DismissOnboarding)
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        grant()
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
    }

    @Test fun knowledgeChangingWhileWriteSuspendsSuppressesLateShow() = runTest(dispatcher) {
        known()
        val vm = vm()
        runCurrent()
        knowledge.knowledge.value = ApplicationKnowledge.Applied
        grant()
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
    }

    @Test fun restoredDestinationWinsOverPendingInvitation() = runTest(dispatcher) {
        known()
        val vm = vm()
        runCurrent()
        vm.onAction(HubAction.NavigationRestored(HubSection.SETTINGS))
        runCurrent()
        grant()
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        assertEquals(HubSection.SETTINGS, vm.state.value.selectedSection)
    }

    @Test fun freshClaimShowsOnlyAfterDurableCompletion() = runTest(dispatcher) {
        known()
        val vm = vm()
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        grant()
        runCurrent()
        assertTrue(vm.state.value.onboardingVisible)
    }

    @Test fun foregroundForwardsRetryAndReevaluatesKnowledgeWithoutAnEmission() = runTest(dispatcher) {
        known()
        val vm = vm(started = false)
        runCurrent()
        assertEquals(0, claims)
        vm.onAction(HubAction.ForegroundStarted(HubSection.DEVICES))
        runCurrent()
        assertEquals(1, retries)
        assertEquals(0, claims)
        vm.onAction(HubAction.ForegroundStopped)
        vm.onAction(HubAction.ForegroundStarted(HubSection.THEME))
        runCurrent()
        assertEquals(2, retries)
        assertEquals(1, claims)
        vm.onAction(HubAction.ForegroundStopped)
        grant()
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.ForegroundStarted(HubSection.THEME))
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
        assertEquals(1, claims)
    }

    @Test fun foregroundDoesNotTreatExpiredKnowledgeAsFresh() = runTest(dispatcher) {
        knowledge.knowledge.value = ApplicationKnowledge.NotApplied(now.minusSeconds(60), now)
        val vm = vm(started = false)
        runCurrent()
        vm.onAction(HubAction.ForegroundStarted(HubSection.THEME))
        runCurrent()
        assertEquals(1, retries)
        assertEquals(0, claims)
    }

    @Test fun neverEmittingHistoryCannotBlockNavigationAndManualHelp() = runTest(dispatcher) {
        val neverEmits = object : HubSettingsRepository {
            override val history: Flow<Outcome<InvitationHistory, SettingsFailure>> = flow { awaitCancellation() }
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant): Outcome<InvitationClaim, SettingsFailure> = error("No claim while loading")
        }
        val vm = HubViewModel(neverEmits, knowledge, Clock.fixed(now, ZoneOffset.UTC)).also { store.put("hub", it) }
        vm.onAction(HubAction.ForegroundStarted(HubSection.THEME))
        vm.onAction(HubAction.SectionSelected(HubSection.DEVICES))
        vm.onAction(HubAction.OpenOnboarding)
        runCurrent()
        assertNull(vm.state.value.settings)
        assertEquals(HubSection.DEVICES, vm.state.value.selectedSection)
        assertTrue(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.ForegroundStopped)
        vm.onAction(HubAction.ForegroundStarted(HubSection.DEVICES))
        runCurrent()
        assertTrue(vm.state.value.onboardingVisible)
        vm.onAction(HubAction.DismissOnboarding)
        runCurrent()
        assertFalse(vm.state.value.onboardingVisible)
    }

    @Test fun runtimeSettingsReachTheProductionViewModelBridgeAndPersistActions() = runTest(dispatcher) {
        val stored = MutableStateFlow(StoredHubSettings(null, 0, false))
        val dataStore = object : DataStore<StoredHubSettings> {
            override val data: Flow<StoredHubSettings> = stored
            override suspend fun updateData(transform: suspend (StoredHubSettings) -> StoredHubSettings): StoredHubSettings =
                transform(stored.value).also { stored.value = it }
        }
        val vm = HubViewModel(
            repository,
            knowledge,
            Clock.fixed(now, ZoneOffset.UTC),
            DataStoreHubRuntimeSettings(dataStore),
        ).also { store.put("runtime", it) }
        runCurrent()
        assertEquals(HubMotionMode.NORMAL, vm.hubMotion.value)
        vm.setHubMotionMode(HubMotionMode.REDUCED)
        vm.dismissReleaseNote("0.1.0")
        runCurrent()
        assertEquals(HubMotionMode.REDUCED, vm.hubMotion.value)
        assertEquals("0.1.0", vm.dismissedReleaseVersion.value)
    }
}
