package app.livosphere.sets.chromatic_flow.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class ChromaticFlowWallpaperService : StaticPhaseWallpaperService("chromatic-flow-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_chromatic_flow_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_chromatic_flow_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_chromatic_flow_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_chromatic_flow_wallpaper_night,
        ),
    )
}
