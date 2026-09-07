package app.livosphere.hub.wallpaper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import app.livosphere.hub.onboarding.Outcome
import java.util.concurrent.CancellationException

/** Activity-owned boundary. Neither an Activity nor a launcher is retained by the gateway. */
fun interface WallpaperLauncher {
    fun launch(request: WallpaperLaunchRequest): Outcome<Unit, WallpaperLaunchFailure>
}

internal class AndroidWallpaperLauncher(
    private val context: Context,
    private val start: (Intent) -> Unit,
) : WallpaperLauncher {
    override fun launch(request: WallpaperLaunchRequest): Outcome<Unit, WallpaperLaunchFailure> = try {
        start(when (request.route) {
            WallpaperRoute.DIRECT -> AndroidWallpaperTarget.directPreviewIntent(context)
            WallpaperRoute.CHOOSER -> AndroidWallpaperTarget.chooserIntent()
        })
        Outcome.Success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: ActivityNotFoundException) {
        Outcome.Failure(WallpaperLaunchFailure.NO_HANDLER)
    } catch (_: SecurityException) {
        Outcome.Failure(WallpaperLaunchFailure.ACCESS_DENIED)
    } catch (_: IllegalArgumentException) {
        Outcome.Failure(WallpaperLaunchFailure.INVALID_REQUEST)
    } catch (_: Exception) {
        Outcome.Failure(WallpaperLaunchFailure.PLATFORM_INCIDENT)
    }
}
