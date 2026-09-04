package app.livosphere.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubReducerTest {
    @Test
    fun `theme is the only initial section`() {
        assertEquals(HubSection.THEME, HubState().selectedSection)
    }

    @Test
    fun `selecting every other section produces matching state and command`() {
        HubSection.entries.filterNot { it == HubSection.THEME }.forEach { section ->
            val transition = HubReducer.reduce(
                state = HubState(),
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
}
