package app.livosphere.hub.theme

import app.livosphere.hub.wallpaper.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.livosphere.R
import app.livosphere.hub.HubControlBorder
import app.livosphere.hub.HubDisabledContainer
import app.livosphere.hub.HubOnDisabledContainer
import app.livosphere.hub.HubPrimary
import app.livosphere.hub.HubSelected
import app.livosphere.hub.HubStage
import app.livosphere.hub.HubSurface
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal const val STARTUP_DURATION_MILLIS = 440
internal const val SWITCH_DURATION_MILLIS = 220
internal val PreviewMotionPhaseKey = SemanticsPropertyKey<String>("PreviewMotionPhase")
internal var SemanticsPropertyReceiver.previewMotionPhase by PreviewMotionPhaseKey
internal val PreviewStartupAlphaKey = SemanticsPropertyKey<Float>("PreviewStartupAlpha")
internal var SemanticsPropertyReceiver.previewStartupAlpha by PreviewStartupAlphaKey
internal val PreviewTargetAssetKey = SemanticsPropertyKey<String>("PreviewTargetAsset")
internal var SemanticsPropertyReceiver.previewTargetAsset by PreviewTargetAssetKey
internal val PreviewCurrentAssetKey = SemanticsPropertyKey<String>("PreviewCurrentAsset")
internal var SemanticsPropertyReceiver.previewCurrentAsset by PreviewCurrentAssetKey
internal val PreviewSwitchProgressKey = SemanticsPropertyKey<Float>("PreviewSwitchProgress")
internal var SemanticsPropertyReceiver.previewSwitchProgress by PreviewSwitchProgressKey

@Composable
internal fun ThemeScreen(
    selectedSurface: HubSurface,
    hasSeenThemePreview: Boolean,
    onSurfaceSelected: (HubSurface) -> Unit,
    onThemePreviewSeen: () -> Unit,
    previewPainter: (@Composable (HubSurface) -> Painter)? = null,
    phoneState: PhoneWallpaperState? = null,
    widgetAvailable: Boolean = true,
    widgetDisplayName: String? = null,
    onTry: () -> Unit = {},
    onPhoneRefresh: () -> Unit = {},
    onPhoneHelp: () -> Unit = {},
    hubMotionReduced: Boolean = false,
    setId: String? = PreviewAssetResolver.initialBrowsingSetId,
    onSetSelected: (app.livosphere.contract.SetDescriptor) -> Unit = {},
) {
    val descriptor = PreviewAssetResolver.descriptor(setId)
    val availableSetId = descriptor?.setId?.value
    val context = LocalContext.current
    val displayName = remember(descriptor, context) { descriptor?.let { PreviewAssetResolver.displayName(context, it) } }
    val systemMotionReduced = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val reduced = systemMotionReduced || hubMotionReduced
    if (descriptor != null) {
        WallpaperFeed(
            setId = descriptor.setId.value, phoneState = phoneState,
            onSetSelected = onSetSelected, onSeen = onThemePreviewSeen,
            onInstall = onTry, onSupport = onPhoneHelp,
        )
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .semantics { testTag = "hub-screen-theme" },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProductHeader(displayName)
            SurfaceSelector(
                selectedSurface = selectedSurface,
                onSurfaceSelected = onSurfaceSelected,
                reduced = reduced,
            )
            key(availableSetId) {
            ArtworkStage(
                selectedSurface = selectedSurface,
                setId = availableSetId,
                displayName = displayName,
                playStartup = !hasSeenThemePreview,
                onThemePreviewSeen = onThemePreviewSeen,
                previewPainter = previewPainter,
                widgetDisplayName = widgetDisplayName,
                reduced = reduced,
            )
            }
            TryOnAction(selectedSurface, phoneState, widgetAvailable, onTry)
            if (selectedSurface == HubSurface.WALLPAPER && phoneState != null &&
                (phoneState.failure != null || phoneState.helpVisible ||
                    (phoneState.path.route == null && !phoneState.refreshing && !phoneState.busy))) {
                RecoveryPanel(phoneState, onPhoneRefresh, onPhoneHelp)
            }
        }
    }
}

@Composable
private fun ProductHeader(displayName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = displayName ?: stringResource(R.string.theme_preview_unavailable),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
        )
        Text(
            text = stringResource(R.string.hub_theme_value),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SurfaceSelector(
    selectedSurface: HubSurface,
    onSurfaceSelected: (HubSurface) -> Unit,
    reduced: Boolean,
) {
    val highlight by animateFloatAsState(
        targetValue = selectedSurface.ordinal.toFloat(),
        animationSpec = tween(if (reduced) 0 else SWITCH_DURATION_MILLIS),
        label = "surface-selector-highlight",
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
        )
        val textMeasurer = rememberTextMeasurer()
        val equalRowTextWidth = ((maxWidth - 8.dp) / 2 - 24.dp).coerceAtLeast(0.dp)
        val labelLayouts = HubSurface.entries.associateWith { surface ->
            textMeasurer.measure(
                text = AnnotatedString(stringResource(surface.labelResource)),
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
            )
        }
        val equalRowFits = with(LocalDensity.current) {
            val available = equalRowTextWidth.roundToPx()
            HubSurface.entries.all { surface ->
                labelLayouts.getValue(surface).size.width + 1 <= available
            }
        }
        val flowLabelSizes = with(LocalDensity.current) {
            HubSurface.entries.associateWith { surface ->
                labelLayouts.getValue(surface).size.let { size ->
                    size.width.toDp() to size.height.toDp()
                }
            }
        }
        if (equalRowFits) Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .drawBehind {
                    val gap = 8.dp.toPx()
                    val itemWidth = (size.width - gap) / 2f
                    val radius = CornerRadius(12.dp.toPx())
                    repeat(2) { index ->
                        drawRoundRect(surfaceColor, Offset(index * (itemWidth + gap), 0f),
                            Size(itemWidth, size.height), radius)
                    }
                    val position = if (reduced) selectedSurface.ordinal.toFloat() else highlight
                    drawRoundRect(HubSelected,
                        Offset((if (rtl) 1f - position else position) * (itemWidth + gap), 0f),
                        Size(itemWidth, size.height), radius)
                }
                .semantics { testTag = "theme-surface-selector" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HubSurface.entries.forEach { surface ->
                SurfaceSelectorItem(
                    surface = surface,
                    selected = surface == selectedSurface,
                    onSurfaceSelected = onSurfaceSelected,
                    modifier = Modifier.weight(1f),
                    ownContainer = false,
                    minHeight = flowLabelSizes.getValue(surface).second + 30.dp,
                )
            }
        } else FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup()
                .semantics { testTag = "theme-surface-selector" },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HubSurface.entries.forEach { surface ->
                SurfaceSelectorItem(
                    surface = surface,
                    selected = surface == selectedSurface,
                    onSurfaceSelected = onSurfaceSelected,
                    modifier = Modifier.width(flowLabelSizes.getValue(surface).first + 32.dp),
                    ownContainer = true,
                    minHeight = flowLabelSizes.getValue(surface).second + 30.dp,
                )
            }
        }
    }
}

@Composable
private fun SurfaceSelectorItem(
    surface: HubSurface,
    selected: Boolean,
    onSurfaceSelected: (HubSurface) -> Unit,
    modifier: Modifier,
    ownContainer: Boolean,
    minHeight: Dp,
) {
    var focused by remember { mutableStateOf(false) }
    var focusAfterSelection by remember { mutableStateOf(false) }
    val focusRequester = remember(surface) { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)
    LaunchedEffect(selected, focusAfterSelection) {
        if (selected && focusAfterSelection) {
            focusRequester.requestFocus()
            focusAfterSelection = false
        }
    }
    Box(
        modifier = modifier
            .heightIn(min = maxOf(52.dp, minHeight))
            .clip(shape)
            .then(
                if (ownContainer) {
                    Modifier.background(if (selected) HubSelected else MaterialTheme.colorScheme.surface)
                } else {
                    Modifier
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) HubPrimary else HubControlBorder,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    focusAfterSelection = true
                    onSurfaceSelected(surface)
                },
            )
            .semantics {
                this.selected = selected
                testTag = "theme-surface-${surface.testName}"
            }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(surface.labelResource),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = true,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
                .semantics { testTag = "theme-surface-label-${surface.testName}" },
        )
    }
}

@Composable
private fun ArtworkStage(
    selectedSurface: HubSurface,
    setId: String?,
    displayName: String?,
    playStartup: Boolean,
    onThemePreviewSeen: () -> Unit,
    previewPainter: (@Composable (HubSurface) -> Painter)?,
    widgetDisplayName: String?,
    reduced: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val startupRequested = remember { playStartup }
    val initialSurface = remember { selectedSurface }
    val initialTranslationY = remember(density) { with(density) { 6.dp.toPx() } }
    val startupAlpha = remember { Animatable(if (startupRequested) 0.45f else 1f) }
    val startupTranslationY = remember {
        Animatable(if (startupRequested) initialTranslationY else 0f)
    }
    val startupScale = remember { Animatable(if (startupRequested) 1.015f else 1f) }
    var startupRunning by remember { mutableStateOf(startupRequested) }
    var firstSeenSent by remember { mutableStateOf(false) }
    var startupAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSurface, reduced) {
        if (!firstSeenSent) {
            firstSeenSent = true
            onThemePreviewSeen()
        }
        val shouldPlayStartup =
            !reduced && !startupAttempted && startupRequested && selectedSurface == initialSurface
        startupAttempted = true
        if (!shouldPlayStartup) {
            startupAlpha.snapTo(1f)
            startupTranslationY.snapTo(0f)
            startupScale.snapTo(1f)
            startupRunning = false
        } else {
            startupRunning = true
            coroutineScope {
                launch { startupAlpha.animateTo(1f, tween(STARTUP_DURATION_MILLIS)) }
                launch { startupTranslationY.animateTo(0f, tween(STARTUP_DURATION_MILLIS)) }
                launch { startupScale.animateTo(1f, tween(STARTUP_DURATION_MILLIS)) }
            }
            startupRunning = false
        }
    }

    var previousSurface by remember { mutableStateOf(selectedSurface) }
    var outgoingSurface by remember { mutableStateOf<HubSurface?>(null) }
    val switchProgress = remember { Animatable(1f) }
    LaunchedEffect(selectedSurface, reduced) {
        val previous = previousSurface
        previousSurface = selectedSurface
        if (reduced || previous == selectedSurface) {
            outgoingSurface = null
            switchProgress.snapTo(1f)
        } else {
            // A new selection cancels the previous tween and replaces its decorative layer.
            outgoingSurface = previous
            switchProgress.snapTo(0f)
            switchProgress.animateTo(1f, tween(SWITCH_DURATION_MILLIS))
            outgoingSurface = null
        }
    }
    val targetAsset = PreviewAssetResolver.resolve(context, setId, selectedSurface)
    val progress = when {
        reduced -> 1f
        previousSurface != selectedSurface -> 0f
        else -> switchProgress.value
    }
    val outgoing = (if (previousSurface != selectedSurface) previousSurface else outgoingSurface)
        .takeIf { progress < 1f && it != selectedSurface }
    val currentAsset = PreviewAssetResolver.resolve(context, setId, outgoing ?: selectedSurface)
    val startupVisuallyRunning =
        startupAlpha.value < 1f || startupTranslationY.value != 0f || startupScale.value != 1f
    val applyStartupTransform =
        !reduced && startupRunning && startupVisuallyRunning && selectedSurface == initialSurface
    val renderedStartupAlpha = if (applyStartupTransform) startupAlpha.value else 1f
    val renderedStartupTranslationY = if (applyStartupTransform) startupTranslationY.value else 0f
    val renderedStartupScale = if (applyStartupTransform) startupScale.value else 1f
    val surfaceSwitchRunning =
        outgoing != null
    val motionPhase = when {
        applyStartupTransform -> "startup"
        surfaceSwitchRunning -> "switch"
        else -> "idle"
    }
    val accessibleDescription = if (
        selectedSurface == HubSurface.WATCH_FACE && targetAsset == null && widgetDisplayName != null
    ) stringResource(R.string.preview_watchface_named, widgetDisplayName)
        else if (targetAsset == null || displayName == null) stringResource(R.string.theme_preview_unavailable)
        else if (setId == "contour-draft") stringResource(selectedSurface.previewDescriptionResource)
        else stringResource(if (selectedSurface == HubSurface.WALLPAPER) R.string.preview_wallpaper_named else R.string.preview_watchface_named, displayName)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(HubStage)
            .semantics {
                testTag = "theme-preview"
                contentDescription = accessibleDescription
                previewMotionPhase = motionPhase
                previewStartupAlpha = renderedStartupAlpha
                previewTargetAsset = targetAsset?.symbolicName ?: "unavailable"
                previewCurrentAsset = currentAsset?.symbolicName ?: "unavailable"
                previewSwitchProgress = progress
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = renderedStartupAlpha
                    translationY = renderedStartupTranslationY
                    scaleX = renderedStartupScale
                    scaleY = renderedStartupScale
                },
            contentAlignment = Alignment.Center,
        ) {
            outgoing?.let { surface ->
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - progress
                    scaleX = 1f + 0.015f * progress
                    scaleY = scaleX
                }) {
                    PreviewImage(PreviewAssetResolver.resolve(context, setId, surface), surface, previewPainter, widgetDisplayName)
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.25f + 0.75f * progress
                scaleX = 0.985f + 0.015f * progress
                scaleY = scaleX
                val direction =
                    if (selectedSurface.ordinal > (outgoing?.ordinal ?: selectedSurface.ordinal)) 1 else -1
                translationX = with(density) { 10.dp.toPx() } * direction * (1f - progress)
            }) {
                PreviewImage(targetAsset, selectedSurface, previewPainter, widgetDisplayName)
            }
        }
    }
}

@Composable
private fun PreviewImage(
    asset: PreviewAsset?,
    surface: HubSurface,
    previewPainter: (@Composable (HubSurface) -> Painter)?,
    widgetDisplayName: String?,
) {
    if (asset == null) {
        if (surface == HubSurface.WATCH_FACE && widgetDisplayName != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("12:34", color = MaterialTheme.colorScheme.onSurface, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
                Text(widgetDisplayName, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else Text(stringResource(R.string.theme_preview_unavailable), modifier = Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        return
    }
    val artTag = "theme-preview-art-${asset.symbolicName}"
    when (surface) {
        HubSurface.WALLPAPER -> Image(
            painter = previewPainter?.invoke(surface) ?: painterResource(asset.drawableId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTag = artTag },
        )

        HubSurface.WATCH_FACE -> BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val previewSize = minOf(maxWidth, maxHeight)
            Image(
                painter = previewPainter?.invoke(surface) ?: painterResource(asset.drawableId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(previewSize)
                    .clip(CircleShape)
                    .semantics { testTag = artTag },
            )
        }
    }
}

@Composable
private fun TryOnAction(surface: HubSurface, phoneState: PhoneWallpaperState?, widgetAvailable: Boolean, onTry: () -> Unit) {
    val phone = phoneState?.takeIf { surface == HubSurface.WALLPAPER }
    val explanation = if (phone != null) phonePathExplanation(phone) else stringResource(surface.actionExplanationResource)
    val label = if (phone?.path?.route == WallpaperRoute.CHOOSER) R.string.phone_chooser_action else surface.actionLabelResource
    val labels = HubSurface.entries.map { stringResource(it.actionLabelResource) } + stringResource(R.string.phone_chooser_action)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = with(density) { (maxWidth - 48.dp).roundToPx().coerceAtLeast(1) }
            val labelHeight = labels.maxOf {
                textMeasurer.measure(
                    AnnotatedString(it),
                    style = labelStyle,
                    constraints = Constraints(maxWidth = labelWidth),
                ).size.height
            }
            Button(
                onClick = onTry,
                enabled = if (surface == HubSurface.WALLPAPER) {
                    phone?.path?.route != null && !phone.busy && !phone.refreshing
                } else widgetAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxOf(52.dp, with(density) { labelHeight.toDp() } + 16.dp))
                    .semantics {
                        testTag = "theme-primary-action"
                        stateDescription = explanation
                    },
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = HubDisabledContainer,
                    disabledContentColor = HubOnDisabledContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(label),
                    style = labelStyle,
                )
            }
        }
        Text(
            text = explanation,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "theme-action-explanation" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val HubSurface.labelResource: Int
    get() = when (this) {
        HubSurface.WALLPAPER -> R.string.hub_surface_wallpaper
        HubSurface.WATCH_FACE -> R.string.hub_surface_watchface
    }

private val HubSurface.previewDescriptionResource: Int
    get() = when (this) {
        HubSurface.WALLPAPER -> R.string.hub_wallpaper_preview_description
        HubSurface.WATCH_FACE -> R.string.hub_watchface_preview_description
    }

private val HubSurface.actionLabelResource: Int
    get() = when (this) {
        HubSurface.WALLPAPER -> R.string.hub_wallpaper_action
        HubSurface.WATCH_FACE -> R.string.hub_watchface_action
    }

private val HubSurface.actionExplanationResource: Int
    get() = when (this) {
        HubSurface.WALLPAPER -> R.string.hub_wallpaper_action_explanation
        HubSurface.WATCH_FACE -> R.string.hub_watchface_action_explanation
    }

private val HubSurface.testName: String
    get() = when (this) {
        HubSurface.WALLPAPER -> "wallpaper"
        HubSurface.WATCH_FACE -> "watchface"
    }
