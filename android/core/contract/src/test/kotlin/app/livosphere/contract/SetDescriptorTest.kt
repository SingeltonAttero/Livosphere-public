package app.livosphere.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SetDescriptorTest {
    @Test
    fun `content status exposes both declared manifest states`() {
        assertEquals(
            setOf(ContentStatus.APPROVED_FOR_START, ContentStatus.RELEASE_READY),
            ContentStatus.entries.toSet(),
        )
    }

    @Test
    fun `identifiers reject values outside lower kebab`() {
        assertThrows(IllegalArgumentException::class.java) { SetId("Contour_Draft") }
        assertThrows(IllegalArgumentException::class.java) { ComponentId("preview main") }
    }

    @Test
    fun `revision must be monotonic positive value`() {
        assertThrows(IllegalArgumentException::class.java) { Revision(0) }
        assertEquals(2, Revision(2).value)
    }

    @Test
    fun `resource reference validates checksum and provenance`() {
        val checksum = "a".repeat(64)
        val resource = ResourceReference(
            "preview-frame",
            "drawable/ls_contour_draft_preview_frame.xml",
            checksum,
            Revision(1),
            "local technical placeholder",
        )

        assertEquals(checksum, resource.sha256)
        assertEquals("drawable/ls_contour_draft_preview_frame.xml", resource.resourcePath)
        assertThrows(IllegalArgumentException::class.java) {
            ResourceReference("preview-frame", "drawable/frame.xml", "abc", Revision(1), "local")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceReference("preview-frame", "drawable/frame.xml", checksum, Revision(1), "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceReference("preview-frame", "../drawable/Frame.XML", checksum, Revision(1), "local")
        }
    }

    @Test
    fun `artifact reference validates stable id and Gradle path`() {
        val artifact = ArtifactReference(ArtifactId("contour-phone"), ":hub:app")

        assertEquals(":hub:app", artifact.projectPath)
        assertThrows(IllegalArgumentException::class.java) {
            ArtifactReference(ArtifactId("contour-phone"), "hub/app")
        }
    }

    @Test
    fun `surface constructors enforce platform route settings and resources`() {
        assertThrows(IllegalArgumentException::class.java) {
            preview(Compatibility(Platform.WEAR_OS, 33), InstallRoute.EmbeddedPreview, setOf(SupportedSetting.NONE), resources())
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview(Compatibility(Platform.ANDROID_PHONE, 29), InstallRoute.SystemWallpaperPreview, setOf(SupportedSetting.NONE), resources())
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview(
                Compatibility(Platform.ANDROID_PHONE, 29),
                InstallRoute.EmbeddedPreview,
                setOf(SupportedSetting.NONE, SupportedSetting.TAP),
                resources(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview(Compatibility(Platform.ANDROID_PHONE, 29), InstallRoute.EmbeddedPreview, emptySet(), emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            preview(Compatibility(Platform.ANDROID_PHONE, 29), InstallRoute.EmbeddedPreview, emptySet(), resources())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperContribution(
                ComponentId("wallpaper"), Revision(1), Revision(1),
                Compatibility(Platform.WEAR_OS, 33), InstallRoute.SystemWallpaperPreview,
                emptySet(), ArtifactReference(ArtifactId("phone"), ":wallpapers:example"), resources(),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WatchFaceContribution(
                ComponentId("watchface"), Revision(1), Revision(1),
                Compatibility(Platform.ANDROID_PHONE, 29), InstallRoute.SeparateWatchFacePackage,
                emptySet(), ArtifactReference(ArtifactId("watchface"), ":watchfaces:example"), resources(),
            )
        }
    }

    private fun preview(
        compatibility: Compatibility,
        route: InstallRoute,
        settings: Set<SupportedSetting>,
        resources: List<ResourceReference>,
    ) = PreviewContribution(
        ComponentId("preview"), Revision(1), Revision(1), compatibility, route, settings,
        ArtifactReference(ArtifactId("phone"), ":sets:example:preview"), resources,
    )

    private fun resources() = listOf(
        ResourceReference(
            "preview-frame",
            "drawable/ls_example_preview_frame.xml",
            "a".repeat(64),
            Revision(1),
            "local technical placeholder",
        ),
    )
}
