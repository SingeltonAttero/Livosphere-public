package app.livosphere.hub.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.livosphere.R

@Composable
internal fun phonePathExplanation(state: PhoneWallpaperState): String = stringResource(when {
    state.busy -> R.string.phone_opening
    state.path.route == WallpaperRoute.DIRECT -> R.string.phone_direct_explanation
    state.path.route == WallpaperRoute.CHOOSER -> R.string.phone_chooser_explanation
    else -> when (state.path.block) {
        WallpaperBlock.UNSUPPORTED -> R.string.phone_unsupported
        WallpaperBlock.POLICY -> R.string.phone_policy
        WallpaperBlock.MISSING -> R.string.phone_missing
        WallpaperBlock.DISABLED -> R.string.phone_disabled
        WallpaperBlock.UNKNOWN_COMPONENT -> R.string.phone_unknown_component
        WallpaperBlock.NO_HANDLER -> R.string.phone_no_handler
        else -> if (state.refreshing) R.string.phone_checking else R.string.phone_probe_failed
    }
})

@Composable
internal fun applicationText(fact: WallpaperFact<WallpaperApplication>?): String = stringResource(when (fact) {
    WallpaperFact.Known(WallpaperApplication.ACTIVE) -> R.string.phone_active
    WallpaperFact.Known(WallpaperApplication.INACTIVE) -> R.string.phone_inactive
    else -> R.string.phone_unknown
})

@Composable
internal fun presenceText(fact: WallpaperFact<WallpaperPresence>?): String = stringResource(when (fact) {
    WallpaperFact.Known(WallpaperPresence.AVAILABLE) -> R.string.phone_present
    WallpaperFact.Known(WallpaperPresence.MISSING) -> R.string.phone_missing_short
    WallpaperFact.Known(WallpaperPresence.DISABLED) -> R.string.phone_disabled_short
    else -> R.string.phone_unknown
})

@Composable
internal fun booleanFactText(fact: WallpaperFact<Boolean>?): String = stringResource(when (fact) {
    WallpaperFact.Known(true) -> R.string.phone_yes
    WallpaperFact.Known(false) -> R.string.phone_no
    else -> R.string.phone_unknown
})

@Composable
internal fun reasonText(fact: WallpaperFact<*>?): String = stringResource(when ((fact as? WallpaperFact.Unknown)?.reason) {
    UnknownReason.LEGACY_API -> R.string.phone_reason_legacy
    UnknownReason.NO_COMPONENT_INFO -> R.string.phone_reason_null
    UnknownReason.COMPONENT_UNAVAILABLE -> R.string.phone_reason_component
    UnknownReason.PROBE_FAILED -> R.string.phone_reason_failed
    else -> if (fact is WallpaperFact.Known) R.string.phone_reason_observed else R.string.phone_reason_not_observed
})

@Composable
internal fun RecoveryPanel(state: PhoneWallpaperState, onRefresh: () -> Unit, onHelp: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("phone-recovery"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.failure?.let { failure ->
            Text(stringResource(if (failure == WallpaperLaunchFailure.ACCESS_DENIED) R.string.phone_launch_denied else R.string.phone_launch_failed),
                style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.refreshing && !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("phone-retry")) {
            Text(stringResource(R.string.phone_refresh))
        }
        val expanded = stringResource(if (state.helpVisible) R.string.phone_expanded else R.string.phone_collapsed)
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("phone-help-toggle").semantics { stateDescription = expanded }) {
            Text(stringResource(if (state.helpVisible) R.string.phone_hide_help else R.string.phone_help))
        }
        if (state.helpVisible) {
            Text(stringResource(when (state.path.block) {
                WallpaperBlock.POLICY -> R.string.phone_policy_help
                WallpaperBlock.MISSING, WallpaperBlock.DISABLED -> R.string.phone_component_help
                WallpaperBlock.UNSUPPORTED -> R.string.phone_unsupported_help
                else -> R.string.phone_system_help
            }), Modifier.testTag("phone-help-text"), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
