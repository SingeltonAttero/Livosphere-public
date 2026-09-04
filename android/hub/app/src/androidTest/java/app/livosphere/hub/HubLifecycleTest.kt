package app.livosphere.hub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.settings.UnknownApplicationKnowledgeProvider
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HubLifecycleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun actualCompositionLifecycleForwardsStopAndForegroundRetry() {
        val retries = AtomicInteger()
        lateinit var owner: TestOwner
        lateinit var vm: HubViewModel
        composeRule.setContent {
            owner = remember { TestOwner().also { it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) } }
            val store = remember { ViewModelStore() }
            vm = remember {
                HubViewModel(object : HubSettingsRepository {
                    override val history = flowOf(Outcome.Success(InvitationHistory()))
                    override fun retryHistory() { retries.incrementAndGet() }
                    override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
                }, UnknownApplicationKnowledgeProvider(), Clock.systemUTC()).also { store.put("hub", it) }
            }
            DisposableEffect(store) { onDispose { store.clear() } }
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { HubApp(vm, onExit = {}) }
        }
        composeRule.runOnIdle { assertFalse(vm.state.value.foreground); owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        composeRule.waitUntil { retries.get() == 1 && vm.state.value.foreground }
        composeRule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        composeRule.waitUntil { !vm.state.value.foreground }
        composeRule.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        composeRule.waitUntil { retries.get() == 2 && vm.state.value.foreground }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
