package app.livosphere.sets.moon_tide.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class MoonTideWallpaperService : StaticPhaseWallpaperService("moon-tide-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_moon_tide_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_moon_tide_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_moon_tide_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_moon_tide_wallpaper_night,
        ),
    )
}
