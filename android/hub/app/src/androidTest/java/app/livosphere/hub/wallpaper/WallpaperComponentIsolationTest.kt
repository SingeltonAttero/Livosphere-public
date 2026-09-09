package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.wallpaper.WallpaperService
import app.livosphere.wallpapers.contour.ContourWallpaperService
import app.livosphere.wallpapers.fixture.FixtureWallpaperService
import app.livosphere.OwnedSettingsFixture
import java.io.File
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.generated.GeneratedSetRegistry
import app.livosphere.hub.HubSurface
import app.livosphere.hub.onboarding.Outcome
import app.livosphere.hub.theme.PreviewAssetResolver
import app.livosphere.settings.WallpaperSettingsRepository
import app.livosphere.settings.WallpaperMotionMode
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Component/intent/settings checks. Actual system preview/cancel/process restart is a separate UI protocol. */
class WallpaperComponentIsolationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun target(id: String) = checkNotNull(AndroidWallpaperTarget.resolve(context, id))

    @Test fun exactRegistryTargetsArePackagedAndLauncherCapturesB() = runBlocking {
        val a = target("contour-wallpaper")
        val b = target("isolation-fixture-wallpaper")
        assertNotEquals(a.component, b.component)
        val gateway = AndroidWallpaperGateway(context, Clock.systemUTC())
        for (target in listOf(a, b)) {
            val observed = gateway.refresh(target)
            assertEquals(target, observed.target)
            assertEquals(WallpaperFact.Known(WallpaperPresence.AVAILABLE), observed.presence)
            val service = context.packageManager.getServiceInfo(AndroidWallpaperTarget.component(target), PackageManager.GET_META_DATA)
            assertTrue(service.exported)
            assertEquals("android.permission.BIND_WALLPAPER", service.permission)
            assertTrue(service.metaData.getInt("android.service.wallpaper") != 0)
        }
        val intents = mutableListOf<Intent>()
        val launcher = AndroidWallpaperLauncher(context, intents::add)
        val request = WallpaperLaunchRequest(1, 1, WallpaperRoute.DIRECT, b)
        assertEquals(Outcome.Success(Unit), launcher.launch(request))
        @Suppress("DEPRECATION")
        assertEquals(AndroidWallpaperTarget.component(b), intents.single()
            .getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT))
        assertNull(AndroidWallpaperTarget.resolve(context, "removed-wallpaper"))
        val invalid = request.copy(target = b.copy(component = a.component))
        assertEquals(Outcome.Failure(WallpaperLaunchFailure.INVALID_REQUEST), launcher.launch(invalid))
        assertEquals(1, intents.size)
    }

    @Test fun directProbeUsesInjectedBComponentInsteadOfInitialBrowsingA() {
        val b = AndroidWallpaperTarget.component(target("isolation-fixture-wallpaper"))
        var captured: Intent? = null
        // The resolver seam captures the actual Intent from production resolvesDirectPreview.
        val probe = AndroidWallpaperProbe({ context }, { b }, 37, { null }, { intent ->
            captured = intent; true
        })
        assertTrue(probe.resolvesDirectPreview())
        @Suppress("DEPRECATION")
        assertEquals(b, checkNotNull(captured).getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT))
    }

    @Test fun previewRolesComeFromEachDescriptorAndMissingSavedIdIsUnavailable() {
        val a = checkNotNull(PreviewAssetResolver.resolve(context, "contour-draft", HubSurface.WALLPAPER))
        val b = checkNotNull(PreviewAssetResolver.resolve(context, "isolation-fixture", HubSurface.WALLPAPER))
        assertNotEquals(a.drawableId, b.drawableId)
        assertEquals(GeneratedSetRegistry.sets.single { it.setId.value == "isolation-fixture" }.preview.wallpaperRef, b.symbolicName)
        assertNull(PreviewAssetResolver.resolve(context, "removed-set", HubSurface.WALLPAPER))
        assertNull(PreviewAssetResolver.resolve(context, null as String?, HubSurface.WALLPAPER))
        assertNull(PreviewAssetResolver.resolve(context, "isolation-fixture", HubSurface.WATCH_FACE))
    }

    @Test fun separateRepositoryOwnersAndColdRecreationPreserveAWithoutActivity() = runBlocking {
        val fixture = OwnedSettingsFixture()
        val a = fixture.repository("contour-wallpaper")
        val b = a.forWallpaper("isolation-fixture-wallpaper")
        val originalA = a.touchReactionsEnabled.first() to a.motionMode.first()
        try {
            b.setTouchReactionsEnabled(false)
            b.setMotionMode(WallpaperMotionMode.REDUCED)
            // Cold repository creation uses only component-owned ID and application context.
            val coldB = fixture.repository(target("isolation-fixture-wallpaper").wallpaperId)
            assertFalse(checkNotNull(coldB.touchReactionsEnabled.first()))
            assertEquals(WallpaperMotionMode.REDUCED, coldB.motionMode.first())
            assertEquals(originalA, a.touchReactionsEnabled.first() to a.motionMode.first())
        } finally {
            fixture.close()
        }
    }
    private class TestA(context: Context) : ContourWallpaperService() { init { attachBaseContext(context) } }
    private class TestB(context: Context) : FixtureWallpaperService() { init { attachBaseContext(context) } }

    @Test fun independentEngineOpenCancelAndColdServiceRecreationNeverWritePreferences() = runBlocking {
        // Read the existing application store before Engine creation; opening preview has no save action.
        val a = WallpaperSettingsRepository(context, "contour-wallpaper")
        val b = WallpaperSettingsRepository(context, "isolation-fixture-wallpaper")
        val beforeA = a.touchReactionsEnabled.first() to a.motionMode.first()
        val beforeB = b.touchReactionsEnabled.first() to b.motionMode.first()
        val file = File(context.filesDir, "datastore/contour-wallpaper-settings.preferences_pb")
        val bytes = if (file.exists()) file.readBytes() else null
        val engines = mutableListOf<WallpaperService.Engine>()
        try {
            instrumentation.runOnMainSync {
                val homeA = TestA(context).onCreateEngine().also(engines::add)
                val previewB = TestB(context).onCreateEngine().also(engines::add)
                listOf(homeA, previewB).forEach { it.onCreate(it.surfaceHolder) }
                assertNotSame(homeA, previewB)
                previewB.onVisibilityChanged(false)
                previewB.onDestroy()
                engines.remove(previewB)
                // Fresh service object recreates B by its own fixed identity, no Hub/Activity/global selection.
                val coldB = TestB(context).onCreateEngine().also(engines::add)
                coldB.onCreate(coldB.surfaceHolder)
                assertNotSame(previewB, coldB)
            }
        } finally {
            instrumentation.runOnMainSync { engines.forEach { it.onDestroy() } }
        }
        assertEquals(beforeA, a.touchReactionsEnabled.first() to a.motionMode.first())
        assertEquals(beforeB, b.touchReactionsEnabled.first() to b.motionMode.first())
        if (bytes == null) assertFalse(file.exists()) else assertArrayEquals(bytes, file.readBytes())
    }

}
