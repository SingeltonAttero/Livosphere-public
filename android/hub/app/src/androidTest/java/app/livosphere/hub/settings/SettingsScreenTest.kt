package app.livosphere.hub.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.livosphere.hub.LivosphereTheme
import app.livosphere.wallpapers.contour.WallpaperMotionMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun confirmedSettingUsesNativeSwitchAndChangesSemanticState() {
        composeRule.setContent {
            LivosphereTheme {
                var enabled by remember { mutableStateOf(true) }
                SettingsScreen(
                    touchReactionsEnabled = enabled,
                    onTouchReactionsChanged = { enabled = it },
                    hubMotionMode = HubMotionMode.NORMAL,
                )
            }
        }
        composeRule.onNodeWithTag("settings-wallpaper-toggle").performClick()
        composeRule.onNodeWithTag("touch-reactions-control").assertIsDisplayed()
        composeRule.onNodeWithTag("touch-reactions-switch", useUnmergedTree = true)
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test fun unavailablePersistenceShowsNoticeAndNeverRendersAFakeSwitch() {
        composeRule.setContent {
            LivosphereTheme {
                SettingsScreen(touchReactionsEnabled = null, onTouchReactionsChanged = {})
            }
        }
        composeRule.onNodeWithTag("settings-wallpaper-toggle").performClick()
        composeRule.onNodeWithTag("touch-reactions-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("touch-reactions-control").assertDoesNotExist()
    }

    @Test fun wallpaperMotionRemainsAvailableWhenTouchPersistenceFails() {
        composeRule.setContent {
            LivosphereTheme {
                SettingsScreen(
                    touchReactionsEnabled = null,
                    onTouchReactionsChanged = {},
                    hubMotionMode = HubMotionMode.NORMAL,
                )
            }
        }
        composeRule.onNodeWithTag("settings-wallpaper-toggle").performClick()
        composeRule.onNodeWithTag("wallpaper-motion-normal").assertIsDisplayed()
        composeRule.onNodeWithTag("touch-reactions-control").assertDoesNotExist()
    }

    @Test fun progressiveRowsRevealIndependentChoicesWithoutReplacingThePage() {
        composeRule.setContent {
            LivosphereTheme {
                var wallpaperMotion by remember { mutableStateOf(WallpaperMotionMode.NORMAL) }
                var hubMotion by remember { mutableStateOf(HubMotionMode.NORMAL) }
                SettingsScreen(
                    touchReactionsEnabled = true,
                    onTouchReactionsChanged = {},
                    wallpaperMotionMode = wallpaperMotion,
                    onWallpaperMotionChanged = { wallpaperMotion = it },
                    hubMotionMode = hubMotion,
                    onHubMotionChanged = { hubMotion = it },
                )
            }
        }

        composeRule.onNodeWithTag("settings-wallpaper-toggle").performClick()
        composeRule.onNodeWithTag("wallpaper-motion-off").performClick()
        composeRule.onNodeWithTag("wallpaper-motion-off").assertIsSelected()
        composeRule.onNodeWithTag("settings-hub-motion-toggle").performClick()
        composeRule.onNodeWithTag("hub-motion-normal").assertIsDisplayed()
        composeRule.onNodeWithTag("hub-motion-reduced").assertIsDisplayed()
        composeRule.onNodeWithTag("hub-motion-reduced").performClick().assertIsSelected()
        composeRule.onNodeWithTag("settings-help-toggle").performClick()
        composeRule.onNodeWithTag("settings-help").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-about-toggle").performScrollTo().assertIsDisplayed()
    }
}
