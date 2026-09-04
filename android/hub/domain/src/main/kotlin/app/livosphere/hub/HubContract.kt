package app.livosphere.hub

enum class HubSection {
    THEME,
    DEVICES,
    SETTINGS,
}

enum class HubSurface {
    WALLPAPER,
    WATCH_FACE,
}

data class HubState(
    val selectedSection: HubSection = HubSection.THEME,
    val selectedSurface: HubSurface = HubSurface.WALLPAPER,
    val hasSeenThemePreview: Boolean = false,
)

sealed interface HubAction {
    data class SectionSelected(val section: HubSection) : HubAction

    data class NavigationRestored(val section: HubSection) : HubAction

    data class SurfaceSelected(val surface: HubSurface) : HubAction

    data object ThemePreviewSeen : HubAction
}

sealed interface HubCommand {
    data class ShowSection(val section: HubSection) : HubCommand
}

data class HubTransition(
    val state: HubState,
    val commands: List<HubCommand> = emptyList(),
)

object HubReducer {
    fun reduce(state: HubState, action: HubAction): HubTransition = when (action) {
        is HubAction.SectionSelected -> selectSection(state, action.section)
        is HubAction.NavigationRestored -> HubTransition(
            state = state.copy(selectedSection = action.section),
        )
        is HubAction.SurfaceSelected -> HubTransition(
            state = state.copy(selectedSurface = action.surface),
        )
        HubAction.ThemePreviewSeen -> HubTransition(
            state = state.copy(hasSeenThemePreview = true),
        )
    }

    private fun selectSection(state: HubState, section: HubSection): HubTransition {
        if (state.selectedSection == section) return HubTransition(state)

        return HubTransition(
            state = state.copy(selectedSection = section),
            commands = listOf(HubCommand.ShowSection(section)),
        )
    }
}
