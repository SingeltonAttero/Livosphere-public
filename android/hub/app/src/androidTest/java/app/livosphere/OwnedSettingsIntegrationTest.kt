package app.livosphere

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.hub.*
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import app.livosphere.settings.*
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.Collections
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

/** Real serialized DataStore, isolated from the installed user's settings. */
internal class OwnedSettingsFixture(initialFileText: String? = null) {
    private val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "owned-settings-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    val file = File(directory, "settings.json").also { if (initialFileText != null) it.writeText(initialFileText) }
    val store = DataStoreFactory.create(serializer = SurfaceSettingsSerializer, scope = scope,
        migrations = listOf(LegacyWallpaperMigration { null })) { file }
    val settings = SurfaceSettingsRepository(store)
    fun repository(id: String) = WallpaperSettingsRepository(settings, id)
    suspend fun close() { job.cancelAndJoin(); directory.deleteRecursively() }
}

class OwnedSettingsIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun target(id: String) = checkNotNull(AndroidWallpaperTarget.resolve(context, id))
    private fun history() = object : HubSettingsRepository {
        override val history = flowOf(Outcome.Success(InvitationHistory()))
        override fun retryHistory() = Unit
        override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
    }
    private fun gateway() = object : PhoneWallpaperGateway {
        override val initialBrowsingTarget = target("contour-wallpaper")
        override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
        override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No system probe needed")
    }

    @Test fun vmWritesBothBSettingsWithoutChangingOrNotifyingA() = runBlocking {
        val fixture = OwnedSettingsFixture(); val models = ViewModelStore()
        val a = fixture.repository("contour-wallpaper")
        val b = a.forWallpaper("isolation-fixture-wallpaper")
        val events = Collections.synchronizedList(mutableListOf<Pair<Boolean?, WallpaperMotionMode?>>())
        var observer: Job? = null
        try {
            a.setTouchReactionsEnabled(true); a.setMotionMode(WallpaperMotionMode.OFF)
            b.setTouchReactionsEnabled(true); b.setMotionMode(WallpaperMotionMode.NORMAL)
            observer = launch(Dispatchers.Default) { combine(a.touchReactionsEnabled, a.motionMode, ::Pair).collect { events += it } }
            withTimeout(5_000) { while (events.isEmpty()) yield() }
            lateinit var vm: HubViewModel
            instrumentation.runOnMainSync { vm = HubViewModel(history(), gateway(), Clock.systemUTC(), a).also { models.put("hub", it) } }
            vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(target(b.wallpaperId))))
            withTimeout(5_000) { vm.state.first { it.phone.target?.wallpaperId == b.wallpaperId } }
            instrumentation.runOnMainSync { vm.setWallpaperMotionMode(WallpaperMotionMode.REDUCED); vm.setTouchReactionsEnabled(false) }
            withTimeout(5_000) { b.motionMode.first { it == WallpaperMotionMode.REDUCED }; b.touchReactionsEnabled.first { it == false } }
            // A consumer's actor is caught up after both B writes have committed.
            delay(100)
            assertEquals(listOf(true to WallpaperMotionMode.OFF), events.toList())
            assertEquals(true, a.touchReactionsEnabled.first()); assertEquals(WallpaperMotionMode.OFF, a.motionMode.first())
        } finally {
            observer?.cancelAndJoin(); instrumentation.runOnMainSync { models.clear() }; fixture.close()
        }
    }

    @Test fun foregroundRecoversBothSettingsAfterWriteAndReadFailuresForSameOwner(): Unit = runBlocking {
        val fixture = OwnedSettingsFixture(); val models = ViewModelStore()
        var readsFail = false; var writesFail = false
        val readFailureSeen = CompletableDeferred<Unit>()
        val unreliable = object : DataStore<StoredSurfaceSettings> {
            override val data = flow { if (readsFail) { readFailureSeen.complete(Unit); throw IOException("controlled read") }; emitAll(fixture.store.data) }
            override suspend fun updateData(transform: suspend (StoredSurfaceSettings) -> StoredSurfaceSettings): StoredSurfaceSettings {
                if (writesFail) throw IOException("controlled write")
                return fixture.store.updateData(transform)
            }
        }
        val settings = WallpaperSettingsRepository(SurfaceSettingsRepository(unreliable), "contour-wallpaper")
        try {
            settings.setTouchReactionsEnabled(false); settings.setMotionMode(WallpaperMotionMode.REDUCED)
            lateinit var vm: HubViewModel
            instrumentation.runOnMainSync { vm = HubViewModel(history(), gateway(), Clock.systemUTC(), settings).also { models.put("hub", it) } }
            withTimeout(5_000) { vm.touchReactions.first { it == false }; vm.wallpaperMotion.first { it == WallpaperMotionMode.REDUCED } }
            writesFail = true
            instrumentation.runOnMainSync { vm.setTouchReactionsEnabled(true); vm.setWallpaperMotionMode(WallpaperMotionMode.OFF) }
            withTimeout(5_000) { vm.touchReactions.first { it == null }; vm.wallpaperMotion.first { it == null } }
            writesFail = false; readsFail = true
            vm.onAction(HubAction.ForegroundStarted(HubSection.SETTINGS))
            withTimeout(5_000) { readFailureSeen.await() }
            instrumentation.waitForIdleSync()
            assertNull(vm.touchReactions.value); assertNull(vm.wallpaperMotion.value)
            readsFail = false
            vm.onAction(HubAction.ForegroundStarted(HubSection.SETTINGS))
            withTimeout(5_000) { vm.touchReactions.first { it == false }; vm.wallpaperMotion.first { it == WallpaperMotionMode.REDUCED } }
        } finally { instrumentation.runOnMainSync { models.clear() }; fixture.close() }
    }
}
