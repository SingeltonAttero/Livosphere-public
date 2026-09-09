package app.livosphere

import app.livosphere.hub.wallpaper.WallpaperTarget
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import app.livosphere.hub.HubViewModel
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.hub.onboarding.InvitationClaim
import app.livosphere.hub.onboarding.InvitationHistory
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.onboarding.SettingsFailure
import app.livosphere.hub.wallpaper.PhoneWallpaperGateway
import app.livosphere.hub.wallpaper.PhoneWallpaperSnapshot
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.WallpaperPreferences
import app.livosphere.contract.SurfaceSettingsFailure
import app.livosphere.settings.SurfaceSettingsException
import app.livosphere.settings.WallpaperMotionMode
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import app.livosphere.hub.HubAction
import app.livosphere.hub.wallpaper.PhoneWallpaperAction
import app.livosphere.hub.wallpaper.AndroidWallpaperTarget
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The two repositories deliberately have no HubViewModel or Activity dependency. */
class WallpaperSettingsRepositoryTest {
    private lateinit var fixture: OwnedSettingsFixture
    @org.junit.Before fun createStore() { fixture = OwnedSettingsFixture() }
    @org.junit.After fun closeStore() = runBlocking { fixture.close() }

    @Test fun sharedApplicationDatastoreIsVisibleToAnIndependentServiceRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assert.assertSame(app.livosphere.settings.ApplicationSurfaceSettings.get(context),
            app.livosphere.settings.ApplicationSurfaceSettings.get(context.applicationContext))
        val hubRepository = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val serviceRepository = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val original = requireNotNull(hubRepository.touchReactionsEnabled.first())
        try {
            hubRepository.setTouchReactionsEnabled(false)
            assertFalse(requireNotNull(serviceRepository.touchReactionsEnabled.first()))
            hubRepository.setTouchReactionsEnabled(true)
            assertTrue(requireNotNull(serviceRepository.touchReactionsEnabled.first()))
        } finally {
            hubRepository.setTouchReactionsEnabled(original)
        }
    }

    @Test fun productionViewModelSwitchWriteIsObservedByIndependentServiceRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hubRepository = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val serviceRepository = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val original = requireNotNull(hubRepository.touchReactionsEnabled.first())
        val store = ViewModelStore()
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = app.livosphere.hub.wallpaper.AndroidWallpaperTarget.initialBrowsingTarget(context)
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("Not used by settings")
        }
        try {
            val viewModel = HubViewModel(history, gateway, Clock.systemUTC(), hubRepository).also {
                store.put("hub", it)
            }
            viewModel.setTouchReactionsEnabled(false)
            assertFalse(withTimeout(5_000) { serviceRepository.touchReactionsEnabled.filter { it == false }.first()!! })
            assertEquals(false, withTimeout(5_000) { viewModel.touchReactions.filter { it == false }.first() })
        } finally {
            store.clear()
            hubRepository.setTouchReactionsEnabled(original)
        }
    }

    @Test fun motionModesPersistIndependentlyFromExistingTouchPreference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val observer = WallpaperSettingsRepository(fixture.settings, "contour-wallpaper")
        val originalTouch = requireNotNull(writer.touchReactionsEnabled.first())
        val originalMode = requireNotNull(writer.motionMode.first())
        try {
            writer.setTouchReactionsEnabled(false)
            WallpaperMotionMode.entries.forEach { mode ->
                writer.setMotionMode(mode)
                assertEquals(mode, withTimeout(5_000) { observer.motionMode.filter { it == mode }.first() })
                assertFalse(requireNotNull(observer.touchReactionsEnabled.first()))
            }
        } finally {
            writer.setTouchReactionsEnabled(originalTouch)
            writer.setMotionMode(originalMode)
        }
    }

    @Test fun failedProductionSwitchWriteReturnsUiStateToUnavailable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unavailable = object : WallpaperSettingsRepository(fixture.settings, "contour-wallpaper") {
            override val settings = MutableStateFlow<SettingsOutcome<WallpaperPreferences>>(SettingsOutcome.Success(WallpaperPreferences()))
            override suspend fun setTouchReactionsEnabled(enabled: Boolean) {
                // Simulate a stale replay after the failed edit; it must not restore a fake switch.
                settings.value = SettingsOutcome.Success(WallpaperPreferences())
                throw IOException("simulated edit failure")
            }
        }
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = app.livosphere.hub.wallpaper.AndroidWallpaperTarget.initialBrowsingTarget(context)
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("Not used by settings")
        }
        val store = ViewModelStore()
        try {
            val viewModel = HubViewModel(history, gateway, Clock.systemUTC(), unavailable).also { store.put("hub", it) }
            viewModel.setTouchReactionsEnabled(false)
            assertNull(withTimeout(5_000) { viewModel.touchReactions.filter { it == null }.first() })
        } finally {
            store.clear()
        }
    }
    @Test fun delayedSuccessForALeavesBFailedWriteUnavailable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val aTarget = checkNotNull(AndroidWallpaperTarget.resolve(context, "contour-wallpaper"))
        val bTarget = checkNotNull(AndroidWallpaperTarget.resolve(context, "isolation-fixture-wallpaper"))
        val aEntered = CompletableDeferred<Unit>()
        val finishA = CompletableDeferred<Unit>()
        val bFailed = CompletableDeferred<Unit>()
        val bSettings = object : WallpaperSettingsRepository(fixture.settings, bTarget.wallpaperId) {
            override val settings = MutableStateFlow<SettingsOutcome<WallpaperPreferences>>(SettingsOutcome.Success(WallpaperPreferences()))
            override suspend fun setTouchReactionsEnabled(enabled: Boolean) {
                bFailed.complete(Unit)
                throw SurfaceSettingsException(SurfaceSettingsFailure.Write)
            }
        }
        val settings = object : WallpaperSettingsRepository(fixture.settings, aTarget.wallpaperId) {
            override val settings = MutableStateFlow<SettingsOutcome<WallpaperPreferences>>(SettingsOutcome.Success(WallpaperPreferences()))
            override suspend fun setTouchReactionsEnabled(enabled: Boolean) {
                aEntered.complete(Unit)
                finishA.await()
            }
            override fun forWallpaper(wallpaperId: String): WallpaperSettingsRepository =
                if (wallpaperId == bTarget.wallpaperId) bSettings else this
        }
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val initialBrowsingTarget = aTarget
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No foreground probe in this test")
        }
        val store = ViewModelStore()
        val vm = HubViewModel(history, gateway, Clock.systemUTC(), settings).also { store.put("hub", it) }
        try {
            instrumentation.runOnMainSync { vm.setTouchReactionsEnabled(false) }
            withTimeout(5_000) { aEntered.await() }
            vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(bTarget)))
            withTimeout(5_000) { vm.state.first { it.phone.target == bTarget } }
            withTimeout(5_000) { vm.touchReactions.first { it == true } }
            instrumentation.runOnMainSync { vm.setTouchReactionsEnabled(false) }
            withTimeout(5_000) { bFailed.await() }
            instrumentation.waitForIdleSync()
            assertNull(vm.touchReactions.value)
            finishA.complete(Unit)
            instrumentation.waitForIdleSync()
            bSettings.settings.value = SettingsOutcome.Success(WallpaperPreferences(interactionsEnabled = false)) // Delayed old DataStore replay must still fail closed.
            instrumentation.waitForIdleSync()
            assertNull(vm.touchReactions.value)
        } finally {
            finishA.complete(Unit)
            instrumentation.runOnMainSync { store.clear() }
        }
    }

    @Test fun delayedMotionFailureFromPreviousASelectionCannotEraseCurrentASetting() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val a = checkNotNull(AndroidWallpaperTarget.resolve(context, "contour-wallpaper"))
        val b = checkNotNull(AndroidWallpaperTarget.resolve(context, "isolation-fixture-wallpaper"))
        val entered = CompletableDeferred<Unit>()
        val finishOld = CompletableDeferred<Unit>()
        var writes = 0
        val settings = object : WallpaperSettingsRepository(fixture.settings, a.wallpaperId) {
            override val settings = MutableStateFlow<SettingsOutcome<WallpaperPreferences>>(SettingsOutcome.Success(WallpaperPreferences()))
            override suspend fun setMotionMode(mode: WallpaperMotionMode) {
                if (++writes == 1) {
                    entered.complete(Unit)
                    finishOld.await()
                    throw SurfaceSettingsException(SurfaceSettingsFailure.CorruptFile)
                }
                settings.value = SettingsOutcome.Success(WallpaperPreferences(motionMode = mode))
            }
        }
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val initialBrowsingTarget = a
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No foreground probe")
        }
        val store = ViewModelStore()
        val vm = HubViewModel(history, gateway, Clock.systemUTC(), settings).also { store.put("hub", it) }
        try {
            instrumentation.runOnMainSync { vm.setWallpaperMotionMode(WallpaperMotionMode.OFF) }
            withTimeout(5_000) { entered.await() }
            vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(b)))
            withTimeout(5_000) { vm.state.first { it.phone.target == b } }
            vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(a)))
            withTimeout(5_000) { vm.state.first { it.phone.target == a } }
            instrumentation.runOnMainSync { vm.setWallpaperMotionMode(WallpaperMotionMode.REDUCED) }
            withTimeout(5_000) { vm.wallpaperMotion.first { it == WallpaperMotionMode.REDUCED } }
            finishOld.complete(Unit)
            instrumentation.waitForIdleSync()
            assertEquals(WallpaperMotionMode.REDUCED, vm.wallpaperMotion.value)
            assertNull(vm.wallpaperSettingsUi.value.failure)
        } finally {
            finishOld.complete(Unit)
            instrumentation.runOnMainSync { store.clear() }
        }
    }

}
