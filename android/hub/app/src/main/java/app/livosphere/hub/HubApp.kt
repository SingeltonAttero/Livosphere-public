package app.livosphere.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import app.livosphere.hub.theme.WallpaperFeed
import app.livosphere.hub.theme.WidgetFeed
import app.livosphere.hub.more.MoreScreen
import app.livosphere.hub.navigation.MoreKey
import app.livosphere.hub.navigation.WidgetsKey
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.TextButton
import app.livosphere.hub.onboarding.OnboardingDialog
import app.livosphere.hub.onboarding.Outcome
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import app.livosphere.widgets.runtime.ClockWidgetRuntime
import app.livosphere.widgets.RegistryWidgetCatalog

@Composable
fun HubApp(
    viewModel: HubViewModel,
    onExit: () -> Unit,
    wallpaperLauncher: WallpaperLauncher? = null,
    widgetLauncher: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wallpaperSettings by viewModel.wallpaperSettingsUi.collectAsStateWithLifecycle()
    val hubMotion by viewModel.hubMotion.collectAsStateWithLifecycle()
    val dismissedReleaseVersion by viewModel.dismissedReleaseVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // This is a platform fact, deliberately read from the installed package rather than persisted.
    val installedVersionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }
    val displayedVersionName = installedVersionName ?: stringResource(R.string.settings_version_unavailable)
    val backStack = rememberNavBackStack(ThemeKey)
    val navigator = remember(backStack) { HubNavigator(backStack) }
    val widgetListState = rememberLazyListState()
    val restoredSection = backStack.lastOrNull().toSection()
    val latestRestoredSection by rememberUpdatedState(restoredSection)

    val widgetReconciliationScope = rememberCoroutineScope()

    LifecycleResumeEffect(viewModel) {
        widgetReconciliationScope.launch {
            val catalog = RegistryWidgetCatalog(context)
            ClockWidgetRuntime.repository(context).pendingPins(catalog::contains).cleanup(System.currentTimeMillis())
            ClockWidgetRuntime.reconcileOwnWidgets(context)
        }
        // The actual restored destination and foreground eligibility enter the reducer together.
        viewModel.onAction(HubAction.ForegroundStarted(latestRestoredSection))
        onPauseOrDispose { viewModel.onAction(HubAction.ForegroundStopped) }
    }

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

    val immersive = restoredSection == HubSection.THEME
    val density = LocalDensity.current
    var navHeightPx by remember { mutableIntStateOf(with(density) { 100.dp.roundToPx() }) }
    val navHeight = with(density) { navHeightPx.toDp() }
    val view = LocalView.current
    val window = (context as? android.app.Activity)?.window
    SideEffect {
        window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !immersive
                isAppearanceLightNavigationBars = !immersive
            }
            it.isNavigationBarContrastEnforced = false
        }
    }
    val goBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            viewModel.onAction(HubAction.NavigationRestored(backStack.lastOrNull().toSection()))
        } else onExit()
    }
    LivosphereTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                onBack = goBack,
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                entryProvider = entryProvider {
                    entry<ThemeKey> {
                        val selectedSet = app.livosphere.content.AuthoredContentCatalog.sets.singleOrNull {
                            it.wallpaper.componentId.value == state.phone.target?.wallpaperId
                        }
                        WallpaperFeed(
                            setId = selectedSet?.setId?.value,
                            bottomInset = navHeight,
                            phoneState = state.phone,
                            onSetSelected = { set ->
                                val target = AndroidWallpaperTarget.resolve(context, set.wallpaper.componentId.value)
                                if (target != null) viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(target)))
                            },
                            onSeen = { viewModel.onAction(HubAction.ThemePreviewSeen) },
                            onInstall = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.TryOn)) },
                            onSupport = { viewModel.onAction(HubAction.SectionSelected(HubSection.DEVICES)) },
                        )
                    }
                    entry<WidgetsKey> {
                        HubContentArea(navHeight) {
                            WidgetFeed(widgetListState) { widgetId ->
                                if (widgetLauncher != null) widgetLauncher(widgetId)
                                else context.startActivity(ClockWidgetRuntime.prePinIntent(context, widgetId))
                            }
                        }
                    }
                    entry<MoreKey> {
                        HubContentArea(navHeight) {
                            MoreScreen(
                                onSettings = { viewModel.onAction(HubAction.SectionSelected(HubSection.SETTINGS)) },
                                onInstallation = { viewModel.onAction(HubAction.SectionSelected(HubSection.DEVICES)) },
                                onGuide = { viewModel.onAction(HubAction.OpenOnboarding) },
                            )
                        }
                    }
                    entry<DevicesKey> {
                        NestedScreen(navHeight, goBack) {
                            DevicesScreen(settingsFailed = state.settings is Outcome.Failure,
                                phoneState = state.phone,
                                onRefresh = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.Refresh)) },
                                onPhoneHelp = { viewModel.onAction(HubAction.Phone(PhoneWallpaperAction.ToggleHelp)) },
                                onHelp = { viewModel.onAction(HubAction.OpenOnboarding) })
                        }
                    }
                    entry<SettingsKey> {
                        NestedScreen(navHeight, goBack) {
                            SettingsScreen(
                                touchReactionsEnabled = wallpaperSettings.touchReactions,
                                supportsTouchReactions = app.livosphere.content.AuthoredContentCatalog.sets
                                    .singleOrNull { it.wallpaper.componentId.value == state.phone.target?.wallpaperId }
                                    ?.wallpaper?.supportedSettings?.contains(app.livosphere.contract.SupportedSetting.TAP) == true,
                                onTouchReactionsChanged = viewModel::setTouchReactionsEnabled,
                                wallpaperMotionMode = wallpaperSettings.motion,
                                wallpaperSettingsFailure = wallpaperSettings.failure,
                                onWallpaperMotionChanged = viewModel::setWallpaperMotionMode,
                                hubMotionMode = hubMotion,
                                onHubMotionChanged = viewModel::setHubMotionMode,
                                releaseNoteVisible = if (hubMotion == null || installedVersionName == null) null
                                else dismissedReleaseVersion != installedVersionName,
                                installedVersionName = displayedVersionName,
                                onReleaseNoteDismissed = { installedVersionName?.let(viewModel::dismissReleaseNote) },
                            )
                        }
                    }
                },
            )
            Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { navHeightPx = it.height }) {
                HubBottomNavigation(
                    selectedSection = when (restoredSection) {
                        HubSection.DEVICES, HubSection.SETTINGS -> HubSection.MORE
                        else -> restoredSection
                    },
                    immersive = immersive,
                    onSectionSelected = { viewModel.onAction(HubAction.SectionSelected(it)) },
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
private fun HubContentArea(bottomInset: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    // Insets belong to the destination, so the outgoing list never receives the
    // wallpaper's larger viewport and clamps its saved scroll position.
    Box(Modifier.fillMaxSize().padding(bottom = bottomInset)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        .clipToBounds()) { content() }
}

@Composable
private fun NestedScreen(bottomInset: androidx.compose.ui.unit.Dp, onBack: () -> Unit, content: @Composable () -> Unit) {
    HubContentArea(bottomInset) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)
                .semantics { testTag = "hub-back" }) { Text(stringResource(R.string.hub_back)) }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
