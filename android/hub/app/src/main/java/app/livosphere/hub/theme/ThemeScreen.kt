package app.livosphere.hub.theme

import androidx.compose.runtime.Composable
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen

@Composable
internal fun ThemeScreen() {
    HubSectionScreen(
        title = R.string.hub_section_theme,
        description = R.string.hub_theme_description,
        testTag = "hub-screen-theme",
    )
}
