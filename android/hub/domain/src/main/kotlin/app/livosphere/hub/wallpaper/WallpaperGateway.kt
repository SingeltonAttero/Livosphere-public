package app.livosphere.hub.wallpaper

import app.livosphere.hub.onboarding.Outcome
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** Android-free identity: both package and fully qualified service class must match. */
data class WallpaperComponent(val packageName: String, val className: String)
enum class WallpaperProbeId { FEATURE, SUPPORTED, ALLOWED, SERVICE, DIRECT_PREVIEW, CHOOSER, HOME, LOCK }
enum class WallpaperFailureCode { ACCESS_DENIED, UNAVAILABLE, INVALID_REQUEST, PLATFORM_INCIDENT }
data class WallpaperFailure(val probe: WallpaperProbeId, val code: WallpaperFailureCode)
enum class UnknownReason { NOT_OBSERVED, LEGACY_API, NO_COMPONENT_INFO, COMPONENT_UNAVAILABLE, PROBE_FAILED }
sealed interface WallpaperFact<out T> {
    data class Known<T>(val value: T) : WallpaperFact<T>
    data class Unknown(val reason: UnknownReason, val failure: WallpaperFailure? = null) : WallpaperFact<Nothing>
}
enum class WallpaperPresence { AVAILABLE, MISSING, DISABLED }
enum class WallpaperApplication { ACTIVE, INACTIVE }
enum class WallpaperSurface { HOME, LOCK }

data class PhoneWallpaperSnapshot(
    val observedAt: Instant,
    val component: WallpaperComponent,
    val minimumApi: Int,
    val deviceApi: Int,
    val feature: WallpaperFact<Boolean>,
    val supported: WallpaperFact<Boolean>,
    val allowed: WallpaperFact<Boolean>,
    val presence: WallpaperFact<WallpaperPresence>,
    val directPreview: WallpaperFact<Boolean>,
    val chooser: WallpaperFact<Boolean>,
    val home: WallpaperFact<WallpaperApplication>,
    val lock: WallpaperFact<WallpaperApplication>,
) {
    val compatible: Boolean get() = deviceApi >= minimumApi
}

interface PhoneWallpaperGateway {
    /** Null until the first completed observation; never restored from app preferences. */
    val snapshots: StateFlow<PhoneWallpaperSnapshot?>
    suspend fun refresh(): PhoneWallpaperSnapshot
}

/** Calls are deliberately separate: one failed probe cannot erase another fact. */
interface WallpaperPlatformProbe {
    val deviceApi: Int
    fun hasLiveWallpaperFeature(): Boolean
    fun isWallpaperSupported(): Boolean
    fun isSetWallpaperAllowed(): Boolean
    fun servicePresence(): WallpaperPresence
    fun resolvesDirectPreview(): Boolean
    fun resolvesChooser(): Boolean
    fun appliedComponent(surface: WallpaperSurface): WallpaperComponent?
}

object WallpaperObservation {
    fun capture(probe: WallpaperPlatformProbe, component: WallpaperComponent, minimumApi: Int, at: Instant): PhoneWallpaperSnapshot {
        val feature = query(WallpaperProbeId.FEATURE, probe::hasLiveWallpaperFeature).fact()
        val supported = query(WallpaperProbeId.SUPPORTED, probe::isWallpaperSupported).fact()
        val allowed = query(WallpaperProbeId.ALLOWED, probe::isSetWallpaperAllowed).fact()
        val presence = query(WallpaperProbeId.SERVICE, probe::servicePresence).fact()
        val direct = query(WallpaperProbeId.DIRECT_PREVIEW, probe::resolvesDirectPreview).fact()
        val chooser = query(WallpaperProbeId.CHOOSER, probe::resolvesChooser).fact()
        fun application(surface: WallpaperSurface): WallpaperFact<WallpaperApplication> {
            if (probe.deviceApi < 34) return WallpaperFact.Unknown(UnknownReason.LEGACY_API)
            val id = if (surface == WallpaperSurface.HOME) WallpaperProbeId.HOME else WallpaperProbeId.LOCK
            // Even unavailable presence does not suppress the independent HOME/LOCK probe.
            return when (val result = query(id) { probe.appliedComponent(surface) }) {
                is Outcome.Failure -> WallpaperFact.Unknown(UnknownReason.PROBE_FAILED, result.reason)
                is Outcome.Success -> when {
                    result.value == null -> WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO)
                    result.value != component -> WallpaperFact.Known(WallpaperApplication.INACTIVE)
                    (presence is WallpaperFact.Known && presence.value != WallpaperPresence.AVAILABLE) || probe.deviceApi < minimumApi ->
                        WallpaperFact.Unknown(UnknownReason.COMPONENT_UNAVAILABLE)
                    else -> WallpaperFact.Known(WallpaperApplication.ACTIVE)
                }
            }
        }
        return PhoneWallpaperSnapshot(at, component, minimumApi, probe.deviceApi, feature, supported, allowed,
            presence, direct, chooser, application(WallpaperSurface.HOME), application(WallpaperSurface.LOCK))
    }

    private fun <T> query(id: WallpaperProbeId, block: () -> T): Outcome<T, WallpaperFailure> = try {
        Outcome.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        Outcome.Failure(WallpaperFailure(id, WallpaperFailureCode.ACCESS_DENIED))
    } catch (_: UnsupportedOperationException) {
        Outcome.Failure(WallpaperFailure(id, WallpaperFailureCode.UNAVAILABLE))
    } catch (_: IllegalArgumentException) {
        Outcome.Failure(WallpaperFailure(id, WallpaperFailureCode.INVALID_REQUEST))
    } catch (_: Exception) {
        Outcome.Failure(WallpaperFailure(id, WallpaperFailureCode.PLATFORM_INCIDENT))
    }

    private fun <T> Outcome<T, WallpaperFailure>.fact(): WallpaperFact<T> = when (this) {
        is Outcome.Success -> WallpaperFact.Known(value)
        is Outcome.Failure -> WallpaperFact.Unknown(UnknownReason.PROBE_FAILED, reason)
    }
}
