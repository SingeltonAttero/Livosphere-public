package app.livosphere.hub.devices

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import app.livosphere.widgets.RegistryWidgetCatalog
import app.livosphere.widgets.runtime.ClockWidgetRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal data class WidgetInstance(
    val id: Int,
    val name: String?,
    val size: WidgetSize,
    val needsConfiguration: Boolean,
)

internal data class WidgetInstances(
    val items: List<WidgetInstance> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** A provider binding is known; placement or visibility on HOME/LOCK is not. */
internal class WidgetInstancesReader(
    private val ids: () -> List<Pair<Int, WidgetSize>>,
    private val preferences: suspend (Int) -> SettingsOutcome<WidgetPreferences>,
    private val name: (String) -> String?,
) {
    suspend fun read(): WidgetInstances = try {
        WidgetInstances(items = ids().distinctBy { it.first }.sortedBy { it.first }.map { (id, size) ->
            val saved = (preferences(id) as? SettingsOutcome.Success)?.value
            WidgetInstance(id, saved?.let { name(it.widgetId) }, size, saved == null)
        })
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { WidgetInstances(failed = true) }

    companion object {
        fun android(context: Context): WidgetInstancesReader {
            val app = context.applicationContext
            val catalog = RegistryWidgetCatalog(app)
            val manager = AppWidgetManager.getInstance(app)
            return WidgetInstancesReader(
                ids = { WidgetSize.entries.flatMap { size ->
                    manager.getAppWidgetIds(ComponentName(app, ClockWidgetRuntime.providerFor(size)))
                        .map { it to size }
                } },
                preferences = { ClockWidgetRuntime.repository(app).widgets(catalog::contains).observe(it).first() },
                name = { id -> catalog.items().singleOrNull { it.widgetId == id }?.displayName },
            )
        }
    }
}
