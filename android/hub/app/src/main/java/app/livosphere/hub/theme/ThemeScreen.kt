package app.livosphere.hub.theme

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
private val WallpaperPreviewAlignment = BiasAlignment(
    horizontalBias = 0f,
    verticalBias = 0.24f,
)

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
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .semantics { testTag = "hub-screen-theme" },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProductHeader()
            SurfaceSelector(
                selectedSurface = selectedSurface,
                onSurfaceSelected = onSurfaceSelected,
            )
            ArtworkStage(
                selectedSurface = selectedSurface,
                playStartup = !hasSeenThemePreview,
                onThemePreviewSeen = onThemePreviewSeen,
                previewPainter = previewPainter,
            )
            TryOnAction(selectedSurface)
        }
    }
}

@Composable
private fun ProductHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.hub_theme_name),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 44.sp,
        )
        Text(
            text = stringResource(R.string.hub_theme_value),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SurfaceSelector(
    selectedSurface: HubSurface,
    onSurfaceSelected: (HubSurface) -> Unit,
) {
    val reduced = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val highlight by animateFloatAsState(
        targetValue = selectedSurface.ordinal.toFloat(),
        animationSpec = tween(if (reduced) 0 else SWITCH_DURATION_MILLIS),
        label = "surface-selector-highlight",
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .drawBehind {
                val gap = 8.dp.toPx()
                val itemWidth = (size.width - gap) / 2f
                val radius = CornerRadius(12.dp.toPx())
                repeat(2) { index ->
                    drawRoundRect(
                        surfaceColor,
                        Offset(index * (itemWidth + gap), 0f),
                        Size(itemWidth, size.height),
                        radius,
                    )
                }
                val position = if (reduced) selectedSurface.ordinal.toFloat() else highlight
                drawRoundRect(
                    HubSelected,
                    Offset((if (rtl) 1f - position else position) * (itemWidth + gap), 0f),
                    Size(itemWidth, size.height),
                    radius,
                )
            }
            .semantics { testTag = "theme-surface-selector" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HubSurface.entries.forEach { surface ->
            val selected = surface == selectedSurface
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
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(shape)
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
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun ArtworkStage(
    selectedSurface: HubSurface,
    playStartup: Boolean,
    onThemePreviewSeen: () -> Unit,
    previewPainter: (@Composable (HubSurface) -> Painter)?,
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
    val reduced = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f

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
    val targetAsset = PreviewAssetResolver.resolve(context, selectedSurface)
    val progress = when {
        reduced -> 1f
        previousSurface != selectedSurface -> 0f
        else -> switchProgress.value
    }
    val outgoing = (if (previousSurface != selectedSurface) previousSurface else outgoingSurface)
        .takeIf { progress < 1f && it != selectedSurface }
    val currentAsset = PreviewAssetResolver.resolve(context, outgoing ?: selectedSurface)
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
    val accessibleDescription = stringResource(selectedSurface.previewDescriptionResource)
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
                previewTargetAsset = targetAsset.symbolicName
                previewCurrentAsset = currentAsset.symbolicName
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
                    PreviewImage(PreviewAssetResolver.resolve(context, surface), surface, previewPainter)
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
                PreviewImage(targetAsset, selectedSurface, previewPainter)
            }
        }
    }
}

@Composable
private fun PreviewImage(
    asset: PreviewAsset,
    surface: HubSurface,
    previewPainter: (@Composable (HubSurface) -> Painter)?,
) {
    val artTag = "theme-preview-art-${asset.symbolicName}"
    when (surface) {
        HubSurface.WALLPAPER -> Image(
            painter = previewPainter?.invoke(surface) ?: painterResource(asset.drawableId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = WallpaperPreviewAlignment,
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
private fun TryOnAction(surface: HubSurface) {
    val explanation = stringResource(surface.actionExplanationResource)
    val labels = HubSurface.entries.map { stringResource(it.actionLabelResource) }
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
                onClick = {},
                enabled = false,
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
                    text = stringResource(surface.actionLabelResource),
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
