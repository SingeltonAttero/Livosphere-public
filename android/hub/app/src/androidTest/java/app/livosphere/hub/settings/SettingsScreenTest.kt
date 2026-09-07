package app.livosphere.hub.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.livosphere.hub.LivosphereTheme
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
                SettingsScreen(enabled) { enabled = it }
            }
        }
        composeRule.onNodeWithTag("touch-reactions-control").assertIsDisplayed()
        composeRule.onNodeWithTag("touch-reactions-switch", useUnmergedTree = true)
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test fun unavailablePersistenceShowsNoticeAndNeverRendersAFakeSwitch() {
        composeRule.setContent { LivosphereTheme { SettingsScreen(null) {} } }
        composeRule.onNodeWithTag("touch-reactions-notice").assertIsDisplayed()
        composeRule.onNodeWithTag("touch-reactions-control").assertDoesNotExist()
    }
}
