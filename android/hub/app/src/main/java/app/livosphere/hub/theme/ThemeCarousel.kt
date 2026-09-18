package app.livosphere.hub.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.livosphere.R
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.SetDescriptor
import app.livosphere.hub.*
import app.livosphere.hub.wallpaper.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
internal fun ThemeCarousel(
    setId: String,
    selectedSurface: HubSurface,
    onSurfaceSelected: (HubSurface) -> Unit,
    onSetSelected: (SetDescriptor) -> Unit,
    onThemePreviewSeen: () -> Unit,
    phoneState: PhoneWallpaperState?,
    widgetAvailable: Boolean,
    onTry: () -> Unit,
    onPhoneRefresh: () -> Unit,
    onPhoneHelp: () -> Unit,
    reduced: Boolean,
    previewPainter: (@Composable (HubSurface) -> Painter)?,
) {
    val sets = remember { AuthoredContentCatalog.sets }
    val pager = rememberPagerState(initialPage = sets.indexOfFirst { it.setId.value == setId }.coerceAtLeast(0)) { sets.size }
    val scope = rememberCoroutineScope()
    val selectSet by rememberUpdatedState(onSetSelected)
    val seen by rememberUpdatedState(onThemePreviewSeen)
    LaunchedEffect(pager) {
        seen()
        // currentPage changes as soon as the new page is nearest the snap position.
        // Do not wait for settling: the visible product owns the selection and CTA.
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { selectSet(sets[it]) }
    }
    HorizontalPager(
        state = pager,
        key = { sets[it].setId.value },
        modifier = Modifier.fillMaxSize().semantics { testTag = "hub-screen-theme" },
    ) { index ->
        val set = sets[index]
        val active = index == pager.currentPage
        val context = LocalContext.current
        val name = remember(set, context) { PreviewAssetResolver.displayName(context, set) }
        val asset = remember(set, selectedSurface, context) { PreviewAssetResolver.resolve(context, set, selectedSurface) }
        val wallpaper = selectedSurface == HubSurface.WALLPAPER
        // Adjacent pages can be composed during a swipe; expose only the current page to accessibility.
        BoxWithConstraints(Modifier.fillMaxSize().background(HubArtworkBackdrop)
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})) {
            val viewportHeight = maxHeight
            Box(Modifier.fillMaxSize().semantics {
                testTag = "theme-preview"
                contentDescription = context.getString(if (wallpaper) R.string.preview_wallpaper_named else R.string.preview_watchface_named, name)
                previewTargetAsset = asset?.symbolicName ?: "unavailable"
                previewCurrentAsset = asset?.symbolicName ?: "unavailable"
                previewMotionPhase = "idle"
                previewStartupAlpha = 1f
                previewSwitchProgress = 1f
            }) {
                if (wallpaper && asset != null) Image(
                    painter = previewPainter?.invoke(selectedSurface) ?: painterResource(asset.drawableId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().semantics { testTag = "theme-preview-art-${asset.symbolicName}" },
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to HubArtworkBackdrop.copy(alpha = .08f),
                    .35f to Color.Transparent,
                    1f to HubArtworkBackdrop,
                )))
            }
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                    SurfaceSelector(selectedSurface, onSurfaceSelected, reduced)
                }
                if (wallpaper) Spacer(Modifier.weight(1f).heightIn(min = 180.dp))
                else Box(Modifier.weight(1f).heightIn(min = 240.dp).widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center) {
                    if (asset != null) Image(
                        painter = previewPainter?.invoke(selectedSurface) ?: painterResource(asset.drawableId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(220.dp)
                            .semantics { testTag = "theme-preview-art-${asset.symbolicName}" },
                    )
                }
                // A fixed-opacity neutral backing guarantees contrast independently of the artwork.
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)).background(HubArtworkBackdrop.copy(alpha = .94f))
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.collection_page, index + 1, sets.size), Modifier.weight(1f).padding(end = 8.dp),
                            color = HubOnArtworkSecondary, style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sets.forEachIndexed { page, item ->
                                val itemName = PreviewAssetResolver.displayName(context, item)
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (page == index) HubSelected else HubArtworkControl)
                                    .selectable(selected = page == index, role = Role.RadioButton, onClick = {
                                        // Direct controls jump immediately, including reduced motion; no queued animations.
                                        scope.launch { pager.scrollToPage(page) }
                                    }).semantics { testTag = "collection-${item.setId.value}"; contentDescription = itemName },
                                    contentAlignment = Alignment.Center) {
                                    Text("${page + 1}", color = if (page == index) HubText else HubOnArtwork,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    Text(stringResource(if (wallpaper) R.string.collection_wallpaper_kind else R.string.collection_widget_kind),
                        color = HubOnArtworkSecondary, style = MaterialTheme.typography.labelLarge)
                    Text(name, color = HubOnArtwork, fontSize = 28.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading(); testTag = "collection-title" })
                    Text(stringResource(descriptionResource(set.setId.value, wallpaper)), color = HubOnArtworkSecondary,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { testTag = "collection-description" })
                    Text(stringResource(if (wallpaper) R.string.collection_wallpaper_features else R.string.collection_widget_features),
                        color = HubOnArtwork, style = MaterialTheme.typography.labelMedium)
                    CarouselInstallAction(selectedSurface, phoneState, widgetAvailable,
                        selectionReady = active && setId == set.setId.value &&
                            (!wallpaper || phoneState?.target?.wallpaperId == set.wallpaper.componentId.value),
                        onTry = onTry)
                    if (wallpaper && active && phoneState != null &&
                        (phoneState.failure != null || phoneState.helpVisible ||
                            (phoneState.path.route == null && !phoneState.refreshing && !phoneState.busy))) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                            Box(Modifier.padding(12.dp)) { RecoveryPanel(phoneState, onPhoneRefresh, onPhoneHelp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselInstallAction(
    surface: HubSurface, phoneState: PhoneWallpaperState?, widgetAvailable: Boolean,
    selectionReady: Boolean, onTry: () -> Unit,
) {
    val wallpaper = surface == HubSurface.WALLPAPER
    val available = if (wallpaper) phoneState?.path?.route != null && !phoneState.busy && !phoneState.refreshing else widgetAvailable
    val explanation = when {
        !selectionReady -> stringResource(R.string.phone_checking)
        wallpaper && phoneState?.path?.route == WallpaperRoute.DIRECT && !phoneState.busy -> stringResource(R.string.collection_install_wallpaper_hint)
        wallpaper && phoneState != null -> phonePathExplanation(phoneState)
        wallpaper -> stringResource(R.string.hub_wallpaper_action_explanation)
        else -> stringResource(R.string.collection_install_widget_hint)
    }
    Button(onClick = onTry, enabled = selectionReady && available,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            testTag = "theme-primary-action"; stateDescription = explanation
        }, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(disabledContainerColor = HubDisabledContainer, disabledContentColor = HubOnDisabledContainer)) {
        Text(stringResource(R.string.collection_install), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    Text(explanation, Modifier.fillMaxWidth().semantics { testTag = "theme-action-explanation" },
        color = HubOnArtworkSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
}

private fun descriptionResource(setId: String, wallpaper: Boolean): Int = when (setId) {
    "night-sakura" -> if (wallpaper) R.string.collection_sakura_description else R.string.collection_sakura_clock_description
    "electric-harbor" -> if (wallpaper) R.string.collection_harbor_description else R.string.collection_harbor_clock_description
    "last-light" -> if (wallpaper) R.string.collection_sunset_description else R.string.collection_sunset_clock_description
    else -> R.string.hub_theme_value
}
