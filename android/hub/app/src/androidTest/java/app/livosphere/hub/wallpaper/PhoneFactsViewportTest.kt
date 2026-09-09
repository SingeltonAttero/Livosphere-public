package app.livosphere.hub.wallpaper

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.livosphere.hub.LivosphereTheme
import app.livosphere.hub.HubApp
import app.livosphere.hub.devices.DevicesScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PhoneFactsViewportTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val composeRule = createComposeRule()

    @Test fun independentFactsDisclosureAndHelpRemainReachableWithOneScroll() {
        var refreshed = 0
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    LivosphereTheme {
                        DevicesScreen(false, onHelp = {}, phoneState = PhoneWallpaperState(
                            snapshot = phoneUiSnapshot().copy(home = WallpaperFact.Known(WallpaperApplication.ACTIVE)),
                            helpVisible = true), onRefresh = { refreshed++ })
                    }
                }
            }
        }
        composeRule.onNodeWithText("Главный экран (HOME): применены выбранные обои").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Экран блокировки (LOCK): не удалось определить").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("phone-facts-toggle").performScrollTo().performClick()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Развёрнуто"))
        composeRule.onNodeWithText("LOCK — эта версия Android не предоставляет независимую проверку HOME и LOCK.")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("phone-retry").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, refreshed)
        composeRule.onNodeWithTag("phone-help-text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("devices-help").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(1)
    }

    @Test fun fullHubRecoveryKeepsHelpActionAndNavigationReachable() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    HubApp(rememberPhoneTestHubViewModel(refresh = {
                        phoneUiSnapshot().copy(allowed = WallpaperFact.Known(false))
                    }), onExit = {})
                }
            }
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("phone-help-toggle").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("phone-help-text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("phone-retry").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(1)
        composeRule.onNodeWithTag("hub-nav-devices").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("phone-facts-toggle").performScrollTo().performClick()
        composeRule.onNodeWithText("Изменение разрешено: нет").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hub-nav-settings").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("hub-nav-theme").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("theme-surface-watchface").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithTag("phone-recovery").assertDoesNotExist()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}")
        fun viewports(): List<Array<Any>> = listOf(
            arrayOf(375, 812, 1f), arrayOf(320, 568, 1f), arrayOf(812, 375, 1f),
            arrayOf(375, 812, 2f), arrayOf(320, 568, 2f), arrayOf(812, 375, 2f),
        )
    }
}
