package app.livosphere.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class WallpaperSettingsRepositoryFailureTest {
    private class UnavailableStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("storage unavailable") }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("storage unavailable")
    }

    @Test fun ioReadFailureIsUnavailableRatherThanTheEnabledDefault() = runBlocking {
        assertNull(WallpaperSettingsRepository(UnavailableStore(), LEGACY_CONTOUR_WALLPAPER_ID).touchReactionsEnabled.first())
        assertNull(WallpaperSettingsRepository(UnavailableStore(), LEGACY_CONTOUR_WALLPAPER_ID).motionMode.first())
    }

    @Test fun invalidPersistedMotionModeIsUnavailableRatherThanNormal() = runBlocking {
        val invalid = mutablePreferencesOf(motionModeKey to "TURBO")
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(invalid)
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(invalid)
        }
        assertNull(WallpaperSettingsRepository(store, LEGACY_CONTOUR_WALLPAPER_ID).motionMode.first())
    }

    @Test(expected = IOException::class)
    fun writeFailureIsExposedSoTheViewModelCanReturnTheUiToUnavailable() = runBlocking {
        WallpaperSettingsRepository(UnavailableStore(), LEGACY_CONTOUR_WALLPAPER_ID).setTouchReactionsEnabled(true)
    }
}
