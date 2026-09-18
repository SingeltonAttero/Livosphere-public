package app.livosphere.wallpapers.neon

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.LruCache
import android.view.SurfaceHolder
import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.*
import kotlin.math.*

data class NeonFrame(
    val phase: DayPhase,
    val previousPhase: DayPhase,
    val phaseChangedAt: Long,
    val elapsed: Long,
    val motion: EffectiveMotion,
    val interaction: NeonInteractionFrame = NeonInteractionFrame(),
)

/** Product-owned Canvas composition; wallpaper scheduling stays in the shared engine. */
class NeonSceneRenderer(
    private val context: Context,
    private val theme: NeonTheme,
    private val holder: SurfaceHolder,
    private val state: () -> NeonFrame,
) : WallpaperRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blinkLayer = NeonBlinkLayer(context, theme)
    private val texture = cloudTexture(context, theme)
    private val skyMask = skyMask(theme)
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val images = object : LruCache<DayPhase, Bitmap>(2) {
        override fun entryRemoved(evicted: Boolean, key: DayPhase, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue) oldValue.recycle()
        }
    }

    override fun render(timeMillis: Long, reducedMotion: Boolean): WallpaperFrameResult {
        if (!holder.surface.isValid) return WallpaperFrameResult.INVALID
        val canvas = try { holder.lockCanvas() } catch (_: RuntimeException) { return WallpaperFrameResult.RETRY }
            ?: return WallpaperFrameResult.RETRY
        var result = WallpaperFrameResult.DRAWN
        try { draw(canvas, state(), timeMillis) }
        finally { try { holder.unlockCanvasAndPost(canvas) } catch (_: RuntimeException) { result = WallpaperFrameResult.RETRY } }
        return result
    }

    fun draw(canvas: Canvas, frame: NeonFrame, now: Long = SystemClock.elapsedRealtime()) {
        val current = image(frame.phase)
        if (!frame.motion.staticFrame) blinkLayer.prepare(frame.phase)
        val scale = max(canvas.width.toFloat() / current.width, canvas.height.toFloat() / current.height)
        val width = current.width * scale
        val height = current.height * scale
        val left = (canvas.width - width) / 2f
        val top = (canvas.height - height) / 2f
        val destination = RectF(left, top, left + width, top + height)
        paint.alpha = 255; paint.colorFilter = null
        canvas.drawColor(Color.BLACK)
        // Never cross-fade baked night emitters into day/dawn.
        val blendAllowed = !frame.motion.staticFrame && frame.phase != DayPhase.DAY && frame.phase != DayPhase.MORNING
        val progress = ((now - frame.phaseChangedAt) / 6000f).coerceIn(0f, 1f)
        if (blendAllowed && progress < 1f && frame.previousPhase != frame.phase) {
            canvas.drawBitmap(image(frame.previousPhase), null, destination, paint)
            blinkLayer.draw(canvas, frame.previousPhase, destination, frame.interaction.blink)
            paint.alpha = (progress * 255).roundToInt()
        }
        canvas.drawBitmap(current, null, destination, paint)
        blinkLayer.draw(canvas, frame.phase, destination, frame.interaction.blink * paint.alpha / 255f)
        paint.alpha = 255
        canvas.save()
        canvas.translate(left, top); canvas.scale(width, height)
        drawClouds(canvas, frame)
        drawLights(canvas, frame, now)
        canvas.restore()
    }

    private fun image(phase: DayPhase): Bitmap = images[phase] ?: run {
        val name = theme.resourcePrefix + "_phase_" + phase.name.lowercase()
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        require(id != 0) { "Missing authored environment: $name" }
        val bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, id,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
        images.put(phase, bitmap)
        bitmap
    }

    private fun drawClouds(canvas: Canvas, frame: NeonFrame) {
        if (frame.motion.effectiveLevel == null) return
        canvas.saveLayer(0f, 0f, 1f, .32f, null)
        val color = when (frame.phase) {
            DayPhase.MORNING -> Color.rgb(244, 224, 224)
            DayPhase.DAY -> Color.rgb(252, 253, 255)
            DayPhase.EVENING -> Color.rgb(247, 188, 179)
            DayPhase.NIGHT -> Color.rgb(108, 132, 180)
        }
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)
        val elapsed = frame.elapsed / 1000f
        val layers = if (frame.motion.allowedEffectIds.contains("near-clouds")) 2 else 1
        repeat(layers) { layer ->
            val speed = when (theme) {
                NeonTheme.SAKURA -> if (layer == 0) .0012f else .0022f
                NeonTheme.HARBOR -> if (layer == 0) .0009f else .0016f
                NeonTheme.SUNSET -> if (layer == 0) .0007f else .0013f
            }
            val tileWidth = if (layer == 0) 1.65f else 1.25f
            val initial = if (layer == 0) .03f else .42f
            val position = ((elapsed * speed * theme.cloudDirection + initial) % tileWidth + tileWidth) % tileWidth
            val y = (if (layer == 0) .025f else if (theme == NeonTheme.HARBOR) .09f else .135f) +
                frame.interaction.cloudOffsetY * if (layer == 0) .55f else 1f
            paint.alpha = if (frame.phase == DayPhase.NIGHT) 90 else 160
            for (tile in -2..1) {
                val x = position + tile * tileWidth + frame.interaction.cloudOffset * if (layer == 0) .55f else 1f
                canvas.drawBitmap(texture, null, RectF(x, y, x + tileWidth, y + .12f), paint)
            }
        }
        paint.colorFilter = null; paint.alpha = 255
        canvas.drawBitmap(skyMask, null, RectF(0f, 0f, 1f, 1f), maskPaint)
        canvas.restore()
    }

    private fun drawLights(canvas: Canvas, frame: NeonFrame, now: Long) {
        if (!NeonScenePolicy.lightsEnabled(frame.phase)) return
        val level = frame.motion.effectiveLevel ?: return
        val groups = NeonScenePolicy.lightGroups(theme)
        val count = NeonScenePolicy.groupCount(level, theme)
        groups.take(count).forEachIndexed { index, group ->
            val (x, y) = group
            val intensity = (NeonScenePolicy.emitterIntensity(frame.phase, frame.elapsed, index, theme, frame.motion) +
                if (frame.interaction.lightGroup == index) frame.interaction.lightBoost else 0f).coerceAtMost(1.3f) *
                NeonScenePolicy.nightEntryGain(frame.phase, frame.previousPhase, now - frame.phaseChangedAt, index, frame.motion.staticFrame)
            glow.color = when (index % 3) { 0 -> Color.rgb(114, 229, 255); 1 -> Color.rgb(247, 130, 212); else -> Color.rgb(244, 205, 148) }
            glow.alpha = (intensity * 190).roundToInt()
            repeat(5) { window ->
                val px = x + window % 2 * .006f
                val py = y + window * .005f
                canvas.drawRoundRect(px, py, px + .0025f, py + .0015f, .0005f, .0005f, glow)
            }
            group.reflection?.let { patch -> drawReflection(canvas, patch, intensity) }
        }
    }

    private fun drawReflection(canvas: Canvas, patch: NeonReflection, intensity: Float) {
        val color = glow.color or (0xff shl 24)
        val alpha = (NeonScenePolicy.reflectionIntensity(intensity) * 190).roundToInt()
        glow.shader = LinearGradient(0f, patch.top, 0f, patch.top + patch.height,
            intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT), floatArrayOf(0f, .18f, 1f), Shader.TileMode.CLAMP)
        glow.alpha = alpha
        // Fixed narrow ripples keep the pulse local; the same envelope drives the source windows.
        repeat(12) { stripe ->
            val fraction = stripe / 12f
            val y = patch.top + fraction * patch.height
            val halfWidth = patch.width * (.3f + .2f * sin(stripe * 2.4f))
            canvas.drawRoundRect(patch.x - halfWidth, y, patch.x + halfWidth, y + patch.height / 24f,
                .001f, .001f, glow)
        }
        glow.shader = null
    }

    override fun close() { images.evictAll(); blinkLayer.close(); texture.recycle(); skyMask.recycle() }

    companion object {
        /** Feather the empty-sky boundary instead of cutting clouds across a visible polygon edge. */
        private fun skyMask(theme: NeonTheme): Bitmap {
        val sky = Path().apply {
            when (theme) {
                NeonTheme.SAKURA -> {
                    moveTo(.49f, 0f); lineTo(1f, 0f); lineTo(1f, .26f)
                    lineTo(.27f, .26f); lineTo(.27f, .20f); lineTo(.43f, .12f)
                }
                NeonTheme.HARBOR -> { moveTo(0f, 0f); lineTo(1f, 0f); lineTo(1f, .18f); lineTo(0f, .18f) }
                NeonTheme.SUNSET -> {
                    moveTo(.35f, 0f); lineTo(1f, 0f); lineTo(1f, .27f)
                    lineTo(.14f, .27f); lineTo(.14f, .15f); lineTo(.32f, .1f)
                }
            }
            close()
        }
            sky.transform(Matrix().apply { setScale(512f, 910f) })
            return Bitmap.createBitmap(512, 910, Bitmap.Config.ARGB_8888).also { bitmap ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
                }
                Canvas(bitmap).drawPath(sky, paint)
            }
        }

        /** Decode once and feather tile edges for continuous drift without a hard seam. */
        private fun cloudTexture(context: Context, theme: NeonTheme): Bitmap {
            val id = context.resources.getIdentifier(theme.resourcePrefix + "_clouds", "drawable", context.packageName)
            val source = requireNotNull(BitmapFactory.decodeResource(context.resources, id,
                BitmapFactory.Options().apply { inScaled = false; inSampleSize = 2 }))
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            val mask = Paint().apply {
                shader = LinearGradient(0f, 0f, source.width.toFloat(), 0f,
                    intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
                    floatArrayOf(0f, .12f, .88f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), mask)
            source.recycle()
            return result
        }
    }
}
