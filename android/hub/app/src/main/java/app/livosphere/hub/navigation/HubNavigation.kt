package app.livosphere.hub.navigation

import androidx.navigation3.runtime.NavKey
import app.livosphere.hub.HubCommand
import app.livosphere.hub.HubSection
import kotlinx.serialization.Serializable

@Serializable
internal data object ThemeKey : NavKey

@Serializable
internal data object WidgetsKey : NavKey

@Serializable
internal data object MoreKey : NavKey

@Serializable
internal data object DevicesKey : NavKey

@Serializable
internal data object SettingsKey : NavKey

internal class HubNavigator(
    private val backStack: MutableList<NavKey>,
) {
    fun execute(command: HubCommand.ShowSection) {
        show(command.section)
    }

    private fun show(section: HubSection) {
        val key = section.toNavKey()
        if (backStack.size == 1 && backStack.lastOrNull() == key) return

        backStack.clear()
        if (section == HubSection.SETTINGS || section == HubSection.DEVICES) backStack.add(MoreKey)
        backStack.add(key)
    }
}

internal fun HubSection.toNavKey(): NavKey = when (this) {
    HubSection.THEME -> ThemeKey
    HubSection.WIDGETS -> WidgetsKey
    HubSection.MORE -> MoreKey
    HubSection.DEVICES -> DevicesKey
    HubSection.SETTINGS -> SettingsKey
}

internal fun NavKey?.toSection(): HubSection = when (this) {
    WidgetsKey -> HubSection.WIDGETS
    MoreKey -> HubSection.MORE
    DevicesKey -> HubSection.DEVICES
    SettingsKey -> HubSection.SETTINGS
    ThemeKey, null -> HubSection.THEME
    else -> error("Unsupported hub navigation key: $this")
}
