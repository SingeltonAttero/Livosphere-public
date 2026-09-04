package app.livosphere.hub

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val HubBackground = Color(0xFFF8F5F0)
internal val HubSurface = Color(0xFFFFFFFF)
internal val HubText = Color(0xFF2E223B)
internal val HubTextSecondary = Color(0xFF6B6074)
internal val HubPrimary = Color(0xFF69428C)
internal val HubSelected = Color(0xFFEAE4F3)

@Composable
internal fun LivosphereTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = HubPrimary,
            onPrimary = Color.White,
            background = HubBackground,
            onBackground = HubText,
            surface = HubSurface,
            onSurface = HubText,
            onSurfaceVariant = HubTextSecondary,
            secondaryContainer = HubSelected,
            onSecondaryContainer = HubText,
        ),
        content = content,
    )
}
