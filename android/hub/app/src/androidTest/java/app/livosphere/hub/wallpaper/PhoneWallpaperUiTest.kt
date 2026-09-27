package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.hub.*
import app.livosphere.hub.onboarding.*
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal fun phoneUiSnapshot() = PhoneWallpaperSnapshot(Instant.now(), WallpaperComponent("app.livosphere", "ContourService"), 29, 34,
    WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Known(WallpaperPresence.AVAILABLE),
    WallpaperFact.Known(true), WallpaperFact.Known(true), WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO),
    WallpaperFact.Unknown(UnknownReason.LEGACY_API), "night-sakura-wallpaper")

class PhoneWallpaperUiTest {
    @get:Rule val composeRule = createComposeRule()


    @Test fun missingSelectionFromAvailableAInvalidatesCtaAndNeverLaunchesOldTarget() {
        lateinit var vm: HubViewModel
        val launches = mutableListOf<WallpaperLaunchRequest>()
        composeRule.setContent {
            vm = rememberPhoneTestHubViewModel()
            HubApp(vm, onExit = {}, wallpaperLauncher = WallpaperLauncher { launches += it; Outcome.Success(Unit) })
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled()
        composeRule.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(null))) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme-primary-action").assertIsNotEnabled()
        composeRule.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)) }
        composeRule.waitForIdle()
        assertTrue(launches.isEmpty())
        assertNull(vm.state.value.phone.readyRequest)
    }

    @Test fun androidBoundaryUsesExactPackagedComponentAndIndependentChooser() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intents = mutableListOf<Intent>()
        val launcher = AndroidWallpaperLauncher(context) { intents += it }
        assertEquals(Outcome.Success(Unit), launcher.launch(WallpaperLaunchRequest(1, 1, WallpaperRoute.DIRECT, checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context)))))
        assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, intents[0].action)
        @Suppress("DEPRECATION")
        val component = intents[0].getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT)
        assertEquals(AndroidWallpaperTarget.component(checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context))), component)
        assertEquals("app.livosphere.sets.night_sakura.wallpaper.SceneWallpaperService", component?.className)
        launcher.launch(WallpaperLaunchRequest(2, 1, WallpaperRoute.CHOOSER, checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context))))
        assertEquals(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER, intents[1].action)
        assertFalse(intents[1].hasExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT))
    }

    @Test fun androidBoundarySanitizesExpectedAndUnexpectedLaunchFailures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            ActivityNotFoundException("private handler") to WallpaperLaunchFailure.NO_HANDLER,
            SecurityException("private policy") to WallpaperLaunchFailure.ACCESS_DENIED,
            IllegalArgumentException("private input") to WallpaperLaunchFailure.INVALID_REQUEST,
            IllegalStateException("private incident") to WallpaperLaunchFailure.PLATFORM_INCIDENT,
        ).forEach { (failure, expected) ->
            val launcher = AndroidWallpaperLauncher(context) { throw failure }
            assertEquals(Outcome.Failure(expected), launcher.launch(WallpaperLaunchRequest(1, 1, WallpaperRoute.DIRECT, checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context)))))
        }
    }

    @Test fun enabledPhoneCtaLaunchesOnceAndReturnKeepsUnknownAndRetryAvailable() {
        lateinit var viewModel: HubViewModel
        val launches = mutableListOf<WallpaperLaunchRequest>()
        composeRule.setContent {
            val store = remember { ViewModelStore() }
            viewModel = remember {
                HubViewModel(object : HubSettingsRepository {
                    override val history = flowOf(Outcome.Success(InvitationHistory()))
                    override fun retryHistory() = Unit
                    override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
                }, object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = phoneUiSnapshot().target
                    override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
                    override suspend fun refresh(target: WallpaperTarget) = phoneUiSnapshot().also { snapshots.value = it }
                }, Clock.systemUTC()).also { store.put("hub", it) }
            }
            DisposableEffect(store) { onDispose { store.clear() } }
            val launcher = remember { WallpaperLauncher { launches += it; Outcome.Success(Unit) } }
            HubApp(viewModel, onExit = {}, wallpaperLauncher = launcher)
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            repeat(10) { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)) }
        }
        composeRule.waitForIdle()
        assertEquals(1, launches.size)
        assertEquals(WallpaperRoute.DIRECT, launches.single().route)
        composeRule.runOnIdle { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Returned)) }
        composeRule.onNodeWithTag("theme-primary-action").assertIsEnabled()
        composeRule.runOnIdle { assertEquals(ApplicationKnowledge.Unknown, viewModel.state.value.knowledge) }
        composeRule.onNodeWithTag("theme-surface-clock-widget").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsNotEnabled()
        assertEquals(1, launches.size)
        composeRule.onNodeWithTag("theme-surface-wallpaper").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(2, launches.size)
        assertNotEquals(launches[0].id, launches[1].id)
    }

    @Test fun realHubBoundaryRecoversFromMissingDirectHandlerThroughEnabledChooser() {
        lateinit var vm: HubViewModel
        val routes = mutableListOf<WallpaperRoute>()
        composeRule.setContent {
            vm = rememberPhoneTestHubViewModel()
            val launcher = remember { WallpaperLauncher { request ->
                routes += request.route
                if (request.route == WallpaperRoute.DIRECT) Outcome.Failure(WallpaperLaunchFailure.NO_HANDLER)
                else Outcome.Success(Unit)
            } }
            HubApp(vm, onExit = {}, wallpaperLauncher = launcher)
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitUntil { vm.state.value.phone.path.route == WallpaperRoute.CHOOSER && !vm.state.value.phone.refreshing }
        composeRule.onNodeWithText("Предыдущая попытка открыть системную примерку не удалась. Можно повторить доступный путь.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled()
            .assertTextEquals("Выбрать живые обои").performClick()
        composeRule.waitUntil { routes.size == 2 }
        assertEquals(listOf(WallpaperRoute.DIRECT, WallpaperRoute.CHOOSER), routes)
    }

    @Test fun selectionChangingDuringAcknowledgementAbandonsClaimAndEnablesFreshRetry() {
        lateinit var vm: HubViewModel
        val entered = CompletableDeferred<Unit>()
        val acknowledgement = CompletableDeferred<Unit>()
        val launches = mutableListOf<WallpaperLaunchRequest>()
        composeRule.setContent {
            vm = rememberPhoneTestHubViewModel(beforeAcknowledgement = { entered.complete(Unit); acknowledgement.await() })
            val launcher = remember { WallpaperLauncher { launches += it; Outcome.Success(Unit) } }
            HubApp(vm, onExit = {}, wallpaperLauncher = launcher)
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().performClick()
        composeRule.waitUntil { entered.isCompleted }
        composeRule.onNodeWithTag("theme-surface-clock-widget").performScrollTo().performClick()
        composeRule.runOnIdle { acknowledgement.complete(Unit) }
        composeRule.waitUntil { !vm.state.value.phone.busy }
        assertTrue(launches.isEmpty())
        composeRule.onNodeWithTag("theme-surface-wallpaper").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitUntil { launches.size == 1 }
    }

    @Test fun pauseDuringAcknowledgementRunsFinallyAndNeverLaunchesOnResume() {
        lateinit var vm: HubViewModel
        lateinit var owner: PhoneTestLifecycleOwner
        val entered = CompletableDeferred<Unit>()
        val acknowledgement = CompletableDeferred<Unit>()
        val launches = mutableListOf<WallpaperLaunchRequest>()
        composeRule.setContent {
            owner = remember { PhoneTestLifecycleOwner().also { it.registry.currentState = Lifecycle.State.RESUMED } }
            vm = rememberPhoneTestHubViewModel(beforeAcknowledgement = { entered.complete(Unit); acknowledgement.await() })
            val launcher = remember { WallpaperLauncher { launches += it; Outcome.Success(Unit) } }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { HubApp(vm, onExit = {}, wallpaperLauncher = launcher) }
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().performClick()
        composeRule.waitUntil { entered.isCompleted }
        composeRule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        composeRule.waitUntil { !vm.state.value.foreground && !vm.state.value.phone.busy }
        composeRule.runOnIdle {
            acknowledgement.complete(Unit)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeRule.waitUntil { vm.state.value.foreground && !vm.state.value.phone.refreshing }
        assertTrue(launches.isEmpty())
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitUntil { launches.size == 1 }
    }

    @Test fun refreshingFactsShowProgressThenUnknownRouteReasons() {
        lateinit var vm: HubViewModel
        val result = CompletableDeferred<PhoneWallpaperSnapshot>()
        composeRule.setContent {
            vm = rememberPhoneTestHubViewModel(refresh = { result.await() })
            HubApp(vm, onExit = {})
        }
        composeRule.onNodeWithTag("hub-nav-devices").performClick()
        composeRule.onNodeWithTag("phone-facts-checking").assertIsDisplayed()
        composeRule.onNodeWithText("Главный экран (HOME): не удалось определить").assertDoesNotExist()
        composeRule.runOnIdle { result.complete(phoneUiSnapshot().copy(
            directPreview = WallpaperFact.Unknown(UnknownReason.PROBE_FAILED),
            chooser = WallpaperFact.Unknown(UnknownReason.PROBE_FAILED))) }
        composeRule.waitUntil { !vm.state.value.phone.refreshing }
        composeRule.onNodeWithTag("phone-facts-checking").assertDoesNotExist()
        composeRule.onNodeWithTag("phone-facts-toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Прямая примерка — проверка не удалась или доступ ограничен. Можно повторить.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Системный выбор — проверка не удалась или доступ ограничен. Можно повторить.")
            .performScrollTo().assertIsDisplayed()
    }
}
