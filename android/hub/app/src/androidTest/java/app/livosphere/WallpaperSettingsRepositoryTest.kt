package app.livosphere

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
import app.livosphere.wallpapers.contour.WallpaperSettingsRepository
import app.livosphere.wallpapers.contour.WallpaperMotionMode
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    @Test fun sharedApplicationDatastoreIsVisibleToAnIndependentServiceRepository() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hubRepository = WallpaperSettingsRepository(context)
        val serviceRepository = WallpaperSettingsRepository(context)
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
        val hubRepository = WallpaperSettingsRepository(context)
        val serviceRepository = WallpaperSettingsRepository(context)
        val original = requireNotNull(hubRepository.touchReactionsEnabled.first())
        val store = ViewModelStore()
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(): PhoneWallpaperSnapshot = error("Not used by settings")
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
        val writer = WallpaperSettingsRepository(context)
        val observer = WallpaperSettingsRepository(context)
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
        val unavailable = object : WallpaperSettingsRepository(context) {
            override val touchReactionsEnabled = MutableStateFlow<Boolean?>(true)
            override suspend fun setTouchReactionsEnabled(enabled: Boolean) {
                // Simulate a stale replay after the failed edit; it must not restore a fake switch.
                touchReactionsEnabled.value = true
                throw IOException("simulated edit failure")
            }
        }
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(): PhoneWallpaperSnapshot = error("Not used by settings")
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
}
