package app.livosphere.hub.theme

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.hub.HubApp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CarouselViewportTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val compose = createComposeRule()

    @Test fun bothSurfacesKeepInstallSelectionAndNavigationReachable() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    HubApp(rememberCarouselViewModel(), onExit = {})
                }
            }
        }
        val action = compose.onNodeWithTag("theme-primary-action")
        action.assertIsDisplayed().assertIsEnabled()
        val button = action.getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag("wallpaper-next").assertIsDisplayed().getUnclippedBoundsInRoot()
        val nav = compose.onNodeWithTag("hub-nav-theme").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("CTA overlaps next thumbnail", button.bottom <= next.top)
        assertTrue("Thumbnail overlaps navigation", next.bottom <= nav.top)
        assertTrue("CTA too small", button.bottom - button.top >= 47.5.dp)
        compose.onNodeWithTag("hub-nav-widgets").performClick()
        val widget = AuthoredContentCatalog.sets.first().clockWidget!!.componentId.value
        compose.onNodeWithTag("hub-screen-widgets").performScrollToNode(hasTestTag("widget-install-$widget"))
        compose.onNodeWithTag("widget-install-$widget").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("hub-nav-more").performClick()
        compose.onNodeWithTag("more-settings").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("hub-back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("hub-screen-more").assertIsDisplayed()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}")
        fun viewports(): List<Array<Any>> = listOf(
            arrayOf(375, 812, 1f), arrayOf(320, 568, 1f), arrayOf(812, 375, 1f),
            arrayOf(375, 812, 2f), arrayOf(320, 568, 2f), arrayOf(812, 375, 2f),
            arrayOf(800, 1100, 1f),
        )
    }
}
