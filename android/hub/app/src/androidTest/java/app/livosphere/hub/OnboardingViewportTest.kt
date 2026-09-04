package app.livosphere.hub

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class OnboardingViewportTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val composeRule = createComposeRule()

    @Test fun helpAndBothExitsRemainReachable() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    HubApp(rememberTestHubViewModel(), onExit = {})
                }
            }
        }
        composeRule.onNodeWithTag("hub-nav-devices").performClick()
        composeRule.onNodeWithTag("devices-help").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-skip").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("devices-help").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding-go").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}")
        fun viewports(): List<Array<Any>> = listOf(
            arrayOf(375, 812, 1f), arrayOf(320, 568, 1f), arrayOf(812, 375, 1f),
            arrayOf(375, 812, 2f), arrayOf(320, 568, 2f), arrayOf(812, 375, 2f),
        )
    }
}
