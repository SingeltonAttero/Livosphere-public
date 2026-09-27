package app.livosphere.hub

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

@RunWith(Parameterized::class)
class HubLargeTextNavigationTest(private val width: Int, private val fontScale: Float) {
    @get:Rule val composeRule = createComposeRule()

    @Test fun adaptiveTextKeepsEveryNavigationLabelWhole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 812.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                    HubApp(rememberTestHubViewModel(), onExit = {})
                }
            }
        }
        listOf(
            "theme" to R.string.hub_section_theme,
            "devices" to R.string.hub_section_devices,
            "settings" to R.string.hub_section_settings,
        ).forEach { (section, label) ->
            composeRule.onNodeWithTag("hub-nav-$section")
                .assertIsDisplayed()
                .assertTextEquals(context.getString(label))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag("hub-nav-label-$section", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("$section at ${width}dp/font$fontScale", 1, layout.lineCount)
            assertFalse("$section at ${width}dp/font$fontScale overflows: size=${layout.size}, " +
                "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        }
        listOf(
            "wallpaper" to R.string.hub_surface_wallpaper,
            "clock-widget" to R.string.hub_surface_widget,
        ).forEach { (surface, label) ->
            composeRule.onNodeWithTag("theme-surface-$surface")
                .assertIsDisplayed()
                .assertTextEquals(context.getString(label))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithTag("theme-surface-label-$surface", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("$surface at ${width}dp/font$fontScale", 1, layout.lineCount)
            assertFalse("$surface at ${width}dp/font$fontScale overflows: size=${layout.size}, " +
                "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp-font{1}")
        fun viewports(): List<Array<Any>> = listOf(
            arrayOf(320, 1f), arrayOf(320, 1.2f), arrayOf(320, 2f),
            arrayOf(480, 1f), arrayOf(480, 1.2f), arrayOf(480, 2f),
        )
    }
}
