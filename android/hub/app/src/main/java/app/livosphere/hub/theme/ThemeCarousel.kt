package app.livosphere.hub.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.SetDescriptor
import app.livosphere.hub.*
import app.livosphere.hub.wallpaper.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The artwork fills the window; all controls are inset above the overlay navigation. */
@Composable
internal fun WallpaperFeed(
    setId: String?,
    phoneState: PhoneWallpaperState?,
    bottomInset: Dp = 0.dp,
    onSetSelected: (SetDescriptor) -> Unit,
    onSeen: () -> Unit,
    onInstall: () -> Unit,
    onSupport: () -> Unit,
    onCatalog: (() -> Unit)? = null,
    sets: List<SetDescriptor> = AuthoredContentCatalog.sets,
) {
    if (sets.isEmpty() || sets.none { it.setId.value == setId }) {
        Box(Modifier.fillMaxSize().background(HubArtworkBackdrop), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.theme_preview_unavailable), color = HubOnArtwork)
                TextButton(onClick = onSupport, modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "catalog-recovery" }) {
                    Text(stringResource(R.string.wallpaper_support), color = HubOnArtwork)
                }
            }
        }
        return
    }
    val pager = rememberPagerState(initialPage = sets.indexOfFirst { it.setId.value == setId }) { sets.size }
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSetSelected)
    val seen by rememberUpdatedState(onSeen)
    LaunchedEffect(pager) {
        // A catalog selection takes precedence over a restored pager position.
        pager.scrollToPage(sets.indexOfFirst { it.setId.value == setId })
        seen()
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { select(sets[it]) }
    }
    val context = LocalContext.current
    val current = sets[pager.currentPage]
    val currentAsset = remember(current) { PreviewAssetResolver.resolve(context, current, HubSurface.WALLPAPER) }
    val name = remember(current) { PreviewAssetResolver.displayName(context, current) }
    val nextPage = (pager.currentPage + 1) % sets.size
    val next = sets[nextPage]
    val nextName = remember(next) { PreviewAssetResolver.displayName(context, next) }
    val nextAsset = remember(next) { PreviewAssetResolver.resolve(context, next, HubSurface.WALLPAPER) }
    val nextDescription = stringResource(R.string.wallpaper_next, nextName)
    val previousDescription = stringResource(R.string.wallpaper_previous)
    val ready = phoneState?.target?.wallpaperId == current.wallpaper.componentId.value && setId == current.setId.value
    val enabled = currentAsset != null && ready && phoneState?.path?.route != null && !phoneState.busy && !phoneState.refreshing
    val hasProblem = ready && phoneState != null && (phoneState.failure != null ||
        (phoneState.path.route == null && !phoneState.refreshing && !phoneState.busy))
    val explanation = if (phoneState != null) phonePathExplanation(phoneState) else stringResource(R.string.phone_checking)
    Box(Modifier.fillMaxSize().background(HubArtworkBackdrop).semantics {
        testTag = "hub-screen-theme"
        stateDescription = name
        customActions = listOf(
            CustomAccessibilityAction(nextDescription) { scope.launch { pager.scrollToPage(nextPage) }; true },
            CustomAccessibilityAction(previousDescription) { scope.launch { pager.scrollToPage((pager.currentPage + sets.size - 1) % sets.size) }; true },
        )
    }) {
        HorizontalPager(state = pager, key = { sets[it].setId.value }, modifier = Modifier.fillMaxSize().semantics { testTag = "wallpaper-pager" }) { index ->
            val set = sets[index]
            val asset = remember(set) { PreviewAssetResolver.resolve(context, set, HubSurface.WALLPAPER) }
            Box(Modifier.fillMaxSize().then(if (index == pager.currentPage) Modifier else Modifier.clearAndSetSemantics {})) {
                if (asset != null) Image(painterResource(asset.drawableId), null, Modifier.fillMaxSize().semantics {
                    testTag = "theme-preview-art-${asset.symbolicName}"
                }, contentScale = ContentScale.Crop)
            }
        }
        // The same neutral gradient protects system icons and translucent controls for every scene.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to HubArtworkBackdrop.copy(alpha = .4f), .12f to Color.Transparent,
            .62f to Color.Transparent, 1f to HubArtworkBackdrop.copy(alpha = .85f),
        )))
        if (onCatalog != null) Button(onClick = onCatalog,
            modifier = Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 48.dp)
                .semantics { testTag = "wallpaper-catalog-open" },
            colors = ButtonDefaults.buttonColors(containerColor = HubArtworkBackdrop.copy(alpha = .7f), contentColor = HubOnArtwork),
            shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.wallpaper_catalog_title))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + 8.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (hasProblem) TextButton(onClick = onSupport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { testTag = "wallpaper-support" }) {
                Text(stringResource(R.string.wallpaper_support), color = HubOnArtwork, textAlign = TextAlign.Center)
            }
            if (currentAsset == null) Text(stringResource(R.string.theme_preview_unavailable), color = HubOnArtwork)
            Button(onClick = onInstall, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    testTag = "theme-primary-action"; stateDescription = explanation
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HubArtworkBackdrop.copy(alpha = .56f), contentColor = HubOnArtwork,
                    disabledContainerColor = HubArtworkBackdrop.copy(alpha = .7f), disabledContentColor = HubOnArtworkSecondary,
                ), border = BorderStroke(1.dp, HubOnArtwork.copy(alpha = .48f)), shape = RoundedCornerShape(20.dp)) {
                Text(stringResource(R.string.wallpaper_install), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            if (sets.size > 1) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(HubArtworkBackdrop.copy(alpha = .58f)).clickable(role = Role.Button) {
                    scope.launch { pager.scrollToPage(nextPage) }
                }.semantics { testTag = "wallpaper-next"; contentDescription = nextDescription }
                .heightIn(min = 56.dp).padding(6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (nextAsset != null) Image(painterResource(nextAsset.drawableId), null,
                    Modifier.size(56.dp, 48.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Text(stringResource(R.string.wallpaper_next_short, nextName), Modifier.weight(1f),
                    color = HubOnArtwork, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
