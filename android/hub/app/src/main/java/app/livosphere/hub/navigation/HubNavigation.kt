package app.livosphere.hub.navigation

import androidx.navigation3.runtime.NavKey
import app.livosphere.hub.HubCommand
import app.livosphere.hub.HubSection
import kotlinx.serialization.Serializable

@Serializable
internal data object ThemeKey : NavKey

@Serializable
internal data object DevicesKey : NavKey

@Serializable
internal data object SettingsKey : NavKey

internal class HubNavigator(
    private val backStack: MutableList<NavKey>,
) {
    val currentSection: HubSection
        get() = backStack.lastOrNull().toSection()

    fun execute(command: HubCommand) {
        when (command) {
            is HubCommand.ShowSection -> show(command.section)
        }
    }

    private fun show(section: HubSection) {
        val key = section.toNavKey()
        if (backStack.size == 1 && backStack.lastOrNull() == key) return

        backStack.clear()
        backStack.add(key)
    }
}

internal fun HubSection.toNavKey(): NavKey = when (this) {
    HubSection.THEME -> ThemeKey
    HubSection.DEVICES -> DevicesKey
    HubSection.SETTINGS -> SettingsKey
}

internal fun NavKey?.toSection(): HubSection = when (this) {
    DevicesKey -> HubSection.DEVICES
    SettingsKey -> HubSection.SETTINGS
    ThemeKey, null -> HubSection.THEME
    else -> error("Unsupported hub navigation key: $this")
}
