package app.livosphere.hub.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import app.livosphere.hub.HubViewModel
import app.livosphere.hub.onboarding.*
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun rememberPhoneTestHubViewModel(
    refresh: suspend () -> PhoneWallpaperSnapshot = { phoneUiSnapshot() },
    beforeAcknowledgement: (suspend (WallpaperLaunchRequest) -> Unit)? = null,
): HubViewModel {
    val store = remember { ViewModelStore() }
    val vm = remember {
        val observe = refresh
        val repository = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh() = observe().also { snapshots.value = it }
        }
        (if (beforeAcknowledgement == null) HubViewModel(repository, gateway, Clock.systemUTC())
        else HubViewModel(repository, gateway, Clock.systemUTC(), beforeAcknowledgement)).also { store.put("hub", it) }
    }
    DisposableEffect(store) { onDispose { store.clear() } }
    return vm
}

internal class PhoneTestLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry
}
