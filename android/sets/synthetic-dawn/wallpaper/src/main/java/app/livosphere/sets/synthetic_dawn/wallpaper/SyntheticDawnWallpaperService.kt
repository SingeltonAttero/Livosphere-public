package app.livosphere.sets.synthetic_dawn.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class SyntheticDawnWallpaperService : StaticPhaseWallpaperService("synthetic-dawn-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_synthetic_dawn_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_synthetic_dawn_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_synthetic_dawn_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_synthetic_dawn_wallpaper_night,
        ),
    )
}
