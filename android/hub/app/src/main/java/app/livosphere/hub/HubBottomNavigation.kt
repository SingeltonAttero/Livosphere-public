package app.livosphere.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.livosphere.R

@Composable
internal fun HubBottomNavigation(selectedSection: HubSection, immersive: Boolean, onSectionSelected: (HubSection) -> Unit) {
    val sections = listOf(HubSection.THEME, HubSection.WIDGETS, HubSection.MORE)
    Surface(color = if (immersive) HubArtworkBackdrop.copy(alpha = .76f) else MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.Center) {
            Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().selectableGroup().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { section ->
                    val chosen = selectedSection == section
                    val foreground = if (immersive) HubOnArtwork else if (chosen) HubPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    val selectedColor = if (immersive) HubOnArtwork.copy(alpha = .16f) else HubSelected
                    Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (chosen) selectedColor else Color.Transparent)
                        .selectable(selected = chosen, role = Role.Tab, onClick = { onSectionSelected(section) })
                        .semantics { testTag = "hub-nav-${section.name.lowercase()}" }.padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val icon = when (section) {
                            HubSection.THEME -> R.drawable.ic_hub_wallpapers
                            HubSection.WIDGETS -> R.drawable.ic_hub_widgets
                            else -> R.drawable.ic_hub_more
                        }
                        val label = when (section) {
                            HubSection.THEME -> R.string.hub_section_theme
                            HubSection.WIDGETS -> R.string.hub_section_widgets
                            else -> R.string.hub_section_more
                        }
                        Icon(painterResource(icon), contentDescription = null, tint = foreground, modifier = Modifier.size(24.dp))
                        Text(stringResource(label), color = foreground, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { testTag = "hub-nav-label-${section.name.lowercase()}" })
                    }
                }
            }
        }
    }
}
