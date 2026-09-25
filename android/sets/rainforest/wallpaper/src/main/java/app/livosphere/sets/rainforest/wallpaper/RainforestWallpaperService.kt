package app.livosphere.sets.rainforest.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class RainforestWallpaperService : StaticPhaseWallpaperService("rainforest-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_rainforest_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_rainforest_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_rainforest_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_rainforest_wallpaper_night,
        ),
    )
}
