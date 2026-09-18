package app.livosphere.hub.theme

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.RegistryWidgetCatalog
import app.livosphere.widgets.runtime.ClockLayoutAdapter

/** Uses the same RemoteViews and size adaptation as an installed widget. */
@Composable
internal fun NativeWidgetPreview(widgetId: String, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val catalog = remember(context) { RegistryWidgetCatalog(context) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = if (size == WidgetSize.S) minOf(maxWidth, 144.dp) else maxWidth
        val height = width * when (size) { WidgetSize.S -> 1f; WidgetSize.M -> 110f / 250; WidgetSize.L -> 180f / 250 }
        val views = remember(widgetId, size, width, height, density.fontScale) {
            RemoteViews(context.packageName, catalog.layoutResource(context, widgetId, size)).also {
                ClockLayoutAdapter.adapt(context, it, size, Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width.value.toInt())
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height.value.toInt())
                }, catalog, widgetId)
            }
        }
        AndroidView(modifier = Modifier.width(width).height(height), factory = { FrameLayout(it) }, update = { parent ->
            if (parent.tag !== views) {
                parent.removeAllViews()
                parent.addView(views.apply(context, parent), FrameLayout.LayoutParams(-1, -1))
                parent.tag = views
            }
        })
    }
}
