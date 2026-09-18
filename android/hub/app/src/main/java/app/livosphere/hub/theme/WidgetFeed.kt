package app.livosphere.hub.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.livosphere.R
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.hub.HubStage
import app.livosphere.hub.HubSurface
import app.livosphere.widgets.RegistryWidgetCatalog

@Composable
internal fun WidgetFeed(listState: LazyListState, onInstall: (String) -> Unit) {
    val context = LocalContext.current
    val catalog = remember(context) { RegistryWidgetCatalog(context) }
    val sets = remember { AuthoredContentCatalog.sets }
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().semantics { testTag = "hub-screen-widgets" },
            state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.hub_section_widgets), color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.widget_feed_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(sets, key = { it.setId.value }) { set ->
                val widget = remember(set) { catalog.itemForSet(set.setId.value) }
                val preview = remember(set) { PreviewAssetResolver.resolve(context, set, HubSurface.WATCH_FACE) }
                if (widget != null) Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().semantics { testTag = "widget-${widget.widgetId}" }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (preview != null) Image(painterResource(preview.drawableId), widget.displayName,
                            Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(HubStage).padding(16.dp),
                            contentScale = ContentScale.Fit)
                        Text(widget.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.widget_feed_sizes), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onInstall(widget.widgetId) }, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { testTag = "widget-install-${widget.widgetId}" }) {
                            Text(stringResource(R.string.widget_install))
                        }
                    }
                }
            }
        }
    }
}
