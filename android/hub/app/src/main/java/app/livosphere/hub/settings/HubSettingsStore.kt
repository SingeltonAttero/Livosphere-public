package app.livosphere.hub.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import app.livosphere.hub.onboarding.HubSettingsRepository
import app.livosphere.hub.onboarding.InvitationClaim
import app.livosphere.hub.onboarding.InvitationHistory
import app.livosphere.hub.onboarding.InvitationPolicy
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.onboarding.SettingsFailure
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class StoredHubSettings(
    val lastInvitedAtEpochMillis: Long?,
    val invitationCount: Int,
    val repeatUsed: Boolean,
    val hubMotionMode: HubMotionMode = HubMotionMode.NORMAL,
    val dismissedReleaseVersion: String? = null,
) {
    fun toHistory() = InvitationHistory(lastInvitedAtEpochMillis?.let(Instant::ofEpochMilli), invitationCount, repeatUsed)

    companion object {
        fun from(history: InvitationHistory) = StoredHubSettings(
            history.lastInvitedAt?.toEpochMilli(), history.invitationCount, history.repeatUsed,
        )
    }
}

/** Hub-only visual preference; it never changes the wallpaper service or a watch face. */
@Serializable
enum class HubMotionMode { NORMAL, REDUCED }

data class HubRuntimeSettings(
    val hubMotionMode: HubMotionMode,
    val dismissedReleaseVersion: String?,
)

object HubSettingsSerializer : Serializer<StoredHubSettings> {
    override val defaultValue = StoredHubSettings.from(InvitationHistory())

    override suspend fun readFrom(input: InputStream): StoredHubSettings = try {
        Json.decodeFromString<StoredHubSettings>(input.readBytes().decodeToString()).also { it.toHistory() }
    } catch (error: SerializationException) {
        throw CorruptionException("Invalid invitation history", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Inconsistent invitation history", error)
    }

    override suspend fun writeTo(t: StoredHubSettings, output: OutputStream) {
        t.toHistory()
        output.write(Json.encodeToString(t).encodeToByteArray())
    }
}

class DataStoreHubSettingsRepository @Inject constructor(
    private val store: DataStore<StoredHubSettings>,
) : HubSettingsRepository {
    private val readGeneration = MutableStateFlow(0L)

    override fun retryHistory() { readGeneration.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val history: Flow<Outcome<InvitationHistory, SettingsFailure>> = readGeneration.flatMapLatest { store.data
        .map<StoredHubSettings, Outcome<InvitationHistory, SettingsFailure>> { Outcome.Success(it.toHistory()) }
        .catch { error ->
            if (error is CancellationException) throw error
            if (error !is Exception) throw error
            emit(Outcome.Failure(error.asFailure(writing = false)))
            // The outer flow stays alive. A foreground retry reopens it; no background polling.
        }
    }

    override suspend fun claimInvitation(now: Instant): Outcome<InvitationClaim, SettingsFailure> = try {
        var granted = false
        val updated = store.updateData { stored ->
            val history = stored.toHistory()
            if (InvitationPolicy.eligible(history, now)) {
                granted = true
                // Invitation bookkeeping shares this file with runtime-only settings.
                // Updating the former must never reset the latter.
                val claimed = InvitationPolicy.claimed(history, now)
                stored.copy(
                    lastInvitedAtEpochMillis = claimed.lastInvitedAt?.toEpochMilli(),
                    invitationCount = claimed.invitationCount,
                    repeatUsed = claimed.repeatUsed,
                )
            } else stored
        }
        Outcome.Success(if (granted) InvitationClaim.Granted(updated.toHistory()) else InvitationClaim.Suppressed)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Outcome.Failure(error.asFailure(writing = true))
    }
}

/** Typed app-owned settings kept separately from facts reported by Android. */
class DataStoreHubRuntimeSettings @Inject constructor(
    private val store: DataStore<StoredHubSettings>,
) {
    private val readGeneration = MutableStateFlow(0L)

    /**
     * Reopens the finite read flow after a transient DataStore failure. This is deliberately
     * caller-driven (on foreground), rather than a hidden polling loop.
     */
    fun retrySettings() { readGeneration.update { it + 1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: Flow<HubRuntimeSettings?> = readGeneration.flatMapLatest { store.data
        .map<StoredHubSettings, HubRuntimeSettings?> { HubRuntimeSettings(it.hubMotionMode, it.dismissedReleaseVersion) }
        .catch { error ->
            if (error is CancellationException) throw error
            if (error is Exception) emit(null) else throw error
            // The outer flow remains alive: foreground explicitly retries after an unavailable read.
        }
    }

    suspend fun setHubMotionMode(mode: HubMotionMode) {
        store.updateData { it.copy(hubMotionMode = mode) }
    }

    suspend fun dismissReleaseNote(versionName: String) {
        store.updateData { it.copy(dismissedReleaseVersion = versionName) }
    }
}

private fun Throwable.asFailure(writing: Boolean): SettingsFailure = when (this) {
    is CorruptionException, is IllegalArgumentException -> SettingsFailure.Corrupt
    is IOException -> if (writing) SettingsFailure.Write else SettingsFailure.Read
    else -> SettingsFailure.Unavailable
}
