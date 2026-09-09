package app.livosphere.hub

import app.livosphere.hub.wallpaper.*
import java.time.Instant
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class WallpaperObservationTest {
    private val target = WallpaperComponent("app.livosphere", "app.livosphere.wallpapers.contour.ContourWallpaperService")
    private val at = Instant.parse("2026-09-05T10:00:00Z")

    private inner class Probe : WallpaperPlatformProbe {
        override var deviceApi = 34
        var booleans = true
        var feature: Boolean? = null
        var supported: Boolean? = null
        var allowed: Boolean? = null
        var direct: Boolean? = null
        var chooser: Boolean? = null
        var presence = WallpaperPresence.AVAILABLE
        var home: WallpaperComponent? = target
        var lock: WallpaperComponent? = null
        var failing: WallpaperProbeId? = null
        var exception: Exception = SecurityException("sensitive text must never escape")
        val calls = mutableListOf<WallpaperProbeId>()
        fun <T> call(id: WallpaperProbeId, value: T): T {
            calls += id
            if (failing == id) throw exception
            return value
        }
        override fun hasLiveWallpaperFeature() = call(WallpaperProbeId.FEATURE, feature ?: booleans)
        override fun isWallpaperSupported() = call(WallpaperProbeId.SUPPORTED, supported ?: booleans)
        override fun isSetWallpaperAllowed() = call(WallpaperProbeId.ALLOWED, allowed ?: booleans)
        override fun servicePresence() = call(WallpaperProbeId.SERVICE, presence)
        override fun resolvesDirectPreview() = call(WallpaperProbeId.DIRECT_PREVIEW, direct ?: booleans)
        override fun resolvesChooser() = call(WallpaperProbeId.CHOOSER, chooser ?: booleans)
        override fun appliedComponent(surface: WallpaperSurface) = when (surface) {
            WallpaperSurface.HOME -> call(WallpaperProbeId.HOME, home)
            WallpaperSurface.LOCK -> call(WallpaperProbeId.LOCK, lock)
        }
        fun capture(minApi: Int = 29) = WallpaperObservation.capture(this, target, minApi, at, "contour-wallpaper")
    }

    private fun PhoneWallpaperSnapshot.facts() = listOf(feature, supported, allowed, presence, directPreview, chooser, home, lock)

    @Test fun `capabilities retain true and false independently from application`() {
        for (value in listOf(true, false)) {
            val snapshot = Probe().apply { booleans = value }.capture()
            listOf(snapshot.feature, snapshot.supported, snapshot.allowed, snapshot.directPreview, snapshot.chooser)
                .forEach { assertEquals(WallpaperFact.Known(value), it) }
            assertEquals(WallpaperFact.Known(WallpaperApplication.ACTIVE), snapshot.home)
            assertEquals(WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO), snapshot.lock)
        }
    }

    @Test fun `each failure is target specific sanitized and preserves all other facts`() {
        val baseline = Probe().capture().facts()
        for (id in WallpaperProbeId.entries) {
            val probe = Probe().apply { failing = id }
            val result = probe.capture()
            val fact = result.facts()[id.ordinal]
            assertEquals(WallpaperFact.Unknown(UnknownReason.PROBE_FAILED,
                WallpaperFailure(id, WallpaperFailureCode.ACCESS_DENIED)), fact)
            assertEquals(WallpaperProbeId.entries.toList(), probe.calls)
            baseline.indices.filter { it != id.ordinal }
                .forEach { assertEquals("$id preserving $it", baseline[it], result.facts()[it]) }
            assertFalse(result.toString().contains("sensitive"))
        }
    }

    @Test fun `mixed capability facts remain independent for every boolean combination`() {
        for (bits in 0 until 32) {
            val values = (0..4).map { bits and (1 shl it) != 0 }
            val snapshot = Probe().apply {
                feature = values[0]; supported = values[1]; allowed = values[2]
                direct = values[3]; chooser = values[4]
            }.capture()
            assertEquals(values.map { WallpaperFact.Known(it) }, listOf(
                snapshot.feature, snapshot.supported, snapshot.allowed, snapshot.directPreview, snapshot.chooser))
            assertEquals(WallpaperFact.Known(WallpaperApplication.ACTIVE), snapshot.home)
        }
    }

    @Test fun `unavailable own component preserves proven other component and null uncertainty`() {
        val other = WallpaperComponent("other.package", "other.Service")
        for (presence in WallpaperPresence.entries) for (minimumApi in listOf(29, 35)) {
            for (surface in WallpaperSurface.entries) {
                val snapshot = Probe().apply {
                    this.presence = presence
                    home = if (surface == WallpaperSurface.HOME) other else null
                    lock = if (surface == WallpaperSurface.LOCK) other else null
                }.capture(minimumApi)
                assertEquals(WallpaperFact.Known(WallpaperApplication.INACTIVE),
                    if (surface == WallpaperSurface.HOME) snapshot.home else snapshot.lock)
                assertEquals(WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO),
                    if (surface == WallpaperSurface.HOME) snapshot.lock else snapshot.home)
            }
        }
    }

    @Test fun `full component comparison and independent surfaces`() {
        val variants = listOf(target to WallpaperFact.Known(WallpaperApplication.ACTIVE),
            WallpaperComponent(target.packageName, "other.Service") to WallpaperFact.Known(WallpaperApplication.INACTIVE),
            WallpaperComponent("other.package", target.className) to WallpaperFact.Known(WallpaperApplication.INACTIVE),
            null to WallpaperFact.Unknown(UnknownReason.NO_COMPONENT_INFO))
        for ((home, homeExpected) in variants) for ((lock, lockExpected) in variants) {
            val snapshot = Probe().apply { this.home = home; this.lock = lock }.capture()
            assertEquals(homeExpected, snapshot.home)
            assertEquals(lockExpected, snapshot.lock)
        }
    }

    @Test fun `legacy API never asks ambiguous unflagged application query`() {
        for (api in 29..33) {
            val probe = Probe().apply { deviceApi = api }
            val snapshot = probe.capture()
            assertEquals(WallpaperFact.Unknown(UnknownReason.LEGACY_API), snapshot.home)
            assertEquals(WallpaperFact.Unknown(UnknownReason.LEGACY_API), snapshot.lock)
            assertFalse(probe.calls.contains(WallpaperProbeId.HOME))
            assertFalse(probe.calls.contains(WallpaperProbeId.LOCK))
        }
    }

    @Test fun `missing disabled or incompatible target cannot yield stale ACTIVE`() {
        for (presence in listOf(WallpaperPresence.MISSING, WallpaperPresence.DISABLED)) {
            val snapshot = Probe().apply { this.presence = presence; lock = target }.capture()
            assertEquals(WallpaperFact.Known(presence), snapshot.presence)
            assertEquals(WallpaperFact.Unknown(UnknownReason.COMPONENT_UNAVAILABLE), snapshot.home)
            assertEquals(WallpaperFact.Unknown(UnknownReason.COMPONENT_UNAVAILABLE), snapshot.lock)
        }
        assertEquals(WallpaperFact.Unknown(UnknownReason.COMPONENT_UNAVAILABLE), Probe().capture(35).home)
    }

    @Test fun `route resolutions are independent`() {
        for (direct in listOf(true, false)) for (chooser in listOf(true, false)) {
            val snapshot = Probe().apply { this.direct = direct; this.chooser = chooser }.capture()
            assertEquals(WallpaperFact.Known(direct), snapshot.directPreview)
            assertEquals(WallpaperFact.Known(chooser), snapshot.chooser)
        }
        for (id in listOf(WallpaperProbeId.DIRECT_PREVIEW, WallpaperProbeId.CHOOSER)) {
            val result = Probe().apply { failing = id }.capture()
            assertEquals(WallpaperFact.Known(true), if (id == WallpaperProbeId.CHOOSER) result.directPreview else result.chooser)
        }
    }

    @Test fun `known and unexpected exceptions become safe codes`() {
        for ((exception, code) in listOf(
            UnsupportedOperationException("secret") to WallpaperFailureCode.UNAVAILABLE,
            IllegalArgumentException("secret") to WallpaperFailureCode.INVALID_REQUEST,
            IllegalStateException("secret") to WallpaperFailureCode.PLATFORM_INCIDENT,
        )) {
            val result = Probe().apply { failing = WallpaperProbeId.HOME; this.exception = exception }.capture()
            assertEquals(WallpaperFact.Unknown(UnknownReason.PROBE_FAILED, WallpaperFailure(WallpaperProbeId.HOME, code)), result.home)
        }
    }

    @Test fun `cancellation rethrows from every probe`() {
        for (id in WallpaperProbeId.entries) {
            val cancellation = CancellationException("cancel")
            try {
                Probe().apply { failing = id; exception = cancellation }.capture()
                fail("Cancellation swallowed at $id")
            } catch (actual: CancellationException) { assertSame(cancellation, actual) }
        }
    }
}
