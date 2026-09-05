package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import org.junit.Assert.*
import org.junit.Test

class AndroidWallpaperProbeTest {
    @Test fun `API34 reads HOME and LOCK with separate exact Android flags`() {
        val calls = mutableListOf<Int>()
        val home = WallpaperComponent("app.livosphere", "HomeService")
        val lock = WallpaperComponent("app.livosphere", "LockService")
        val query: (Int) -> WallpaperComponent? = { flag ->
            calls += flag
            when (flag) {
                WallpaperManager.FLAG_SYSTEM -> home
                WallpaperManager.FLAG_LOCK -> lock
                else -> error("Combined or unexpected flag")
            }
        }
        val probe = AndroidWallpaperProbe({ error("Unrelated context access") },
            { error("Unrelated target access") }, 34, query)
        assertEquals(home, probe.appliedComponent(WallpaperSurface.HOME))
        assertEquals(lock, probe.appliedComponent(WallpaperSurface.LOCK))
        assertEquals(listOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK), calls)
    }

    @Test fun `legacy never invokes flagged query or substitutes unflagged API`() {
        for (api in 29..33) for (surface in WallpaperSurface.entries) {
            val probe = AndroidWallpaperProbe({ error("Unrelated context access") },
                { error("Unrelated target access") }, api) { error("Not supported on $api") }
            assertNull(probe.appliedComponent(surface))
        }
    }

    @Test fun `null is preserved for each independent platform query`() {
        for (surface in WallpaperSurface.entries) assertNull(readApplicationComponent(surface, 34) { null })
    }
}
