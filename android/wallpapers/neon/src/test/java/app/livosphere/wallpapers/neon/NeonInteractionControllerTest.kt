package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NeonInteractionControllerTest {
    private fun controller(theme: NeonTheme = NeonTheme.SAKURA, phase: DayPhase = DayPhase.DAY,
        level: AuthoredEffectLevel = AuthoredEffectLevel.FULL) = NeonInteractionController(theme).apply {
        configure(true, phase, level, 941, 1672)
    }
    private fun face(theme: NeonTheme) = when (theme) {
        NeonTheme.SAKURA -> NeonPoint(386f, 710f)
        NeonTheme.HARBOR -> NeonPoint(500f, 665f)
        NeonTheme.SUNSET -> NeonPoint(374f, 575f)
    }
    private fun tap(c: NeonInteractionController, p: NeonPoint, at: Long) {
        c.down(p.x, p.y, at); c.up(p.x, p.y, at + 30)
    }
    @Test fun eachCharacterBlinksInEveryPhaseWithShortRecoveryAndNoQueue() {
        for (theme in NeonTheme.entries) for (phase in DayPhase.entries) {
            val c = controller(theme, phase)
            tap(c, face(theme), 1000)
            assertEquals(1, c.blinks)
            assertEquals(1f, c.frame(1110).blink, .001f)
            assertTrue(c.frame(1200).blink in .01f.. .99f)
            tap(c, face(theme), 1200)
            assertEquals(1, c.blinks)
            assertEquals(0f, c.frame(1300).blink)
            tap(c, face(theme), 2500)
            assertEquals(2, c.blinks)
            assertEquals(0, c.pulses)
        }
    }
    @Test fun coalescedHorizontalAndVerticalDragsLongPressAndCancelNeverBecomeTaps() {
        val c = controller()
        c.down(386f,710f,0); c.up(426f,710f,100)
        assertEquals(0, c.blinks); assertEquals(1, c.swipes)
        c.down(386f,680f,2000); c.up(386f,710f,2100)
        c.down(386f,710f,3000); c.up(386f,710f,3600)
        c.down(386f,710f,4000); c.pointerCancel(4010); c.up(386f,710f,4030)
        assertEquals(0, c.blinks)
    }
    @Test fun swipesEaseWithinBoundsThenReturnAndNewDirectionReplaces() {
        val c = controller()
        c.down(100f,200f,0); c.move(900f,200f,50)
        assertEquals(0f, c.frame(50).cloudOffset)
        assertTrue(c.frame(95).cloudOffset > 0f)
        assertEquals(NeonInteractionController.MAX_SHIFT, c.frame(150).cloudOffset, .00001f)
        c.up(900f,200f,200)
        assertTrue(c.frame(500).cloudOffset < c.frame(300).cloudOffset)
        c.down(900f,200f,550); c.move(100f,200f,600)
        assertEquals(-NeonInteractionController.MAX_SHIFT, c.frame(700).cloudOffset, .00001f)
        c.up(100f,200f,750)
        assertEquals(0f, c.frame(1800).cloudOffset, .00001f)
        assertEquals(2, c.swipes)
    }
    @Test fun nightOnlyPulsesNearestAvailableGroupAndNeverQueues() {
        for (theme in NeonTheme.entries) for (phase in DayPhase.entries) for (level in AuthoredEffectLevel.entries) {
            val c = controller(theme, phase, level)
            val point = NeonScenePolicy.lightGroups(theme)[1].let { NeonPoint(it.x * 941, it.y * 1672) }
            tap(c, point, 1000)
            val frame = c.frame(1630)
            if (phase == DayPhase.NIGHT) {
                assertEquals(1, c.pulses)
                assertEquals(if (level == AuthoredEffectLevel.SUBTLE) 0 else 1, frame.lightGroup)
                assertEquals(.4f, frame.lightBoost, .001f)
                tap(c, point, 1800); assertEquals(1, c.pulses)
                assertEquals(0f, c.frame(2230).lightBoost)
            } else { assertEquals(0, c.pulses); assertEquals(0f, frame.lightBoost) }
        }
    }
    @Test fun disabledPhaseChangeAndResizeCancelResponsesWithoutReplay() {
        val c = controller()
        for (enabled in listOf(false, true)) {
            tap(c, face(NeonTheme.SAKURA), 1000)
            c.configure(enabled, DayPhase.NIGHT, AuthoredEffectLevel.FULL, 1080, 2400)
            assertEquals(NeonInteractionFrame(), c.frame(1110))
        }
        c.configure(false, DayPhase.DAY, AuthoredEffectLevel.FULL, 941, 1672)
        val count = c.blinks; tap(c, face(NeonTheme.SAKURA), 5000)
        c.configure(true, DayPhase.DAY, AuthoredEffectLevel.FULL, 941, 1672)
        assertEquals(count, c.blinks); assertEquals(NeonInteractionFrame(), c.frame(5100))
        val other = controller(); tap(c, face(NeonTheme.SAKURA), 6000)
        assertEquals(0, other.blinks); assertEquals(0f, other.frame(6110).blink)
    }
    @Test fun launcherOffsetsRequireBaselineAndDoNotDuplicateRawTouch() {
        val c = controller()
        c.offset(.1f, .5f, 0); assertEquals(0, c.swipes)
        c.offset(.9f, .5f, 100); assertEquals(1, c.swipes)
        assertTrue(abs(c.frame(200).cloudOffset) <= NeonInteractionController.MAX_SHIFT)
        c.down(100f,200f,200); c.move(200f,200f,230)
        c.offset(.5f,.5f,250); c.up(200f,200f,260)
        c.offset(.8f,.5f,300); assertEquals(2, c.swipes)
        c.offset(.85f,.5f,500); c.offset(.9f,.5f,700); assertEquals(2, c.swipes)
        c.offset(Float.NaN,.5f,600); c.offset(.5f,0f,700)
        c.offset(.2f,.5f,800); assertEquals(2, c.swipes)
        c.offset(.8f,.5f,1200); assertEquals(3, c.swipes)
        assertEquals(0f, c.frame(2200).cloudOffset)
    }
    @Test fun cropMappingWorksInPortraitLandscapeAndRejectsInvalidInput() {
        for ((w,h) in listOf(1080 to 2400, 2400 to 1080, 941 to 1672)) {
            val scale = maxOf(w / 941f, h / 1672f)
            val x = (w - 941 * scale) / 2 + 386 * scale
            val y = (h - 1672 * scale) / 2 + 710 * scale
            val point = checkNotNull(NeonCoordinates.point(x,y,w,h))
            assertEquals(386 / 941f, point.x, .0001f)
            assertEquals(710 / 1672f, point.y, .0001f)
        }
        assertNull(NeonCoordinates.point(Float.NaN,0f,941,1672))
        assertNull(NeonCoordinates.point(-1f,0f,941,1672))
        assertNull(NeonCoordinates.point(0f,0f,0,0))
    }
}
