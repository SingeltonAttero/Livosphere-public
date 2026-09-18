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
        if (width == 375 && fontScale == 1f) action.assertIsDisplayed()
        for (surface in listOf("wallpaper", "watchface")) {
            compose.onNodeWithTag("theme-surface-$surface").performScrollTo().performClick().assertIsSelected()
            action.performScrollTo().assertIsDisplayed().assertIsEnabled()
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("Install touch target: $bounds", bounds.bottom - bounds.top >= 47.5.dp)
            AuthoredContentCatalog.sets.forEach { set ->
                val selection = compose.onNodeWithTag("collection-${set.setId.value}")
                selection.performScrollTo().assertIsDisplayed()
                val target = selection.getUnclippedBoundsInRoot()
                // Root coordinates are rounded through physical pixels.
                assertTrue("Collection touch target: $target", target.right - target.left >= 47.5.dp && target.bottom - target.top >= 47.5.dp)
            }
            compose.onNodeWithTag("hub-nav-settings").assertIsDisplayed()
        }
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
