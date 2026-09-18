package app.livosphere.hub.devices

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.livosphere.contract.*
import app.livosphere.hub.LivosphereTheme
import app.livosphere.hub.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Epic9ClosureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun instancesEditExactIdAndRemovalHelpDoesNotClaimDeletion() {
        var edited: Int? = null
        var home = 0
        compose.setContent { LivosphereTheme {
            DevicesScreen(false, {}, widgets = WidgetInstances(items = listOf(
                WidgetInstance(7, "Сакура", WidgetSize.S, false), WidgetInstance(8, "Набережная", WidgetSize.L, false))),
                onEditWidget = { edited = it }, onHome = { home++ })
        } }
        compose.onNodeWithTag("widget-edit-8").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(8, edited)
        compose.onNodeWithTag("widget-removal-help").performScrollTo().performClick()
        compose.onNodeWithText("Перейти на главный экран").performClick()
        assertEquals(1, home)
        compose.onNodeWithTag("widget-instance-8").assertExists()
    }

    @Test fun failedInventoryHasRetryAndDoesNotPretendToBeEmpty() {
        var refreshed = 0
        compose.setContent { LivosphereTheme {
            var failed by remember { mutableStateOf(true) }
            DevicesScreen(false, {}, widgets = WidgetInstances(failed = failed), onRefresh = { refreshed++; failed = false })
        } }
        compose.onNodeWithTag("widget-instances-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("widget-instances-empty").assertDoesNotExist()
        compose.onNodeWithTag("phone-retry").performScrollTo().performClick()
        assertEquals(1, refreshed)
        compose.onNodeWithTag("widget-instances-empty").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyWallpaperCatalogOffersRecoveryWithoutInstallation() {
        var helped = false
        compose.setContent { LivosphereTheme {
            WallpaperFeed(null, null, onSetSelected = {}, onSeen = {}, onInstall = { error("No content") },
                onSupport = { helped = true }, sets = emptyList())
        } }
        compose.onNodeWithTag("theme-primary-action").assertDoesNotExist()
        compose.onNodeWithTag("catalog-recovery").performClick()
        assertTrue(helped)
    }

    @Test fun emptyWidgetCatalogIsExplicitAndMissingNativePreviewDoesNotCrash() {
        compose.setContent { LivosphereTheme {
            androidx.compose.foundation.layout.Column {
                NativeWidgetPreview("missing-widget", WidgetSize.M, Modifier)
                WidgetFeed(rememberLazyListState(), sets = emptyList(), onOpen = { error("No content") })
            }
        } }
        compose.onNodeWithTag("widget-catalog-empty").assertIsDisplayed()
    }

    @Test fun instanceActionsFitCompactAndLandscapeAtLargeText() {
        var geometry by mutableStateOf(DpSize(320.dp, 568.dp))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(geometry)) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
                    LivosphereTheme {
                        DevicesScreen(false, {}, widgets = WidgetInstances(items = listOf(
                            WidgetInstance(7, "Неоновая набережная", WidgetSize.L, false))))
                    }
                }
            }
        }
        for (size in listOf(DpSize(320.dp, 568.dp), DpSize(812.dp, 375.dp))) {
            compose.runOnIdle { geometry = size }
            compose.onNodeWithTag("widget-edit-7").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("devices-widget-catalog").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("widget-removal-help").performScrollTo().assertIsDisplayed()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(1)
        }
    }
}
