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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.livosphere.hub.wallpaper.*
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
import app.livosphere.hub.onboarding.OnboardingDialog
import app.livosphere.hub.onboarding.Outcome
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HubApp(
    viewModel: HubViewModel,
    onExit: () -> Unit,
    wallpaperLauncher: WallpaperLauncher? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(ThemeKey)
    val navigator = remember(backStack) { HubNavigator(backStack) }
    val restoredSection = backStack.lastOrNull().toSection()
    val latestRestoredSection by rememberUpdatedState(restoredSection)

    LifecycleResumeEffect(viewModel) {
        // The actual restored destination and foreground eligibility enter the reducer together.
        viewModel.onAction(HubAction.ForegroundStarted(latestRestoredSection))
        onPauseOrDispose { viewModel.onAction(HubAction.ForegroundStopped) }
    }

    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val systemResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // RESULT_OK and cancellation carry no application truth.
        viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Returned))
    }
    val launcher = wallpaperLauncher ?: remember(context, systemResult) {
        AndroidWallpaperLauncher(context, systemResult::launch)
    }
    LaunchedEffect(viewModel, launcher, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Acknowledging state must not cancel this collector's own pending launch.
            viewModel.state.collect { observed ->
                val request = observed.phone.readyRequest ?: return@collect
                var dispatched = false
                try {
                    if (viewModel.consumeWallpaperRequest(request)) {
                        // The acknowledgement suspends; recheck actual lifecycle and selection.
                        val current = viewModel.state.value
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && current.foreground &&
                            current.selectedSection == HubSection.THEME && current.selectedSurface == HubSurface.WALLPAPER &&
                            current.phone.generation == request.generation) {
                            val result = launcher.launch(request)
                            dispatched = true
                            if (result is Outcome.Failure) viewModel.onAction(HubAction.Phone(
                                PhoneWallpaperAction.LaunchFailed(request, result.reason)))
                        }
                    }
                } finally {
                    if (!dispatched) viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Abandoned(request)))
                }
            }
        }
    }

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
                    modifier = Modifier.weight(1f).clipToBounds(),
                    onBack = onExit,
                    entryProvider = entryProvider {
                        entry<ThemeKey> {
                            ThemeScreen(
                                selectedSurface = state.selectedSurface,
                                hasSeenThemePreview = state.hasSeenThemePreview,
                                onSurfaceSelected = { surface ->
                                    viewModel.onAction(HubAction.SurfaceSelected(surface))
                                },
                                onThemePreviewSeen = {
                                    viewModel.onAction(HubAction.ThemePreviewSeen)
                                },
                                phoneState = state.phone,
                                onTry = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)) },
                                onPhoneRefresh = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh)) },
                                onPhoneHelp = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.ToggleHelp)) },
                            )
                        }
                        entry<DevicesKey> {
                            DevicesScreen(settingsFailed = state.settings is Outcome.Failure,
                                phoneState = state.phone,
                                onRefresh = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh)) },
                                onPhoneHelp = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.ToggleHelp)) },
                                onHelp = { viewModel.onAction(HubAction.OpenOnboarding) })
                        }
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
        if (state.onboardingVisible) {
            OnboardingDialog(
                onDismiss = { viewModel.onAction(HubAction.DismissOnboarding) },
                onGo = { viewModel.onAction(HubAction.GoToTheme) },
            )
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
