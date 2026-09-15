package app.livosphere.hub

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModelStore
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.SurfaceSettingsFailure
import app.livosphere.contract.WallpaperPreferences
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import app.livosphere.settings.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val a = WallpaperTarget("wallpaper-a", WallpaperComponent("app.livosphere", "A"), 29)
    private val b = WallpaperTarget("wallpaper-b", WallpaperComponent("app.livosphere", "B"), 29)
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { models.clear(); Dispatchers.resetMain() }
    private val history = object : HubSettingsRepository {
        override val history = flowOf(Outcome.Success(InvitationHistory()))
        override fun retryHistory() = Unit
        override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
    }
    private val gateway = object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = a
        override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
        override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No system probe")
    }
    private class Store : DataStore<StoredSurfaceSettings> {
        var opens = 0
        var readsFail = false
        var writeFailure: Exception? = null
        val values = MutableStateFlow(StoredSurfaceSettings(schemaVersion = SURFACE_SETTINGS_SCHEMA))
        override val data = flow { opens++; if (readsFail) throw IOException("controlled read"); emitAll(values) }
        override suspend fun updateData(transform: suspend (StoredSurfaceSettings) -> StoredSurfaceSettings): StoredSurfaceSettings {
            writeFailure?.let { throw it }
            return transform(values.value).also { values.value = it }
        }
    }
    private fun vm(settings: WallpaperSettingsRepository) = HubViewModel(history, gateway, Clock.systemUTC(), settings).also { models.put("hub", it) }

    @Test fun oneColdReadPublishesCoherentFailureThenSettingsEntryRetriesAndClearsIt() = runTest(dispatcher) {
        val store = Store().also { it.readsFail = true }
        val model = vm(WallpaperSettingsRepository(SurfaceSettingsRepository(store), a.wallpaperId))
        runCurrent()
        assertEquals(1, store.opens)
        assertEquals(WallpaperSettingsUiState(failure = SurfaceSettingsFailure.Read), model.wallpaperSettingsUi.value)
        store.readsFail = false
        model.onAction(HubAction.SectionSelected(HubSection.SETTINGS)); runCurrent()
        assertEquals(2, store.opens)
        assertEquals(WallpaperSettingsUiState(true, WallpaperMotionMode.NORMAL), model.wallpaperSettingsUi.value)
        model.onAction(HubAction.ForegroundStarted(HubSection.SETTINGS)); runCurrent()
        assertEquals(3, store.opens)
        model.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(b))); runCurrent()
        assertEquals(4, store.opens)
    }

    @Test fun typedWriteFailureSurvivesStaleReplayThenSuccessfulRecoveryClearsNotice() = runTest(dispatcher) {
        val source = SurfaceSettingsRepository(Store())
        var failWrite = true
        val settings = object : WallpaperSettingsRepository(source, a.wallpaperId) {
            override val settings = MutableStateFlow<SettingsOutcome<WallpaperPreferences>>(SettingsOutcome.Success(WallpaperPreferences()))
            override suspend fun setMotionMode(mode: WallpaperMotionMode) {
                if (failWrite) throw SurfaceSettingsException(SurfaceSettingsFailure.CorruptFile)
                settings.value = SettingsOutcome.Success(WallpaperPreferences(motionMode = mode))
            }
        }
        val model = vm(settings); runCurrent()
        model.setWallpaperMotionMode(WallpaperMotionMode.OFF); runCurrent()
        assertEquals(SurfaceSettingsFailure.CorruptFile, model.wallpaperSettingsUi.value.failure)
        assertNull(model.wallpaperSettingsUi.value.motion)
        settings.settings.value = SettingsOutcome.Success(WallpaperPreferences(motionMode = WallpaperMotionMode.REDUCED))
        runCurrent()
        assertEquals(SurfaceSettingsFailure.CorruptFile, model.wallpaperSettingsUi.value.failure)
        assertNull(model.wallpaperSettingsUi.value.motion)
        failWrite = false
        model.setWallpaperMotionMode(WallpaperMotionMode.OFF); runCurrent()
        assertEquals(WallpaperSettingsUiState(true, WallpaperMotionMode.OFF), model.wallpaperSettingsUi.value)
    }

    @Test fun successfulWriteReopensFailedReaderAndTypedWriteFailureOverridesEarlierReadError() = runTest(dispatcher) {
        val store = Store().also { it.readsFail = true }
        val model = vm(WallpaperSettingsRepository(SurfaceSettingsRepository(store), a.wallpaperId))
        runCurrent()
        assertEquals(SurfaceSettingsFailure.Read, model.wallpaperSettingsUi.value.failure)
        store.writeFailure = SurfaceSettingsException(SurfaceSettingsFailure.UnsupportedVersion(99))
        model.setWallpaperMotionMode(WallpaperMotionMode.OFF); runCurrent()
        assertEquals(SurfaceSettingsFailure.UnsupportedVersion(99), model.wallpaperSettingsUi.value.failure)
        store.readsFail = false; store.writeFailure = null
        model.setWallpaperMotionMode(WallpaperMotionMode.REDUCED); runCurrent()
        assertEquals(2, store.opens)
        assertEquals(WallpaperSettingsUiState(true, WallpaperMotionMode.REDUCED), model.wallpaperSettingsUi.value)
    }

    @Test fun obsoleteOwnerAndPreviousSelectionTypedErrorsCannotReplaceCurrentSuccess() = runTest(dispatcher) {
        val source = SurfaceSettingsRepository(Store())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val settings = object : WallpaperSettingsRepository(source, a.wallpaperId) {
            override suspend fun setTouchReactionsEnabled(enabled: Boolean) {
                entered.complete(Unit); release.await()
                throw SurfaceSettingsException(SurfaceSettingsFailure.UnsupportedVersion(99))
            }
        }
        val model = vm(settings); runCurrent()
        model.setTouchReactionsEnabled(false); runCurrent(); assertTrue(entered.isCompleted)
        model.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(b))); runCurrent()
        assertEquals(WallpaperSettingsUiState(true, WallpaperMotionMode.NORMAL), model.wallpaperSettingsUi.value)
        model.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(a))); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(WallpaperSettingsUiState(true, WallpaperMotionMode.NORMAL), model.wallpaperSettingsUi.value)
    }
}
