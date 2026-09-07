package app.livosphere.hub.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.hub.wallpaper.*

@Composable
internal fun DevicesScreen(settingsFailed: Boolean, onHelp: () -> Unit,
    phoneState: PhoneWallpaperState = PhoneWallpaperState(), onRefresh: () -> Unit = {}, onPhoneHelp: () -> Unit = {}) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val snapshot = phoneState.snapshot
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = 720.dp).fillMaxWidth()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("hub-screen-devices"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.hub_section_devices), Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.testTag("phone-summary").semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phoneState.refreshing) {
                Text(stringResource(R.string.phone_facts_checking), Modifier.testTag("phone-facts-checking"))
            } else {
            Text(stringResource(R.string.phone_availability, availabilityText(snapshot)))
            Text(stringResource(R.string.phone_component_fact, presenceText(snapshot?.presence)))
            Text(stringResource(R.string.phone_home_fact, applicationText(snapshot?.home)))
            Text(stringResource(R.string.phone_lock_fact, applicationText(snapshot?.lock)))
            }
        }
        val disclosureState = stringResource(if (expanded) R.string.phone_expanded else R.string.phone_collapsed)
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("phone-facts-toggle").semantics { stateDescription = disclosureState }) {
            Text(stringResource(if (expanded) R.string.phone_hide_facts else R.string.phone_show_facts))
        }
        if (expanded) {
            Column(Modifier.testTag("phone-facts"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.phone_feature_fact, booleanFactText(snapshot?.feature)))
                Text(stringResource(R.string.phone_support_fact, booleanFactText(snapshot?.supported)))
                Text(stringResource(R.string.phone_policy_fact, booleanFactText(snapshot?.allowed)))
                Text(stringResource(R.string.phone_direct_fact, booleanFactText(snapshot?.directPreview)))
                Text(stringResource(R.string.phone_chooser_fact, booleanFactText(snapshot?.chooser)))
                if (snapshot?.feature !is WallpaperFact.Known) Text(stringResource(R.string.phone_feature_reason, reasonText(snapshot?.feature)))
                if (snapshot?.supported !is WallpaperFact.Known) Text(stringResource(R.string.phone_support_reason, reasonText(snapshot?.supported)))
                if (snapshot?.allowed !is WallpaperFact.Known) Text(stringResource(R.string.phone_policy_reason, reasonText(snapshot?.allowed)))
                if (snapshot?.directPreview !is WallpaperFact.Known) Text(stringResource(R.string.phone_direct_reason, reasonText(snapshot?.directPreview)))
                if (snapshot?.chooser !is WallpaperFact.Known) Text(stringResource(R.string.phone_chooser_reason, reasonText(snapshot?.chooser)))
                Text(stringResource(R.string.phone_component_reason, reasonText(snapshot?.presence)))
                Text(stringResource(R.string.phone_home_reason, reasonText(snapshot?.home)))
                Text(stringResource(R.string.phone_lock_reason, reasonText(snapshot?.lock)))
            }
        }
        RecoveryPanel(phoneState, onRefresh, onPhoneHelp)
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

@Composable
private fun availabilityText(snapshot: PhoneWallpaperSnapshot?): String = stringResource(when {
    snapshot == null -> R.string.phone_unknown
    !snapshot.compatible || snapshot.feature == WallpaperFact.Known(false) || snapshot.supported == WallpaperFact.Known(false) -> R.string.phone_unsupported_short
    snapshot.allowed == WallpaperFact.Known(false) -> R.string.phone_policy_short
    snapshot.feature == WallpaperFact.Known(true) && snapshot.supported == WallpaperFact.Known(true) && snapshot.allowed == WallpaperFact.Known(true) -> R.string.phone_available
    else -> R.string.phone_unknown
})
