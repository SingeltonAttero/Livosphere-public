package app.livosphere.widgets.runtime

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Debug probe contract only. It proves native RemoteViews expressibility, not production art. */
enum class ClockProbeKind(
    internal val layoutResourceName: String,
) {
    DIGITAL("ls_contour_probe_digital"),
    ANALOG("ls_contour_probe_analog"),
}

object NativeClockProbe {
    fun remoteViews(context: Context, kind: ClockProbeKind): RemoteViews {
        val layoutId = context.resources.getIdentifier(
            kind.layoutResourceName,
            "layout",
            context.packageName,
        )
        require(layoutId != 0) { "Missing debug clock probe layout: ${kind.layoutResourceName}" }
        return RemoteViews(context.packageName, layoutId)
    }

    internal fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        kind: ClockProbeKind,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, remoteViews(context, kind))
        }
    }
}

class DigitalClockProbeProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        NativeClockProbe.update(context, manager, appWidgetIds, ClockProbeKind.DIGITAL)
    }
}

class AnalogClockProbeProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        NativeClockProbe.update(context, manager, appWidgetIds, ClockProbeKind.ANALOG)
    }
}
