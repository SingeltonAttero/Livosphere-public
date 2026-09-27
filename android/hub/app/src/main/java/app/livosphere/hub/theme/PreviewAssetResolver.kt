package app.livosphere.hub.theme

import android.content.Context
import android.content.ComponentName
import app.livosphere.R
import androidx.annotation.DrawableRes
import app.livosphere.contract.SetDescriptor
import app.livosphere.content.AuthoredContentCatalog
import app.livosphere.hub.HubSurface
import app.livosphere.contract.WidgetSize
import app.livosphere.contract.DayPhase

internal data class PreviewAsset(@param:DrawableRes val drawableId: Int, val symbolicName: String)

internal object PreviewAssetResolver {
    /** Default only for a new presentation session; explicit missing IDs remain unavailable. */
    val initialBrowsingSetId get() = AuthoredContentCatalog.wallpaperSets.firstOrNull()?.setId?.value

    fun descriptor(setId: String?): SetDescriptor? = AuthoredContentCatalog.wallpaperSets.singleOrNull { it.setId.value == setId }

    fun displayName(context: Context, descriptor: SetDescriptor): String = try {
        context.packageManager.getServiceInfo(ComponentName(context.packageName, descriptor.wallpaper.serviceClassName), 0)
            .loadLabel(context.packageManager).toString()
    } catch (_: Exception) { context.getString(R.string.theme_set_name_unavailable) }

    fun resolve(context: Context, setId: String?, surface: HubSurface): PreviewAsset? =
        resolve(context, descriptor(setId), surface)

    fun resolve(context: Context, descriptor: SetDescriptor?, surface: HubSurface, widgetSize: WidgetSize = WidgetSize.M): PreviewAsset? {
        descriptor ?: return null
        val roleRef = when (surface) {
            HubSurface.WALLPAPER -> descriptor.preview.wallpaperRef
            HubSurface.CLOCK_WIDGET -> descriptor.preview.widgetRefs[widgetSize]
        }
        val reference = roleRef?.let { ref -> descriptor.preview.resources.singleOrNull { it.symbolicName == ref } }
        reference ?: return null
        val directory = reference.resourcePath.substringBefore('/')
        val fileName = reference.resourcePath.substringAfter('/')
        if (directory != "drawable-nodpi" || !fileName.endsWith(".png")) return null
        val drawableId = context.resources.getIdentifier(fileName.removeSuffix(".png"), "drawable", context.packageName)
        return if (drawableId == 0) null else PreviewAsset(drawableId, reference.symbolicName)
    }

    fun resolveWallpaperPhase(context: Context, descriptor: SetDescriptor, phase: DayPhase): PreviewAsset? {
        val roleRef = descriptor.wallpaper.phaseRefs[phase] ?: return null
        val reference = descriptor.wallpaper.resources.singleOrNull { it.symbolicName == roleRef } ?: return null
        val directory = reference.resourcePath.substringBefore('/')
        val fileName = reference.resourcePath.substringAfter('/')
        val supportedImage = fileName.endsWith(".png") || fileName.endsWith(".webp") ||
            (descriptor.schemaVersion in 3..4 && (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")))
        if (directory != "drawable-nodpi" || !supportedImage) return null
        val resourceName = fileName.substringBeforeLast('.')
        val drawableId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (drawableId == 0) null else PreviewAsset(drawableId, reference.symbolicName)
    }
}
