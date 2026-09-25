package app.livosphere.content

import app.livosphere.contract.WidgetLayoutStatus
import app.livosphere.generated.GeneratedSetRegistry

/** Owner-facing projections stay independent when a package contributes only one surface. */
object AuthoredContentCatalog {
    val wallpaperSets get() = GeneratedSetRegistry.sets.filter {
        it.schemaVersion == 3 || it.clockWidget?.layoutStatus == WidgetLayoutStatus.NATIVE
    }
    val clockSets get() = GeneratedSetRegistry.sets.filter {
        it.clockWidget?.layoutStatus == WidgetLayoutStatus.NATIVE
    }

    /** Compatibility for existing callers/tests; new surface code uses an explicit projection. */
    val sets get() = clockSets
}
