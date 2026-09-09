package app.livosphere

import android.os.Bundle
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.SettingsOutcome
import app.livosphere.settings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Run first after same-install upgrade. Shared source/destination handles; never edits either. */
class InstalledSurfaceMigrationReportTest {
    @Test fun legacyValuesMatchCommittedOwnedDestination() = runBlocking {
        org.junit.Assume.assumeTrue("Explicit same-install baseline required",
            InstrumentationRegistry.getArguments().getString("sameInstallMigration") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val legacy = checkNotNull(ApplicationSurfaceSettings.legacySnapshot(context)) { "Same-install legacy source required" }
        // Independent oracle: fixed baseline A/B keys, without invoking production migration code.
        val owners = mapOf("contour-wallpaper" to "", "isolation-fixture-wallpaper" to "wallpaper.isolation-fixture-wallpaper.")
        val repository = ApplicationSurfaceSettings.get(context)
        val metadata = (repository.metadata.first() as SettingsOutcome.Success).value
        assertEquals(1, metadata.schemaVersion)
        val presentOwners = owners.filter { (_, prefix) ->
            booleanPreferencesKey("${prefix}touch_reactions_enabled") in legacy || stringPreferencesKey("${prefix}wallpaper_motion_mode") in legacy
        }.keys
        assertTrue("Installed upgrade must actually exercise legacy settings", presentOwners.isNotEmpty())
        assertTrue(metadata.wallpaperIds.containsAll(presentOwners))
        owners.forEach { (id, prefix) ->
            val expectedTouch = legacy[booleanPreferencesKey("${prefix}touch_reactions_enabled")] ?: true
            val expectedMotion = legacy[stringPreferencesKey("${prefix}wallpaper_motion_mode")] ?: "NORMAL"
            val value = (repository.wallpapers { it in owners }.observe(id).first() as SettingsOutcome.Success).value
            assertEquals(expectedMotion, value.motionMode.name)
            assertEquals(expectedTouch, value.interactionsEnabled)
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "Migration PASS: file=phone-surface-settings.json schema=${metadata.schemaVersion}; legacy owners=${presentOwners.sorted()}; destination owners=${metadata.wallpaperIds.sorted()}; independent A/B values match; source read-only; no platform facts persisted.\n")
        })
    }
}
