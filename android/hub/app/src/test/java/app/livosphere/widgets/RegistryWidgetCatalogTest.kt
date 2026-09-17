package app.livosphere.widgets

import org.junit.Assert.*
import org.junit.Test

class RegistryWidgetCatalogTest {
    @Test fun onlyThreeAuthoredCollectionsAreSelectable() {
        val catalog = RegistryWidgetCatalog()
        assertEquals(setOf("sakura-clock", "harbor-clock", "sunset-clock"), catalog.items().map { it.widgetId }.toSet())
        assertFalse(catalog.contains("isolation-fixture-clock-widget"))
        assertFalse(catalog.contains("contour-debug-clock-widget"))
        assertTrue(catalog.isAnalog("sunset-clock"))
        assertFalse(catalog.isAnalog("sakura-clock"))
    }
}
