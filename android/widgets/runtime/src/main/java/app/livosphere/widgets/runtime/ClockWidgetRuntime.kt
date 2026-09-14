package app.livosphere.widgets.runtime

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.RemoteViews
import app.livosphere.contract.*
import app.livosphere.settings.ApplicationSurfaceSettings
import app.livosphere.settings.PIN_VALID_MILLIS
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

interface WidgetCatalog {
    fun contains(widgetId: String): Boolean
    fun layoutResource(context: Context, widgetId: String, size: WidgetSize): Int
    fun rootViewId(context: Context): Int
    fun needsConfigurationLayout(context: Context): Int
}

object ClockWidgetRuntime {
    const val DEFAULT_DEBUG_WIDGET_ID = "isolation-fixture-clock-widget"
    const val EXTRA_PIN_TOKEN = "app.livosphere.extra.PIN_TOKEN"
    const val EXTRA_PRE_PIN = "app.livosphere.extra.PRE_PIN"
    const val EXTRA_WIDGET_ID = "app.livosphere.extra.WIDGET_ID"
    const val CONFIGURATION_ACTIVITY = "app.livosphere.widgets.ClockWidgetConfigurationActivity"
    const val ROUTER_ACTIVITY = "app.livosphere.widgets.ClockLaunchRouterActivity"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coordinator = WidgetRenderCoordinator()
    @Volatile private var catalog: WidgetCatalog? = null

    fun install(catalog: WidgetCatalog) { this.catalog = catalog }
    fun repository(context: Context) = ApplicationSurfaceSettings.get(context)
    fun catalog(): WidgetCatalog = checkNotNull(catalog) { "Clock widget catalog is not installed" }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }

    suspend fun update(context: Context, appWidgetId: Int): Boolean {
        val catalog = catalog()
        val port = repository(context).widgets(catalog::contains)
        val outcome = port.observe(appWidgetId).first()
        val manager = AppWidgetManager.getInstance(context)
        if (outcome !is SettingsOutcome.Success) {
            manager.updateAppWidget(appWidgetId, needsConfigurationViews(context, appWidgetId, catalog))
            return false
        }
        val preferences = outcome.value
        val ticket = coordinator.ticket(appWidgetId, preferences)
        val layout = catalog.layoutResource(context, preferences.widgetId, preferences.size)
        if (layout == 0) {
            manager.updateAppWidget(appWidgetId, needsConfigurationViews(context, appWidgetId, catalog))
            return false
        }
        val latest = (port.observe(appWidgetId).first() as? SettingsOutcome.Success)?.value
        if (!coordinator.isCurrent(ticket, latest)) return false
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(catalog.rootViewId(context), routerPendingIntent(context, appWidgetId))
        manager.updateAppWidget(appWidgetId, views)
        return true
    }

    fun providerFor(size: WidgetSize): Class<out AppWidgetProvider> = when (size) {
        WidgetSize.S -> SmallClockWidgetProvider::class.java
        WidgetSize.M -> MediumClockWidgetProvider::class.java
        WidgetSize.L -> LargeClockWidgetProvider::class.java
    }

    fun sizeForProvider(className: String?): WidgetSize? = when (className) {
        SmallClockWidgetProvider::class.java.name -> WidgetSize.S
        MediumClockWidgetProvider::class.java.name -> WidgetSize.M
        LargeClockWidgetProvider::class.java.name -> WidgetSize.L
        else -> null
    }

    fun ownProvider(context: Context, appWidgetId: Int): ComponentName? =
        AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
            ?.takeIf { it.packageName == context.packageName && sizeForProvider(it.className) != null }

    fun routerPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent().setComponent(ComponentName(context, ROUTER_ACTIVITY))
            .setData(Uri.parse("livosphere://clock-widget/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun configurationIntent(context: Context, appWidgetId: Int): Intent =
        Intent().setComponent(ComponentName(context, CONFIGURATION_ACTIVITY))
            .setData(Uri.parse("livosphere://clock-widget/configure/$appWidgetId"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    fun prePinIntent(context: Context, widgetId: String): Intent =
        Intent().setComponent(ComponentName(context, CONFIGURATION_ACTIVITY))
            .putExtra(EXTRA_PRE_PIN, true).putExtra(EXTRA_WIDGET_ID, widgetId)

    private fun needsConfigurationViews(context: Context, appWidgetId: Int, catalog: WidgetCatalog): RemoteViews =
        RemoteViews(context.packageName, catalog.needsConfigurationLayout(context)).also {
            it.setOnClickPendingIntent(catalog.rootViewId(context), PendingIntent.getActivity(
                context, appWidgetId, configurationIntent(context, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
        }

    internal fun deleted(appWidgetId: Int) { coordinator.delete(appWidgetId) }
    internal fun restored(oldId: Int, newId: Int, generation: Long) { coordinator.restore(oldId, newId, generation) }
}

abstract class BaseClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        ClockWidgetRuntime.launch { try { appWidgetIds.forEach { ClockWidgetRuntime.update(context, it) } } finally { pending.finish() } }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        ClockWidgetRuntime.launch { try { ClockWidgetRuntime.update(context, appWidgetId) } finally { pending.finish() } }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        ClockWidgetRuntime.launch {
            try {
                val port = ClockWidgetRuntime.repository(context).widgets(ClockWidgetRuntime.catalog()::contains)
                appWidgetIds.forEach { id -> port.delete(id); ClockWidgetRuntime.deleted(id) }
            } finally { pending.finish() }
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val pending = goAsync()
        ClockWidgetRuntime.launch {
            try {
                if (oldWidgetIds.size != newWidgetIds.size || oldWidgetIds.isEmpty()) return@launch
                val mapping = oldWidgetIds.indices.associate { oldWidgetIds[it] to newWidgetIds[it] }
                val port = ClockWidgetRuntime.repository(context).widgets(ClockWidgetRuntime.catalog()::contains)
                when (val result = port.remap(mapping)) {
                    is SettingsOutcome.Success -> result.value.forEach { (newId, preferences) ->
                        val oldId = mapping.entries.single { it.value == newId }.key
                        ClockWidgetRuntime.restored(oldId, newId, preferences.generation)
                        if (ClockWidgetRuntime.update(context, newId) && Build.VERSION.SDK_INT >= 30) {
                            AppWidgetManager.getInstance(context).updateAppWidgetOptions(
                                newId, Bundle().apply { putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true) },
                            )
                        }
                    }
                    is SettingsOutcome.Failure -> newWidgetIds.forEach { ClockWidgetRuntime.update(context, it) }
                }
            } finally { pending.finish() }
        }
    }
}

class SmallClockWidgetProvider : BaseClockWidgetProvider()
class MediumClockWidgetProvider : BaseClockWidgetProvider()
class LargeClockWidgetProvider : BaseClockWidgetProvider()

enum class PinRequestResult { REQUESTED, UNSUPPORTED, FAILED }
object WidgetPinLauncher {
    suspend fun request(
        context: Context,
        widgetId: String,
        size: WidgetSize,
        target: ClockTarget?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PinRequestResult {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return PinRequestResult.UNSUPPORTED
        val provider = ComponentName(context, ClockWidgetRuntime.providerFor(size))
        val token = UUID.randomUUID().toString().replace("-", "_")
        val pin = PendingWidgetPin(token, widgetId, size, target, provider.className, nowEpochMillis)
        val pins = ClockWidgetRuntime.repository(context).pendingPins(ClockWidgetRuntime.catalog()::contains)
        if (pins.create(pin) !is SettingsOutcome.Success) return PinRequestResult.FAILED
        val callbackIntent = Intent(context, WidgetPinCallbackReceiver::class.java)
            .setData(Uri.parse("livosphere://widget-pin/$token"))
            .putExtra(ClockWidgetRuntime.EXTRA_PIN_TOKEN, token)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, token.hashCode(), callbackIntent, flags)
        return if (manager.requestPinAppWidget(provider, null, callback)) PinRequestResult.REQUESTED else PinRequestResult.FAILED
    }
}

class WidgetPinCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ClockWidgetRuntime.launch {
            try {
                val token = intent.getStringExtra(ClockWidgetRuntime.EXTRA_PIN_TOKEN) ?: return@launch
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val provider = ClockWidgetRuntime.ownProvider(context, appWidgetId) ?: return@launch
                val pins = ClockWidgetRuntime.repository(context).pendingPins(ClockWidgetRuntime.catalog()::contains)
                val pin = (pins.observe(token).first() as? SettingsOutcome.Success)?.value ?: return@launch
                val decision = WidgetPinFlow.callback(
                    pin.providerClassName, provider.className, appWidgetId,
                    System.currentTimeMillis() - pin.createdAtEpochMillis > PIN_VALID_MILLIS,
                    pin.boundAppWidgetId,
                )
                if (decision == PinCallbackDecision.CONSUME || decision == PinCallbackDecision.REPLAY) {
                    val result = pins.consume(token, provider.className, appWidgetId, System.currentTimeMillis())
                    if (result is SettingsOutcome.Success) ClockWidgetRuntime.update(context, appWidgetId)
                }
            } finally { pending.finish() }
        }
    }
}

class ClockTargetResolver(private val context: Context) {
    fun targets(): List<ClockTarget> = query(Intent(AlarmClock.ACTION_SHOW_ALARMS)).mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        ClockTarget(activity.packageName, activity.name, AlarmClock.ACTION_SHOW_ALARMS).takeIf(ClockTargetPolicy::structurallyAllowed)
    }.distinct()

    fun resolves(target: ClockTarget): Boolean {
        if (!ClockTargetPolicy.structurallyAllowed(target)) return false
        val intent = Intent(target.action).setComponent(ComponentName(target.packageName, target.className))
        return query(intent).any { it.activityInfo?.let { activity -> activity.packageName == target.packageName && activity.name == target.className } == true }
    }

    @Suppress("DEPRECATION")
    private fun query(intent: Intent) = if (Build.VERSION.SDK_INT >= 33)
        context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    else context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
}
