package app.livosphere.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class WallpaperSettingsIsolationTest {
    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val writer = Mutex()
        var writes = 0
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = writer.withLock {
            transform(data.value).also { data.value = it; writes++ }
        }
    }

    @Test fun legacyAKeysSurviveBReadWriteAndRepositoryRecreation() = runBlocking {
        val legacy = mutablePreferencesOf(booleanPreferencesKey("touch_reactions_enabled") to false,
            stringPreferencesKey("wallpaper_motion_mode") to "OFF")
        val store = Store(legacy)
        val a = WallpaperSettingsRepository(store, LEGACY_CONTOUR_WALLPAPER_ID)
        val b = a.forWallpaper("isolation-fixture-wallpaper")
        assertFalse(a.touchReactionsEnabled.first()!!)
        assertEquals(WallpaperMotionMode.OFF, a.motionMode.first())
        assertTrue(b.touchReactionsEnabled.first()!!)
        assertEquals(WallpaperMotionMode.NORMAL, b.motionMode.first())
        assertEquals(0, store.writes)
        b.setTouchReactionsEnabled(false)
        b.setMotionMode(WallpaperMotionMode.REDUCED)
        assertEquals(WallpaperMotionMode.OFF, a.motionMode.first())
        assertEquals("OFF", store.data.value[stringPreferencesKey("wallpaper_motion_mode")])
        assertEquals("REDUCED", store.data.value[stringPreferencesKey("wallpaper.isolation-fixture-wallpaper.wallpaper_motion_mode")])
        assertEquals(WallpaperMotionMode.REDUCED,
            WallpaperSettingsRepository(store, "isolation-fixture-wallpaper").motionMode.first())
    }

    @Test fun concurrentOwnersNeverOverwriteEachOtherAndCorruptBDoesNotBorrowA() = runBlocking {
        val store = Store()
        val a = WallpaperSettingsRepository(store, LEGACY_CONTOUR_WALLPAPER_ID)
        val b = a.forWallpaper("isolation-fixture-wallpaper")
        listOf(async { a.setMotionMode(WallpaperMotionMode.OFF) },
            async { b.setMotionMode(WallpaperMotionMode.REDUCED) },
            async { a.setTouchReactionsEnabled(false) }, async { b.setTouchReactionsEnabled(true) }).awaitAll()
        assertEquals(WallpaperMotionMode.OFF, a.motionMode.first())
        assertEquals(WallpaperMotionMode.REDUCED, b.motionMode.first())
        assertFalse(a.touchReactionsEnabled.first()!!)
        assertTrue(b.touchReactionsEnabled.first()!!)
        val corrupt = Store(mutablePreferencesOf(stringPreferencesKey("wallpaper_motion_mode") to "NORMAL",
            stringPreferencesKey("wallpaper.isolation-fixture-wallpaper.wallpaper_motion_mode") to "CORRUPT"))
        assertNull(WallpaperSettingsRepository(corrupt, "isolation-fixture-wallpaper").motionMode.first())
        assertEquals(0, corrupt.writes)
    }
}
