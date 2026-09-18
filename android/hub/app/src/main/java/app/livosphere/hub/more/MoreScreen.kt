package app.livosphere.hub.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen

@Composable
internal fun MoreScreen(onSettings: () -> Unit, onInstallation: () -> Unit, onGuide: () -> Unit) {
    HubSectionScreen(R.string.hub_section_more, R.string.more_subtitle, "hub-screen-more") {
        Destination(R.string.hub_section_settings, R.string.more_settings_summary, "more-settings", onSettings)
        Destination(R.string.wallpaper_support, R.string.more_installation_summary, "more-installation", onInstallation)
        Destination(R.string.more_guide, R.string.more_guide_summary, "more-guide", onGuide)
    }
}

@Composable
private fun Destination(title: Int, summary: Int, tag: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag }) {
        Column(Modifier.clickable(role = Role.Button, onClick = onClick).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
