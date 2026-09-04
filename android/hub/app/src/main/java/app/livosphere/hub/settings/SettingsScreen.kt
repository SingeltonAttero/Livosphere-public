package app.livosphere.hub.settings

import androidx.compose.runtime.Composable
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen

@Composable
internal fun SettingsScreen() {
    HubSectionScreen(
        title = R.string.hub_section_settings,
        description = R.string.hub_settings_description,
        testTag = "hub-screen-settings",
    )
}
