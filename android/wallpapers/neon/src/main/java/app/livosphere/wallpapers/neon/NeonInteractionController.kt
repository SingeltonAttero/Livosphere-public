package app.livosphere.wallpapers.neon

import app.livosphere.contract.DayPhase
import app.livosphere.wallpapers.engine.AuthoredEffectLevel
import kotlin.math.*

data class NeonPoint(val x: Float, val y: Float)
data class NeonInteractionFrame(val blink: Float = 0f, val cloudOffset: Float = 0f,
    val lightGroup: Int = -1, val lightBoost: Float = 0f)

/** Matches the renderer's centre crop. Scene coordinates never depend on the launcher's icon grid. */
object NeonCoordinates {
    fun point(x: Float, y: Float, width: Int, height: Int): NeonPoint? {
        if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite() || x !in 0f..width.toFloat() || y !in 0f..height.toFloat()) return null
        val scale = max(width / 941f, height / 1672f)
        return NeonPoint((x - (width - 941 * scale) / 2) / (941 * scale),
            (y - (height - 1672 * scale) / 2) / (1672 * scale))
    }
}

/** Short per-Engine responses. No event queue, timers or Android objects are retained here. */
class NeonInteractionController(private val theme: NeonTheme, private val touchSlop: Float = 12f) {
    private var allowed = false
    private var phase = DayPhase.DAY
    private var level = AuthoredEffectLevel.FULL
    private var width = 0
    private var height = 0
    private data class Gesture(val x: Float, val y: Float, val at: Long, var moved: Boolean = false, var swiped: Boolean = false)
    private var gesture: Gesture? = null
    private var blinkAt: Long? = null
    private var lightAt: Long? = null
    private var lightGroup = -1
    private var lastBlink: Long? = null
    private var lastLight: Long? = null
    private var lastTouch: Long? = null
    private var lastOffset: Float? = null
    private var cloudAt = 0L
    private var cloudFrom = 0f
    private var cloudTarget = 0f
    private var holding = false
    var blinks = 0L; private set
    var swipes = 0L; private set
    var pulses = 0L; private set

    fun configure(enabled: Boolean, phase: DayPhase, level: AuthoredEffectLevel, width: Int, height: Int) {
        if (!enabled || !allowed || this.phase != phase || this.level != level || this.width != width || this.height != height) cancel()
        allowed = enabled && width > 0 && height > 0
        this.phase = phase; this.level = level; this.width = width; this.height = height
    }
    fun cancel() {
        gesture = null; blinkAt = null; lightAt = null; lightGroup = -1
        cloudFrom = 0f; cloudTarget = 0f; holding = false; lastOffset = null
        lastBlink = null; lastLight = null; lastTouch = null
    }
    fun pointerCancel(now: Long) { gesture = null; releaseCloud(now); lastTouch = now }
    fun down(x: Float, y: Float, now: Long) {
        gesture = if (allowed && NeonCoordinates.point(x, y, width, height) != null) Gesture(x, y, now) else null
        lastTouch = now
    }
    fun move(x: Float, y: Float, now: Long) {
        val g = gesture ?: return
        if (!x.isFinite() || !y.isFinite()) { pointerCancel(now); return }
        val dx = x - g.x; val dy = y - g.y
        if (hypot(dx, dy) > touchSlop) g.moved = true
        if (g.moved && abs(dx) > abs(dy)) {
            if (!g.swiped) { g.swiped = true; swipes++ }
            aimCloud((dx / width * .055f).coerceIn(-MAX_SHIFT, MAX_SHIFT), now, true)
        }
        lastTouch = now
    }
    fun up(x: Float, y: Float, now: Long) {
        val g = gesture ?: return
        move(x, y, now) // A launcher may coalesce every MOVE; UP still cannot become a false tap.
        gesture = null; releaseCloud(now); lastTouch = now
        if (!allowed || g.moved || now - g.at !in 0L..350L) return
        val point = NeonCoordinates.point(x, y, width, height) ?: return
        if (isCharacter(point)) {
            if (lastBlink == null || now - checkNotNull(lastBlink) >= COOLDOWN) {
                blinkAt = now; lastBlink = now; blinks++
            }
        } else if (phase == DayPhase.NIGHT && isCity(point) && (lastLight == null || now - checkNotNull(lastLight) >= COOLDOWN)) {
            val groups = NeonScenePolicy.lightGroups(theme).take(NeonScenePolicy.groupCount(level, theme))
            lightGroup = groups.indices.minBy { hypot(groups[it].x - point.x, groups[it].y - point.y) }
            lightAt = now; lastLight = now; pulses++
        }
    }
    fun offset(value: Float, step: Float, now: Long) {
        if (!allowed || !value.isFinite() || !step.isFinite() || value !in 0f..1f || step <= 0f) { lastOffset = null; return }
        val previous = lastOffset; lastOffset = value
        if (previous == null || gesture != null) return
        // Launcher settling can continue after UP. Suppress that entire offset burst.
        if (lastTouch != null && now - checkNotNull(lastTouch) < 300) { lastTouch = now; return }
        val delta = value - previous
        if (abs(delta) < .0001f) return
        val amount = (frame(now).cloudOffset + delta * .08f).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        aimCloud(amount, now, false); swipes++
    }
    private fun aimCloud(target: Float, now: Long, hold: Boolean) {
        cloudFrom = cloudValue(now); cloudAt = now; cloudTarget = target; holding = hold
    }
    private fun releaseCloud(now: Long) {
        if (!holding) return
        aimCloud(cloudTarget, now, false)
    }
    private fun cloudValue(now: Long): Float {
        val t = (now - cloudAt).coerceAtLeast(0)
        val rise = (t / 90f).coerceIn(0f, 1f)
        val reached = cloudFrom + (cloudTarget - cloudFrom) * (1 - (1 - rise).pow(3))
        if (holding || t <= 90) return reached
        val fall = ((t - 90) / 900f).coerceIn(0f, 1f)
        return cloudTarget * (1 - fall).pow(3)
    }
    fun frame(now: Long): NeonInteractionFrame {
        if (!allowed) return NeonInteractionFrame()
        val blink = blinkAt?.let { start ->
            when (val t = now - start) {
                in 0L..50L -> t / 50f
                in 51L..120L -> 1f
                in 121L..239L -> 1f - (t - 120) / 120f
                else -> 0f
            }
        } ?: 0f
        val boost = lightAt?.let { start ->
            val t = now - start
            if (t in 0L..1199L) sin(t / 1200.0 * PI).toFloat() * .4f else 0f
        } ?: 0f
        return NeonInteractionFrame(blink, cloudValue(now), if (boost > 0) lightGroup else -1, boost)
    }
    fun isCharacter(p: NeonPoint): Boolean {
        val polygon = when (theme) {
            NeonTheme.SAKURA -> listOf(.36f to .37f,.48f to .405f,.48f to .47f,.53f to .48f,.49f to .59f,.59f to .71f,.72f to .87f,.60f to .885f,.48f to .78f,.40f to .66f,.28f to .61f,.24f to .51f,.28f to .43f)
            NeonTheme.HARBOR -> listOf(.47f to .35f,.59f to .36f,.61f to .43f,.67f to .51f,.65f to .64f,.80f to .84f,.69f to .875f,.63f to .90f,.58f to .87f,.57f to .79f,.49f to .72f,.48f to .63f,.42f to .57f,.36f to .51f,.39f to .44f,.45f to .42f)
            NeonTheme.SUNSET -> listOf(.30f to .30f,.43f to .30f,.45f to .40f,.50f to .44f,.55f to .52f,.64f to .535f,.64f to .60f,.62f to .69f,.67f to .76f,.83f to .86f,.76f to .88f,.65f to .82f,.55f to .74f,.48f to .71f,.46f to .60f,.32f to .62f,.24f to .59f,.10f to .595f,.08f to .58f,.20f to .55f,.19f to .48f,.23f to .41f)
        }
        var inside = false; var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[j]
            if ((a.second > p.y) != (b.second > p.y) && p.x < (b.first - a.first) * (p.y - a.second) / (b.second - a.second) + a.first) inside = !inside
            j = i
        }
        return inside
    }
    private fun isCity(p: NeonPoint) = when (theme) {
        NeonTheme.SAKURA -> p.x in .28f..1f && p.y in .28f.. .52f
        NeonTheme.HARBOR -> p.x in .07f..1f && p.y in .18f.. .42f
        NeonTheme.SUNSET -> p.x in 0f..1f && p.y in .32f.. .52f
    }
    companion object { const val MAX_SHIFT = .014f; const val COOLDOWN = 1400L }
}
