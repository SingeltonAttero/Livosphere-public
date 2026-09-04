package app.livosphere.hub.theme

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
internal fun isMotionComplete(elapsedMillis: Long, durationMillis: Int): Boolean =
    elapsedMillis >= durationMillis

internal val PreviewMotionPhaseKey = SemanticsPropertyKey<String>("PreviewMotionPhase")
internal var SemanticsPropertyReceiver.previewMotionPhase by PreviewMotionPhaseKey
internal val PreviewStartupAlphaKey = SemanticsPropertyKey<Float>("PreviewStartupAlpha")
internal var SemanticsPropertyReceiver.previewStartupAlpha by PreviewStartupAlphaKey
internal val PreviewTargetAssetKey = SemanticsPropertyKey<String>("PreviewTargetAsset")
internal var SemanticsPropertyReceiver.previewTargetAsset by PreviewTargetAssetKey
internal val PreviewCurrentAssetKey = SemanticsPropertyKey<String>("PreviewCurrentAsset")
internal var SemanticsPropertyReceiver.previewCurrentAsset by PreviewCurrentAssetKey

@Composable
internal fun ThemeScreen(
    selectedSurface: HubSurface,
    hasSeenThemePreview: Boolean,
    onSurfaceSelected: (HubSurface) -> Unit,
    onThemePreviewSeen: () -> Unit,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
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
                    .background(if (selected) HubSelected else MaterialTheme.colorScheme.surface)
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

    LaunchedEffect(selectedSurface) {
        if (!firstSeenSent) {
            firstSeenSent = true
            onThemePreviewSeen()
        }
        if (
            !startupRequested ||
            selectedSurface != initialSurface
        ) {
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

    val surfaceTransition = updateTransition(
        targetState = selectedSurface,
        label = "theme-surface-preview",
    )
    val targetAsset = PreviewAssetResolver.resolve(context, selectedSurface)
    val currentAsset = PreviewAssetResolver.resolve(context, surfaceTransition.currentState)
    val startupVisuallyRunning =
        startupAlpha.value < 1f || startupTranslationY.value != 0f || startupScale.value != 1f
    val applyStartupTransform =
        startupRunning && startupVisuallyRunning && selectedSurface == initialSurface
    val renderedStartupAlpha = if (applyStartupTransform) startupAlpha.value else 1f
    val renderedStartupTranslationY = if (applyStartupTransform) startupTranslationY.value else 0f
    val renderedStartupScale = if (applyStartupTransform) startupScale.value else 1f
    val surfaceSwitchRunning =
        surfaceTransition.currentState != surfaceTransition.targetState || surfaceTransition.isRunning
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
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = renderedStartupAlpha
                translationY = renderedStartupTranslationY
                scaleX = renderedStartupScale
                scaleY = renderedStartupScale
            },
            contentAlignment = Alignment.Center,
        ) {
            surfaceTransition.AnimatedContent(
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        fadeIn(tween(SWITCH_DURATION_MILLIS), initialAlpha = 0.25f) +
                            scaleIn(tween(SWITCH_DURATION_MILLIS), initialScale = 0.985f) +
                            slideInHorizontally(tween(SWITCH_DURATION_MILLIS)) {
                                with(density) { (10.dp * direction).roundToPx() }
                            }
                        ).togetherWith(
                        fadeOut(tween(SWITCH_DURATION_MILLIS)) +
                            scaleOut(tween(SWITCH_DURATION_MILLIS), targetScale = 1.015f),
                    )
                },
                contentKey = { it },
            ) { surface ->
                PreviewImage(PreviewAssetResolver.resolve(context, surface))
            }
        }
    }
}

@Composable
private fun PreviewImage(asset: PreviewAsset) {
    Image(
        painter = painterResource(asset.drawableId),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "theme-preview-art-${asset.symbolicName}" },
    )
}

@Composable
private fun TryOnAction(surface: HubSurface) {
    val explanation = stringResource(surface.actionExplanationResource)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
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
