package app.livosphere.sets.emerald_cove.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class EmeraldCoveWallpaperService : StaticPhaseWallpaperService("emerald-cove-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_emerald_cove_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_emerald_cove_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_emerald_cove_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_emerald_cove_wallpaper_night,
        ),
    )
}
