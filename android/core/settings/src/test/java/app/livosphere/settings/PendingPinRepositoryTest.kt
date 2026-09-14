package app.livosphere.settings

import androidx.datastore.core.DataStoreFactory
import app.livosphere.contract.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingPinRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private fun repository(file: File): SurfaceSettingsRepository = SurfaceSettingsRepository(DataStoreFactory.create(
        serializer = SurfaceSettingsSerializer,
        scope = CoroutineScope(SupervisorJob().also(jobs::add) + Dispatchers.IO),
        migrations = listOf(LegacyWallpaperMigration { null }, SurfaceSettingsV2Migration()),
    ) { file })
    private suspend fun close() { jobs.forEach { it.cancelAndJoin() }; jobs.clear() }
    private fun <T> SettingsOutcome<T>.value() = (this as SettingsOutcome.Success).value
    private val token = "pin_token_1234567890"
    private val provider = "app.livosphere.widgets.runtime.SmallClockWidgetProvider"
    private fun pin(createdAt: Long = 1_000) = PendingWidgetPin(token, "isolation-fixture-clock-widget", WidgetSize.S, null, provider, createdAt)

    @Test fun consumeIsAtomicIdempotentAndCannotOverwriteNewerRevision() = runBlocking {
        val file = File(temporary.root, "pin.json")
        val repository = repository(file)
        val pins = repository.pendingPins { it == "isolation-fixture-clock-widget" }
        pins.create(pin()).value()
        assertTrue(pins.consume(token, "app.livosphere.widgets.runtime.Foreign", 101, 2_000) is SettingsOutcome.Failure)
        assertTrue(pins.consume(token, provider, 101, 2_000).value() is PendingPinConsumeResult.Consumed)
        repository.widgets { true }.configure(101, "isolation-fixture-clock-widget", WidgetSize.M, null).value()
        assertEquals(PendingPinConsumeResult.Replay(101), pins.consume(token, provider, 101, 3_000).value())
        assertEquals(WidgetSize.M, repository.widgets { true }.observe(101).first().value().size)
        assertEquals(2L, repository.widgets { true }.observe(101).first().value().configurationRevision)
        close()

        val reopened = repository(file)
        assertEquals(PendingPinStatus.CONSUMED, reopened.pendingPins { true }.observe(token).first().value()!!.status)
        assertEquals(WidgetSize.M, reopened.widgets { true }.observe(101).first().value().size)
        close()
    }

    @Test fun expiryKeepsSevenDayTombstoneWithoutInferringFailure() = runBlocking {
        val repository = repository(File(temporary.root, "expiry.json"))
        val pins = repository.pendingPins { true }
        pins.create(pin(createdAt = 0)).value()
        pins.cleanup(PIN_VALID_MILLIS + 1).value()
        val expired = pins.observe(token).first().value()!!
        assertEquals(PendingPinStatus.EXPIRED, expired.status)
        assertNull(expired.boundAppWidgetId)
        assertTrue(pins.consume(token, provider, 101, PIN_VALID_MILLIS + 2) is SettingsOutcome.Failure)
        pins.cleanup(PIN_VALID_MILLIS + 1 + PIN_TOMBSTONE_MILLIS + 1).value()
        assertNull(pins.observe(token).first().value())
        close()
    }
}
