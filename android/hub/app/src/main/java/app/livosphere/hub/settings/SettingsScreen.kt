package app.livosphere.hub.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen

/** A control is rendered only after the shared DataStore has supplied an actual setting value. */
@Composable
internal fun SettingsScreen(
    touchReactionsEnabled: Boolean?,
    onTouchReactionsChanged: (Boolean) -> Unit,
) {
    HubSectionScreen(
        title = R.string.hub_section_settings,
        description = R.string.hub_settings_description,
        testTag = "hub-screen-settings",
    ) {
        if (touchReactionsEnabled == null) {
            Text(
                text = stringResource(R.string.hub_touch_reactions_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { testTag = "touch-reactions-notice" },
            )
        } else {
            val touchTitle = stringResource(R.string.hub_touch_reactions_title)
            val touchState = stringResource(if (touchReactionsEnabled) R.string.hub_touch_reactions_on else R.string.hub_touch_reactions_off)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { testTag = "touch-reactions-control" }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = touchTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.hub_touch_reactions_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = touchReactionsEnabled,
                    onCheckedChange = onTouchReactionsChanged,
                    // Native Switch preserves the minimum 48dp target; name and state make this
                    // independent control understandable outside the surrounding visual row.
                    modifier = Modifier.semantics {
                        testTag = "touch-reactions-switch"
                        contentDescription = touchTitle
                        stateDescription = touchState
                    },
                )
            }
        }
    }
}
