package app.livosphere.hub

import androidx.datastore.core.DataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.hub.onboarding.InvitationClaim
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.settings.DataStoreHubSettingsRepository
import app.livosphere.hub.settings.HubSettingsSerializer
import java.io.File
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class OnboardingPersistenceTest {
    @Test fun durableHistorySurvivesStoreRecreationOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "onboarding-test-${System.nanoTime()}.json")
        val now = Instant.parse("2026-09-04T12:00:00Z")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun open() = DataStoreHubSettingsRepository(DataStoreFactory.create(
            serializer = HubSettingsSerializer, scope = scope, produceFile = { file }))
        try {
            var repo = open()
            assertTrue((repo.claimInvitation(now) as Outcome.Success).value is InvitationClaim.Granted)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = open()
            assertEquals(now, (repo.history.first() as Outcome.Success).value.lastInvitedAt)
            assertEquals(Outcome.Success(InvitationClaim.Suppressed), repo.claimInvitation(now.plusSeconds(604799)))
            assertTrue((repo.claimInvitation(now.plusSeconds(604800)) as Outcome.Success).value is InvitationClaim.Granted)
            assertTrue((repo.history.first() as Outcome.Success).value.repeatUsed)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = open()
            assertEquals(Outcome.Success(InvitationClaim.Suppressed), repo.claimInvitation(now.plusSeconds(60480000)))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
