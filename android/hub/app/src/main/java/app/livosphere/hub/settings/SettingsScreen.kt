package app.livosphere.hub.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.hub.HubSectionScreen
import app.livosphere.settings.WallpaperMotionMode
import app.livosphere.contract.SurfaceSettingsFailure
import app.livosphere.contract.WallpaperEffectLevel

/** One scrollable page: preferences are app-owned and never represent platform facts. */
@Composable
internal fun SettingsScreen(
    touchReactionsEnabled: Boolean?,
    onTouchReactionsChanged: (Boolean) -> Unit,
    supportsTouchReactions: Boolean = true,
    wallpaperMotionMode: WallpaperMotionMode? = WallpaperMotionMode.NORMAL,
    wallpaperSettingsFailure: SurfaceSettingsFailure? = null,
    pendingMotion: WallpaperMotionMode? = null,
    pendingTouch: Boolean? = null,
    effectLevel: WallpaperEffectLevel? = null,
    pendingEffectLevel: WallpaperEffectLevel? = null,
    supportsEffectLevels: Boolean = false,
    onEffectLevelChanged: (WallpaperEffectLevel) -> Unit = {},
    onRetryWallpaperSettings: () -> Unit = {},
    onDiscardWallpaperSettings: () -> Unit = {},
    wallpaperName: String? = null,
    onWallpaperMotionChanged: (WallpaperMotionMode) -> Unit = {},
    hubMotionMode: HubMotionMode? = null,
    pendingHubMotion: HubMotionMode? = null,
    onRetryHubMotion: () -> Unit = {},
    onDiscardHubMotion: () -> Unit = {},
    onHubMotionChanged: (HubMotionMode) -> Unit = {},
    /** null means the persisted acknowledgement is unavailable, not that it was dismissed. */
    releaseNoteVisible: Boolean? = null,
    installedVersionName: String = "0.1.0",
    onReleaseNoteDismissed: () -> Unit = {},
) = HubSectionScreen(
    title = R.string.hub_section_settings,
    description = R.string.hub_settings_description,
    testTag = "hub-screen-settings",
) {
    ExpandableSetting("settings-wallpaper", R.string.settings_wallpaper_title, R.string.settings_wallpaper_summary) {
        wallpaperName?.let { Text(stringResource(R.string.phone_selected_wallpaper, it)) }
        if (wallpaperSettingsFailure != null) UnavailableSettingsNotice(when (wallpaperSettingsFailure) {
            is SurfaceSettingsFailure.NeedsConfiguration -> R.string.settings_surface_missing
            is SurfaceSettingsFailure.CorruptRecord, SurfaceSettingsFailure.CorruptFile -> R.string.settings_surface_corrupt
            is SurfaceSettingsFailure.UnsupportedVersion -> R.string.settings_surface_version
            SurfaceSettingsFailure.Read, SurfaceSettingsFailure.Write -> R.string.settings_surface_io
        })
        if (wallpaperSettingsFailure != null) {
            pendingEffectLevel?.let { Text(stringResource(R.string.settings_pending_effect, stringResource(it.labelResource))) }
            pendingMotion?.let { Text(stringResource(R.string.settings_pending_motion, stringResource(it.labelResource))) }
            pendingTouch?.let { Text(stringResource(R.string.settings_pending_touch,
                stringResource(if (it) R.string.hub_touch_reactions_on else R.string.hub_touch_reactions_off))) }
            TextButton(onClick = onRetryWallpaperSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { testTag = "settings-wallpaper-retry" }) { Text(stringResource(R.string.settings_retry)) }
            if (pendingMotion != null || pendingTouch != null || pendingEffectLevel != null) TextButton(onClick = onDiscardWallpaperSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "settings-wallpaper-discard" }) {
                Text(stringResource(R.string.settings_discard))
            }
        }
        if (wallpaperMotionMode == null && wallpaperSettingsFailure == null) UnavailableSettingsNotice(R.string.settings_wallpaper_motion_unavailable)
        else if (wallpaperMotionMode != null && wallpaperSettingsFailure == null) MotionChoice("wallpaper-motion", wallpaperMotionMode, WallpaperMotionMode.entries.toList(), { stringResource(it.labelResource) }, onWallpaperMotionChanged)
        if (supportsEffectLevels && effectLevel != null && wallpaperSettingsFailure == null) {
            Text(stringResource(R.string.settings_effect_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_effect_summary), style = MaterialTheme.typography.bodyMedium)
            MotionChoice("wallpaper-effect", effectLevel, WallpaperEffectLevel.entries.toList(), { stringResource(it.labelResource) }, onEffectLevelChanged)
        }
        if (supportsTouchReactions) {
            if (touchReactionsEnabled == null && wallpaperSettingsFailure == null) UnavailableSettingsNotice(R.string.hub_touch_reactions_unavailable)
            else if (touchReactionsEnabled != null && wallpaperSettingsFailure == null) {
                TouchReactionSwitch(touchReactionsEnabled, onTouchReactionsChanged)
            }
        }
    }
    ExpandableSetting("settings-hub-motion", R.string.settings_hub_motion_title, R.string.settings_hub_motion_summary) {
        if (hubMotionMode == null) {
            UnavailableSettingsNotice(R.string.settings_hub_motion_unavailable)
            pendingHubMotion?.let { Text(stringResource(R.string.settings_pending_motion, stringResource(it.labelResource))) }
            TextButton(onClick = onRetryHubMotion, modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "settings-hub-retry" }) {
                Text(stringResource(R.string.settings_retry))
            }
            if (pendingHubMotion != null) TextButton(onClick = onDiscardHubMotion, modifier = Modifier.heightIn(min = 48.dp)
                .semantics { testTag = "settings-hub-discard" }) { Text(stringResource(R.string.settings_discard)) }
        }
        else MotionChoice("hub-motion", hubMotionMode, HubMotionMode.entries.toList(), { stringResource(it.labelResource) }, onHubMotionChanged)
    }
    ExpandableSetting("settings-whats-new", R.string.settings_whats_new_title, R.string.settings_whats_new_summary) {
        when (releaseNoteVisible) {
            true -> {
                Text(stringResource(R.string.settings_whats_new_body, installedVersionName), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onReleaseNoteDismissed, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.settings_whats_new_dismiss))
                }
            }
            false -> Text(stringResource(R.string.settings_whats_new_dismissed), style = MaterialTheme.typography.bodyMedium)
            null -> Text(
                stringResource(R.string.settings_whats_new_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { testTag = "settings-whats-new-unavailable" },
            )
        }
    }
    ExpandableSetting("settings-help", R.string.settings_help_title, R.string.settings_help_summary) {
        Text(stringResource(R.string.settings_help_body), style = MaterialTheme.typography.bodyMedium)
    }
    ExpandableSetting("settings-about", R.string.settings_about_title, R.string.settings_about_summary) {
        Text(stringResource(R.string.settings_about_body, installedVersionName), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TouchReactionSwitch(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    val title = stringResource(R.string.hub_touch_reactions_title)
    val state = stringResource(if (enabled) R.string.hub_touch_reactions_on else R.string.hub_touch_reactions_off)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { testTag = "touch-reactions-control" }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.hub_touch_reactions_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = enabled, onCheckedChange = onChanged, modifier = Modifier.semantics {
            testTag = "touch-reactions-switch"; contentDescription = title; stateDescription = state
        })
    }
}

@Composable
private fun UnavailableSettingsNotice(messageResource: Int) = Text(
    stringResource(messageResource), color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { testTag = "touch-reactions-notice" },
)

@Composable
private fun ExpandableSetting(tag: String, titleResource: Int, summaryResource: Int, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(titleResource)
    val expandedState = stringResource(if (expanded) R.string.phone_expanded else R.string.phone_collapsed)
    Column(Modifier.fillMaxWidth().semantics { testTag = tag }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    stateDescription = expandedState
                    testTag = "$tag-toggle"
                }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(summaryResource), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (expanded) "−" else "+",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        if (expanded) content()
    }
}

@Composable
private fun <T> MotionChoice(tag: String, selected: T, choices: List<T>, label: @Composable (T) -> String, onSelected: (T) -> Unit) {
    Column(Modifier.selectableGroup().semantics { testTag = "$tag-choice" }) {
        choices.forEach { choice ->
            val choiceLabel = label(choice)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = choice == selected,
                    role = Role.RadioButton,
                    onClick = { onSelected(choice) },
                )
                .semantics {
                    testTag = "$tag-${choice.toString().lowercase()}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = choice == selected, onClick = null)
                Text(choiceLabel, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val WallpaperMotionMode.labelResource: Int get() = when (this) {
    WallpaperMotionMode.NORMAL -> R.string.settings_motion_normal
    WallpaperMotionMode.REDUCED -> R.string.settings_motion_reduced
    WallpaperMotionMode.OFF -> R.string.settings_motion_off
}

private val HubMotionMode.labelResource: Int get() = when (this) {
    HubMotionMode.NORMAL -> R.string.settings_motion_normal
    HubMotionMode.REDUCED -> R.string.settings_motion_reduced
}

private val WallpaperEffectLevel.labelResource: Int get() = when (this) {
    WallpaperEffectLevel.SUBTLE -> R.string.settings_effect_subtle
    WallpaperEffectLevel.BALANCED -> R.string.settings_effect_balanced
    WallpaperEffectLevel.FULL -> R.string.settings_effect_full
}
