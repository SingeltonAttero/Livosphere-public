package app.livosphere.hub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import app.livosphere.MainActivity
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun help() {
        composeRule.onNodeWithTag("hub-nav-devices").performClick()
        composeRule.onNodeWithTag("devices-unknown").assertIsDisplayed()
        composeRule.onNodeWithTag("devices-help").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding").assertIsDisplayed()
    }

    @Test fun unknownOpensThemeAndSkipPreservesDevices() {
        composeRule.onNodeWithTag("hub-nav-theme").assertIsSelected()
        composeRule.onNodeWithTag("onboarding").assertDoesNotExist()
        help()
        composeRule.onNodeWithTag("onboarding-skip").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding").assertDoesNotExist()
        composeRule.onNodeWithTag("hub-nav-devices").assertIsSelected()
        composeRule.onNodeWithTag("devices-help").assertIsDisplayed()
    }

    @Test fun goReturnsToExistingPreviewWithoutApplying() {
        help()
        composeRule.onNodeWithTag("onboarding-go").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding").assertDoesNotExist()
        composeRule.onNodeWithTag("hub-nav-theme").assertIsSelected()
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed()
    }

    @Test fun backDismissesAndRecreationKeepsRestoredSection() {
        help()
        // Compose Dialog owns a separate window: select it explicitly and let Espresso await focus.
        onView(isRoot()).inRoot(isDialog()).perform(pressBack())
        composeRule.onNodeWithTag("onboarding").assertDoesNotExist()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("hub-nav-devices").assertIsSelected()
        composeRule.onNodeWithTag("onboarding").assertDoesNotExist()
        composeRule.onNodeWithTag("devices-help").performScrollTo().performClick()
        composeRule.onNodeWithTag("onboarding").assertIsDisplayed()
    }
}
