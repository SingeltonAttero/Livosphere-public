package app.livosphere.hub.theme

import android.content.Context
import androidx.annotation.DrawableRes
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.hub.HubSurface

internal data class PreviewAsset(
    @param:DrawableRes val drawableId: Int,
    val symbolicName: String,
)

internal object PreviewAssetResolver {
    private const val SET_ID = "contour-draft"

    fun resolve(context: Context, surface: HubSurface): PreviewAsset {
        val descriptor = GeneratedSetRegistry.sets.singleOrNull { it.setId.value == SET_ID }
            ?: error("Expected exactly one $SET_ID descriptor")
        val rolePrefix = when (surface) {
            HubSurface.WALLPAPER -> "preview-wallpaper-"
            HubSurface.WATCH_FACE -> "preview-watchface-"
        }
        val reference = descriptor.preview.resources.singleOrNull {
            it.symbolicName.startsWith(rolePrefix)
        } ?: error("Expected exactly one preview resource for role $rolePrefix*")
        val directory = reference.resourcePath.substringBefore('/')
        val fileName = reference.resourcePath.substringAfter('/')
        check(directory == "drawable-nodpi" && fileName.endsWith(".png")) {
            "Preview role $rolePrefix* must resolve to a drawable-nodpi PNG"
        }
        val resourceName = fileName.removeSuffix(".png")
        val drawableId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        check(drawableId != 0) { "Drawable $resourceName from GeneratedSetRegistry is not packaged" }
        return PreviewAsset(drawableId = drawableId, symbolicName = reference.symbolicName)
    }
}
