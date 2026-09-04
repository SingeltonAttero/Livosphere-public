package app.livosphere.hub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.settings.DataStoreHubSettingsRepository
import app.livosphere.hub.settings.HubSettingsSerializer
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OnboardingAutomaticPersistenceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun freshViewModelsUseDurableFirstAndWeekRepeatButNeverThirdInvitation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "onboarding-automatic-${System.nanoTime()}.json")
        val firstTime = Instant.parse("2026-09-04T12:00:00Z")
        var current by mutableStateOf<HubViewModel?>(null)
        var scope: CoroutineScope? = null
        var models = ViewModelStore()
        composeRule.setContent { current?.let { HubApp(it, onExit = {}) } }

        suspend fun openSession(now: Instant): HubViewModel {
            composeRule.runOnIdle { current = null; models.clear() }
            composeRule.waitForIdle()
            scope?.coroutineContext?.get(Job)?.cancelAndJoin()
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
            val repo = DataStoreHubSettingsRepository(DataStoreFactory.create(
                serializer = HubSettingsSerializer, scope = newScope, produceFile = { file }))
            val knowledge = object : ApplicationKnowledgeProvider {
                override val knowledge = MutableStateFlow<ApplicationKnowledge>(
                    ApplicationKnowledge.NotApplied(now.minusSeconds(1), now.plusSeconds(3600)))
            }
            lateinit var vm: HubViewModel
            composeRule.runOnIdle {
                models = ViewModelStore()
                vm = HubViewModel(repo, knowledge, Clock.fixed(now, ZoneOffset.UTC))
                models.put("hub", vm)
                current = vm
            }
            composeRule.waitUntil { vm.state.value.settings is Outcome.Success && vm.state.value.foreground }
            return vm
        }

        try {
            var vm = openSession(firstTime)
            composeRule.waitUntil {
                vm.state.value.onboardingVisible &&
                    (vm.state.value.settings as? Outcome.Success)?.value?.invitationCount == 1
            }
            assertEquals(1, (vm.state.value.settings as Outcome.Success).value.invitationCount)
            composeRule.onNodeWithTag("onboarding-skip").performScrollTo().performClick()
            vm = openSession(firstTime.plusSeconds(604799))
            composeRule.waitForIdle()
            assertFalse(vm.state.value.onboardingVisible)
            vm = openSession(firstTime.plusSeconds(604800))
            composeRule.waitUntil {
                vm.state.value.onboardingVisible &&
                    (vm.state.value.settings as? Outcome.Success)?.value?.invitationCount == 2
            }
            assertEquals(2, (vm.state.value.settings as Outcome.Success).value.invitationCount)
            composeRule.onNodeWithTag("onboarding-go").performScrollTo().performClick()
            vm = openSession(firstTime.plusSeconds(60480000))
            composeRule.waitForIdle()
            assertFalse(vm.state.value.onboardingVisible)
            assertTrue((vm.state.value.settings as Outcome.Success).value.repeatUsed)
        } finally {
            composeRule.runOnIdle { current = null; models.clear() }
            scope?.coroutineContext?.get(Job)?.cancelAndJoin()
            file.delete()
        }
    }
}
