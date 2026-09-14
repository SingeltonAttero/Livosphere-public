package app.livosphere.hub.theme

import android.content.Context
import android.content.ComponentName
import app.livosphere.R
import androidx.annotation.DrawableRes
import app.livosphere.contract.SetDescriptor
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.hub.HubSurface
import app.livosphere.contract.WidgetSize

internal data class PreviewAsset(@param:DrawableRes val drawableId: Int, val symbolicName: String)

internal object PreviewAssetResolver {
    /** Default only for a new presentation session; explicit missing IDs remain unavailable. */
    val initialBrowsingSetId get() = GeneratedSetRegistry.sets.firstOrNull()?.setId?.value

    fun descriptor(setId: String?): SetDescriptor? = GeneratedSetRegistry.sets.singleOrNull { it.setId.value == setId }

    fun displayName(context: Context, descriptor: SetDescriptor): String = try {
        context.packageManager.getServiceInfo(ComponentName(context.packageName, descriptor.wallpaper.serviceClassName), 0)
            .loadLabel(context.packageManager).toString()
    } catch (_: Exception) { context.getString(R.string.theme_set_name_unavailable) }

    fun resolve(context: Context, setId: String?, surface: HubSurface): PreviewAsset? =
        resolve(context, descriptor(setId), surface)

    fun resolve(context: Context, descriptor: SetDescriptor?, surface: HubSurface): PreviewAsset? {
        descriptor ?: return null
        val roleRef = when (surface) {
            HubSurface.WALLPAPER -> descriptor.preview.wallpaperRef
            HubSurface.WATCH_FACE -> descriptor.preview.widgetRefs[WidgetSize.M]
        }
        val reference = if (roleRef != null) descriptor.preview.resources.singleOrNull { it.symbolicName == roleRef }
            else if (descriptor.schemaVersion == 1 && surface == HubSurface.WALLPAPER) descriptor.preview.resources.singleOrNull {
                it.symbolicName.startsWith(if (surface == HubSurface.WALLPAPER) "preview-wallpaper-" else "preview-watchface-")
            } else null
        reference ?: return null
        val directory = reference.resourcePath.substringBefore('/')
        val fileName = reference.resourcePath.substringAfter('/')
        if (directory != "drawable-nodpi" || !fileName.endsWith(".png")) return null
        val drawableId = context.resources.getIdentifier(fileName.removeSuffix(".png"), "drawable", context.packageName)
        return if (drawableId == 0) null else PreviewAsset(drawableId, reference.symbolicName)
    }
}
