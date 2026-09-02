package app.livosphere.wallpapers.engine

class WallpaperEngineLifecycle {
    var isVisible: Boolean = false
        private set

    var isDestroyed: Boolean = false
        private set

    fun onVisibilityChanged(visible: Boolean) {
        if (!isDestroyed) {
            isVisible = visible
        }
    }

    fun onDestroy() {
        isVisible = false
        isDestroyed = true
    }
}
