package app.livosphere.widgets

import android.content.Context
import app.livosphere.contract.WidgetSize
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.widgets.runtime.WidgetCatalog

class RegistryWidgetCatalog : WidgetCatalog {
    override fun contains(widgetId: String): Boolean = GeneratedSetRegistry.sets.any {
        it.clockWidget?.componentId?.value == widgetId
    }

    override fun layoutResource(context: Context, widgetId: String, size: WidgetSize): Int {
        val contribution = GeneratedSetRegistry.sets.singleOrNull {
            it.clockWidget?.componentId?.value == widgetId
        }?.clockWidget ?: return 0
        if (contribution.layouts[size] == null) return 0
        // TEST_DECLARATION remains registry metadata. These separate debug native adaptations
        // do not masquerade as set-owned generated resources or change contribution status.
        val fileName = "clock_widget_fixture_${size.name.lowercase()}"
        return context.resources.getIdentifier(fileName, "layout", context.packageName)
    }

    override fun rootViewId(context: Context): Int =
        context.resources.getIdentifier("clock_widget_root", "id", context.packageName)

    override fun needsConfigurationLayout(context: Context): Int =
        context.resources.getIdentifier("clock_widget_needs_configuration", "layout", context.packageName)
}
