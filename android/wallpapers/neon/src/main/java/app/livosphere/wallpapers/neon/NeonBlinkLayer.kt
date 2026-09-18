package app.livosphere.wallpapers.neon

import android.content.Context
import android.graphics.*
import android.util.LruCache
import app.livosphere.contract.DayPhase
import kotlin.math.roundToInt

/** Phase-matched authored eyelids. Only small feathered eye patches are kept in memory. */
internal class NeonBlinkLayer(private val context: Context, private val theme: NeonTheme) {
    private data class Eye(val x: Float, val y: Float, val rx: Float, val ry: Float, val angle: Float)
    private val eyes = when (theme) {
        NeonTheme.SAKURA -> listOf(Eye(386f, 704f, 17f, 10f, 25f), Eye(414f, 719f, 15f, 9f, 25f))
        NeonTheme.HARBOR -> listOf(Eye(496f, 654f, 18f, 11f, 25f), Eye(529f, 672f, 16f, 10f, 25f))
        NeonTheme.SUNSET -> listOf(Eye(374f, 575f, 23f, 11f, 0f))
    }
    private val region = when (theme) {
        NeonTheme.SAKURA -> Rect(363, 686, 437, 739)
        NeonTheme.HARBOR -> Rect(471, 636, 553, 690)
        NeonTheme.SUNSET -> Rect(344, 558, 404, 592)
    }
    private val cache = object : LruCache<DayPhase, Bitmap>(2) {
        override fun entryRemoved(evicted: Boolean, key: DayPhase, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue) oldValue.recycle()
        }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mask = Bitmap.createBitmap(region.width(), region.height(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, .78f, 1f), Shader.TileMode.CLAMP)
        }
        eyes.forEach { eye ->
            canvas.save(); canvas.translate(eye.x - region.left, eye.y - region.top)
            canvas.rotate(eye.angle); canvas.scale(eye.rx, eye.ry)
            canvas.drawCircle(0f, 0f, 1f, soft); canvas.restore()
        }
    }
    fun prepare(phase: DayPhase): Bitmap = cache[phase] ?: run {
        val name = theme.resourcePrefix + "_blink_" + phase.name.lowercase()
        val resource = context.resources.getIdentifier(name, "drawable", context.packageName)
        require(resource != 0) { "Missing authored blink frame: $name" }
        val decoded = context.resources.openRawResource(resource).use { stream ->
            @Suppress("DEPRECATION")
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
            try {
                require(decoder.width == 941 && decoder.height == 1672) { "Blink frame must preserve authored coordinates: $name" }
                requireNotNull(decoder.decodeRegion(region, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }))
            } finally { decoder.recycle() }
        }
        val result = requireNotNull(decoded.copy(Bitmap.Config.ARGB_8888, true)); decoded.recycle()
        // The source PNG is opaque; copy() retains that flag even after DST_IN writes alpha.
        result.setHasAlpha(true)
        Canvas(result).drawBitmap(mask, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        cache.put(phase, result); result
    }
    fun draw(canvas: Canvas, phase: DayPhase, destination: RectF, amount: Float) {
        if (amount <= 0f) return
        paint.alpha = (amount.coerceIn(0f, 1f) * 255).roundToInt()
        val target = RectF(destination.left + region.left / 941f * destination.width(),
            destination.top + region.top / 1672f * destination.height(),
            destination.left + region.right / 941f * destination.width(),
            destination.top + region.bottom / 1672f * destination.height())
        canvas.drawBitmap(prepare(phase), null, target, paint)
    }
    fun close() { cache.evictAll(); mask.recycle() }
}
