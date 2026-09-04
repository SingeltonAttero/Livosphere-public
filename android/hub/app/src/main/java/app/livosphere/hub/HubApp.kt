package app.livosphere.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.livosphere.R
import app.livosphere.hub.devices.DevicesScreen
import app.livosphere.hub.navigation.DevicesKey
import app.livosphere.hub.navigation.HubNavigator
import app.livosphere.hub.navigation.SettingsKey
import app.livosphere.hub.navigation.ThemeKey
import app.livosphere.hub.navigation.toSection
import app.livosphere.hub.settings.SettingsScreen
import app.livosphere.hub.theme.ThemeScreen
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HubApp(
    viewModel: HubViewModel,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(ThemeKey)
    val navigator = remember(backStack) { HubNavigator(backStack) }
    val restoredSection = backStack.lastOrNull().toSection()

    LaunchedEffect(restoredSection) {
        viewModel.onAction(HubAction.NavigationRestored(restoredSection))
    }
    LaunchedEffect(viewModel, navigator) {
        viewModel.commands.collectLatest(navigator::execute)
    }

    LivosphereTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.weight(1f),
                    onBack = onExit,
                    entryProvider = entryProvider {
                        entry<ThemeKey> { ThemeScreen() }
                        entry<DevicesKey> { DevicesScreen() }
                        entry<SettingsKey> { SettingsScreen() }
                    },
                )
                HubBottomNavigation(
                    selectedSection = if (state.selectedSection == restoredSection) {
                        state.selectedSection
                    } else {
                        restoredSection
                    },
                    onSectionSelected = { section ->
                        viewModel.onAction(HubAction.SectionSelected(section))
                    },
                )
            }
        }
    }
}

@Composable
private fun HubBottomNavigation(
    selectedSection: HubSection,
    onSectionSelected: (HubSection) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                HubSection.entries.forEach { section ->
                    val selected = section == selectedSection
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .heightIn(min = 56.dp)
                            .clip(shape)
                            .background(
                                if (selected) HubSelected else MaterialTheme.colorScheme.surface,
                            )
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSectionSelected(section) },
                            )
                            .semantics {
                                testTag = "hub-nav-${section.testName}"
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(section.labelResource),
                            color = if (selected) HubPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private val HubSection.labelResource: Int
    get() = when (this) {
        HubSection.THEME -> R.string.hub_section_theme
        HubSection.DEVICES -> R.string.hub_section_devices
        HubSection.SETTINGS -> R.string.hub_section_settings
    }

private val HubSection.testName: String
    get() = name.lowercase()
