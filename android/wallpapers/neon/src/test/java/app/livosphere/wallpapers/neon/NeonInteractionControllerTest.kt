package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import org.junit.Assert.*
import org.junit.Test

class NeonInteractionControllerTest {
    @Test fun allScenesBlinkAndShiftButOnlyNightEmits() {
        for (theme in NeonTheme.entries) for (phase in DayPhase.entries) {
            val c = NeonInteractionController(theme)
            c.configure(true, phase, AuthoredEffectLevel.FULL, 1080, 2400)
            c.tilt(NeonTiltFrame(.5f, -.5f, true), 1000)
            val f = c.frame(1100)
            assertEquals(1f, f.blink, .001f)
            assertEquals(.007f, f.cloudOffset, .001f)
            assertEquals(-.003f, f.cloudOffsetY, .001f)
            assertEquals(phase == DayPhase.NIGHT, f.lightBoost > 0)
            assertEquals(0f, c.frame(1240).blink, .001f)
        }
    }
    @Test fun disabledAndChangedSessionClearEveryResponse() {
        val c = NeonInteractionController(NeonTheme.HARBOR)
        c.configure(true, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400)
        c.tilt(NeonTiltFrame(1f, 1f, true), 0)
        c.configure(false, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400)
        c.tilt(NeonTiltFrame(1f, 1f, true), 50)
        assertEquals(NeonInteractionFrame(), c.frame(100))
        c.configure(true, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400)
        assertEquals(NeonInteractionFrame(), c.frame(120))
        c.tilt(NeonTiltFrame(1f, 1f, true), 200)
        c.configure(true, DayPhase.DAY, AuthoredEffectLevel.FULL, 1080, 2400)
        assertEquals(NeonInteractionFrame(), c.frame(250))
    }
    @Test fun staleSensorReturnsCloudsAndRespectsCurrentLightBudget() {
        val c = NeonInteractionController(NeonTheme.SUNSET)
        c.configure(true, DayPhase.NIGHT, AuthoredEffectLevel.SUBTLE, 1080, 2400)
        c.tilt(NeonTiltFrame(2f, -2f, true), 0)
        assertEquals(.014f, c.frame(100).cloudOffset, .001f)
        assertEquals(0, c.frame(100).lightGroup)
        assertEquals(.007f, c.frame(750).cloudOffset, .001f)
        assertEquals(0f, c.frame(1000).cloudOffset, .001f)
        assertEquals(NeonInteractionFrame(), c.frame(1300))
    }
}
