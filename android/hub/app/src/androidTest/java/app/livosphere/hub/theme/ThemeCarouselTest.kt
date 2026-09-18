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

    @Test fun swipeAndNextThumbnailInstallTheVisibleCollection() {
        lateinit var vm: HubViewModel
        val launches = mutableListOf<WallpaperLaunchRequest>()
        compose.setContent {
            vm = rememberCarouselViewModel()
            HubApp(vm, onExit = {}, wallpaperLauncher = WallpaperLauncher { launches += it; Outcome.Success(Unit) })
        }
        val sets = AuthoredContentCatalog.sets
        sets.forEachIndexed { index, set ->
            if (index == 1) compose.onNodeWithTag("wallpaper-pager").performTouchInput {
                swipe(start = androidx.compose.ui.geometry.Offset(center.x, height * .55f),
                    end = androidx.compose.ui.geometry.Offset(center.x, height * .15f), durationMillis = 400)
            }
            if (index == 2) compose.onNodeWithTag("wallpaper-next").performClick()
            compose.onNodeWithTag("theme-preview-art-${set.preview.wallpaperRef}").assertIsDisplayed()
            compose.onNodeWithTag("collection-description").assertDoesNotExist()
            compose.onNodeWithTag("theme-primary-action").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil { launches.size == index + 1 }
            assertEquals(set.wallpaper.componentId.value, launches.last().target.wallpaperId)
            assertEquals(set.wallpaper.serviceClassName, launches.last().target.component.className)
            compose.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.Returned)) }
        }
        compose.onNodeWithTag("wallpaper-next").performClick()
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
        compose.waitUntil { launches.size == 4 }
        assertEquals(sets.first().wallpaper.componentId.value, launches.last().target.wallpaperId)
    }

    @Test fun widgetFeedInstallsEachWidgetWithoutChangingWallpaper() {
        lateinit var vm: HubViewModel
        val installs = mutableListOf<String>()
        compose.setContent {
            vm = rememberCarouselViewModel()
            HubApp(vm, onExit = {}, widgetLauncher = { installs += it })
        }
        compose.onNodeWithTag("wallpaper-next").performClick()
        val selectedWallpaper = AuthoredContentCatalog.sets[1].wallpaper.componentId.value
        compose.onNodeWithTag("hub-nav-widgets").performClick().assertIsSelected()
        AuthoredContentCatalog.sets.forEach { set ->
            val id = set.clockWidget!!.componentId.value
            compose.onNodeWithTag("hub-screen-widgets").performScrollToNode(hasTestTag("widget-install-$id"))
            compose.onNodeWithTag("widget-install-$id").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(id, installs.last()); assertEquals(selectedWallpaper, vm.state.value.phone.target?.wallpaperId) }
        }
        compose.onNodeWithTag("hub-nav-theme").performClick()
        compose.onNodeWithTag("theme-preview-art-${AuthoredContentCatalog.sets[1].preview.wallpaperRef}").assertIsDisplayed()
        compose.onNodeWithTag("hub-nav-widgets").performClick()
        val lastWidget = AuthoredContentCatalog.sets.last().clockWidget!!.componentId.value
        compose.onNodeWithTag("widget-install-$lastWidget").assertIsDisplayed()
    }

    @Test fun moreOwnsSettingsAndSupportWithBackNavigation() {
        compose.setContent { HubApp(rememberCarouselViewModel(), onExit = {}) }
        compose.onNodeWithTag("hub-nav-settings").assertDoesNotExist()
        compose.onNodeWithTag("hub-nav-devices").assertDoesNotExist()
        compose.onNodeWithTag("hub-nav-more").performClick().assertIsSelected()
        compose.onNodeWithTag("more-settings").performClick()
        compose.onNodeWithTag("hub-screen-settings").assertIsDisplayed()
        compose.onNodeWithTag("hub-nav-more").assertIsSelected()
        compose.onNodeWithTag("hub-back").performClick()
        compose.onNodeWithTag("hub-screen-more").assertIsDisplayed()
        compose.onNodeWithTag("more-installation").performClick()
        compose.onNodeWithTag("hub-screen-devices").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("hub-screen-more").assertIsDisplayed()
        compose.onNodeWithTag("hub-nav-theme").performClick()
        compose.onNodeWithTag("theme-primary-action").assertIsDisplayed()
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
        compose.onNodeWithTag("wallpaper-next").performClick()
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
