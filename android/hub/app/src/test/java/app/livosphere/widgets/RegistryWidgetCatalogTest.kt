package app.livosphere.widgets

import app.livosphere.contract.ClockStyle
import app.livosphere.contract.WidgetLayoutStatus
import app.livosphere.generated.GeneratedSetRegistry
import org.junit.Assert.*
import org.junit.Test

class RegistryWidgetCatalogTest {
    @Test fun nativeClockCatalogFollowsGeneratedDescriptorsAndPreservesLegacyWidgets() {
        val catalog = RegistryWidgetCatalog()
        val expected = GeneratedSetRegistry.sets
            .mapNotNull { set -> set.clockWidget?.takeIf { it.layoutStatus == WidgetLayoutStatus.NATIVE } }
            .associateBy { it.componentId.value }

        assertEquals(expected.keys, catalog.items().map { it.widgetId }.toSet())
        assertTrue(expected.keys.containsAll(setOf("sakura-clock", "harbor-clock", "sunset-clock")))
        assertFalse(catalog.contains("isolation-fixture-clock-widget"))
        assertFalse(catalog.contains("contour-debug-clock-widget"))
        expected.forEach { (widgetId, descriptor) ->
            assertEquals(descriptor.style == ClockStyle.ANALOG, catalog.isAnalog(widgetId))
        }
    }
}
