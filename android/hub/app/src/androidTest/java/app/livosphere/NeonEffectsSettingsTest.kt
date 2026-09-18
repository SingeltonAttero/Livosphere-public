package app.livosphere

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.WallpaperEffectLevel
import app.livosphere.hub.*
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NeonEffectsSettingsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun levelSelectionPersistsOnReturnAndDoesNotChangeAnotherWallpaper() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = OwnedSettingsFixture()
        val models = ViewModelStore()
        val target = checkNotNull(AndroidWallpaperTarget.resolve(context, "electric-harbor-wallpaper"))
        val other = checkNotNull(AndroidWallpaperTarget.resolve(context, "night-sakura-wallpaper"))
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val initialBrowsingTarget = target
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No platform probe")
        }
        lateinit var vm: HubViewModel
        instrumentation.runOnMainSync {
            vm = HubViewModel(history, gateway, Clock.systemUTC(), fixture.repository(target.wallpaperId))
            models.put("hub", vm)
        }
        try {
            compose.setContent { LivosphereTheme { HubApp(vm, onExit = {}) } }
            compose.onNodeWithTag("hub-nav-more").performClick()
            compose.onNodeWithTag("more-settings").performClick()
            compose.onNodeWithTag("settings-wallpaper-toggle").performClick()
            compose.waitUntil(5_000) { vm.wallpaperSettingsUi.value.effectLevel != null }
            compose.onNodeWithTag("wallpaper-effect-balanced").performScrollTo().performClick()
            compose.waitUntil(5_000) { vm.wallpaperSettingsUi.value.effectLevel == WallpaperEffectLevel.BALANCED }
            compose.onNodeWithTag("wallpaper-effect-balanced").assertIsSelected()
            val image = instrumentation.uiAutomation.takeScreenshot()
            val file = File(context.getExternalFilesDir("neon-effects-review"), "settings.png")
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
            compose.onNodeWithTag("hub-back").performClick()
            compose.onNodeWithTag("more-settings").performClick()
            compose.onNodeWithTag("settings-wallpaper-toggle").performClick()
            compose.onNodeWithTag("wallpaper-effect-balanced").performScrollTo().assertIsSelected()
            compose.runOnIdle { vm.onAction(HubAction.Phone(PhoneWallpaperAction.TargetSelected(other))) }
            compose.waitUntil(5_000) { vm.wallpaperSettingsUi.value.effectLevel == WallpaperEffectLevel.FULL }
            compose.onNodeWithTag("wallpaper-effect-full").performScrollTo().assertIsSelected()
            runBlocking {
                val saved = fixture.repository(target.wallpaperId).settings.first() as app.livosphere.contract.SettingsOutcome.Success
                assertEquals(WallpaperEffectLevel.BALANCED, saved.value.effectLevel)
            }
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            runBlocking { fixture.close() }
        }
    }
}
