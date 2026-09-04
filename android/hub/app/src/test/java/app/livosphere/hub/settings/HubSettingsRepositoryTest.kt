package app.livosphere.hub.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import app.livosphere.hub.onboarding.*
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HubSettingsRepositoryTest {
    @get:Rule val directory = TemporaryFolder()
    private val now = Instant.parse("2026-09-04T12:00:00Z")

    @Test fun atomicClaimsAndRestartRetainLifetimeLimit() = runBlocking {
        val file = directory.root.resolve("settings.json")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun repository() = DataStoreHubSettingsRepository(DataStoreFactory.create(
            serializer = HubSettingsSerializer, scope = scope, produceFile = { file }))
        try {
            var repo = repository()
            val claims = List(20) { async(Dispatchers.Default) { repo.claimInvitation(now) } }.awaitAll()
            assertEquals(1, claims.count { (it as? Outcome.Success)?.value is InvitationClaim.Granted })
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = repository()
            assertEquals(1, (repo.history.first() as Outcome.Success).value.invitationCount)
            assertEquals(Outcome.Success(InvitationClaim.Suppressed), repo.claimInvitation(now.plusSeconds(604799)))
            assertTrue((repo.claimInvitation(now.plusSeconds(604800)) as Outcome.Success).value is InvitationClaim.Granted)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repo = repository()
            val restored = (repo.history.first() as Outcome.Success).value
            assertEquals(2, restored.invitationCount)
            assertTrue(restored.repeatUsed)
            assertEquals(now.plusSeconds(604800), restored.lastInvitedAt)
            assertEquals(Outcome.Success(InvitationClaim.Suppressed), repo.claimInvitation(now.plusSeconds(99999999)))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun corruptHistoryIsTypedAndNeverOverwritten() = runBlocking {
        val file = directory.newFile("bad.json")
        val corrupt = "{\"invitationCount\":0}"
        file.writeText(corrupt)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = DataStoreHubSettingsRepository(DataStoreFactory.create(
                serializer = HubSettingsSerializer, scope = scope, produceFile = { file }))
            assertEquals(Outcome.Failure(SettingsFailure.Corrupt), repo.history.first())
            assertEquals(Outcome.Failure(SettingsFailure.Corrupt), repo.claimInvitation(now))
            assertEquals(corrupt, file.readText())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    @Test fun readFlowRecoversOnExplicitRetryWithoutPolling() = runTest {
        var failing = true
        var reads = 0
        val store = object : DataStore<StoredHubSettings> {
            override val data = flow {
                reads++
                if (failing) throw IOException("read")
                emit(HubSettingsSerializer.defaultValue)
            }
            override suspend fun updateData(transform: suspend (StoredHubSettings) -> StoredHubSettings) =
                transform(HubSettingsSerializer.defaultValue)
        }
        val repo = DataStoreHubSettingsRepository(store)
        val values = mutableListOf<Outcome<InvitationHistory, SettingsFailure>>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repo.history.collect { values += it } }
        testScheduler.runCurrent()
        assertEquals(listOf(Outcome.Failure(SettingsFailure.Read)), values)
        testScheduler.advanceTimeBy(100_000)
        assertEquals(1, reads)
        failing = false
        repo.retryHistory()
        testScheduler.runCurrent()
        assertEquals(Outcome.Success(InvitationHistory()), values.last())
        collector.cancel()
    }

    @Test fun writeFailureAndCancellationRemainDistinct() = runBlocking {
        var cancellation = false
        val repo = DataStoreHubSettingsRepository(object : DataStore<StoredHubSettings> {
            override val data = flowOf(HubSettingsSerializer.defaultValue)
            override suspend fun updateData(transform: suspend (StoredHubSettings) -> StoredHubSettings): StoredHubSettings {
                if (cancellation) throw CancellationException("cancel")
                throw IOException("write")
            }
        })
        assertEquals(Outcome.Failure(SettingsFailure.Write), repo.claimInvitation(now))
        cancellation = true
        try { repo.claimInvitation(now); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }

    @Test fun serializerRejectsInconsistentHistoryAndEmptyExistingFile() = runBlocking {
        listOf("", "{}", "{\"lastInvitedAtEpochMillis\":0,\"invitationCount\":2,\"repeatUsed\":false}").forEach {
            try {
                HubSettingsSerializer.readFrom(ByteArrayInputStream(it.toByteArray()))
                fail("Corrupt input must be rejected")
            } catch (_: androidx.datastore.core.CorruptionException) { }
        }
    }
}
