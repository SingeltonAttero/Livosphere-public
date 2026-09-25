package app.livosphere.sets.golden_dunes.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class GoldenDunesWallpaperService : StaticPhaseWallpaperService("golden-dunes-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_golden_dunes_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_golden_dunes_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_golden_dunes_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_golden_dunes_wallpaper_night,
        ),
    )
}
