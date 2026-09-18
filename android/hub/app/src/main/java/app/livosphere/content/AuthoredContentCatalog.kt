package app.livosphere.content

import app.livosphere.contract.WidgetLayoutStatus
import app.livosphere.generated.GeneratedSetRegistry

/** Only complete native phone contributions belong in the owner-facing collection. */
object AuthoredContentCatalog {
    val sets get() = GeneratedSetRegistry.sets.filter { it.clockWidget?.layoutStatus == WidgetLayoutStatus.NATIVE }
}
