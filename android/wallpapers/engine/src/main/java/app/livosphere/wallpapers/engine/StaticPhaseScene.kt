package app.livosphere.wallpapers.engine

import app.livosphere.contract.DayPhase
import kotlin.math.max

data class SourceCrop(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Four immutable plates and the platform-neutral aspect-fill crop used by static wallpapers. */
class StaticPhaseScene(private val plates: Map<DayPhase, Int>) {
    init {
        require(plates.keys == DayPhase.entries.toSet()) { "Static phase scene requires four plates" }
        require(plates.values.all { it > 0 }) { "Static phase plate resource IDs must be positive" }
        require(plates.values.distinct().size == DayPhase.entries.size) { "Static phase plates must be distinct resources" }
    }

    fun plate(phase: DayPhase): Int = plates.getValue(phase)

    fun aspectFillCrop(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): SourceCrop {
        require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0) {
            "Aspect-fill dimensions must be positive"
        }
        val scale = max(targetWidth.toFloat() / sourceWidth, targetHeight.toFloat() / sourceHeight)
        val visibleWidth = targetWidth / scale
        val visibleHeight = targetHeight / scale
        val left = (sourceWidth - visibleWidth) / 2f
        val top = (sourceHeight - visibleHeight) / 2f
        return SourceCrop(left, top, left + visibleWidth, top + visibleHeight)
    }
}
