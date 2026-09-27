package app.livosphere.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubReducerTest {
    @Test
    fun `wallpaper catalog owns wallpaper surface and invalidates pending launch`() {
        val wallpaper = HubState(foreground = true)
        val catalog = HubReducer.reduce(wallpaper, HubAction.SectionSelected(HubSection.WALLPAPER_CATALOG)).state
        assertEquals(HubSurface.WALLPAPER, catalog.selectedSurface)
        assertTrue(catalog.phone.generation > wallpaper.phone.generation)
        val restored = HubReducer.reduce(HubState(selectedSurface = HubSurface.CLOCK_WIDGET),
            HubAction.NavigationRestored(HubSection.WALLPAPER_CATALOG)).state
        assertEquals(HubSurface.WALLPAPER, restored.selectedSurface)
    }

    @Test
    fun `tabs own their surfaces and returning to wallpaper invalidates old launch`() {
        val widgets = HubReducer.reduce(HubState(), HubAction.SectionSelected(HubSection.WIDGETS)).state
        assertEquals(HubSurface.CLOCK_WIDGET, widgets.selectedSurface)
        val wallpaper = HubReducer.reduce(widgets, HubAction.SectionSelected(HubSection.THEME)).state
        assertEquals(HubSurface.WALLPAPER, wallpaper.selectedSurface)
        assertTrue(wallpaper.phone.generation > widgets.phone.generation)
    }

    @Test
    fun `restored widget tab and foreground keep widget surface`() {
        val restored = HubReducer.reduce(HubState(), HubAction.NavigationRestored(HubSection.WIDGETS)).state
        assertEquals(HubSurface.CLOCK_WIDGET, restored.selectedSurface)
        val foreground = HubReducer.reduce(HubState(), HubAction.ForegroundStarted(HubSection.WIDGETS)).state
        assertEquals(HubSurface.CLOCK_WIDGET, foreground.selectedSurface)
        val support = HubReducer.reduce(restored, HubAction.SectionSelected(HubSection.SETTINGS)).state
        assertEquals(HubSurface.CLOCK_WIDGET, support.selectedSurface)
    }

    @Test
    fun `theme is the only initial section`() {
        val state = HubState()

        assertEquals(HubSection.THEME, state.selectedSection)
        assertEquals(HubSurface.WALLPAPER, state.selectedSurface)
        assertEquals(false, state.hasSeenThemePreview)
    }

    @Test
    fun `selecting any different section produces matching state and command`() {
        HubSection.entries.forEach { currentSection ->
            val section = HubSection.entries.first { it != currentSection }
            val transition = HubReducer.reduce(
                state = HubState(selectedSection = currentSection),
                action = HubAction.SectionSelected(section),
            )

            assertEquals(section, transition.state.selectedSection)
            assertEquals(listOf(HubCommand.ShowSection(section)), transition.commands)
        }
    }

    @Test
    fun `selecting current section is a no-op`() {
        val initial = HubState(selectedSection = HubSection.SETTINGS)
        val transition = HubReducer.reduce(
            state = initial,
            action = HubAction.SectionSelected(HubSection.SETTINGS),
        )

        assertEquals(initial, transition.state)
        assertTrue(transition.commands.isEmpty())
    }

    @Test
    fun `restored navigation updates state without a new command`() {
        val transition = HubReducer.reduce(
            state = HubState(),
            action = HubAction.NavigationRestored(HubSection.DEVICES),
        )

        assertEquals(HubSection.DEVICES, transition.state.selectedSection)
        assertTrue(transition.commands.isEmpty())
    }

    @Test
    fun `surface selection is immediate and never emits a command`() {
        val transition = HubReducer.reduce(
            state = HubState(selectedSurface = HubSurface.WALLPAPER),
            action = HubAction.SurfaceSelected(HubSurface.CLOCK_WIDGET),
        )

        assertEquals(HubSurface.CLOCK_WIDGET, transition.state.selectedSurface)
        assertTrue(transition.commands.isEmpty())
    }

    @Test
    fun `latest rapid surface selection wins without a command queue`() {
        val actions = listOf(
            HubAction.SurfaceSelected(HubSurface.CLOCK_WIDGET),
            HubAction.SurfaceSelected(HubSurface.WALLPAPER),
            HubAction.SurfaceSelected(HubSurface.CLOCK_WIDGET),
        )

        val final = actions.fold(HubTransition(HubState())) { transition, action ->
            HubReducer.reduce(transition.state, action)
        }

        assertEquals(HubSurface.CLOCK_WIDGET, final.state.selectedSurface)
        assertTrue(final.commands.isEmpty())
    }

    @Test
    fun `preview first seen is session state and idempotent`() {
        val first = HubReducer.reduce(HubState(), HubAction.ThemePreviewSeen)
        val repeated = HubReducer.reduce(first.state, HubAction.ThemePreviewSeen)

        assertTrue(first.state.hasSeenThemePreview)
        assertEquals(first.state, repeated.state)
        assertTrue(first.commands.isEmpty())
        assertTrue(repeated.commands.isEmpty())
    }
}
