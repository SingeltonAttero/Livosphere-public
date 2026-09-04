package app.livosphere.hub.devices

import androidx.compose.runtime.Composable
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen

@Composable
internal fun DevicesScreen() {
    HubSectionScreen(
        title = R.string.hub_section_devices,
        description = R.string.hub_devices_description,
        testTag = "hub-screen-devices",
    )
}
