package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.livosphere.content.AuthoredContentCatalog
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
import kotlinx.coroutines.withContext

/** Single explicit mapping from logical registry component to the packaged Android service. */
object AndroidWallpaperTarget {
    fun resolve(context: Context, wallpaperId: String): WallpaperTarget? =
        AuthoredContentCatalog.wallpaperSets.singleOrNull { it.wallpaper.componentId.value == wallpaperId }?.wallpaper?.let {
            WallpaperTarget(it.componentId.value, WallpaperComponent(context.packageName, it.serviceClassName), it.compatibility.minimumApi)
        }
    /** Only the initial presentation default; never used for a missing saved reference or a launch. */
    fun initialBrowsingTarget(context: Context): WallpaperTarget? = AuthoredContentCatalog.wallpaperSets.firstOrNull()?.let {
        resolve(context, it.wallpaper.componentId.value)
    }
    fun component(target: WallpaperTarget): ComponentName = ComponentName(target.component.packageName, target.component.className)
    fun directPreviewIntent(target: WallpaperTarget): Intent = directPreviewIntent(component(target))
    internal fun directPreviewIntent(component: ComponentName): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    fun chooserIntent(): Intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
}

@Singleton
class AndroidWallpaperGateway internal constructor(
    private val clock: Clock,
    override val initialBrowsingTarget: WallpaperTarget?,
    private val observe: (WallpaperTarget, Instant) -> PhoneWallpaperSnapshot,
) : PhoneWallpaperGateway {
    @Inject constructor(@ApplicationContext context: Context, clock: Clock) : this(
        clock,
        AndroidWallpaperTarget.initialBrowsingTarget(context),
        platformObservation(context.applicationContext),
    )

    private val mutableSnapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
    override val snapshots = mutableSnapshots.asStateFlow()
    private val publicationLock = Any()
    private var latestRequest = 0L

    override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot {
        val request = synchronized(publicationLock) { ++latestRequest }
        return withContext(Dispatchers.IO) {
            val snapshot = observe(target, clock.instant())
            currentCoroutineContext().ensureActive()
            synchronized(publicationLock) {
                if (request == latestRequest) mutableSnapshots.value = snapshot
            }
            snapshot
        }
    }
}

private fun platformObservation(context: Context): (WallpaperTarget, Instant) -> PhoneWallpaperSnapshot = { target, at ->
    require(AndroidWallpaperTarget.resolve(context, target.wallpaperId) == target) { "Unavailable wallpaper target" }
    WallpaperObservation.capture(
        AndroidWallpaperProbe(context, AndroidWallpaperTarget.component(target)),
        target.component, target.minimumApi, at, target.wallpaperId,
    )
}

internal class AndroidWallpaperProbe internal constructor(
    private val contextProvider: () -> Context,
    private val targetProvider: () -> ComponentName,
    override val deviceApi: Int,
    private val applicationQuery: (Int) -> WallpaperComponent?,
    private val intentResolution: ((Intent) -> Boolean)? = null,
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

    override fun resolvesDirectPreview() = resolves(AndroidWallpaperTarget.directPreviewIntent(targetProvider()))
    override fun resolvesChooser() = resolves(AndroidWallpaperTarget.chooserIntent())
    private fun resolves(intent: Intent): Boolean = intentResolution?.invoke(intent) ?: (intent.resolveActivity(packages) != null)

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
