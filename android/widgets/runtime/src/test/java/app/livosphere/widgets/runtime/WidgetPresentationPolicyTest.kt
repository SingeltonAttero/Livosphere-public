package app.livosphere.widgets.runtime

import app.livosphere.contract.WidgetSize
import org.junit.Assert.*
import org.junit.Test

class WidgetPresentationPolicyTest {
    @Test fun largeFontCannotOverflowACompactDigitalHost() {
        for (size in WidgetSize.entries) for (scale in listOf(1f, 1.5f, 2f)) {
            val metrics = WidgetPresentationPolicy.metrics(size, 110, scale, false)
            assertTrue(metrics.timeSp * scale * 3.2f <= 86.01f)
            assertTrue(metrics.timeSp > 0)
        }
    }
    @Test fun analogDateFitsBesideDialAndFullDigitalKeepsHierarchy() {
        assertEquals("d MMM", WidgetPresentationPolicy.metrics(WidgetSize.M, 250, 1f, true).datePattern)
        val medium = WidgetPresentationPolicy.metrics(WidgetSize.M, 250, 1f, false)
        val large = WidgetPresentationPolicy.metrics(WidgetSize.L, 250, 1f, false)
        assertTrue(large.timeSp > medium.timeSp)
        assertTrue(medium.timeSp > medium.dateSp * 3)
    }
}
