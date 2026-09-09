package app.livosphere.settings

import androidx.datastore.core.DataStore
import app.livosphere.contract.SettingsOutcome
import app.livosphere.contract.SurfaceSettingsFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WallpaperSettingsRepositoryFailureTest {
    private fun repository(error: Exception) = SurfaceSettingsRepository(object : DataStore<StoredSurfaceSettings> {
        override val data = flow<StoredSurfaceSettings> { throw error }
        override suspend fun updateData(transform: suspend (StoredSurfaceSettings) -> StoredSurfaceSettings): StoredSurfaceSettings = throw error
    })

    @Test fun ioReadFailureIsUnavailableRatherThanTheEnabledDefault() = runBlocking {
        val repository = repository(IOException("unavailable"))
        assertEquals(SettingsOutcome.Failure(SurfaceSettingsFailure.Read), repository.wallpapers { true }.observe(LEGACY_CONTOUR_WALLPAPER_ID).first())
        val facade = WallpaperSettingsRepository(repository, LEGACY_CONTOUR_WALLPAPER_ID)
        assertNull(facade.touchReactionsEnabled.first()); assertNull(facade.motionMode.first())
    }
    @Test(expected = IOException::class) fun writeFailureReachesExistingViewModel() = runBlocking {
        WallpaperSettingsRepository(repository(IOException("unavailable")), LEGACY_CONTOUR_WALLPAPER_ID).setTouchReactionsEnabled(true)
    }
    @Test fun cancellationRemainsCancellationOnBothBoundaries() = runBlocking {
        val port = repository(CancellationException("cancelled")).wallpapers { true }
        try { port.observe(LEGACY_CONTOUR_WALLPAPER_ID).first(); fail("read swallowed cancellation") }
        catch (_: CancellationException) { }
        try { port.setMotion(LEGACY_CONTOUR_WALLPAPER_ID, WallpaperMotionMode.OFF); fail("write swallowed cancellation") }
        catch (_: CancellationException) { }
    }
}
