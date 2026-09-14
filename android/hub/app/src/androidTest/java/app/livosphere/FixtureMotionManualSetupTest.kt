package app.livosphere

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.settings.WallpaperMotionMode
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** AndroidTest-only setup for native evidence; preserves and restores this fixture owner's value. */
class FixtureMotionManualSetupTest {
    @Test fun applyRequestedMotionForManualEvidence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val requested = requireNotNull(arguments.getString(ARGUMENT)) {
            "Pass -e $ARGUMENT off|normal|reduced|restore"
        }
        val repository = WallpaperSettingsRepository(context, FixtureWallpaperService.WALLPAPER_ID)
        val baseline = context.getSharedPreferences(BASELINE_STORE, 0)
        val old = checkNotNull(repository.motionMode.first())
        val target = if (requested == "restore") {
            WallpaperMotionMode.valueOf(requireNotNull(baseline.getString(BASELINE_KEY, null)) {
                "No captured fixture motion baseline"
            })
        } else {
            if (!baseline.contains(BASELINE_KEY)) baseline.edit().putString(BASELINE_KEY, old.name).commit()
            WallpaperMotionMode.valueOf(requested.uppercase())
        }
        repository.setMotionMode(target)
        val updated = checkNotNull(repository.motionMode.first())
        assertEquals(target, updated)
        if (requested == "restore") baseline.edit().remove(BASELINE_KEY).commit()
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "fixture motion old=$old new=$updated owner=${FixtureWallpaperService.WALLPAPER_ID}\n")
        })
    }

    private companion object {
        const val ARGUMENT = "fixtureMotion"
        const val BASELINE_STORE = "fixture-motion-manual-evidence"
        const val BASELINE_KEY = "original"
    }
}
