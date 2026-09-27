package app.livosphere

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import app.livosphere.contract.SurfaceSettingsFailure
import app.livosphere.hub.*
import app.livosphere.hub.onboarding.*
import app.livosphere.hub.wallpaper.*
import app.livosphere.settings.WallpaperMotionMode
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual isolated file -> typed adapter -> VM -> HubApp -> Settings notice and user navigation retry. */
class OwnedSettingsHubAppTest {
    @get:Rule val compose = createComposeRule()
    private val healthy = """{"schemaVersion":1,"browsing":null,"wallpapers":{},"widgets":{}}"""
    private val corruptNotice = "Настройки оформления повреждены. Данные сохранены; автоматический сброс не выполнен."
    private val versionNotice = "Версия сохранённых настроек не поддерживается. Обновите приложение; данные сохранены."

    @Test fun corruptFileNoticeComesThroughHubAppAndSettingsNavigationRetriesRepairedFile() =
        checkFileRecovery("corrupt", "{damaged", SurfaceSettingsFailure.CorruptFile, corruptNotice)

    @Test fun unsupportedFileNoticeComesThroughHubAppAndSettingsNavigationRetriesRepairedFile() =
        checkFileRecovery("version", """{"schemaVersion":99,"future":true}""", SurfaceSettingsFailure.UnsupportedVersion(99), versionNotice)

    private fun checkFileRecovery(case: String, initial: String, expected: SurfaceSettingsFailure, notice: String) {
        val fixture = OwnedSettingsFixture(initial)
        val models = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val history = object : HubSettingsRepository {
            override val history = flowOf(Outcome.Success(InvitationHistory()))
            override fun retryHistory() = Unit
            override suspend fun claimInvitation(now: Instant) = Outcome.Success(InvitationClaim.Suppressed)
        }
        val gateway = object : PhoneWallpaperGateway {
            override val initialBrowsingTarget = checkNotNull(AndroidWallpaperTarget.resolve(context, "night-sakura-wallpaper"))
            override val snapshots = MutableStateFlow<PhoneWallpaperSnapshot?>(null)
            override suspend fun refresh(target: WallpaperTarget): PhoneWallpaperSnapshot = error("No system probe")
        }
        lateinit var vm: HubViewModel
        instrumentation.runOnMainSync {
            vm = HubViewModel(history, gateway, Clock.systemUTC(), fixture.repository("night-sakura-wallpaper")).also { models.put("hub", it) }
        }
        try {
            compose.setContent { LivosphereTheme { HubApp(vm, onExit = {}) } }
            compose.onNodeWithTag("hub-nav-settings").performClick()
            compose.waitUntil(5_000) { vm.wallpaperSettingsUi.value.failure == expected }
            compose.onNodeWithTag("settings-wallpaper-toggle").performClick()
            compose.onNodeWithText(notice).assertIsDisplayed()
            screenshot("$case-notice")
            compose.onNodeWithTag("touch-reactions-control").assertDoesNotExist()
            assertNull(vm.wallpaperSettingsUi.value.touchReactions)
            assertNull(vm.wallpaperSettingsUi.value.motion)
            assertEquals(initial, fixture.file.readText())
            // Explicit test-only repair of an isolated file after failed initialization; no second DataStore writer.
            compose.onNodeWithTag("hub-nav-theme").performClick()
            fixture.file.writeText(healthy)
            compose.onNodeWithTag("hub-nav-settings").performClick()
            compose.waitUntil(5_000) {
                vm.wallpaperSettingsUi.value == WallpaperSettingsUiState(true, WallpaperMotionMode.NORMAL, effectLevel = app.livosphere.contract.WallpaperEffectLevel.FULL)
            }
            if (compose.onAllNodesWithTag("touch-reactions-control").fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithTag("settings-wallpaper-toggle").performClick()
            }
            compose.onNodeWithText(notice).assertDoesNotExist()
            compose.onNodeWithTag("touch-reactions-control").assertIsDisplayed()
            screenshot("$case-recovered")
            assertEquals(healthy, fixture.file.readText())
        } finally {
            instrumentation.runOnMainSync { models.clear() }
            runBlocking { fixture.close() }
        }
    }
    private fun screenshot(state: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = java.io.File(instrumentation.targetContext.filesDir, "evidence").apply { mkdirs() }
            java.io.File(directory, "story83-$state.png").outputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally { bitmap.recycle() }
    }

}
