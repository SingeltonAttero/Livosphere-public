package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.wallpapers.contour.ContourWallpaperService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Single explicit mapping from logical registry component to the packaged Android service. */
object AndroidWallpaperTarget {
    val descriptor get() = GeneratedSetRegistry.sets.single { it.wallpaper.componentId.value == "contour-wallpaper" }
    fun component(context: Context): ComponentName = ComponentName(context, ContourWallpaperService::class.java)
    fun directPreviewIntent(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
    fun chooserIntent(): Intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
}

@Singleton
class AndroidWallpaperGateway internal constructor(
    private val clock: Clock,
    private val observe: (Instant) -> PhoneWallpaperSnapshot,
) : PhoneWallpaperGateway {
    @Inject constructor(@ApplicationContext context: Context, clock: Clock) : this(
        clock,
        platformObservation(context.applicationContext),
    )

    private val mutableSnapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
    override val snapshots = mutableSnapshots.asStateFlow()
    private val mutex = Mutex()

    override suspend fun refresh(): PhoneWallpaperSnapshot = withContext(Dispatchers.IO) {
        mutex.withLock {
            val snapshot = observe(clock.instant())
            currentCoroutineContext().ensureActive()
            mutableSnapshots.value = snapshot
            snapshot
        }
    }
}

private fun platformObservation(context: Context): (Instant) -> PhoneWallpaperSnapshot = { at ->
    val target = AndroidWallpaperTarget.component(context)
    WallpaperObservation.capture(
        AndroidWallpaperProbe(context, target),
        WallpaperComponent(target.packageName, target.className),
        AndroidWallpaperTarget.descriptor.wallpaper.compatibility.minimumApi,
        at,
    )
}

internal class AndroidWallpaperProbe internal constructor(
    private val contextProvider: () -> Context,
    private val targetProvider: () -> ComponentName,
    override val deviceApi: Int,
    private val applicationQuery: (Int) -> WallpaperComponent?,
) : WallpaperPlatformProbe {
    constructor(context: Context, target: ComponentName) : this(
        { context }, { target }, Build.VERSION.SDK_INT,
        { flag ->
            if (Build.VERSION.SDK_INT >= 34) {
                WallpaperManager.getInstance(context).getWallpaperInfo(flag)?.component?.let {
                    WallpaperComponent(it.packageName, it.className)
                }
            } else null
        },
    )

    private val context get() = contextProvider()
    private val packages get() = context.packageManager
    private val manager get() = WallpaperManager.getInstance(context)
    override fun hasLiveWallpaperFeature() = packages.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)
    override fun isWallpaperSupported() = manager.isWallpaperSupported
    override fun isSetWallpaperAllowed() = manager.isSetWallpaperAllowed

    @Suppress("DEPRECATION")
    override fun servicePresence(): WallpaperPresence {
        val target = targetProvider()
        val info = try {
            packages.getServiceInfo(target, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            return WallpaperPresence.MISSING
        }
        fun enabled(override: Int, manifest: Boolean) = when (override) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifest
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
        val appEnabled = enabled(packages.getApplicationEnabledSetting(target.packageName), info.applicationInfo.enabled)
        val serviceEnabled = enabled(packages.getComponentEnabledSetting(target), info.enabled)
        return if (appEnabled && serviceEnabled) WallpaperPresence.AVAILABLE else WallpaperPresence.DISABLED
    }

    override fun resolvesDirectPreview() = resolves(AndroidWallpaperTarget.directPreviewIntent(context))
    override fun resolvesChooser() = resolves(AndroidWallpaperTarget.chooserIntent())
    private fun resolves(intent: Intent): Boolean = intent.resolveActivity(packages) != null

    override fun appliedComponent(surface: WallpaperSurface): WallpaperComponent? =
        readApplicationComponent(surface, deviceApi, applicationQuery)
}

/** Small platform-call seam verifies the actual FLAG_SYSTEM/FLAG_LOCK routing on the JVM. */
internal fun readApplicationComponent(
    surface: WallpaperSurface,
    deviceApi: Int,
    query: (Int) -> WallpaperComponent?,
): WallpaperComponent? {
    if (deviceApi < 34) return null
    return query(when (surface) {
        WallpaperSurface.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperSurface.LOCK -> WallpaperManager.FLAG_LOCK
    })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WallpaperGatewayModule {
    @Binds @Singleton
    abstract fun gateway(implementation: AndroidWallpaperGateway): PhoneWallpaperGateway
}
