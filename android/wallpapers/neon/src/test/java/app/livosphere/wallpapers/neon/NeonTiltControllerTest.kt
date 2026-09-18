package app.livosphere.wallpapers.neon

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class NeonTiltControllerTest {
    private var time = 0L
    private fun feed(c: NeonTiltController, roll: Float = 0f, pitch: Float = 35f, rotation: Int = 0, samples: Int = 30): List<NeonTiltFrame> =
        (1..samples).mapNotNull {
            time += 50
            val r = roll * PI / 180; val p = pitch * PI / 180
            val x = (9.81 * sin(r)).toFloat(); val y = (9.81 * cos(r) * sin(p)).toFloat(); val z = (9.81 * cos(r) * cos(p)).toFloat()
            val raw = when (rotation) { 1 -> -y to x; 2 -> -x to -y; 3 -> y to -x; else -> x to y }
            c.sample(raw.first, raw.second, z, rotation, time)
        }
    @Test fun initialPoseCalibratesWithoutBlinkInEveryOrientation() {
        for (rotation in 0..3) {
            val frames = feed(NeonTiltController(), 20f, 50f, rotation)
            assertTrue(frames.all { abs(it.x) < .001f && abs(it.y) < .001f && !it.react })
        }
    }
    @Test fun jitterIsIgnoredAndMotionIsSmoothedAndBounded() {
        val c = NeonTiltController(); feed(c)
        assertTrue(feed(c, 1f).all { it.x == 0f && !it.react })
        val moving = feed(c, 40f)
        assertTrue(moving.first().x < moving.last().x)
        assertTrue(moving.all { it.x in 0f..1f && it.y in -1f..1f })
        assertEquals(1f, moving.last().x, .001f)
    }
    @Test fun displayRotationMapsEquivalentPhysicalTilts() {
        for (rotation in 0..3) {
            val c = NeonTiltController(); feed(c, rotation = rotation)
            val tilt = feed(c, 15f, 45f, rotation).last()
            assertTrue(tilt.x > .65f); assertTrue(tilt.y > .3f)
        }
    }
    @Test fun deliberateTiltReactsOnceUntilReturnedToNeutral() {
        val c = NeonTiltController(); feed(c)
        assertEquals(1, feed(c, 17f, samples = 100).count { it.react })
        assertEquals(0, feed(c, 17f, samples = 100).count { it.react })
        feed(c, samples = 40)
        assertEquals(1, feed(c, -17f).count { it.react })
    }
    @Test fun rapidReturnDoesNotBypassCooldown() {
        val c = NeonTiltController(); feed(c)
        assertEquals(1, feed(c, 17f, samples = 10).count { it.react })
        feed(c, samples = 12)
        assertEquals(0, feed(c, 17f, samples = 10).count { it.react })
    }
    @Test fun invalidSamplesAndShakeCannotTrigger() {
        val c = NeonTiltController(); feed(c)
        assertNull(c.sample(Float.NaN, 0f, 9f, 0, time + 50))
        assertNull(c.sample(40f, 0f, 9f, 0, time + 50))
        assertNull(c.sample(0f, 0f, 0f, 0, time + 50))
        assertNull(c.sample(0f, 0f, 9f, 0, time - 50))
        assertTrue(feed(c).none { it.react })
    }
    @Test fun resumeGapResetAndRotationStartFromNeutral() {
        val c = NeonTiltController(); feed(c); feed(c, 17f)
        c.reset(); assertEquals(NeonTiltFrame(), feed(c, 17f, samples = 1).single())
        time += 2000; assertEquals(NeonTiltFrame(), feed(c, -17f, samples = 1).single())
        assertEquals(NeonTiltFrame(), feed(c, 25f, rotation = 1, samples = 1).single())
    }
    @Test fun separateEnginesDoNotShareCalibration() {
        val a = NeonTiltController(); val b = NeonTiltController()
        feed(a); time = 0; feed(b, 20f)
        assertTrue(feed(a, 20f).last().x > .9f)
        time = 1500
        assertEquals(0f, feed(b, 20f).last().x, .001f)
    }
}
