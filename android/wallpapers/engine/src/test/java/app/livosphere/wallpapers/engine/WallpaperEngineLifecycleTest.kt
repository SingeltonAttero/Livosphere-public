package app.livosphere.wallpapers.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperEngineLifecycleTest {
    @Test
    fun `destroyed engine stays stopped`() {
        val lifecycle = WallpaperEngineLifecycle()

        lifecycle.onVisibilityChanged(true)
        assertTrue(lifecycle.isVisible)

        lifecycle.onDestroy()
        lifecycle.onVisibilityChanged(true)

        assertTrue(lifecycle.isDestroyed)
        assertFalse(lifecycle.isVisible)
    }

    @Test
    fun `each engine owns independent lifecycle state`() {
        val previewEngine = WallpaperEngineLifecycle()
        val activeEngine = WallpaperEngineLifecycle()

        previewEngine.onVisibilityChanged(true)

        assertTrue(previewEngine.isVisible)
        assertFalse(activeEngine.isVisible)
    }

    @Test
    fun `temporary surface loss does not destroy engine lifecycle`() {
        val lifecycle = WallpaperEngineLifecycle()

        lifecycle.onVisibilityChanged(true)
        lifecycle.onVisibilityChanged(false)
        lifecycle.onVisibilityChanged(true)

        assertFalse(lifecycle.isDestroyed)
        assertTrue(lifecycle.isVisible)
    }
}
