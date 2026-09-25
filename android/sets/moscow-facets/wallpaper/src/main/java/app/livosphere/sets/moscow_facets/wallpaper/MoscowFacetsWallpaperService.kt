package app.livosphere.sets.moscow_facets.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class MoscowFacetsWallpaperService : StaticPhaseWallpaperService("moscow-facets-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_moscow_facets_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_moscow_facets_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_moscow_facets_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_moscow_facets_wallpaper_night,
        ),
    )
}
