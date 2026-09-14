package app.livosphere.widgets

import android.content.Context
import app.livosphere.contract.WidgetSize
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.widgets.runtime.WidgetCatalog
import app.livosphere.widgets.runtime.WidgetCatalogItem

class RegistryWidgetCatalog : WidgetCatalog {
    private val definitions = listOf(
        Definition("contour-debug-clock-widget", "contour-draft", "Контур — технические часы", "clock_widget_contour"),
        Definition("isolation-fixture-clock-widget", "isolation-fixture", "Изоляция — технические часы", "clock_widget_fixture"),
    )

    init {
        require(definitions.all { definition -> GeneratedSetRegistry.sets.any { it.setId.value == definition.setId } })
        require(GeneratedSetRegistry.sets.single { it.setId.value == "isolation-fixture" }
            .clockWidget?.componentId?.value == "isolation-fixture-clock-widget")
    }

    override fun items() = definitions.map { WidgetCatalogItem(it.widgetId, it.setId, it.displayName) }
    override fun contains(widgetId: String) = definitions.any { it.widgetId == widgetId }

    override fun layoutResource(context: Context, widgetId: String, size: WidgetSize): Int {
        val definition = definitions.singleOrNull { it.widgetId == widgetId } ?: return 0
        val fileName = "${definition.resourcePrefix}_${size.name.lowercase()}"
        return context.resources.getIdentifier(fileName, "layout", context.packageName)
    }

    override fun rootViewId(context: Context): Int =
        context.resources.getIdentifier("clock_widget_root", "id", context.packageName)

    override fun needsConfigurationLayout(context: Context): Int =
        context.resources.getIdentifier("clock_widget_needs_configuration", "layout", context.packageName)

    override fun timeViewId(context: Context): Int = context.resources.getIdentifier("clock_widget_time", "id", context.packageName)
    override fun dateViewId(context: Context): Int = context.resources.getIdentifier("clock_widget_date", "id", context.packageName)

    private data class Definition(val widgetId: String, val setId: String, val displayName: String, val resourcePrefix: String)
}
