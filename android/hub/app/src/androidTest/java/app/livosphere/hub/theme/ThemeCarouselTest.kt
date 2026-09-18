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
                swipeLeft()
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
        compose.onNodeWithTag("wallpaper-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("theme-preview-art-${sets[1].preview.wallpaperRef}").assertIsDisplayed()
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
        compose.waitUntil { launches.size == 4 }
        assertEquals(sets[1].wallpaper.componentId.value, launches.last().target.wallpaperId)
        compose.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.Returned)) }
        compose.onNodeWithTag("wallpaper-next").performClick()
        compose.onNodeWithTag("theme-preview-art-${sets.last().preview.wallpaperRef}").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper-next").performClick()
        compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
        compose.waitUntil { launches.size == 5 }
        assertEquals(sets.first().wallpaper.componentId.value, launches.last().target.wallpaperId)
    }

    @Test fun widgetFeedOpensEachCardWithoutChangingWallpaper() {
        lateinit var vm: HubViewModel
        val installs = mutableListOf<String>()
        compose.setContent {
            vm = rememberCarouselViewModel()
            HubApp(vm, onExit = {}, widgetLauncher = { installs += it })
        }
        compose.onNodeWithTag("wallpaper-next").performClick()
        val selectedWallpaper = AuthoredContentCatalog.sets[1].wallpaper.componentId.value
        compose.onNodeWithTag("hub-nav-widgets").performClick().assertIsSelected()
        compose.onNodeWithText("Установить виджет").assertDoesNotExist()
        AuthoredContentCatalog.sets.forEach { set ->
            val id = set.clockWidget!!.componentId.value
            compose.onNodeWithTag("hub-screen-widgets").performScrollToNode(hasTestTag("widget-$id"))
            compose.onNodeWithTag("widget-$id").assertIsDisplayed().assertHasClickAction().performClick()
            compose.runOnIdle { assertEquals(id, installs.last()); assertEquals(selectedWallpaper, vm.state.value.phone.target?.wallpaperId) }
        }
        compose.onNodeWithTag("hub-nav-theme").performClick()
        compose.onNodeWithTag("theme-preview-art-${AuthoredContentCatalog.sets[1].preview.wallpaperRef}").assertIsDisplayed()
        compose.onNodeWithTag("hub-nav-widgets").performClick()
        val lastWidget = AuthoredContentCatalog.sets.last().clockWidget!!.componentId.value
        compose.onNodeWithTag("widget-$lastWidget").assertIsDisplayed()
    }

    @Test fun wallpaperCatalogSelectsAnySceneAndBackKeepsSelection() {
        val launches = mutableListOf<WallpaperLaunchRequest>()
        lateinit var vm: HubViewModel
        compose.setContent {
            vm = rememberCarouselViewModel()
            HubApp(vm, onExit = {}, wallpaperLauncher = WallpaperLauncher { launches += it; Outcome.Success(Unit) })
        }
        AuthoredContentCatalog.sets.reversed().forEachIndexed { index, set ->
            compose.onNodeWithTag("wallpaper-catalog-open").performClick()
            compose.onNodeWithTag("hub-nav-theme").assertIsSelected()
            compose.onNodeWithTag("wallpaper-catalog").performScrollToNode(hasTestTag("wallpaper-catalog-${set.setId.value}"))
            compose.onNodeWithTag("wallpaper-catalog-${set.setId.value}").performClick()
            compose.onNodeWithTag("theme-preview-art-${set.preview.wallpaperRef}").assertIsDisplayed()
            compose.onNodeWithTag("theme-primary-action").assertIsEnabled().performClick()
            compose.waitUntil { launches.size == index + 1 }
            assertEquals(set.wallpaper.componentId.value, launches.last().target.wallpaperId)
            compose.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.Returned)) }
            compose.onNodeWithTag("wallpaper-catalog-open").performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("theme-preview-art-${set.preview.wallpaperRef}").assertIsDisplayed()
        }
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
