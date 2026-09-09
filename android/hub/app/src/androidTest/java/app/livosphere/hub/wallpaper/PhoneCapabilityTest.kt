package app.livosphere.hub.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.wallpaper.WallpaperService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Local smoke assertions are separate from physical SP-01 acceptance, which remains UNKNOWN. */
@RunWith(AndroidJUnit4::class)
class PhoneCapabilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val emulator get() = Build.FINGERPRINT.contains("generic", true) ||
        Build.FINGERPRINT.contains("emulator", true) || Build.MODEL.contains("sdk", true) ||
        Build.PRODUCT.contains("sdk", true)
    private val controlledEmulator get() =
        InstrumentationRegistry.getArguments().getString("controlledEmulator") == "true"

    @Test fun recordsIndependentPhoneCapabilities() = runBlocking<Unit> {
        val gateway = AndroidWallpaperGateway(context, Clock.systemUTC())
        assertNull(gateway.snapshots.value)
        val snapshot = gateway.refresh(checkNotNull(gateway.initialBrowsingTarget))
        val report = report("recordsIndependentPhoneCapabilities", snapshot)
        try {
            if (controlledEmulator) assertTrue("Controlled scenario requires an emulator", emulator)
            assertEquals(snapshot, gateway.snapshots.value)
            assertEquals(WallpaperFact.Known(WallpaperPresence.AVAILABLE), snapshot.presence)
            val direct = AndroidWallpaperTarget.directPreviewIntent(checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context)))
            assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, direct.action)
            @Suppress("DEPRECATION")
            assertEquals(AndroidWallpaperTarget.component(checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context))),
                direct.getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT))
            assertEquals(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER, AndroidWallpaperTarget.chooserIntent().action)

            // Explicit subsequent verification reads; these are not values from the gateway capture.
            val verification = JSONObject().put("kind", "SUBSEQUENT_INDEPENDENT_VERIFICATION_READS")
            report.put("verificationReads", verification)
            for (surface in WallpaperSurface.entries) {
                val id = if (surface == WallpaperSurface.HOME) WallpaperProbeId.HOME else WallpaperProbeId.LOCK
                val actual = if (surface == WallpaperSurface.HOME) snapshot.home else snapshot.lock
                if (Build.VERSION.SDK_INT >= 34) {
                    val flag = if (surface == WallpaperSurface.HOME) WallpaperManager.FLAG_SYSTEM else WallpaperManager.FLAG_LOCK
                    val raw = read(id) { WallpaperManager.getInstance(context).getWallpaperInfo(flag)?.component }
                    verification.put(surface.name.lowercase(), raw.json().put("flag", flag))
                    val expected = when {
                        raw.failure != null -> WallpaperFact.Unknown(UnknownReason.PROBE_FAILED, raw.failure)
                        raw.value == null -> WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO)
                        raw.value == AndroidWallpaperTarget.component(checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context))) -> WallpaperFact.Known(WallpaperApplication.ACTIVE)
                        else -> WallpaperFact.Known(WallpaperApplication.INACTIVE)
                    }
                    assertEquals("Independent $surface component comparison", expected, actual)
                } else {
                    verification.put(surface.name.lowercase(), JSONObject().put("verdict", "NOT_QUERIED_LEGACY_API"))
                    assertEquals(WallpaperFact.Unknown(UnknownReason.LEGACY_API), actual)
                }
            }
            for ((id, intent, actual) in listOf(
                Triple(WallpaperProbeId.DIRECT_PREVIEW, direct, snapshot.directPreview),
                Triple(WallpaperProbeId.CHOOSER, AndroidWallpaperTarget.chooserIntent(), snapshot.chooser),
            )) {
                val raw = read(id) { intent.resolveActivity(context.packageManager) }
                verification.put(id.name.lowercase(), raw.json())
                val expected = raw.failure?.let { WallpaperFact.Unknown(UnknownReason.PROBE_FAILED, it) }
                    ?: WallpaperFact.Known(raw.value != null)
                assertEquals("Independent $id handler comparison", expected, actual)
            }
            verifyPackagedService(report)
            if (controlledEmulator) {
                listOf(snapshot.feature, snapshot.supported, snapshot.allowed, snapshot.directPreview, snapshot.chooser)
                    .forEach { assertEquals("Controlled emulator capability", WallpaperFact.Known(true), it) }
                listOf(snapshot.feature, snapshot.supported, snapshot.allowed, snapshot.presence,
                    snapshot.directPreview, snapshot.chooser, snapshot.home, snapshot.lock).forEach {
                    assertFalse("Controlled emulator probe failed", it is WallpaperFact.Unknown && it.reason == UnknownReason.PROBE_FAILED)
                }
            }
            report.put("localVerdict", if (controlledEmulator) "PASS" else "OBSERVATION_ONLY")
        } finally {
            save(report)
        }
    }

    @Test fun disabledServiceIsObservedAndOriginalOverrideRestored() = runBlocking<Unit> {
        org.junit.Assume.assumeTrue("Negative presence scenario requires the controlled emulator", controlledEmulator && emulator)
        val selected = checkNotNull(AndroidWallpaperTarget.resolve(context,
            InstrumentationRegistry.getArguments().getString("disabledWallpaperId") ?: "isolation-fixture-wallpaper"))
        val target = AndroidWallpaperTarget.component(selected)
        val packages = context.packageManager
        val original = packages.getComponentEnabledSetting(target)
        val gateway = AndroidWallpaperGateway(context, Clock.systemUTC())
        val initial = gateway.refresh(selected)
        // App API UNKNOWN remains UNKNOWN. A controlled test uses a separate shell observation
        // to prove that B is not applied before temporarily disabling it; no production inference changes.
        val appliedBefore = systemAppliedComponents()
        org.junit.Assume.assumeTrue("Only a proven inactive fixture may be disabled",
            appliedBefore.isNotEmpty() && target.flattenToString() !in appliedBefore)
        val report = report("disabledServiceIsObservedAndOriginalOverrideRestored", initial)
            .put("originalComponentOverride", original)
            .put("negativeTargetInactivitySource", "CONTROLLED_EMULATOR_DUMPSYS_WALLPAPER")
            .put("appliedComponentsBefore", org.json.JSONArray(appliedBefore))
        try {
            assertEquals(WallpaperFact.Known(WallpaperPresence.AVAILABLE), initial.presence)
            try {
                packages.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                val disabled = gateway.refresh(selected)
                report.put("disabledObservation", snapshot(disabled))
                assertEquals(WallpaperFact.Known(WallpaperPresence.DISABLED), disabled.presence)
                for (application in listOf(disabled.home, disabled.lock)) {
                    assertNotEquals(WallpaperFact.Known(WallpaperApplication.ACTIVE), application)
                }
            } finally {
                packages.setComponentEnabledSetting(target, original, PackageManager.DONT_KILL_APP)
                val recovered = gateway.refresh(selected)
                report.put("recoveredObservation", snapshot(recovered))
                report.put("restoredComponentOverride", packages.getComponentEnabledSetting(target))
                assertEquals(original, packages.getComponentEnabledSetting(target))
                assertEquals(WallpaperFact.Known(WallpaperPresence.AVAILABLE), recovered.presence)
                val appliedAfter = systemAppliedComponents()
                report.put("appliedComponentsAfter", org.json.JSONArray(appliedAfter))
                assertEquals("Disabling B must preserve applied components", appliedBefore, appliedAfter)
            }
            report.put("localVerdict", if (emulator) "PASS" else "OBSERVATION_ONLY")
        } finally {
            save(report)
        }
    }

    private fun systemAppliedComponents(): List<String> {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys wallpaper")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return Regex("mWallpaperComponent=ComponentInfo\\{([^}]+)\\}").findAll(dump).map { it.groupValues[1] }.toList()
    }

    @Suppress("DEPRECATION")
    private fun verifyPackagedService(report: JSONObject) {
        val info = context.packageManager.getServiceInfo(AndroidWallpaperTarget.component(checkNotNull(AndroidWallpaperTarget.initialBrowsingTarget(context))), PackageManager.GET_META_DATA)
        val metadata = info.metaData?.getInt(WallpaperService.SERVICE_META_DATA, 0) ?: 0
        report.put("packagedService", JSONObject().put("exported", info.exported)
            .put("permission", info.permission ?: JSONObject.NULL).put("wallpaperMetadataResource", metadata))
        assertTrue(info.exported)
        assertEquals("android.permission.BIND_WALLPAPER", info.permission)
        assertTrue("Packaged wallpaper metadata missing", metadata != 0)
        context.resources.getXml(metadata).use { parser ->
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.START_TAG && event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                event = parser.next()
            }
            assertEquals("wallpaper", parser.name)
        }
    }

    private data class Read(val value: ComponentName?, val failure: WallpaperFailure?, val observedAt: Instant) {
        fun json() = JSONObject().put("observedAt", observedAt.toString())
            .put("verdict", if (failure == null) "OBSERVED" else "UNKNOWN")
            .put("component", value?.let { JSONObject().put("packageName", it.packageName).put("className", it.className) }
                ?: JSONObject.NULL)
            .put("failure", failure?.let { JSONObject().put("probe", it.probe.name).put("code", it.code.name) } ?: JSONObject.NULL)
    }

    private fun read(id: WallpaperProbeId, query: () -> ComponentName?): Read {
        val at = Instant.now()
        return try {
            Read(query(), null, at)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            val code = when (exception) {
                is SecurityException -> WallpaperFailureCode.ACCESS_DENIED
                is UnsupportedOperationException -> WallpaperFailureCode.UNAVAILABLE
                is IllegalArgumentException -> WallpaperFailureCode.INVALID_REQUEST
                else -> WallpaperFailureCode.PLATFORM_INCIDENT
            }
            Read(null, WallpaperFailure(id, code), at)
        }
    }

    private fun report(testId: String, observation: PhoneWallpaperSnapshot): JSONObject {
        val descriptor = app.livosphere.generated.GeneratedSetRegistry.sets.single { it.wallpaper.componentId.value == observation.wallpaperId }
        val artifact = File(context.applicationInfo.sourceDir)
        val sha = MessageDigest.getInstance("SHA-256")
        artifact.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        val digest = sha.digest().joinToString("") { "%02x".format(it) }
        assertTrue(digest.matches(Regex("[0-9a-f]{64}")))
        assertTrue(artifact.length() > 0)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val launcher = JSONObject().put("observedAt", Instant.now().toString())
        try {
            val component = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivity(context.packageManager)
            launcher.put("verdict", "OBSERVED").put("component", component?.flattenToString() ?: JSONObject.NULL)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            launcher.put("verdict", "UNKNOWN").put("failureCode", "LAUNCHER_METADATA_UNAVAILABLE")
        }
        return snapshot(observation)
            .put("protocolId", "local-capability-smoke-v1")
            .put("scenarioId", if (controlledEmulator) "controlled-emulator-capability" else "device-capability-observation")
            .put("testId", testId).put("runId", UUID.randomUUID().toString())
            .put("localVerdict", "FAIL").put("physicalSp01Verdict", "UNKNOWN")
            .put("deviceVerdict", if (emulator) "EMULATOR_ONLY" else "DEVICE_OBSERVATION_ONLY")
            .put("artifact", JSONObject().put("baseApkSha256", digest).put("baseApkBytes", artifact.length())
                .put("packageName", context.packageName).put("versionCode", packageInfo.longVersionCode)
                .put("versionName", packageInfo.versionName).put("setId", descriptor.setId.value)
                .put("setRevision", descriptor.setRevision.value).put("componentId", descriptor.wallpaper.componentId.value)
                .put("componentRevision", descriptor.wallpaper.componentRevision.value))
            .put("device", JSONObject().put("model", Build.MODEL).put("manufacturer", Build.MANUFACTURER)
                .put("osRelease", Build.VERSION.RELEASE).put("api", Build.VERSION.SDK_INT)
                .put("buildFingerprint", Build.FINGERPRINT).put("launcherVerificationRead", launcher).put("emulator", emulator))
    }

    private fun snapshot(snapshot: PhoneWallpaperSnapshot) = JSONObject()
        .put("observedAt", snapshot.observedAt.toString())
        .put("target", ComponentName(snapshot.component.packageName, snapshot.component.className).flattenToString())
        .put("minimumApi", snapshot.minimumApi).put("compatible", snapshot.compatible)
        .put("feature", fact(snapshot.feature)).put("supported", fact(snapshot.supported))
        .put("allowed", fact(snapshot.allowed)).put("presence", fact(snapshot.presence))
        .put("directPreview", fact(snapshot.directPreview)).put("chooser", fact(snapshot.chooser))
        .put("home", fact(snapshot.home)).put("lock", fact(snapshot.lock))

    private fun fact(fact: WallpaperFact<*>): JSONObject = when (fact) {
        is WallpaperFact.Known -> JSONObject().put("verdict", "KNOWN").put("value", fact.value)
        is WallpaperFact.Unknown -> JSONObject().put("verdict", "UNKNOWN").put("reason", fact.reason.name)
            .put("failure", fact.failure?.let { JSONObject().put("probe", it.probe.name).put("code", it.code.name) } ?: JSONObject.NULL)
    }

    private fun save(report: JSONObject) {
        val latest = File(context.filesDir, REPORT_PATH)
        check(latest.parentFile!!.mkdirs() || latest.parentFile!!.isDirectory)
        val json = report.toString(2)
        File(latest.parentFile, "phone-capability-${report.getString("runId")}.json").writeText(json)
        // Stable file is the primary capability report; each test also has immutable run history.
        if (report.getString("testId") == "recordsIndependentPhoneCapabilities") latest.writeText(json)
        if (report.getString("testId") == "disabledServiceIsObservedAndOriginalOverrideRestored")
            File(latest.parentFile, "phone-capability-disabled-fixture.json").writeText(json)
    }

    companion object {
        const val REPORT_PATH = "evidence/phone-capability-v1.json"
    }
}
