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
class HubLargeTextNavigationTest(private val width: Int) {
    @get:Rule val composeRule = createComposeRule()

    @Test fun largeTextKeepsEveryNavigationLabelWhole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 812.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) {
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
            assertEquals("$section at ${width}dp", 1, layout.lineCount)
            assertFalse("$section at ${width}dp overflows", layout.hasVisualOverflow)
        }
        composeRule.onNodeWithTag("theme-primary-action").performScrollTo().assertIsDisplayed()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp-font2")
        fun widths(): List<Array<Any>> = listOf(arrayOf(320), arrayOf(480))
    }
}
