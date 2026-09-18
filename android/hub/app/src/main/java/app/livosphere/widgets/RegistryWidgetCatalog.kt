package app.livosphere.widgets

import android.content.Context
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.contract.ClockStyle
import app.livosphere.contract.WidgetSize
import app.livosphere.hub.theme.PreviewAssetResolver
import app.livosphere.widgets.runtime.WidgetCatalog
import app.livosphere.widgets.runtime.WidgetCatalogItem

class RegistryWidgetCatalog(private val context: Context? = null) : WidgetCatalog {
    private val definitions get() = AuthoredContentCatalog.sets
    override fun items() = definitions.map { set ->
        WidgetCatalogItem(checkNotNull(set.clockWidget).componentId.value, set.setId.value,
            context?.let { PreviewAssetResolver.displayName(it, set) } ?: set.setId.value)
    }
    override fun contains(widgetId: String) = definitions.any { it.clockWidget?.componentId?.value == widgetId }
    override fun isAnalog(widgetId: String) = definitions.singleOrNull { it.clockWidget?.componentId?.value == widgetId }
        ?.clockWidget?.style == ClockStyle.ANALOG
    override fun layoutResource(context: Context, widgetId: String, size: WidgetSize): Int {
        val clock = definitions.singleOrNull { it.clockWidget?.componentId?.value == widgetId }?.clockWidget ?: return 0
        val ref = clock.resources.singleOrNull { it.symbolicName == clock.layouts[size] } ?: return 0
        return context.resources.getIdentifier(ref.resourcePath.substringAfter('/').removeSuffix(".xml"), "layout", context.packageName)
    }
    override fun rootViewId(context: Context) = context.resources.getIdentifier("clock_widget_root", "id", context.packageName)
    override fun needsConfigurationLayout(context: Context) = context.resources.getIdentifier("clock_widget_needs_configuration", "layout", context.packageName)
    override fun timeViewId(context: Context) = context.resources.getIdentifier("clock_widget_time", "id", context.packageName)
    override fun dateViewId(context: Context) = context.resources.getIdentifier("clock_widget_date", "id", context.packageName)
}
