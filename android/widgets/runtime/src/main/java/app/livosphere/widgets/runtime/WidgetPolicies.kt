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

enum class ConfigurationCommitResult { UPDATED, ROLLED_BACK, ROLLBACK_FAILED, REJECTED }

enum class ConfigurationFailureState { PREVIOUS_PRESERVED, STATE_UNKNOWN, WRITE_REJECTED }

data class ClockLayoutMetrics(val timeSp: Float, val dateSp: Float, val datePattern: String)
object ClockGeometryPolicy {
    fun metrics(size: WidgetSize, widthDp: Int, fontScale: Float): ClockLayoutMetrics {
        val scale = fontScale.coerceAtLeast(1f)
        val compact = widthDp in 1..270 || scale >= 1.5f
        val timeBase = when {
            size == WidgetSize.S && compact -> 32f
            size == WidgetSize.L -> 44f
            else -> 34f
        }
        val dateBase = when (size) { WidgetSize.L -> 16f; else -> 14f }
        return ClockLayoutMetrics(
            timeSp = (timeBase / scale).coerceAtLeast(12f),
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
    fun failureState(result: ConfigurationCommitResult): ConfigurationFailureState? = when (result) {
        ConfigurationCommitResult.UPDATED -> null
        ConfigurationCommitResult.ROLLED_BACK -> ConfigurationFailureState.PREVIOUS_PRESERVED
        ConfigurationCommitResult.ROLLBACK_FAILED -> ConfigurationFailureState.STATE_UNKNOWN
        ConfigurationCommitResult.REJECTED -> ConfigurationFailureState.WRITE_REJECTED
    }
}

enum class PinPublicationResult {
    COMMITTED_UPDATED,
    COMMITTED_RETRY_REQUIRED,
    REPLAY_UPDATED,
    REPLAY_RETRY_REQUIRED,
    REJECTED,
}

fun WidgetPinFlow.publicationResult(
    consumeResult: app.livosphere.contract.PendingPinConsumeResult,
    updated: Boolean,
): PinPublicationResult = when (consumeResult) {
    is app.livosphere.contract.PendingPinConsumeResult.Consumed ->
        if (updated) PinPublicationResult.COMMITTED_UPDATED else PinPublicationResult.COMMITTED_RETRY_REQUIRED
    is app.livosphere.contract.PendingPinConsumeResult.Replay ->
        if (updated) PinPublicationResult.REPLAY_UPDATED else PinPublicationResult.REPLAY_RETRY_REQUIRED
}

object ClockTargetPolicy {
    const val SHOW_ALARMS = "android.intent.action.SHOW_ALARMS"
    fun structurallyAllowed(target: ClockTarget): Boolean =
        target.action == SHOW_ALARMS && PACKAGE.matches(target.packageName) &&
            CLASS.matches(target.className)

    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val CLASS = Regex("[A-Za-z][A-Za-z0-9_$]*(?:\\.[A-Za-z][A-Za-z0-9_$]*)+")
}

/** Stable clock hierarchy inside host geometry; text never grows beyond its allocated face. */
object WidgetPresentationPolicy {
    fun metrics(size: WidgetSize, widthDp: Int, fontScale: Float, analog: Boolean): ClockLayoutMetrics {
        val width = widthDp.coerceAtLeast(80)
        val scale = fontScale.coerceAtLeast(1f)
        val base = when (size) { WidgetSize.S -> 29f; WidgetSize.M -> 51f; WidgetSize.L -> 65f }
        val time = minOf(base, (width - 24) / 3.2f) / scale
        val compact = width < 240 || analog
        return ClockLayoutMetrics(time, (if (size == WidgetSize.L && !analog) 14f else 12f) / scale,
            if (analog) "d MMM" else if (compact) "EEE, d MMM" else "EEE, d MMMM")
    }
}
