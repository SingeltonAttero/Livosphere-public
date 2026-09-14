package app.livosphere.widgets.runtime

import app.livosphere.contract.ClockTarget
import app.livosphere.contract.WidgetPreferences
import app.livosphere.contract.WidgetSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RenderTicket(val appWidgetId: Int, val revision: Long, val generation: Long)

/** Per-ID freshness guard used immediately before every RemoteViews publication. */
class WidgetRenderCoordinator {
    private val operationGate = Mutex()
    private val generations = mutableMapOf<Int, Long>()

    suspend fun <T> serialize(block: suspend () -> T): T = operationGate.withLock { block() }

    @Synchronized
    fun ticket(appWidgetId: Int, preferences: WidgetPreferences): RenderTicket {
        require(appWidgetId > 0)
        val generation = maxOf(generations[appWidgetId] ?: 0, preferences.generation)
        generations[appWidgetId] = generation
        return RenderTicket(appWidgetId, preferences.configurationRevision, generation)
    }

    @Synchronized
    fun isCurrent(ticket: RenderTicket, current: WidgetPreferences?): Boolean =
        current != null && generations[ticket.appWidgetId] == ticket.generation &&
            current.configurationRevision == ticket.revision && current.generation == ticket.generation

    @Synchronized
    fun delete(appWidgetId: Int) { generations[appWidgetId] = (generations[appWidgetId] ?: 0) + 1 }

    @Synchronized
    fun restore(oldId: Int, newId: Int, generation: Long) {
        delete(oldId)
        generations[newId] = maxOf(generations[newId] ?: 0, generation)
    }
}

enum class ConfigurationCommitResult { UPDATED, ROLLED_BACK, REJECTED }

data class ClockLayoutMetrics(val timeSp: Float, val dateSp: Float, val datePattern: String)
object ClockGeometryPolicy {
    fun metrics(size: WidgetSize, widthDp: Int, fontScale: Float): ClockLayoutMetrics {
        val scale = fontScale.coerceAtLeast(1f)
        val compact = widthDp in 1..270 || scale >= 1.5f
        val timeBase = when (size) { WidgetSize.L -> 44f; else -> 34f }
        val dateBase = when (size) { WidgetSize.L -> 16f; else -> 14f }
        return ClockLayoutMetrics(
            timeSp = (timeBase / scale).coerceAtLeast(18f),
            dateSp = (dateBase / scale).coerceAtLeast(8f),
            datePattern = if (compact) "EEE, d MMM" else "EEEE, d MMMM",
        )
    }
}

enum class PinCallbackDecision { CONSUME, REPLAY, REJECT_FOREIGN, REJECT_ID, REJECT_EXPIRED }
object WidgetPinFlow {
    fun callback(
        expectedProvider: String,
        actualProvider: String?,
        appWidgetId: Int,
        expired: Boolean,
        consumedId: Int?,
    ): PinCallbackDecision = when {
        appWidgetId <= 0 -> PinCallbackDecision.REJECT_ID
        actualProvider != expectedProvider -> PinCallbackDecision.REJECT_FOREIGN
        expired -> PinCallbackDecision.REJECT_EXPIRED
        consumedId == appWidgetId -> PinCallbackDecision.REPLAY
        consumedId != null -> PinCallbackDecision.REJECT_ID
        else -> PinCallbackDecision.CONSUME
    }
}

data class WidgetConfigurationState(
    val existing: WidgetPreferences?,
    val draft: WidgetPreferences?,
    val saved: Boolean = false,
    val initialUpdateSucceeded: Boolean = false,
)
object WidgetConfigurationFlow {
    fun cancel(state: WidgetConfigurationState) = state.copy(draft = null, saved = false, initialUpdateSucceeded = false)
    fun committed(state: WidgetConfigurationState, value: WidgetPreferences) = state.copy(draft = value, saved = true)
    fun updated(state: WidgetConfigurationState, succeeded: Boolean) = state.copy(initialUpdateSucceeded = succeeded)
    fun canReturnOk(state: WidgetConfigurationState) = state.saved && state.initialUpdateSucceeded
}

object ClockTargetPolicy {
    const val SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"
    fun structurallyAllowed(target: ClockTarget): Boolean =
        target.action == SHOW_ALARMS && PACKAGE.matches(target.packageName) &&
            CLASS.matches(target.className)

    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val CLASS = Regex("[A-Za-z][A-Za-z0-9_$]*(?:\\.[A-Za-z][A-Za-z0-9_$]*)+")
}
