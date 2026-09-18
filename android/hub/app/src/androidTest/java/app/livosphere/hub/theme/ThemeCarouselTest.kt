package app.livosphere.hub.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.hub.*
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant

class ThemeCarouselTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipeAndDirectSelectionInstallTheVisibleCollection() {
        lateinit var vm: HubViewModel
        val launches = mutableListOf<WallpaperLaunchRequest>()
        compose.setContent {
            vm = rememberCarouselViewModel()
            HubApp(vm, onExit = {}, wallpaperLauncher = WallpaperLauncher { launches += it; Outcome.Success(Unit) })
        }
        val sets = AuthoredContentCatalog.sets
        sets.forEachIndexed { index, set ->
            if (index > 0) compose.onNodeWithTag("hub-screen-theme").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("collection-${set.setId.value}").assertIsSelected()
            compose.onNodeWithTag("collection-description").assertIsDisplayed()
            compose.onNodeWithTag("theme-primary-action").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil { launches.size == index + 1 }
            assertEquals(set.wallpaper.componentId.value, launches.last().target.wallpaperId)
            assertEquals(set.wallpaper.serviceClassName, launches.last().target.component.className)
            compose.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.Returned)) }
        }
        compose.onNodeWithTag("collection-${sets.first().setId.value}").performClick().assertIsSelected()
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
        compose.waitUntil { launches.size == 4 }
        assertEquals(sets.first().wallpaper.componentId.value, launches.last().target.wallpaperId)
    }

    @Test fun clocksAndReturnFromSettingsKeepTheChosenSet() {
        compose.setContent { HubApp(rememberCarouselViewModel(), onExit = {}) }
        val set = AuthoredContentCatalog.sets.last()
        compose.onNodeWithTag("collection-${set.setId.value}").performClick()
        compose.onNodeWithTag("theme-surface-watchface").performClick().assertIsSelected()
        compose.onNodeWithTag("theme-preview-art-${set.preview.widgetRefs[app.livosphere.contract.WidgetSize.M]}").assertIsDisplayed()
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled()
        compose.onNodeWithTag("hub-nav-settings").performClick()
        compose.onNodeWithTag("hub-nav-theme").performClick()
        compose.onNodeWithTag("collection-${set.setId.value}").assertIsSelected()
        compose.onNodeWithTag("theme-surface-watchface").assertIsSelected()
        // The test VM has no NORMAL motion preference: selection remains usable in reduced motion.
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithTag("collection-${set.setId.value}").assertIsSelected()
    }

    @Test fun staleTargetDisablesInstallUntilSelectionIsAcknowledged() {
        var selected by mutableStateOf(AuthoredContentCatalog.sets.first())
        var acknowledged by mutableStateOf(false)
        var installs = 0
        compose.setContent {
            val context = LocalContext.current
            val first = AuthoredContentCatalog.sets.first()
            val target = checkNotNull(AndroidWallpaperTarget.resolve(context,
                (if (acknowledged) selected else first).wallpaper.componentId.value))
            LivosphereTheme {
                ThemeScreen(HubSurface.WALLPAPER, true, {}, {}, setId = selected.setId.value,
                    onSetSelected = { selected = it }, phoneState = PhoneWallpaperState(snapshot = carouselSnapshot(target)),
                    onTry = { installs++ }, hubMotionReduced = true)
            }
        }
        compose.onNodeWithTag("collection-${AuthoredContentCatalog.sets.last().setId.value}").performClick()
        compose.onNodeWithTag("theme-primary-action").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, installs); acknowledged = true }
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }

    @Test fun unavailableSavedSetNeverInstallsAnotherSet() {
        compose.setContent {
            LivosphereTheme {
                ThemeScreen(HubSurface.WALLPAPER, true, {}, {}, setId = "removed-set", hubMotionReduced = true)
            }
        }
        compose.onNodeWithTag("theme-preview").assert(SemanticsMatcher.expectValue(PreviewTargetAssetKey, "unavailable"))
        compose.onNodeWithTag("theme-primary-action").performScrollTo().assertIsNotEnabled()
    }
}

internal fun carouselSnapshot(target: WallpaperTarget) = phoneUiSnapshot().copy(
    component = target.component, minimumApi = target.minimumApi, wallpaperId = target.wallpaperId,
)

@Composable
internal fun rememberCarouselViewModel(): HubViewModel {
    val context = LocalContext.current
    val store = remember { ViewModelStore() }
    val vm = remember {
        HubViewModel(object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }, object : PhoneWallpaperGateway {
            override val initialBrowsingTarget = AndroidWallpaperTarget.initialBrowsingTarget(context)
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget) = carouselSnapshot(target).also { snapshots.value = it }
        }, Clock.systemUTC()).also { store.put("carousel", it) }
    }
    DisposableEffect(store) { onDispose { store.clear() } }
    return vm
}
