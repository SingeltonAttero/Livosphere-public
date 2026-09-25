package app.livosphere.sets.orbital_window.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class OrbitalWindowWallpaperService : StaticPhaseWallpaperService("orbital-window-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_orbital_window_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_orbital_window_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_orbital_window_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_orbital_window_wallpaper_night,
        ),
    )
}
