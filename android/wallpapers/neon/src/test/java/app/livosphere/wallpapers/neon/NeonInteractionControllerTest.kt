package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

class NeonInteractionControllerTest {
    @Test fun allScenesBlinkAndShiftButOnlyNightEmits() {
        for (theme in NeonTheme.entries) for (phase in DayPhase.entries) {
            val c = NeonInteractionController(theme)
            c.configure(true, phase, AuthoredEffectLevel.FULL, 1080, 2400)
            c.tilt(NeonTiltFrame(.5f, -.5f, true), 1000)
            val f = c.frame(1100)
            assertEquals(1f, f.blink, .001f)
            val gain = (1 - exp(-100.0 / 350)).toFloat()
            assertEquals(.02f * gain, f.cloudOffset, .00001f)
            assertEquals(-.006f * gain, f.cloudOffsetY, .00001f)
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
        val rising = c.frame(100)
        assertTrue(rising.cloudOffset in 0f..NeonInteractionController.MAX_SHIFT)
        assertEquals(0, rising.lightGroup)
        val peak = c.frame(500)
        assertTrue(peak.cloudOffset > rising.cloudOffset)
        val returning = c.frame(850)
        assertTrue(returning.cloudOffset > 0f && returning.cloudOffset < peak.cloudOffset)
        assertTrue(returning.cloudOffsetY < 0f && returning.cloudOffsetY > peak.cloudOffsetY)
        assertEquals(0f, c.frame(4_000).cloudOffset, .00001f)
        assertEquals(0f, c.frame(4_000).cloudOffsetY, .00001f)
    }

    @Test fun cloudsAreContinuousOnReversalAndIndependentOfFrameRate() {
        val a = NeonInteractionController(NeonTheme.HARBOR)
        val b = NeonInteractionController(NeonTheme.HARBOR)
        for (c in listOf(a, b)) {
            c.configure(true, DayPhase.DAY, AuthoredEffectLevel.FULL, 1080, 2400)
            c.tilt(NeonTiltFrame(1f, 1f), 0)
            assertEquals(0f, c.frame(0).cloudOffset, 0f)
        }
        for (t in 50L..400L step 50) a.frame(t)
        val before = a.frame(400)
        assertEquals(before.cloudOffset, b.frame(400).cloudOffset, .000001f)
        assertTrue(before.cloudOffset > .014f)
        for (c in listOf(a, b)) {
            c.tilt(NeonTiltFrame(-1f, -1f), 400)
            assertEquals(before.cloudOffset, c.frame(400).cloudOffset, .000001f)
        }
        for (t in 450L..1_000L step 50) a.frame(t)
        val after = a.frame(1_000)
        assertTrue(after.cloudOffset < 0f)
        assertEquals(after.cloudOffset, b.frame(1_000).cloudOffset, .000001f)
        assertEquals(after.cloudOffsetY, b.frame(1_000).cloudOffsetY, .000001f)
        assertTrue(after.cloudOffset >= -NeonInteractionController.MAX_SHIFT)
        assertTrue(after.cloudOffsetY >= -NeonInteractionController.MAX_SHIFT_Y)
    }

    @Test fun everySceneAndPhaseBlinksWithoutTiltAndWithoutAutomaticLightPulse() {
        for (theme in NeonTheme.entries) for (phase in DayPhase.entries) {
            val c = NeonInteractionController(theme)
            c.configure(true, phase, AuthoredEffectLevel.FULL, 1080, 2400, tiltEnabled = false)
            var sawBlink = false
            for (now in 0L..15_000L step 50) {
                c.tilt(NeonTiltFrame(1f, 1f, true), now)
                val frame = c.frame(now)
                if (frame.blink > 0) sawBlink = true
                assertEquals(0f, frame.cloudOffset, 0f)
                assertEquals(0f, frame.lightBoost, 0f)
            }
            assertTrue(sawBlink)
            assertTrue(c.blinks >= 2)
            assertEquals(0L, c.pulses)
        }
    }

    @Test fun disablingTiltClearsItsResponseWithoutResettingTheIdleTimer() {
        val c = NeonInteractionController(NeonTheme.SAKURA)
        c.configure(true, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400)
        c.tilt(NeonTiltFrame(1f, 1f), 3_900)
        assertTrue(c.frame(4_000).cloudOffset > 0)
        c.configure(true, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400, 4_000, tiltEnabled = false)
        c.frame(4_200)
        val frame = c.frame(4_300)
        assertEquals(1f, frame.blink, 0f)
        assertEquals(0f, frame.cloudOffset, 0f)
        assertEquals(0f, frame.cloudOffsetY, 0f)
        assertEquals(0f, frame.lightBoost, 0f)
    }

    @Test fun unlockSurvivesVisibilityOrderingButHiddenAndMotionStopsCancelWelcome() {
        for (theme in NeonTheme.entries) {
            val c = NeonInteractionController(theme)
            c.configure(false, DayPhase.DAY, AuthoredEffectLevel.FULL, 0, 0, 0)
            c.onUnlocked(1_000)
            c.configure(true, DayPhase.DAY, AuthoredEffectLevel.FULL, 1080, 2400, 1_500)
            c.frame(1_850)
            assertEquals(1f, c.frame(1_950).blink, 0f)
            c.configure(false, DayPhase.DAY, AuthoredEffectLevel.FULL, 1080, 2400, 2_000)
            assertEquals(NeonInteractionFrame(), c.frame(2_600))
            c.configure(true, DayPhase.DAY, AuthoredEffectLevel.FULL, 1080, 2400, 10_000)
            assertEquals(NeonInteractionFrame(), c.frame(10_350))
            assertEquals(1L, c.blinks)
            c.onUnlocked(11_000)
            c.cancel()
            assertEquals(NeonInteractionFrame(), c.frame(11_450))
        }
    }
}
