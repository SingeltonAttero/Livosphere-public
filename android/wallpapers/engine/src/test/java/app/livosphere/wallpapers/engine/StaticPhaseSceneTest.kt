package app.livosphere.wallpapers.engine

import app.livosphere.contract.DayPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StaticPhaseSceneTest {
    private val plates = DayPhase.entries.mapIndexed { index, phase -> phase to index + 1 }.toMap()

    @Test fun `requires four distinct positive phase plates`() {
        val scene = StaticPhaseScene(plates)
        assertEquals(1, scene.plate(DayPhase.MORNING))
        assertEquals(4, scene.plate(DayPhase.NIGHT))

        assertThrows(IllegalArgumentException::class.java) { StaticPhaseScene(plates - DayPhase.DAY) }
        assertThrows(IllegalArgumentException::class.java) { StaticPhaseScene(plates + (DayPhase.DAY to 0)) }
        assertThrows(IllegalArgumentException::class.java) { StaticPhaseScene(plates + (DayPhase.DAY to 1)) }
    }

    @Test fun `aspect fill crops centrally without distortion`() {
        val landscapeToPortrait = StaticPhaseScene(plates).aspectFillCrop(400, 200, 100, 200)
        assertEquals(150f, landscapeToPortrait.left, 0.001f)
        assertEquals(250f, landscapeToPortrait.right, 0.001f)
        assertEquals(0f, landscapeToPortrait.top, 0.001f)
        assertEquals(200f, landscapeToPortrait.bottom, 0.001f)

        val portraitToLandscape = StaticPhaseScene(plates).aspectFillCrop(200, 400, 200, 100)
        assertEquals(0f, portraitToLandscape.left, 0.001f)
        assertEquals(200f, portraitToLandscape.right, 0.001f)
        assertEquals(150f, portraitToLandscape.top, 0.001f)
        assertEquals(250f, portraitToLandscape.bottom, 0.001f)
        assertThrows(IllegalArgumentException::class.java) { StaticPhaseScene(plates).aspectFillCrop(0, 1, 1, 1) }
    }
}
