package app.livosphere.hub.devices

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.livosphere.R

@Composable
internal fun DevicesScreen(settingsFailed: Boolean, onHelp: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("hub-screen-devices"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.hub_section_devices), Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.hub_phone_fact), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.hub_watch_fact), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.hub_unknown_explanation), Modifier.testTag("devices-unknown"),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (settingsFailed) {
            Text(stringResource(R.string.hub_settings_failure), Modifier.testTag("devices-settings-failure"),
                style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onHelp,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("devices-help")) {
            Text(stringResource(R.string.hub_onboarding_help))
        }
    }
    }
}
