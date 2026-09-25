package app.livosphere.sets.neon_express.wallpaper

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.StaticPhaseScene
import app.livosphere.wallpapers.staticwallpaper.StaticPhaseWallpaperService

class NeonExpressWallpaperService : StaticPhaseWallpaperService("neon-express-wallpaper") {
    override fun scene() = StaticPhaseScene(
        mapOf(
            DayPhase.MORNING to R.drawable.ls_neon_express_wallpaper_morning,
            DayPhase.DAY to R.drawable.ls_neon_express_wallpaper_day,
            DayPhase.EVENING to R.drawable.ls_neon_express_wallpaper_evening,
            DayPhase.NIGHT to R.drawable.ls_neon_express_wallpaper_night,
        ),
    )
}
