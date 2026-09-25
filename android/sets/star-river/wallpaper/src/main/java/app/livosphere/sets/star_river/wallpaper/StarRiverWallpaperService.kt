package app.livosphere.sets.star_river.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class StarRiverWallpaperService : StaticPhaseWallpaperService("star-river-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_star_river_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_star_river_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_star_river_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_star_river_wallpaper_night,
        ),
    )
}
