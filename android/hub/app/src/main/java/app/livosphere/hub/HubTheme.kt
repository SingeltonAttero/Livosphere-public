package app.livosphere.hub

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Preview overlays belong to the hub and remain the same for every collection.
internal val HubArtworkBackdrop = Color(0xFF17141F)
internal val HubOnArtwork = Color(0xFFFFFFFF)
internal val HubOnArtworkSecondary = Color(0xFFE8E2EC)
internal val HubArtworkControl = Color(0xFF342E40)
internal val HubBackground = Color(0xFFF8F5F0)
internal val HubSurfaceColor = Color(0xFFFFFFFF)
internal val HubText = Color(0xFF2E223B)
internal val HubTextSecondary = Color(0xFF6B6074)
internal val HubPrimary = Color(0xFF69428C)
internal val HubSelected = Color(0xFFEAE4F3)
internal val HubStage = Color(0xFFEAE4F3)
internal val HubControlBorder = Color(0xFF887590)
internal val HubDisabledContainer = Color(0xFFD8CDE2)
internal val HubOnDisabledContainer = Color(0xFF3E2E4A)

@Composable
internal fun LivosphereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HubPrimary,
            onPrimary = Color.White,
            background = HubBackground,
            onBackground = HubText,
            surface = HubSurfaceColor,
            onSurface = HubText,
            onSurfaceVariant = HubTextSecondary,
            secondaryContainer = HubSelected,
            onSecondaryContainer = HubText,
        ),
        content = content,
    )
}
