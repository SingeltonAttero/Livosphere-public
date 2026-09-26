package app.livosphere.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDescriptorTest {
    @Test
    fun `content status exposes both declared manifest states`() {
        assertEquals(
            setOf(ContentStatus.APPROVED_FOR_START, ContentStatus.RELEASE_READY, ContentStatus.DRAFT, ContentStatus.IMAGE_APPROVED, ContentStatus.HTML_APPROVED),
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
            WatchFaceContribution(
                ComponentId("watchface"), Revision(1), Revision(1),
                Compatibility(Platform.ANDROID_PHONE, 29), InstallRoute.SeparateWatchFacePackage,
                setOf(SupportedSetting.NONE), ArtifactReference(ArtifactId("watchface"), ":watchfaces:example"), resources(),
            )
        }
    }

    @Test
    fun `wallpaper rejects each invalid platform route or setting with a valid service identity`() {
        val valid = phoneSet().wallpaper
        assertEquals("test.phone.WallpaperService", valid.serviceClassName)
        val invalidPlatform = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(compatibility = Compatibility(Platform.WEAR_OS, 33))
        }
        assertEquals("Wallpaper requires ANDROID_PHONE with SystemWallpaperPreview", invalidPlatform.message)
        val invalidRoute = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(installRoute = InstallRoute.EmbeddedPreview)
        }
        assertEquals("Wallpaper requires ANDROID_PHONE with SystemWallpaperPreview", invalidRoute.message)
        val emptySettings = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(supportedSettings = emptySet())
        }
        assertEquals("wallpaper supportedSettings must not be empty; use NONE explicitly", emptySettings.message)
        val conflictingSettings = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(supportedSettings = setOf(SupportedSetting.NONE, SupportedSetting.TAP))
        }
        assertEquals("wallpaper NONE setting cannot be combined with other settings", conflictingSettings.message)
        val emptyResources = assertThrows(IllegalArgumentException::class.java) {
            valid.copy(resources = emptyList(), phaseRefs = emptyMap(), effectsRefs = emptyList(), sceneRef = null)
        }
        assertEquals("wallpaper resources must not be empty", emptyResources.message)
    }

    @Test
    fun `phone descriptor rejects shared resource paths across otherwise independent contributions`() {
        val set = phoneSet()
        val duplicatePath = set.clockWidget!!.resources.first().resourcePath
        val wallpaper = set.wallpaper.copy(resources = set.wallpaper.resources.mapIndexed { index, ref ->
            if (index == 0) ref.copy(resourcePath = duplicatePath) else ref
        })
        val failure = assertThrows(IllegalArgumentException::class.java) { set.copy(wallpaper = wallpaper) }
        assertEquals("duplicate resource path", failure.message)
    }

    @Test
    fun `complete phone declaration has independent wallpaper and widget references without watchface`() {
        val set = phoneSet()
        assertEquals(null, set.watchFace)
        assertEquals(WidgetSize.entries.toSet(), set.clockWidget!!.layouts.keys)
        assertEquals(DayPhase.entries.toSet(), set.wallpaper.phaseRefs.keys)
        assertEquals("test.phone.WallpaperService", set.wallpaper.serviceClassName)
        assertEquals(false, set.releaseEligible)
    }

    @Test
    fun `phone schema rejects incomplete components phases previews and declarations`() {
        val set = phoneSet()
        assertThrows(IllegalArgumentException::class.java) { set.copy(schemaVersion = 3) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(clockWidget = null) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(contentStatus = ContentStatus.RELEASE_READY) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(wallpaper = set.wallpaper.copy(phaseRefs = emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(wallpaper = set.wallpaper.copy(effectsRefs = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(wallpaper = set.wallpaper.copy(sceneRef = null)) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(preview = set.preview.copy(widgetRefs = emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(distribution = Distribution.PUBLIC) }
        assertThrows(IllegalArgumentException::class.java) { set.clockWidget!!.copy(layouts = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { set.wallpaper.copy(serviceClassName = "RelativeClass") }
        assertThrows(IllegalArgumentException::class.java) { set.wallpaper.copy(effectsRefs = listOf("undeclared")) }
    }

    @Test
    fun `approval integrity references gate candidate composition without implying physical acceptance`() {
        val fixture = phoneSet()
        val widget = fixture.clockWidget!!
        val native = widget.copy(
            resources = widget.resources.map { it.copy(resourcePath = it.resourcePath.replace("raw/", "layout/")) },
            layoutStatus = WidgetLayoutStatus.NATIVE,
        )
        val approval = ApprovalReference("approvals/image.md", Revision(1), Revision(1), "a".repeat(64))
        val candidate = fixture.copy(
            clockWidget = native,
            distribution = Distribution.PUBLIC,
            contentStatus = ContentStatus.HTML_APPROVED,
            sourceAssetsRevision = Revision(2),
            approvals = mapOf(ApprovalStage.IMAGE to approval, ApprovalStage.HTML to approval.copy(record = "approvals/html.md")),
        )
        assertEquals(true, candidate.releaseEligible)
        assertThrows(IllegalArgumentException::class.java) {
            candidate.copy(contentStatus = ContentStatus.IMAGE_APPROVED, approvals = mapOf(ApprovalStage.IMAGE to approval))
        }
        assertThrows(IllegalArgumentException::class.java) {
            candidate.copy(contentStatus = ContentStatus.DRAFT, approvals = emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) { candidate.copy(approvals = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { candidate.copy(sourceAssetsRevision = Revision(1), approvals = candidate.approvals.mapValues { it.value.copy(sourceAssetsRevision = Revision(3)) }) }
        assertThrows(IllegalArgumentException::class.java) { approval.copy(record = "../outside.md") }
        assertThrows(IllegalArgumentException::class.java) { approval.copy(record = "source-assets/approval.md") }
        assertThrows(IllegalArgumentException::class.java) { approval.copy(sha256 = "bad") }
    }

    @Test
    fun `legacy schema is explicit debug only and cannot claim phone completeness`() {
        val phone = phoneSet()
        val legacy = phone.copy(
            schemaVersion = 1,
            contentStatus = ContentStatus.RELEASE_READY,
            clockWidget = null,
            watchFace = WatchFaceContribution(
                ComponentId("legacy-watchface"), Revision(1), Revision(1), Compatibility(Platform.WEAR_OS, 33),
                InstallRoute.SeparateWatchFacePackage, setOf(SupportedSetting.NONE),
                ArtifactReference(ArtifactId("legacy-watchface"), ":watchfaces:legacy"),
                listOf(ResourceReference("legacy-watchface", "raw/watchface.xml", "a".repeat(64), Revision(1), "legacy fixture")),
            ),
        )
        assertEquals(false, legacy.releaseEligible)
        assertThrows(IllegalArgumentException::class.java) { legacy.copy(distribution = Distribution.PUBLIC) }
        assertThrows(IllegalArgumentException::class.java) { legacy.copy(watchFace = null) }
    }

    @Test
    fun `static wallpaper only schema requires four phase plates and no clock surfaces`() {
        val phone = phoneSet()
        val wallpaper = phone.wallpaper.copy(
            resources = phone.wallpaper.resources.filterNot { it.symbolicName in setOf("wallpaper-scene", "wallpaper-effects") },
            effectsRefs = emptyList(),
            sceneRef = null,
        )
        val preview = phone.preview.copy(widgetRefs = emptyMap())
        val static = phone.copy(schemaVersion = 3, wallpaper = wallpaper, preview = preview, clockWidget = null)

        assertEquals(3, static.schemaVersion)
        assertEquals(DayPhase.entries.toSet(), static.wallpaper.phaseRefs.keys)
        assertEquals(null, static.clockWidget)
        assertEquals(false, static.releaseEligible)

        assertThrows(IllegalArgumentException::class.java) { static.copy(distribution = Distribution.PUBLIC) }
        assertThrows(IllegalArgumentException::class.java) { static.copy(clockWidget = phone.clockWidget) }
        assertThrows(IllegalArgumentException::class.java) { static.copy(wallpaper = wallpaper.copy(phaseRefs = emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { static.copy(wallpaper = wallpaper.copy(sceneRef = "wallpaper-morning")) }
        assertThrows(IllegalArgumentException::class.java) { static.copy(preview = preview.copy(widgetRefs = WidgetSize.entries.associateWith { "preview-wallpaper" })) }
        assertThrows(IllegalArgumentException::class.java) { static.copy(wallpaper = wallpaper.copy(previewRef = "missing")) }
    }

    @Test
    fun `schema4 static clock accepts approved native roles and rejects incomplete compositions`() {
        val phone = phoneSet()
        val wallpaper = phone.wallpaper.copy(
            resources = phone.wallpaper.resources.filterNot { it.symbolicName in setOf("wallpaper-scene", "wallpaper-effects") },
            effectsRefs = emptyList(),
            sceneRef = null,
        )
        val native = phone.clockWidget!!.copy(
            resources = phone.clockWidget.resources.map { it.copy(resourcePath = it.resourcePath.replace("raw/", "layout/")) },
            layoutStatus = WidgetLayoutStatus.NATIVE,
            displayName = "Часы",
            viewRoles = mapOf(
                WidgetSize.S to mapOf(ClockViewRole.ROOT to "root", ClockViewRole.TIME to "time"),
                WidgetSize.M to mapOf(ClockViewRole.ROOT to "root_m", ClockViewRole.TIME to "time_m", ClockViewRole.DATE to "date_m"),
                WidgetSize.L to mapOf(ClockViewRole.ROOT to "root_l", ClockViewRole.HOURS to "hours_l", ClockViewRole.MINUTES to "minutes_l", ClockViewRole.DAY to "day_l", ClockViewRole.MONTH to "month_l", ClockViewRole.WEEKDAY to "weekday_l"),
            ),
        )
        val approval = ApprovalReference("approvals/image.md", Revision(1), Revision(1), "a".repeat(64))
        val staticClock = phone.copy(
            schemaVersion = 4,
            wallpaper = wallpaper,
            clockWidget = native,
            contentStatus = ContentStatus.HTML_APPROVED,
            approvals = mapOf(ApprovalStage.IMAGE to approval, ApprovalStage.HTML to approval.copy(record = "approvals/html.md")),
        )

        assertEquals(4, staticClock.schemaVersion)
        assertEquals(WidgetLayoutStatus.NATIVE, staticClock.clockWidget!!.layoutStatus)
        assertTrue(ClockViewRole.DATE in staticClock.clockWidget!!.viewRoles.getValue(WidgetSize.M).keys)

        val publicStaticClock = staticClock.copy(distribution = Distribution.PUBLIC)
        assertEquals(true, publicStaticClock.releaseEligible)
        assertEquals(false, staticClock.releaseEligible)
        assertThrows(IllegalArgumentException::class.java) { staticClock.copy(approvals = emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { staticClock.copy(wallpaper = wallpaper.copy(sceneRef = "wallpaper-morning")) }
        assertThrows(IllegalArgumentException::class.java) { staticClock.copy(preview = staticClock.preview.copy(widgetRefs = emptyMap())) }
        assertThrows(IllegalArgumentException::class.java) { staticClock.copy(clockWidget = native.copy(layoutStatus = WidgetLayoutStatus.TEST_DECLARATION)) }
        assertThrows(IllegalArgumentException::class.java) { staticClock.copy(clockWidget = native.copy(displayName = "")) }
        assertThrows(IllegalArgumentException::class.java) {
            staticClock.copy(clockWidget = native.copy(viewRoles = native.viewRoles + (WidgetSize.S to mapOf(ClockViewRole.ROOT to "root", ClockViewRole.TIME to "time", ClockViewRole.HOURS to "hours"))))
        }
    }

    private fun phoneSet(): SetDescriptor {
        fun resource(surface: String, role: String, directory: String = "raw", extension: String = "xml") = ResourceReference(
            "$surface-$role", "$directory/ls_phone_${surface.replace('-', '_')}_$role.$extension", "a".repeat(64), Revision(1),
            "project-authored test declaration; not native or accepted art",
        )
        val previewResources = listOf("wallpaper", "s", "m", "l").map { resource("preview", it, "drawable-nodpi", "png") }
        val wallpaperResources = listOf("scene", "morning", "day", "evening", "night", "effects").map { resource("wallpaper", it) }
        val widgetResources = listOf("s", "m", "l").map { resource("clock-widget", it) }
        val compatibility = Compatibility(Platform.ANDROID_PHONE, 29)
        val artifact = ArtifactReference(ArtifactId("phone"), ":sets:phone")
        return SetDescriptor(
            schemaVersion = 2, setId = SetId("phone"), setRevision = Revision(1), sourceAssetsRevision = Revision(1),
            contentStatus = ContentStatus.DRAFT,
            preview = PreviewContribution(
                ComponentId("phone-preview"), Revision(1), Revision(1), compatibility, InstallRoute.EmbeddedPreview,
                setOf(SupportedSetting.NONE), artifact, previewResources, "preview-wallpaper",
                WidgetSize.entries.associateWith { "preview-${it.name.lowercase()}" },
            ),
            wallpaper = WallpaperContribution(
                ComponentId("phone-wallpaper"), Revision(1), Revision(1), compatibility, InstallRoute.SystemWallpaperPreview,
                setOf(SupportedSetting.NONE), artifact, wallpaperResources, "test.phone.WallpaperService",
                DayPhase.entries.associateWith { "wallpaper-${it.name.lowercase()}" }, listOf("wallpaper-effects"),
                "wallpaper-scene", "preview-wallpaper",
            ),
            clockWidget = ClockWidgetContribution(
                ComponentId("phone-widget"), Revision(1), Revision(1), compatibility, InstallRoute.SystemWidgetPin,
                setOf(SupportedSetting.NONE), artifact, widgetResources, ClockStyle.DIGITAL,
                WidgetSize.entries.associateWith { "clock-widget-${it.name.lowercase()}" }, WidgetLayoutStatus.TEST_DECLARATION,
            ),
        )
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
