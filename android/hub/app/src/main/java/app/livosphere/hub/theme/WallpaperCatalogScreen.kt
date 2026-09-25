package app.livosphere.hub.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.SetDescriptor
import app.livosphere.hub.HubSurface

@Composable
internal fun WallpaperCatalogScreen(listState: LazyListState, selectedWallpaperId: String?, onSelect: (SetDescriptor) -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = listState, modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()
            .semantics { testTag = "wallpaper-catalog" },
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.wallpaper_catalog_title), style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.wallpaper_catalog_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(AuthoredContentCatalog.wallpaperSets, key = { it.setId.value }) { set ->
                val preview = remember(set) { PreviewAssetResolver.resolve(context, set, HubSurface.WALLPAPER) }
                val name = remember(set) { PreviewAssetResolver.displayName(context, set) }
                val selected = selectedWallpaperId == set.wallpaper.componentId.value
                Surface(onClick = { onSelect(set) }, shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "wallpaper-catalog-${set.setId.value}" }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (preview != null) Image(painterResource(preview.drawableId), null,
                            Modifier.size(96.dp, 144.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(if (selected) R.string.wallpaper_catalog_selected else R.string.wallpaper_catalog_view),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
