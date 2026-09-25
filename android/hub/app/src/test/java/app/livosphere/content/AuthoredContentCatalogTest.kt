package app.livosphere.content

import app.livosphere.contract.WidgetLayoutStatus
import app.livosphere.generated.GeneratedSetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoredContentCatalogTest {
    @Test fun `wallpaper and clock projections preserve independent surface eligibility`() {
        val expectedWallpaperIds = GeneratedSetRegistry.sets
            .filter { it.schemaVersion == 3 || it.clockWidget?.layoutStatus == WidgetLayoutStatus.NATIVE }
            .map { it.setId }
            .toSet()
        val nativeClockIds = GeneratedSetRegistry.sets
            .filter { it.clockWidget?.layoutStatus == WidgetLayoutStatus.NATIVE }
            .map { it.setId }
            .toSet()
        val clocklessIds = GeneratedSetRegistry.sets.filter { it.clockWidget == null }.map { it.setId }.toSet()

        assertEquals(expectedWallpaperIds, AuthoredContentCatalog.wallpaperSets.map { it.setId }.toSet())
        assertEquals(nativeClockIds, AuthoredContentCatalog.clockSets.map { it.setId }.toSet())
        assertEquals(nativeClockIds, AuthoredContentCatalog.sets.map { it.setId }.toSet())
        assertTrue(AuthoredContentCatalog.clockSets.none { it.setId in clocklessIds })
    }
}
